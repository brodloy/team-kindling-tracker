package com.teamkindlingtracker.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast by each client with the amount of kindling that member currently holds
 * in their inventory. Used for the party inventory overlay and the above-head counts.
 */
public class KindlingInvMessage extends PartyMemberMessage
{
	private int count;

	public KindlingInvMessage()
	{
	}

	public KindlingInvMessage(int count)
	{
		this.count = count;
	}

	public int getCount()
	{
		return count;
	}
}
