package com.clinica.escuta.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET_KEY = "minha-chave-secreta-super-segura-de-32-chars!";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET_KEY);
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        String token = jwtUtil.generateToken("admin@clinica.com", "ADMINISTRADOR");

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("admin@clinica.com", jwtUtil.getUsernameFromToken(token));
        assertEquals("ADMINISTRADOR", jwtUtil.getAccessLevelFromToken(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.jwt.token"));
    }
}
