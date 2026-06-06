package com.example.notifyx.service;

import com.example.notifyx.model.NotificationLog;
import com.example.notifyx.model.NotificationStatus;
import com.example.notifyx.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled job that picks up FAILED_RETRYING notifications and re-enqueues them.
 * Acts as a resilient fallback for in-memory scheduled retries in case the server restarts.
 */
@Service
@Slf4j
public class RetryScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationQueueService notificationQueueService;

    public RetryScheduler(NotificationRepository notificationRepository,
                          NotificationQueueService notificationQueueService) {
        this.notificationRepository = notificationRepository;
        this.notificationQueueService = notificationQueueService;
    }

    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void retryFailedNotifications() {
        List<NotificationLog> failedLogs = notificationRepository.findByStatus(NotificationStatus.FAILED_RETRYING);

        if (!failedLogs.isEmpty()) {
            log.info("RetryScheduler: Found {} notifications to retry.", failedLogs.size());
        }

        for (NotificationLog logEntity : failedLogs) {
            log.info("Re-enqueuing failed notification ID: {}", logEntity.getId());
            notificationQueueService.enqueue(logEntity);
        }
    }
}