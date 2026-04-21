package com.authentication.service;

import com.authentication.exception.BadRequestException;
import com.authentication.exception.ResourceNotFoundException;
import com.authentication.exception.TokenException;
import com.authentication.security.jwt.JwtUtils;
import com.authentication.security.jwt.UserDetailsImpl;
import com.authentication.dto.request.ForgotPasswordRequest;
import com.authentication.dto.request.LoginRequest;
import com.authentication.dto.request.RegisterRequest;
import com.authentication.dto.request.ResetPasswordRequest;
import com.authentication.dto.request.TokenRefreshRequest;
import com.authentication.dto.response.AuthResponse;
import com.authentication.entity.RefreshToken;
import com.authentication.entity.Role;
import com.authentication.entity.User;
import com.authentication.repo.RoleRepository;
import com.authentication.repo.UserRepository;
import com.authentication.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

       
        // Build user
        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(false)
                .emailVerificationToken(UUID.randomUUID().toString())
                .active(true)
               
                .phoneNumber(request.getPhoneNumber())
          
                .build();

        User savedUser = userRepository.save(user);

        // Send verification email
        emailService.sendVerificationEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getEmailVerificationToken()
        );

        log.info("New user registered: {} ({})", savedUser.getEmail());

        return AuthResponse.builder()
                .message("Registration successful. Please verify your email.")
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Block login if email not verified — return 403 with a clear message
        if (!userDetails.isEmailVerified()) {
            throw new com.authentication.exception.TokenException(
                "EMAIL_NOT_VERIFIED: Please verify your email before logging in.");
        }

        String accessToken = jwtUtils.generateJwtToken(authentication);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toList());

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userDetails.getId()));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(userDetails.getId())
                .email(userDetails.getEmail())
                .fullName(user.getFullName())
                .profilePicture(user.getProfilePicture())
                .roles(roles)
                .build();
    }

    @Override
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken());
        refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtils.generateTokenFromEmail(user.getEmail(), user.getId());

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(roles)
                .build();
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.deleteByUserId(userId);
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new TokenException("Invalid or expired verification token"));

        // Direct JPQL UPDATE — avoids Hibernate merge issues with EAGER roles collection
        int updated = userRepository.markEmailVerified(user.getId());

        if (updated == 0) {
            throw new RuntimeException("Email verification failed — no rows affected");
        }

        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        log.info("Email verified for: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new TokenException("Invalid or expired reset token"));

        if (user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new TokenException("Password reset token has expired");
        }

        // Use direct JPQL UPDATE — guarantees password is persisted regardless
        // of Hibernate entity-tracking or lazy-load state issues with roles
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        int updated = userRepository.updatePasswordAndClearResetToken(user.getId(), encodedPassword);

        if (updated == 0) {
            throw new RuntimeException("Password update failed — no rows affected");
        }

        log.info("Password reset successfully for: {}", user.getEmail());

        // Send confirmation email AFTER successful DB update
        emailService.sendPasswordResetSuccessEmail(user.getEmail(), user.getFullName());
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        String newToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(newToken);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), newToken);
    }
}