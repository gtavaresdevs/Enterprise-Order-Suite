package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.AuthResponse;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidCredentialsException;
import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // will create next

    public AuthResponse register(RegisterRequest request) {

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use.");
        }

        // Load role from DB
        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found."));

        // Create user entity
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setRole(role);

        userRepository.save(user);

        // Generate JWT
        String token = jwtService.generateToken(user);

        // Return response
        return new AuthResponse(token);
    }

    public AuthResponse authenticate(AuthRequest request) {

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // Generate JWT
        String token = jwtService.generateToken(user);

        return new AuthResponse(token);
    }
}
