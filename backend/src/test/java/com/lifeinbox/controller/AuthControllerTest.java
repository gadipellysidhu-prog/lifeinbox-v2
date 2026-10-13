package com.lifeinbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.SecurityConfig;
import com.lifeinbox.dto.AuthResponse;
import com.lifeinbox.dto.LoginRequest;
import com.lifeinbox.dto.RegisterRequest;
import com.lifeinbox.dto.UserResponse;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.DuplicateResourceException;
import com.lifeinbox.exception.InvalidCredentialsException;
import com.lifeinbox.security.CustomUserDetailsService;
import com.lifeinbox.security.JwtAuthenticationFilter;
import com.lifeinbox.security.JwtProperties;
import com.lifeinbox.security.JwtService;
import com.lifeinbox.security.RestAccessDeniedHandler;
import com.lifeinbox.security.RestAuthenticationEntryPoint;
import com.lifeinbox.security.UserPrincipal;
import com.lifeinbox.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, JwtProperties.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private UserResponse sampleUserResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("hashedPassword")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser);
        sampleUserResponse = new UserResponse(userId, "Sidharth", "sid@example.com", sampleUser.getCreatedAt());
    }

    @Test
    @DisplayName("POST /api/auth/register: returns 201 on valid input")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest("Sidharth", "sid@example.com", "Password123!");
        AuthResponse response = new AuthResponse(sampleUserResponse, "valid.jwt.token");

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("valid.jwt.token"))
                .andExpect(jsonPath("$.user.email").value("sid@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register: returns 400 on invalid input")
    void testRegisterValidationFailure() throws Exception {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register: returns 409 on duplicate email")
    void testRegisterDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("Sidharth", "sid@example.com", "Password123!");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("User already exists with email: sid@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/auth/login: returns 200 on valid credentials")
    void testLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("sid@example.com", "Password123!");
        AuthResponse response = new AuthResponse(sampleUserResponse, "valid.jwt.token");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("valid.jwt.token"))
                .andExpect(jsonPath("$.user.name").value("Sidharth"));
    }

    @Test
    @DisplayName("POST /api/auth/login: returns 401 on invalid credentials")
    void testLoginInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("sid@example.com", "WrongPassword!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me: returns 401 without Bearer token")
    void testGetCurrentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me: returns 200 with valid Bearer token")
    void testGetCurrentUserAuthenticated() throws Exception {
        when(jwtService.validateToken("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(samplePrincipal);
        when(authService.getCurrentUser(userId)).thenReturn(sampleUserResponse);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("sid@example.com"));
    }
}
