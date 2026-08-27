package com.example.transactionstarter.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SampleController {

    @GetMapping("/api/sample")
    public Map<String, String> sample() {
        return Map.of("message", "Starter project is running");
    }
}
