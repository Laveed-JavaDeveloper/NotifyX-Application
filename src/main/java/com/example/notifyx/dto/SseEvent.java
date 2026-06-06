package com.example.notifyx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for Server-Sent Events broadcast to connected clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {
    private String type;           // STATUS_CHANGE, STATS_UPDATE, SYSTEM
    private String notificationId;
    private String status;
    private String recipient;
    private String templateId;
    private String message;
    @lombok.Builder.Default
    private long timestamp = System.currentTimeMillis();
}
