package com.salas.psi.harmony.controller;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/version")
public class VersionController {

    @Value("${app.version}")
    private String appVersion;

    @GetMapping
    public ResponseEntity<Map<String, String>> getVersion() {
        Map<String, String> response = new HashMap<>();
        response.put("version", appVersion);
        return ResponseEntity.ok(response);
    }
}
