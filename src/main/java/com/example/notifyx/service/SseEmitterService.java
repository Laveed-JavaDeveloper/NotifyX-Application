package com.example.notifyx.service;

import com.example.notifyx.dto.SseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Event (SSE) connections for real-time dashboard updates.
 * Thread-safe: uses CopyOnWriteArrayList for concurrent client management.
 */
@Service
@Slf4j
public class SseEmitterService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseEmitterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 min timeout

        emitters.add(emitter);
        log.info("New SSE subscriber connected. Total active: {}", emitters.size());

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE emitter completed. Active: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE emitter timed out. Active: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE emitter error. Active: {}", emitters.size());
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"Connected to NotifyX live feed\",\"activeClients\":" + emitters.size() + "}"));
        } catch (Exception e) {
            log.error("Failed to send initial SSE event", e);
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcast(SseEvent event) {
        if (emitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize SSE event", e);
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(json));
            } catch (Exception e) {
                // Catch generic Exception (including IllegalStateException) to clear dropped clients safely
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);

        if (!deadEmitters.isEmpty()) {
            log.debug("Removed {} dead SSE emitters. Active: {}", deadEmitters.size(), emitters.size());
        }
    }

    public void broadcastStatsUpdate(Object stats) {
        try {
            String json = objectMapper.writeValueAsString(stats);
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("STATS_UPDATE")
                            .data(json));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        } catch (Exception e) {
            log.error("Failed to broadcast stats update", e);
        }
    }

    public int getActiveSubscriberCount() {
        return emitters.size();
    }
}