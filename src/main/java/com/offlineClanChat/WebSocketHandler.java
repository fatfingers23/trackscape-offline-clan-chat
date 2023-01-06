package com.offlineClanChat;

import com.google.gson.Gson;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.PusherEvent;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpAuthorizer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

import java.util.HashMap;

@Slf4j
public class WebSocketHandler {


    private final String WsHost;
    private final String RemoteServerRootEndpoint;
    private Pusher pusherClient;
    private PrivateChannel chatChannel;
    private final Gson gson;
    private ClientThread clientThread;
    private Client client;

    private WebSocketHandler(String wsHost, String remoteServerRootEndpoint, Client client, Gson gson, ClientThread clientThread) {
        this.WsHost = wsHost;
        this.RemoteServerRootEndpoint = remoteServerRootEndpoint;
        this.client = client;
        this.gson = gson;
        this.clientThread = clientThread;
    }

    public static WebSocketHandler create(String wsHost, String remoteServerRootEndpoint, Client client, Gson gson, ClientThread clientThread) {
        return new WebSocketHandler(wsHost, remoteServerRootEndpoint, client, gson, clientThread);
    }

    public void CreatePusherClient() {
        //TODO move all the ports and hosts to config
        PusherOptions options = new PusherOptions().setCluster("mt1");
        options.setHost(WsHost);
        options.setWsPort(6001);
        options.setWssPort(6001);
        options.setUseTLS(false);
        HttpAuthorizer authorizer = new HttpAuthorizer(RemoteServerRootEndpoint + "/broadcasting/auth");
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "application/json");
        headers.put("osrs_id", String.valueOf(client.getAccountHash()));
        authorizer.setHeaders(headers);
        options.setAuthorizer(authorizer);
        pusherClient = new Pusher("app-key", options);
        pusherClient.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                System.out.println("State changed to " + change.getCurrentState() +
                        " from " + change.getPreviousState());
                if (change.getCurrentState() == ConnectionState.CONNECTED) {
                    System.out.println("woo");
                }
            }

            @Override
            public void onError(String message, String code, Exception e) {
                System.out.println("There was a problem connecting!");
            }
        }, ConnectionState.ALL);


    }

    public void shutdown() {
        if (pusherClient != null) {
            pusherClient.disconnect();
            pusherClient = null;
        }
    }

    public boolean chatChannelIsSubscribed() {
        if (chatChannel == null) {
            return false;
        }
        return chatChannel.isSubscribed();
    }

    public void subscribeToChatChannel(String clanName) {
        chatChannel = pusherClient.subscribePrivate("private-clan-chat-channel." + clanName,
                new PrivateChannelEventListener() {
                    @Override
                    public void onEvent(PusherEvent event) {
                        System.out.println(
                                "New message"
                        );
                    }

                    @Override
                    public void onSubscriptionSucceeded(String channelName) {
                        System.out.println(
                                "solid"
                        );
                    }

                    @Override
                    public void onAuthenticationFailure(String message, Exception e) {
                        System.out.println(
                                String.format("Authentication failure due to [%s], exception was [%s]", message, e)
                        );
                        chatChannel = null;
                    }

                    // Other ChannelEventListener methods
                });

        chatChannel.bind("discord.chat", new PrivateChannelEventListener() {
            @Override
            public void onAuthenticationFailure(String message, Exception e) {

            }

            @Override
            public void onSubscriptionSucceeded(String channelName) {
                String test = "";

            }

            @Override
            public void onEvent(PusherEvent event) {
                // Called for incoming events named "my-event"
                ChatPayload chatPayload = gson.fromJson(event.getData(), ChatPayload.class);
                log.debug("From WS -> {}: {}", chatPayload.sender, chatPayload.message);
                clientThread.invokeLater(() -> {
                    client.addChatMessage(ChatMessageType.CLAN_CHAT, Text.toJagexName(chatPayload.sender), chatPayload.message, chatPayload.clanName, false);
                });


            }

            @Override
            public void onError(String message, Exception e) {
                PrivateChannelEventListener.super.onError(message, e);
            }
        });
    }

}
