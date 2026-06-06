package com.example.notifyx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class TemplateService {

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateEngine templateEngine, ObjectMapper objectMapper) {
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
    }

    public String renderTemplate(String templateName, String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        
        Context context = new Context();
        context.setVariables(payload);

        // Render template using Thymeleaf. 
        // Note: Thymeleaf will look for templates in src/main/resources/templates/{templateName}.html
        return templateEngine.process(templateName, context);
    }
}
