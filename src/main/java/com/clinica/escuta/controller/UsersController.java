package com.clinica.escuta.controller;

import com.clinica.escuta.DTO.UserDTO;
import com.clinica.escuta.model.User;
import com.clinica.escuta.repository.UserRepository;
import com.clinica.escuta.repository.ProfissionsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('admin')")
public class UsersController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfissionsRepository profissionsRepository;

    @Value("${default.password}")
    private String defaultPassword;

    public UsersController(UserRepository userRepository, PasswordEncoder passwordEncoder, ProfissionsRepository profissionsRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.profissionsRepository = profissionsRepository;
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> listAllUsers(
            @RequestParam(required = false, defaultValue = "false") boolean includePhoto
    ) {
        List<UserDTO> users = userRepository.findAll().stream()
                .map(u -> new UserDTO(u, includePhoto))
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
        user.setStatus(request.getStatus() != null ? request.getStatus() : true);
        String accessLevel = request.getAccessLevel();
        if (accessLevel == null || (!accessLevel.equals("admin") && !accessLevel.equals("psi"))) {
            accessLevel = "psi";
        }
        user.setAccessLevel(accessLevel);
        
        if (request.getProfessionId() != null && request.getProfessionId() != 0) {
            if (!profissionsRepository.existsById(request.getProfessionId())) {
                return ResponseEntity.badRequest().body("Profession ID does not exist.");
            }
            user.setProfessionId(request.getProfessionId());
        } else {
            user.setProfessionId(null);
        }

        user.setPhone(request.getPhone() != null ? request.getPhone() : "");
        user.setCpf(request.getCpf() != null ? request.getCpf() : "");
        user.setAddress(request.getAddress() != null ? request.getAddress() : "");
        user.setCep(request.getCep() != null ? request.getCep() : "");
        user.setPhoto(request.getPhoto());
        user.setBoardNumber(request.getBoardNumber() != null ? request.getBoardNumber() : "");

        User saved = userRepository.save(user);
        return ResponseEntity.ok(new UserDTO(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserDTO request, Authentication authentication) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "admin".equals(a.getAuthority()));
        if (!isAdmin && !user.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Você não tem permissão para alterar o perfil de outro usuário."));
        }

        if (!isAdmin) {
            request.setStatus(user.getStatus());
            request.setAccessLevel(user.getAccessLevel());
        }

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

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        
        if (request.getAccessLevel() != null) {
            String acc = request.getAccessLevel();
            if (acc.equals("admin") || acc.equals("psi")) {
                user.setAccessLevel(acc);
            }
        }

        if (request.getProfessionId() != null) {
            if (request.getProfessionId() != 0) {
                if (!profissionsRepository.existsById(request.getProfessionId())) {
                    return ResponseEntity.badRequest().body("Profession ID does not exist.");
                }
                user.setProfessionId(request.getProfessionId());
            } else {
                user.setProfessionId(null);
            }
        }

        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getCpf() != null) user.setCpf(request.getCpf());
        if (request.getAddress() != null) user.setAddress(request.getAddress());
        if (request.getCep() != null) user.setCep(request.getCep());
        if (request.getPhoto() != null) user.setPhoto(request.getPhoto());
        if (request.getBoardNumber() != null) user.setBoardNumber(request.getBoardNumber());

        if (user.getPhone() == null) user.setPhone("");
        if (user.getCpf() == null) user.setCpf("");
        if (user.getAddress() == null) user.setAddress("");
        if (user.getCep() == null) user.setCep("");
        if (user.getBoardNumber() == null) user.setBoardNumber("");

        User saved = userRepository.save(user);
        return ResponseEntity.ok(new UserDTO(saved));
    }

    @PreAuthorize("hasAuthority('admin')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        String encodedPassword = passwordEncoder.encode(defaultPassword);
        user.setPasswordHash(encodedPassword);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Senha resetada com sucesso",
                "userId", user.getId()
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/completed-tour")
    public ResponseEntity<?> completedTour(@PathVariable Integer id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        user.setMustCompleteTour(false);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Usuário completou o tour inicial",
                "userId", user.getId()
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/first-login")
    public ResponseEntity<?> resetPasswordFirstLogin(
            @PathVariable Integer id, 
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userOpt.get();

        if (!user.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Você não tem permissão para alterar a senha de outro usuário."));
        }

        String newPassword = request.get("password");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Senha não fornecida.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Senha de primeiro login alterada com sucesso",
                "userId", user.getId()
        ));
    }
}
