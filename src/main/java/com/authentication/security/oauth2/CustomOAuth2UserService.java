package com.authentication.security.oauth2;

import com.authentication.entity.Role;
import com.authentication.entity.User;
import com.authentication.repo.RoleRepository;
import com.authentication.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oAuth2User);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest request, OAuth2User oAuth2User) {
        // Extract attributes from Google
        String email     = oAuth2User.getAttribute("email");
        String name      = oAuth2User.getAttribute("name");
        String picture   = oAuth2User.getAttribute("picture");
        String googleId  = oAuth2User.getAttribute("sub"); // Google's user ID

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not provided by Google");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();

            // If user registered with LOCAL provider, block Google login
            // (they must use email/password instead)
            if (user.getProvider() == User.AuthProvider.LOCAL) {
                throw new OAuth2AuthenticationException(
                    "This email is registered with email/password. Please login with your password.");
            }

            // Update profile picture if changed
            if (picture != null && !picture.equals(user.getProfilePicture())) {
                user.setProfilePicture(picture);
                userRepository.save(user);
            }
        } else {
            // New user — auto-register as STUDENT by default
            Role studentRole = roleRepository
                    .findByName(Role.RoleName.ROLE_STUDENT)
                    .orElseThrow(() -> new RuntimeException("ROLE_STUDENT not found. Run DataInitializer."));

            // Generate a safe username from email
            String baseUsername = email.split("@")[0]
                    .replaceAll("[^a-zA-Z0-9_]", "_")
                    .toLowerCase();
            String username = baseUsername;
            int suffix = 1;
            while (userRepository.existsByUsername(username)) {
                username = baseUsername + suffix++;
            }

            user = User.builder()
                    .fullName(name != null ? name : email.split("@")[0])
                    .username(username)
                    .email(email)
                    .password(null) // No password for OAuth users
                    .provider(User.AuthProvider.GOOGLE)
                    .providerId(googleId)
                    .profilePicture(picture)
                    .emailVerified(true) // Google already verified the email
                    .active(true)
                    .roles(Set.of(studentRole))
                    .build();

            user = userRepository.save(user);
            log.info("New Google OAuth2 user registered: {}", email);
        }

        return new OAuth2UserPrincipal(user, oAuth2User.getAttributes());
    }
}