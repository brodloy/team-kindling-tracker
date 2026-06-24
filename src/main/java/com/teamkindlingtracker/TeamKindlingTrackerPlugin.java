package com.teamkindlingtracker;

import com.google.inject.Provides;
import com.teamkindlingtracker.party.BrazierContribMessage;
import com.teamkindlingtracker.party.KindlingInvMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Team Kindling Tracker",
	description = "Tracks kindling added to the four Ice Demon braziers and shares party kindling counts",
	tags = {"cox", "raids", "chambers", "xeric", "ice", "demon", "kindling", "brazier"}
)
public class TeamKindlingTrackerPlugin extends Plugin
{
	/** Maximum braziers we track (the Ice Demon room has four). */
	static final int MAX_BRAZIERS = 4;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TeamKindlingTrackerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BrazierOverlay brazierOverlay;

	@Inject
	private PartyKindlingOverlay partyKindlingOverlay;

	@Inject
	private PlayerKindlingOverlay playerKindlingOverlay;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	// --- Room / brazier state ---------------------------------------------------

	private boolean inRoom = false;

	/** The Ice Demon NPC while it is present in the room. */
	private NPC iceDemon;

	/**
	 * True once the kindling phase is over for this attempt (the demon has thawed/become
	 * attackable or died). While true, the above-head kindling counts are hidden.
	 */
	private boolean kindlingPhaseOver = false;

	/**
	 * Stable, sorted instanced world points of the braziers; index = position in this
	 * list. Instanced world points are invariant across scene reloads and identical for
	 * every party member in the same raid, so the index maps to the same physical brazier
	 * on every client.
	 */
	private final List<WorldPoint> brazierTiles = new ArrayList<>();

	/** Current live brazier objects keyed by instanced world point (for rendering). */
	private final Map<WorldPoint, GameObject> brazierObjs = new HashMap<>();

	// --- Contribution state -----------------------------------------------------

	/** This client's own per-brazier additions this raid. */
	private final int[] localContrib = new int[MAX_BRAZIERS];

	/** Remote members' per-brazier additions, keyed by party member id. */
	private final Map<Long, int[]> remoteContrib = new ConcurrentHashMap<>();

	// --- Inventory state --------------------------------------------------------

	private int localKindling = 0;
	private final Map<Long, Integer> remoteKindling = new ConcurrentHashMap<>();

	/** Cache of item id -> isKindling, to avoid repeated composition lookups. */
	private final Map<Integer, Boolean> kindlingItemCache = new HashMap<>();

	@Provides
	TeamKindlingTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TeamKindlingTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(brazierOverlay);
		overlayManager.add(partyKindlingOverlay);
		overlayManager.add(playerKindlingOverlay);

		wsClient.registerMessage(BrazierContribMessage.class);
		wsClient.registerMessage(KindlingInvMessage.class);

		// Handle being enabled while already inside the room.
		clientThread.invokeLater(this::scanScene);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(brazierOverlay);
		overlayManager.remove(partyKindlingOverlay);
		overlayManager.remove(playerKindlingOverlay);

		wsClient.unregisterMessage(BrazierContribMessage.class);
		wsClient.unregisterMessage(KindlingInvMessage.class);

		resetRoom();
		remoteContrib.clear();
		remoteKindling.clear();
	}

	// ===========================================================================
	// Room detection
	// ===========================================================================

	private boolean inRaid()
	{
		return client.getVarbitValue(Varbits.IN_RAID) == 1;
	}

	private boolean isBrazier(int objectId)
	{
		final ObjectComposition comp = client.getObjectDefinition(objectId);
		if (comp == null)
		{
			return false;
		}
		String name = comp.getName();
		// Some objects expose impostors (lit/unlit variants); resolve if present.
		if (comp.getImpostorIds() != null && comp.getImpostor() != null)
		{
			name = comp.getImpostor().getName();
		}
		return name != null && name.toLowerCase().contains("brazier");
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		// Only track braziers once we're in the Ice Demon room (the demon NPC is present).
		// Other rooms in the raid can contain objects named "brazier" too.
		if (!inRoom)
		{
			return;
		}

		final GameObject obj = event.getGameObject();
		if (isBrazier(obj.getId()))
		{
			handleBrazier(obj);
		}
	}

	/** Find braziers already loaded in the current scene (e.g. plugin enabled mid-room). */
	private void scanScene()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !inRaid())
		{
			return;
		}

		// Enter the room if the demon is already present (plugin enabled mid-room / reload),
		// then only scan braziers while actually in the Ice Demon room.
		maybeEnterRoom();
		if (!inRoom)
		{
			return;
		}

		final net.runelite.api.Scene scene = client.getScene();
		final net.runelite.api.Tile[][][] tiles = scene.getTiles();
		final int plane = client.getPlane();
		for (net.runelite.api.Tile[] row : tiles[plane])
		{
			for (net.runelite.api.Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}
				for (GameObject obj : tile.getGameObjects())
				{
					if (obj != null && isBrazier(obj.getId()))
					{
						handleBrazier(obj);
					}
				}
			}
		}
	}

	private void handleBrazier(GameObject obj)
	{
		final LocalPoint lp = obj.getLocalLocation();
		if (lp == null)
		{
			return;
		}
		final WorldPoint tile = WorldPoint.fromLocalInstance(client, lp);

		brazierObjs.put(tile, obj);
		if (!brazierTiles.contains(tile) && brazierTiles.size() < MAX_BRAZIERS)
		{
			brazierTiles.add(tile);
			brazierTiles.sort(Comparator.<WorldPoint>comparingInt(WorldPoint::getY).thenComparingInt(WorldPoint::getX));
			log.debug("Brazier discovered at world {},{} -> index {}", tile.getX(), tile.getY(), brazierTiles.indexOf(tile));
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		final GameObject obj = event.getGameObject();
		final LocalPoint lp = obj.getLocalLocation();
		if (lp == null)
		{
			return;
		}
		final WorldPoint tile = WorldPoint.fromLocalInstance(client, lp);
		// Only clear the live object if it's the same one (lit/unlit swaps re-add).
		if (brazierObjs.get(tile) == obj)
		{
			brazierObjs.remove(tile);
		}
	}

	private boolean isIceDemon(NPC npc)
	{
		return npc.getName() != null && npc.getName().toLowerCase().contains("ice demon");
	}

	/** Enter the Ice Demon room if the demon NPC is present in the scene. */
	private void maybeEnterRoom()
	{
		if (inRoom)
		{
			return;
		}
		for (NPC npc : client.getNpcs())
		{
			if (isIceDemon(npc))
			{
				iceDemon = npc;
				enterRoom();
				return;
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();
		if (isIceDemon(npc))
		{
			iceDemon = npc;
			log.debug("Ice demon spawned (id {})", npc.getId());
			if (!inRoom)
			{
				enterRoom();
			}
			// Pick up braziers that loaded with the room.
			scanScene();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc() == iceDemon)
		{
			// Only a despawn *while dead* is a real kill. A scene reload despawns and
			// respawns the demon too, and must NOT end the kindling phase.
			final boolean dead = event.getNpc().isDead();
			iceDemon = null;
			if (dead)
			{
				kindlingPhaseOver = true;
				log.debug("Ice demon died - hiding above-head counts");
			}
			else
			{
				log.debug("Ice demon despawned (not dead, likely scene reload)");
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// The demon is encased in ice until thawed; the first real damage on it means it
		// is now attackable, so the kindling phase is over.
		if (!kindlingPhaseOver && iceDemon != null && event.getActor() == iceDemon
			&& event.getHitsplat().getAmount() > 0)
		{
			kindlingPhaseOver = true;
			log.debug("Ice demon took its first hit (thawed) - hiding above-head counts");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Re-log / hop inside the room: GameObjectSpawned won't fire for already-loaded
		// objects, so re-scan. Teardown/reset is handled in onGameTick where the raid
		// varbit is reliably loaded, so a transient load screen never wipes counts.
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			scanScene();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (inRoom && !inRaid())
		{
			resetRoom();
		}

		// Recovery: if we believe we're in the room but have no live brazier objects
		// (e.g. a scene reload whose respawn events we missed), re-scan to repopulate.
		if (inRoom && inRaid() && brazierObjs.isEmpty())
		{
			scanScene();
		}

		// Recovery: re-acquire the demon if a reload despawned it and we missed its respawn,
		// so death detection keeps working. (Won't re-find it once it's actually dead/gone.)
		if (inRoom && !kindlingPhaseOver && iceDemon == null)
		{
			for (NPC npc : client.getNpcs())
			{
				if (isIceDemon(npc))
				{
					iceDemon = npc;
					break;
				}
			}
		}
	}

	private void enterRoom()
	{
		inRoom = true;
		// New raid / new attempt: clear everyone's counts locally and rebroadcast.
		java.util.Arrays.fill(localContrib, 0);
		remoteContrib.clear();
		kindlingPhaseOver = false;
		log.debug("Entered Ice Demon room - counters reset");
		broadcastContrib();
	}

	private void resetRoom()
	{
		inRoom = false;
		brazierTiles.clear();
		brazierObjs.clear();
		iceDemon = null;
		kindlingPhaseOver = false;
	}

	// ===========================================================================
	// Kindling detection
	// ===========================================================================

	private boolean isKindling(int itemId)
	{
		Boolean cached = kindlingItemCache.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		boolean result = false;
		final net.runelite.api.ItemComposition comp = client.getItemDefinition(itemId);
		if (comp != null && comp.getName() != null)
		{
			result = comp.getName().equalsIgnoreCase("Kindling");
		}
		kindlingItemCache.put(itemId, result);
		return result;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final ItemContainer container = event.getItemContainer();
		if (container != client.getItemContainer(InventoryID.INVENTORY))
		{
			return;
		}

		int count = 0;
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && isKindling(item.getId()))
			{
				count += item.getQuantity();
			}
		}

		final int delta = count - localKindling;
		localKindling = count;

		if (delta != 0)
		{
			log.debug("Kindling inventory now {} (delta {})", count, delta);
		}

		// A decrease while in the room means kindling was fed to the brazier we're next to.
		if (delta < 0 && inRoom)
		{
			final int idx = nearestBrazier();
			if (idx != -1)
			{
				localContrib[idx] += -delta;
				log.debug("Added {} kindling to brazier index {} (total {})", -delta, idx, localContrib[idx]);
				broadcastContrib();
			}
			else
			{
				log.debug("Kindling dropped by {} but no nearby brazier found", -delta);
			}
		}

		broadcastKindling();
	}

	/** Index of the brazier closest to the local player, or -1 if none/unknown. */
	private int nearestBrazier()
	{
		final Player local = client.getLocalPlayer();
		if (local == null || local.getLocalLocation() == null || brazierTiles.isEmpty())
		{
			return -1;
		}
		final WorldPoint playerWp = WorldPoint.fromLocalInstance(client, local.getLocalLocation());
		int best = -1;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < brazierTiles.size(); i++)
		{
			final int dist = brazierTiles.get(i).distanceTo(playerWp);
			if (dist < bestDist)
			{
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	// ===========================================================================
	// Party broadcasting / receiving
	// ===========================================================================

	private void broadcastContrib()
	{
		if (partyService.isInParty())
		{
			partyService.send(new BrazierContribMessage(localContrib.clone()));
		}
	}

	private void broadcastKindling()
	{
		if (partyService.isInParty())
		{
			partyService.send(new KindlingInvMessage(localKindling));
		}
	}

	private boolean isSelf(long memberId)
	{
		final PartyMember local = partyService.getLocalMember();
		return local != null && local.getMemberId() == memberId;
	}

	@Subscribe
	public void onBrazierContribMessage(BrazierContribMessage message)
	{
		if (isSelf(message.getMemberId()) || message.getContributions() == null)
		{
			return;
		}
		final int[] copy = new int[MAX_BRAZIERS];
		final int[] in = message.getContributions();
		System.arraycopy(in, 0, copy, 0, Math.min(in.length, MAX_BRAZIERS));
		remoteContrib.put(message.getMemberId(), copy);
	}

	@Subscribe
	public void onKindlingInvMessage(KindlingInvMessage message)
	{
		if (isSelf(message.getMemberId()))
		{
			return;
		}
		remoteKindling.put(message.getMemberId(), message.getCount());
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		// We (re)joined a party session: push our current state so others have it.
		broadcastContrib();
		broadcastKindling();
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		remoteContrib.remove(event.getMemberId());
		remoteKindling.remove(event.getMemberId());
	}

	// ===========================================================================
	// Accessors for overlays
	// ===========================================================================

	boolean isInRoom()
	{
		return inRoom;
	}

	/** Whether the above-head kindling counts should currently show. */
	boolean isAboveHeadsActive()
	{
		return inRoom && !kindlingPhaseOver;
	}

	int getBrazierCount()
	{
		return brazierTiles.size();
	}

	/** Live game object for brazier index, or null if currently despawned. */
	GameObject getBrazierObject(int index)
	{
		if (index < 0 || index >= brazierTiles.size())
		{
			return null;
		}
		return brazierObjs.get(brazierTiles.get(index));
	}

	/** Aggregated party-wide kindling added to a brazier index. */
	int getBrazierTotal(int index)
	{
		int total = (index >= 0 && index < MAX_BRAZIERS) ? localContrib[index] : 0;
		for (int[] contrib : remoteContrib.values())
		{
			if (index < contrib.length)
			{
				total += contrib[index];
			}
		}
		return total;
	}

	/** Display name -> kindling-in-inventory for the local player and all party members. */
	Map<String, Integer> getKindlingByName()
	{
		final Map<String, Integer> out = new HashMap<>();

		final PartyMember local = partyService.getLocalMember();
		if (local != null && local.getDisplayName() != null)
		{
			out.put(Text.sanitize(local.getDisplayName()), localKindling);
		}
		else if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			out.put(Text.sanitize(client.getLocalPlayer().getName()), localKindling);
		}

		for (Map.Entry<Long, Integer> entry : remoteKindling.entrySet())
		{
			final PartyMember m = partyService.getMemberById(entry.getKey());
			if (m != null && m.getDisplayName() != null)
			{
				out.put(Text.sanitize(m.getDisplayName()), entry.getValue());
			}
		}
		return out;
	}
}
