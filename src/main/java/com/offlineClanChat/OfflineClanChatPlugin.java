package com.offlineClanChat;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.PusherEvent;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpAuthorizer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.HashMap;

@Slf4j
@PluginDescriptor(
	name = "Offline Clan Chat"
)
public class OfflineClanChatPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OfflineClanChatConfig config;

	@Inject
	private Gson gson;

	private Pusher PusherClient;

	private PrivateChannel ChatChannel;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			PusherOptions options = new PusherOptions().setCluster("mt1");
			options.setHost("127.0.0.1");
			options.setWsPort(6001);
			options.setWssPort(6001);
			options.setUseTLS(false);
			HttpAuthorizer authorizer = new HttpAuthorizer("http://localhost/broadcasting/auth");
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("Accept", "application/json");
			headers.put("osrs_id", String.valueOf(client.getAccountHash()));
//			headers.put("clan_name", client.getClanSettings().getName());
			authorizer.setHeaders(headers);
			options.setAuthorizer(authorizer);
			PusherClient = new Pusher("app-key", options);
//			headers.put("username", client.getUsername());
			PusherClient.connect(new ConnectionEventListener() {
				@Override
				public void onConnectionStateChange(ConnectionStateChange change) {System.out.println("State changed to " + change.getCurrentState() +
							" from " + change.getPreviousState());
					if(change.getCurrentState() == ConnectionState.CONNECTED){
						System.out.println("woo");
					}
				}

				@Override
				public void onError(String message, String code, Exception e) {
					System.out.println("There was a problem connecting!");
				}
			}, ConnectionState.ALL);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);
		}
	}

	@Provides
	OfflineClanChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OfflineClanChatConfig.class);
	}


	@Subscribe
	public void onChatMessage(ChatMessage event) {
		switch (event.getType()) {

			case CLAN_CHAT:
			case CLAN_MESSAGE:
				ClanChannel clanChannel = client.getClanChannel();
				ChatPayload chatPayload = ChatPayload.from(clanChannel.getName(), event.getSender(), event.getMessage() );
				String jsonPayload = gson.toJson(chatPayload);

				if(ChatChannel == null){
					ChatChannel = PusherClient.subscribePrivate("private-clan-chat-channel." + clanChannel.getName() ,
							new PrivateChannelEventListener() {
								@Override
								public void onEvent(PusherEvent event)
								{
									System.out.println(
											"New message"
									);
								}

								@Override
								public void onSubscriptionSucceeded(String channelName)
								{
									System.out.println(
											"solid"
									);
								}

								@Override
								public void onAuthenticationFailure(String message, Exception e) {
									System.out.println(
											String.format("Authentication failure due to [%s], exception was [%s]", message, e)
									);
									ChatChannel = null;
								}

								// Other ChannelEventListener methods
							});
					ChatChannel.trigger("client-SendChatToGameEvent", jsonPayload);

				}else{
					ChatChannel.trigger("client-SendChatToGameEvent", jsonPayload);

				}

				break;
		}
	}

}
