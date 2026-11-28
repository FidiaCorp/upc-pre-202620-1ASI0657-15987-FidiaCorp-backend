package com.fidiacorp.credicasa.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MS-5 communication-notification-service (:8085, communication_db): US10 (bandeja), US16–US18. */
@SpringBootApplication
public class CommunicationNotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommunicationNotificationApplication.class, args);
    }
}
