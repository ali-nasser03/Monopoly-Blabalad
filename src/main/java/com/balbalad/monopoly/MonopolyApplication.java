package com.balbalad.monopoly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MonopolyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonopolyApplication.class, args);
    }
}
