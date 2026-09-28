package com.clinica.escuta.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersionControllerTest {

    @Test
    void shouldReturnAppVersion() {
        VersionController controller = new VersionController();
        ReflectionTestUtils.setField(controller, "appVersion", "1.0.0-test");

        ResponseEntity<Map<String, String>> response = controller.getVersion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("1.0.0-test", response.getBody().get("version"));
    }
}
