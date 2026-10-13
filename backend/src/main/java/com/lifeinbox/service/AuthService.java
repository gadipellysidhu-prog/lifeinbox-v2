package com.lifeinbox.service;

import com.lifeinbox.dto.AuthResponse;
import com.lifeinbox.dto.LoginRequest;
import com.lifeinbox.dto.RegisterRequest;
import com.lifeinbox.dto.UserResponse;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.DuplicateResourceException;
import com.lifeinbox.exception.InvalidCredentialsException;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.mapper.UserMapper;
import com.lifeinbox.repository.UserRepository;
import com.lifeinbox.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final NotificationPreferencesService notificationPreferencesService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            NotificationPreferencesService notificationPreferencesService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.notificationPreferencesService = notificationPreferencesService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User already exists with email: " + normalizedEmail);
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        User savedUser = userRepository.save(user);

        // Initialize default notification preferences with detected timezone if provided
        notificationPreferencesService.createDefaultPreferences(savedUser.getId(), request.timezone());

        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(userMapper.toResponse(savedUser), token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(userMapper.toResponse(user), token);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toResponse(user);
    }
}
