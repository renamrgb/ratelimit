package com.example.ratelimit.application;

import com.example.ratelimit.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    private final TestService testService;

    @Autowired
    public TestController(TestService testService) {
        this.testService = testService;
    }

    @GetMapping
    public ResponseEntity<String> test() {
        return testService.test();
    }

    @GetMapping("/normal")
    public ResponseEntity<String> normalTest() {
        return testService.normalTest();
    }
    
    @GetMapping("/async")
    public ResponseEntity<String> asyncTest() {
        return testService.asyncTest();
    }
    
    @GetMapping("/async-retry")
    public ResponseEntity<String> asyncRetryTest() {
        return testService.asyncRetryTest();
    }
}
