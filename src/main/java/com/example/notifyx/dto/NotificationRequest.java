package com.example.notifyx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class NotificationRequest {

    @NotBlank(message = "Recipient is mandatory")
    private String recipient;

    @NotBlank(message = "Template ID is mandatory")
    private String templateId;

    @NotEmpty(message = "Payload map cannot be empty")
    private Map<String, Object> payload;
}
