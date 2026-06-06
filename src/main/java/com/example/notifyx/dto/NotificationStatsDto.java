package com.example.notifyx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard statistics DTO — counts of notifications by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatsDto {
    private long total;
    private long pending;
    private long delivered;
    private long failedRetrying;
    private long dlq;
    private int activeConnections;
}
