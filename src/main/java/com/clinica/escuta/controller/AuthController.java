package com.clinica.escuta.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.clinica.escuta.DTO.LoginRequestDTO;
import com.clinica.escuta.DTO.LoginResponseDTO;

import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.security.JwtUtil;
import com.clinica.escuta.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;

import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthController(UserRepository userRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequest) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (Boolean.FALSE.equals(user.getStatus())) {
                return ResponseEntity.status(403).body(LoginResponseDTO.builder().message("User is inactive").build());
            }

            if (passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                String token = jwtUtil.generateToken(user.getUsername(), user.getAccessLevel());
                return ResponseEntity.ok(LoginResponseDTO.builder().
                        token(token).
                        message("Login Successful!").
                        id(user.getId()).
                        username(user.getUsername()).
                        email(user.getEmail()).
                        accessLevel(user.getAccessLevel()).
                        mustCompleteTour(user.getMustCompleteTour()).
                        firstLogin(user.getFirstLogin()).
                        status(user.getStatus()).
                        build());
            }
        }

        return ResponseEntity.status(401).body(LoginResponseDTO.builder().message("Invalid email or password").build());
    }
}