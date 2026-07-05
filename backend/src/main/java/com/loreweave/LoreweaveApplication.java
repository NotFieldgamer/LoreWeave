package com.loreweave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync   // GameMasterService.streamTurn runs the SSE turn loop on a background thread
public class LoreweaveApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoreweaveApplication.class, args);
    }
}
