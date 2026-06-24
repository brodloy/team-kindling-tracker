package com.teamkindlingtracker.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast by each client describing how much kindling THAT member has added to
 * each brazier this raid. The value is cumulative (idempotent) so missed messages
 * or late joiners self-heal: each client simply sums the latest array from every
 * member to get the per-brazier total.
 *
 * {@code contributions[i]} is the kindling this member has added to brazier index i,
 * where the index is the stable position of the brazier sorted by scene coordinates
 * (identical across all clients in the same room template).
 */
public class BrazierContribMessage extends PartyMemberMessage
{
	private int[] contributions;

	public BrazierContribMessage()
	{
	}

	public BrazierContribMessage(int[] contributions)
	{
		this.contributions = contributions;
	}

	public int[] getContributions()
	{
		return contributions;
	}
}
