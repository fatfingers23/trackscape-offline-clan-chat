package com.offlineClanChat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OfflineClanChatPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OfflineClanChatPlugin.class);
		RuneLite.main(args);
	}
}