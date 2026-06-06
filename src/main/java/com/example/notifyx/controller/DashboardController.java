package com.example.notifyx.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf HTML pages for the web UI.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "index";
    }

    @GetMapping("/playground")
    public String playground() {
        return "playground";
    }
}
