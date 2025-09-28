// src/main/java/com/flow/workflow/controller/AuthController.java
package com.flow.workflow.controller;

import com.flow.workflow.model.User;
import com.flow.workflow.repository.UserRepository;
import com.flow.workflow.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import com.flow.workflow.model.Role;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginData.get("username"),
                            loginData.get("password")
                    )
            );

            User user = userRepository.findByUsername(loginData.get("username"))
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String roleName = (user.getRole() != null) ? user.getRole().name() : "USER";
            String token = jwtUtil.generateToken(user.getUsername(), roleName);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "username", user.getUsername(),
                    "role", roleName
            ));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/debug/check")
    public ResponseEntity<?> debugCheck(@RequestBody Map<String,String> body) {
        String username = body.get("username");
        String password = body.get("password"); // plaintext to test
        var uOpt = userRepository.findByUsername(username);
        if (uOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        }
        User u = uOpt.get();
        String storedHash = u.getPassword();

        boolean matches = passwordEncoder.matches(password, storedHash);

        return ResponseEntity.ok(Map.of(
                "username", u.getUsername(),
                "storedHash", storedHash,
                "matches", matches,
                "role", u.getRole() == null ? null : u.getRole().name()
        ));
    }


    // ✅ Signup endpoint
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> signupData) {
        String username = signupData.get("username");
        String password = signupData.get("password");

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User already exists"));
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(Role.USER)  // 👈 default role
                .build();

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
    }
}




