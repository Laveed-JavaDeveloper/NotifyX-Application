package com.example.notifyx.controller;

import com.example.notifyx.dto.NotificationRequest;
import com.example.notifyx.dto.NotificationStatsDto;
import com.example.notifyx.model.NotificationLog;
import com.example.notifyx.model.NotificationStatus;
import com.example.notifyx.repository.NotificationRepository;
import com.example.notifyx.service.NotificationQueueService;
import com.example.notifyx.service.SseEmitterService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationQueueService notificationQueueService;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationQueueService notificationQueueService,
                                  SseEmitterService sseEmitterService,
                                  ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationQueueService = notificationQueueService;
        this.sseEmitterService = sseEmitterService;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────
    // POST /api/notifications — Submit notification
    // ─────────────────────────────────────────────
    @PostMapping("/notifications")
    public ResponseEntity<Map<String, Object>> sendNotification(@Valid @RequestBody NotificationRequest request) {
        try {
            NotificationLog log = NotificationLog.builder()
                    .recipient(request.getRecipient())
                    .templateId(request.getTemplateId())
                    .payload(objectMapper.writeValueAsString(request.getPayload()))
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .build();

            notificationRepository.save(log);

            // Enqueue for async processing (non-blocking)
            notificationQueueService.enqueue(log);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "success", true,
                    "notificationId", log.getId(),
                    "message", "Notification accepted and queued for delivery.",
                    "status", "PENDING"
            ));
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "Invalid payload format: " + e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/notifications — List all (paginated)
    // ─────────────────────────────────────────────
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationLog> result;

        if (status != null && !status.isBlank()) {
            try {
                NotificationStatus statusEnum = NotificationStatus.valueOf(status.toUpperCase());
                result = notificationRepository.findByStatus(statusEnum, pageRequest);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + status));
            }
        } else {
            result = notificationRepository.findAll(pageRequest);
        }

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber()
        ));
    }

    // ─────────────────────────────────────────────
    // GET /api/notifications/{id} — Single notification
    // ─────────────────────────────────────────────
    @GetMapping("/notifications/{id}")
    public ResponseEntity<?> getNotification(@PathVariable String id) {
        return notificationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────
    // POST /api/notifications/{id}/retry — Manual retry
    // ─────────────────────────────────────────────
    @PostMapping("/notifications/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryNotification(@PathVariable String id) {
        return notificationRepository.findById(id).map(log -> {
            log.setStatus(NotificationStatus.PENDING);
            log.setRetryCount(0);
            notificationRepository.save(log);
            notificationQueueService.enqueue(log);
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("success", true); resp.put("message", "Notification re-queued for delivery.");
            return ResponseEntity.<Map<String, Object>>ok(resp);
        }).orElse(ResponseEntity.<Map<String, Object>>notFound().build());
    }

    // ─────────────────────────────────────────────
    // DELETE /api/notifications/{id} — Delete
    // ─────────────────────────────────────────────
    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Map<String, Object>> deleteNotification(@PathVariable String id) {
        if (notificationRepository.existsById(id)) {
            notificationRepository.deleteById(id);
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("success", true); resp.put("message", "Notification deleted.");
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.<Map<String, Object>>notFound().build();
    }

    // ─────────────────────────────────────────────
    // GET /api/notifications/stats — Dashboard stats
    // ─────────────────────────────────────────────
    @GetMapping("/notifications/stats")
    public ResponseEntity<NotificationStatsDto> getStats() {
        NotificationStatsDto stats = NotificationStatsDto.builder()
                .total(notificationRepository.count())
                .pending(notificationRepository.countByStatus(NotificationStatus.PENDING))
                .delivered(notificationRepository.countByStatus(NotificationStatus.DELIVERED))
                .failedRetrying(notificationRepository.countByStatus(NotificationStatus.FAILED_RETRYING))
                .dlq(notificationRepository.countByStatus(NotificationStatus.DLQ))
                .activeConnections(sseEmitterService.getActiveSubscriberCount())
                .build();

        return ResponseEntity.ok(stats);
    }

    // ─────────────────────────────────────────────
    // GET /api/templates — List available templates
    // ─────────────────────────────────────────────
    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, String>>> getTemplates() {
        List<Map<String, String>> templates = List.of(
                Map.of("id", "otp_template", "name", "OTP / Verification Code", "description", "One-time password for login or signup verification"),
                Map.of("id", "welcome_template", "name", "Welcome Email", "description", "Onboarding email for new users"),
                Map.of("id", "alert_template", "name", "System Alert", "description", "Security or system alert notification")
        );
        return ResponseEntity.ok(templates);
    }

    // ─────────────────────────────────────────────
    // GET /api/stream — SSE real-time event stream
    // ─────────────────────────────────────────────
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return sseEmitterService.subscribe();
    }
}
