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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UserResponse sampleUserResponse;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("hashedPassword123")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleUserResponse = new UserResponse(
                userId,
                "Sidharth",
                "sid@example.com",
                sampleUser.getCreatedAt()
        );
    }

    @Test
    @DisplayName("register: successfully registers new user and returns AuthResponse")
    void testRegisterSuccess() {
        RegisterRequest request = new RegisterRequest("Sidharth", "sid@example.com", "Password123!");

        when(userRepository.existsByEmail("sid@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(sampleUser)).thenReturn("sample.jwt.token");
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("sample.jwt.token");
        assertThat(response.user().email()).isEqualTo("sid@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate email throws DuplicateResourceException")
    void testRegisterDuplicateEmailThrowsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest("Sidharth", "sid@example.com", "Password123!");

        when(userRepository.existsByEmail("sid@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("User already exists with email: sid@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("login: valid credentials returns AuthResponse")
    void testLoginSuccess() {
        LoginRequest request = new LoginRequest("sid@example.com", "Password123!");

        when(userRepository.findByEmail("sid@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password123!", "hashedPassword123")).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("sample.jwt.token");
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("sample.jwt.token");
        assertThat(response.user().name()).isEqualTo("Sidharth");
    }

    @Test
    @DisplayName("login: wrong password throws InvalidCredentialsException")
    void testLoginWrongPasswordThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("sid@example.com", "WrongPassword!");

        when(userRepository.findByEmail("sid@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword!", "hashedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("login: user not found throws InvalidCredentialsException")
    void testLoginUserNotFoundThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "Password123!");

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("getCurrentUser: returns UserResponse for valid userId")
    void testGetCurrentUserSuccess() {
        UUID userId = sampleUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userMapper.toResponse(sampleUser)).thenReturn(sampleUserResponse);

        UserResponse response = authService.getCurrentUser(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getCurrentUser: user not found throws ResourceNotFoundException")
    void testGetCurrentUserNotFoundThrowsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id");
    }
}
