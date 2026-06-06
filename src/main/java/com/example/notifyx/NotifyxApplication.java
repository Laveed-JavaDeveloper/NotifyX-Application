package com.example.notifyx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotifyxApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyxApplication.class, args);
    }

}
