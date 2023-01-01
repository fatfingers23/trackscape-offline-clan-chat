package com.offlineClanChat;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.pusher.client.Pusher;
import com.pusher.client.channel.PrivateChannel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "Offline Clan Chat"
)
public class OfflineClanChatPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private OfflineClanChatConfig config;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    //TODO move all the ports and hosts to config so users can setup their own if they want to
    //See if there is a reset config button to default
    private static final String BASE_API_ENDPOINT = "http://localhost";
    private Pusher PusherClient;

    private PrivateChannel ChatChannel;
    private RemoteSubmitter remoteSubmitter;

    private WebSocketHandler webSocketHandler;


    @Override
    protected void startUp() throws Exception {

    }

    @Override
    protected void shutDown() throws Exception {
    }

    private void startRemoteSubmitter() {

        if (remoteSubmitter != null) {
            log.debug("Shutting down previous remoteSubmitter...");
            shutdownRemoteSubmitter();
        }

        log.debug("Starting a new remoteSubmitter...");
        remoteSubmitter = RemoteSubmitter.create(httpClient, gson, BASE_API_ENDPOINT, String.valueOf(client.getAccountHash()));
        remoteSubmitter.initialize();

    }

    private void shutdownRemoteSubmitter() {
        if (remoteSubmitter != null) {
            remoteSubmitter.shutdown();
            remoteSubmitter = null;
        }


    }


    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event)
    {
        if (event.getClanId() == ClanID.CLAN)
        {
            ClanChannel clanChannel = event.getClanChannel();
            if(webSocketHandler == null){
                webSocketHandler = WebSocketHandler.create("127.0.0.1", BASE_API_ENDPOINT, client, gson);
                webSocketHandler.CreatePusherClient();
            }
            if (clanChannel != null) {
                if (!webSocketHandler.chatChannelIsSubscribed()){
                    webSocketHandler.subscribeToChatChannel(clanChannel.getName());
                }

            }
        }

//        if (webSocketHandler != null) {
//            webSocketHandler.shutdown();
//            webSocketHandler = null;
//        }
    }



    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {

        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            if(remoteSubmitter == null){
                startRemoteSubmitter();
            }
        }

        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            shutdownRemoteSubmitter();
        }
    }

    @Provides
    OfflineClanChatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OfflineClanChatConfig.class);
    }


    @Subscribe
    public void onChatMessage(ChatMessage event) {
        switch (event.getType()) {

            case CLAN_CHAT:
            case CLAN_MESSAGE:
                ClanChannel clanChannel = client.getClanChannel();
                String sender = "";
                if(event.getType() == ChatMessageType.CLAN_MESSAGE){
                    sender = clanChannel.getName();
                }else{
                    sender = Text.removeFormattingTags(Text.toJagexName(event.getName()));
                }
                ChatPayload chatPayload = ChatPayload.from(clanChannel.getName(), sender, event.getMessage());

                remoteSubmitter.queue(chatPayload);
                break;
        }
    }

}
