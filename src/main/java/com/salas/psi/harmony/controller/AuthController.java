package com.salas.psi.harmony.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.salas.psi.harmony.DTO.LoginRequestDTO;
import com.salas.psi.harmony.DTO.LoginResponseDTO;

import com.salas.psi.harmony.repository.UserRepository;
import com.salas.psi.harmony.security.JwtUtil;
import com.salas.psi.harmony.model.User;
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
                return ResponseEntity.status(403).body(new LoginResponseDTO(null, "User is inactive"));
            }

            if (passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                String token = jwtUtil.generateToken(user.getUsername(), user.getAccessLevel());
                return ResponseEntity.ok(new LoginResponseDTO(token, "Login Successful!", user.getId(), user.getUsername(), user.getEmail(), user.getAccessLevel()));
            }
        }

        return ResponseEntity.status(401).body(new LoginResponseDTO(null, "Invalid email or password"));
    }
}