package com.offlineClanChat;

import lombok.ToString;
import net.runelite.api.events.ChatMessage;

import java.time.Clock;
import java.time.ZonedDateTime;


@ToString
public class ChatPayload {

    public final String clanName;
    public final String sender;
    public final String message;

    private ChatPayload(String clanName, String sender, String message) {
        this.clanName = clanName;
        this.sender = sender;
        this.message = message;
    }

    public static ChatPayload from(String clanName, String sender, String message){
        return new ChatPayload(clanName, sender, message);
    }
}
