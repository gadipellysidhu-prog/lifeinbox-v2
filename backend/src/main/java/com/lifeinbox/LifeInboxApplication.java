package com.lifeinbox;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class LifeInboxApplication {

    @PostConstruct
    public void init() {
        // Set JVM default timezone to UTC across the application
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(LifeInboxApplication.class, args);
    }
}
