package com.uws.auth_service.service;

import com.uws.auth_service.config.KafkaConfig;
import com.uws.auth_service.dto.AuthResponse;
import com.uws.auth_service.dto.LoginRequest;
import com.uws.auth_service.dto.RefreshTokenRequest;
import com.uws.auth_service.dto.RegisterRequest;
import com.uws.auth_service.event.UserCreatedEvent;
import com.uws.auth_service.exception.DuplicateUserException;
import com.uws.auth_service.exception.InvalidCredentialsException;
import com.uws.auth_service.exception.InvalidTokenException;
import com.uws.auth_service.model.RefreshToken;
import com.uws.auth_service.model.User;
import com.uws.auth_service.repository.RefreshTokenRepository;
import com.uws.auth_service.repository.UserRepository;
import com.uws.auth_service.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Register a new user
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        log.info("Registering new user: {}", req.getUsername());

        if (userRepository.existsByUsername(req.getUsername())) {
            throw new DuplicateUserException("Username already exists: " + req.getUsername());
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateUserException("Email already registered: " + req.getEmail());
        }

        User user = User.builder()
                .username(req.getUsername().toLowerCase().trim())
                .name(req.getName().trim())
                .email(req.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("USER")
                .active(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        // Publish UserCreated event to Kafka
        publishUserCreatedEvent(savedUser);

        // Access token
        String accessToken = jwtUtil.generateAccessToken(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
        System.out.println("access token"+accessToken);

        // Refresh Token
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId());
        System.out.println("refresh token"+refreshToken);

        saveRefreshToken(savedUser.getId(), refreshToken);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    /**
     * Login user with username/email and password
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        log.info("Login attempt started for {}", req.getIdentifier());

        User user = userRepository.findByUsernameOrEmail(req.getIdentifier())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

        if (!user.getActive()) {
            throw new InvalidCredentialsException("Account is deactivated. Please contact support.");
        }

        if(!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        log.info("User logged in successfully: {}", user.getId());

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );
        System.out.println("access token"+accessToken);

        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        System.out.println("refresh token"+refreshToken);
        saveRefreshToken(user.getId(), refreshToken);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        log.info("Refreshing access token");

        String token = req.getRefreshToken();

        if (!jwtUtil.validateTokenType(token, "refresh")) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        // Extract userId from token
        String userId = jwtUtil.extractClaims(token).get("userId", String.class);

        // Check if token exists in database and is valid
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!refreshToken.isValid()) {
            throw new InvalidTokenException("Refresh token is revoked or expired");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        // Check if user is active
        if (!user.getActive()) {
            throw new InvalidCredentialsException("Account is deactivated");
        }

        // Generate new access token
        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole()
        );

        log.info("Access token refreshed for user: {}", userId);

        return buildAuthResponse(user, newAccessToken, token);
    }

    /**
     * Logout user by revoking refresh token
     */
    @Transactional
    public void logout(String refreshToken) {
        log.info("Logging out user");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required for logout");
        }

        // Revoke the specific refresh token
        refreshTokenRepository.revokeByToken(refreshToken);

        log.info("User logged out successfully");
    }

    private void saveRefreshToken(String userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpirationSeconds()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token saved for user: {}", userId);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(user.getRole())
                        .build())
                .build();
    }

    private void publishUserCreatedEvent(User user) {
        UserCreatedEvent userCreatedEvent = UserCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("UserCreated")
                .userId(user.getId())
                .timestamp(LocalDateTime.now())
                .data(UserCreatedEvent.UserData.builder()
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(user.getRole())
                        .build())
                .build();

        kafkaTemplate.send(KafkaConfig.USER_EVENTS_TOPIC, user.getId(), userCreatedEvent);
        log.info("Published UserCreatedEvent for user: {}", user.getId());
    }

    /**
     * Scheduled task to clean up expired or revoked refresh tokens
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired/revoked refresh tokens");
        refreshTokenRepository.deleteExpiredOrRevokedTokens(LocalDateTime.now());
        log.info("Cleanup completed");
    }
}
