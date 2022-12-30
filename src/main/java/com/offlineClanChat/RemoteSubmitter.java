/**
    Forked from hex-agon/chat-logger
    https://github.com/hex-agon/chat-logger/blob/master/src/main/java/fking/work/chatlogger/RemoteSubmitter.java
 */
package com.offlineClanChat;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RemoteSubmitter {

    private static final MediaType APPLICATION_JSON = MediaType.parse("application/json");
    private static final int MAX_ENTRIES_PER_TICK = 30;
    private static final int TICK_INTERVAL = 5;

//    private static final String REMOTE_SERVER_ROOT_URL = "http:://localhost";

    private static final CircuitBreaker<Object> BREAKER = new CircuitBreaker<>()
            .handle(IOException.class)
            .withDelay(Duration.ofMinutes(5))
            .withFailureThreshold(3, Duration.ofSeconds(30))
            .onHalfOpen(RemoteSubmitter::onHalfOpen);

    private final ConcurrentLinkedDeque<ChatPayload> queuedEntries = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

//    private final ChatLoggerConfig config;
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    private final String UserHash;

    private final String RemoteServerRootEndpoint;
    private RemoteSubmitter(OkHttpClient okHttpClient, Gson gson, String remoteServerRootEndpoint, String userHash) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.RemoteServerRootEndpoint = remoteServerRootEndpoint;
        this.UserHash = userHash;
    }

    public static RemoteSubmitter create(OkHttpClient okHttpClient, Gson gson, String remoteServerRootEndpoint, String userHash) {
        return new RemoteSubmitter(okHttpClient, gson, remoteServerRootEndpoint, userHash);
    }

    private static void onHalfOpen() {
        log.info("Checking if remote host is answering properly again (HALF_OPEN)");
    }

    public void initialize() {
        executorService.scheduleAtFixedRate(this::processQueue, TICK_INTERVAL, TICK_INTERVAL, TimeUnit.SECONDS);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void queue(ChatPayload entry) {
        queuedEntries.add(entry);
    }

    private void processQueue() {

        if (queuedEntries.isEmpty()) {
            return;
        }
        RequestBody payload = buildPayload();

        try {
            Failsafe.with(BREAKER).run(() -> {
                Request request = new Builder()
                        .url(RemoteServerRootEndpoint + "/api/chat/osrs")
                        .addHeader("osrs_id", UserHash)
                        .post(payload)
                        .build();

                try (Response response = okHttpClient.newCall(request).execute()) {

                    if (!response.isSuccessful()) {
                        log.warn("Remote endpoint returned non successful response, responseCode={}", response.code());
                    }
                }
            });
        } catch (Exception e) {
            if (!BREAKER.isOpen()) {
                log.warn("Failed to submit chat entries: {}", e.getMessage());
            }
        }
    }

    private RequestBody buildPayload() {
        List<ChatPayload> entries = new ArrayList<>();
        int count = 0;

        while (!queuedEntries.isEmpty() && count < MAX_ENTRIES_PER_TICK) {
            entries.add(queuedEntries.poll());
            count++;
        }
        return RequestBody.create(APPLICATION_JSON, gson.toJson(entries));
    }
}
