package com.example.notifyx.service;

import com.example.notifyx.dto.SseEvent;
import com.example.notifyx.model.NotificationLog;
import com.example.notifyx.model.NotificationStatus;
import com.example.notifyx.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * In-process async notification queue.
 * Replaces Kafka for standalone mode — notifications are processed
 * in a separate thread pool (see AsyncConfig) without any external broker.
 */
@Service
@Slf4j
public class NotificationQueueService {

    private final NotificationRepository notificationRepository;
    private final TemplateService templateService;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    // Use @Lazy self-injection to fix the Spring AOP @Async self-invocation proxy bug
    @Autowired
    @Lazy
    private NotificationQueueService self;

    @Value("${notifyx.delivery.success-rate:80}")
    private int successRate;

    @Value("${notifyx.delivery.simulate-delay-ms:800}")
    private long simulateDelayMs;

    public NotificationQueueService(NotificationRepository notificationRepository,
                                    TemplateService templateService,
                                    SseEmitterService sseEmitterService,
                                    ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateService = templateService;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
    }

    @Async("notificationExecutor")
    public void enqueue(NotificationLog logEntity) {
        log.info("Processing notification ID: {} for recipient: {}", logEntity.getId(), logEntity.getRecipient());

        sseEmitterService.broadcast(SseEvent.builder()
                .type("STATUS_CHANGE")
                .notificationId(logEntity.getId())
                .status(NotificationStatus.PENDING.name())
                .recipient(logEntity.getRecipient())
                .templateId(logEntity.getTemplateId())
                .message("Notification queued — rendering template...")
                .build());

        try {
            Thread.sleep(simulateDelayMs);

            String renderedMessage;
            try {
                renderedMessage = templateService.renderTemplate(logEntity.getTemplateId(), logEntity.getPayload());
                log.info("Template rendered successfully for ID: {}", logEntity.getId());
            } catch (Exception e) {
                log.error("Template rendering failed for ID: {}", logEntity.getId(), e);
                handleFailure(logEntity, "Template rendering failed: " + e.getMessage());
                return;
            }

            boolean deliverySuccess = random.nextInt(100) < successRate;

            if (deliverySuccess) {
                logEntity.setStatus(NotificationStatus.DELIVERED);
                notificationRepository.save(logEntity);
                log.info("Notification ID: {} delivered successfully.", logEntity.getId());

                sseEmitterService.broadcast(SseEvent.builder()
                        .type("STATUS_CHANGE")
                        .notificationId(logEntity.getId())
                        .status(NotificationStatus.DELIVERED.name())
                        .recipient(logEntity.getRecipient())
                        .templateId(logEntity.getTemplateId())
                        .message("✅ Delivered: " + truncate(renderedMessage, 120))
                        .build());
            } else {
                handleFailure(logEntity, "Delivery provider returned error (simulated)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Notification processing interrupted for ID: {}", logEntity.getId());
        }
    }

    private void handleFailure(NotificationLog logEntity, String reason) {
        NotificationLog dbLog = notificationRepository.findById(logEntity.getId())
                .orElse(logEntity);

        dbLog.setRetryCount(dbLog.getRetryCount() + 1);

        if (dbLog.getRetryCount() >= 3) {
            dbLog.setStatus(NotificationStatus.DLQ);
            notificationRepository.save(dbLog);
            log.error("Notification ID: {} moved to DLQ after {} retries.", dbLog.getId(), dbLog.getRetryCount());

            sseEmitterService.broadcast(SseEvent.builder()
                    .type("STATUS_CHANGE")
                    .notificationId(dbLog.getId())
                    .status(NotificationStatus.DLQ.name())
                    .recipient(dbLog.getRecipient())
                    .templateId(dbLog.getTemplateId())
                    .message("💀 DLQ: " + reason)
                    .build());
        } else {
            dbLog.setStatus(NotificationStatus.FAILED_RETRYING);
            notificationRepository.save(dbLog);
            log.warn("Notification ID: {} failed. Retry attempt {}.", dbLog.getId(), dbLog.getRetryCount());

            sseEmitterService.broadcast(SseEvent.builder()
                    .type("STATUS_CHANGE")
                    .notificationId(dbLog.getId())
                    .status(NotificationStatus.FAILED_RETRYING.name())
                    .recipient(dbLog.getRecipient())
                    .templateId(dbLog.getTemplateId())
                    .message("⚠️ Retry #" + dbLog.getRetryCount() + ": " + reason)
                    .build());

            // Call through the proxy to ensure Thread.sleep doesn't block the active thread
            self.scheduleRetry(dbLog);
        }
    }

    @Async("notificationExecutor")
    public void scheduleRetry(NotificationLog logEntity) {
        try {
            long delay = (long) Math.pow(2, logEntity.getRetryCount()) * 1500L;
            log.info("Scheduling retry for ID: {} in {}ms", logEntity.getId(), delay);
            Thread.sleep(delay);
            enqueue(logEntity);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        String stripped = s.replaceAll("<[^>]*>", "").trim();
        return stripped.length() > maxLen ? stripped.substring(0, maxLen) + "..." : stripped;
    }
}