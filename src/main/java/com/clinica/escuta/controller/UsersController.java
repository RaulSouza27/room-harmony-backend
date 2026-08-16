package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.UserDTO;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('admin')")
public class UsersController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UsersController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> listAllUsers() {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(UserDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserDTO request) {
        if (request.getUsername() == null || request.getEmail() == null) {
            return ResponseEntity.badRequest().body("Missing required fields (username, email).");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already exists.");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "psi123"));
        user.setStatus(request.isStatus());
        
        String accessLevel = request.getAccessLevel();
        if (accessLevel == null || (!accessLevel.equals("admin") && !accessLevel.equals("psi"))) {
            accessLevel = "psi";
        }
        user.setAccessLevel(accessLevel);

        User saved = userRepository.save(user);
        return ResponseEntity.ok(new UserDTO(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserDTO request) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            Optional<User> existing = userRepository.findByUsername(request.getUsername());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Username already exists.");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            Optional<User> existing = userRepository.findByEmail(request.getEmail());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Email already exists.");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        user.setStatus(request.isStatus());
        
        if (request.getAccessLevel() != null) {
            String acc = request.getAccessLevel();
            if (acc.equals("admin") || acc.equals("psi")) {
                user.setAccessLevel(acc);
            }
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(new UserDTO(saved));
    }
}
