package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa login OIDC (Google). Complementa CustomOAuth2UserService que
 * maneja proveedores OAuth2 no-OIDC (GitHub).
 *
 * Soporta dos flujos:
 * - Login: crea cuenta nueva o reutiliza existente (merge by email).
 * - Linking: si ya hay usuario autenticado, vincula Google a esa cuenta.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);

    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(request);

        try {
            User user = processGoogleUser(oidcUser);

            return new DefaultOidcUser(
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    "email");
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error procesando usuario OIDC (google): {}", ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oidc_processing_error"), ex.getMessage(), ex);
        }
    }

    private User processGoogleUser(OidcUser oidcUser) {
        String googleId = oidcUser.getSubject();
        String googleEmail = oidcUser.getEmail();

        // 1. Existe usuario con este googleId?
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User existing = byGoogleId.get();
            existing.setGoogleEmail(googleEmail);
            log.debug("Google OIDC login: usuario existente vinculado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 2. Linking: si hay usuario autenticado, vincular Google a esa cuenta
        Optional<User> authenticated = findAuthenticatedUser();
        if (authenticated.isPresent()) {
            User existing = authenticated.get();
            existing.setGoogleId(googleId);
            existing.setGoogleEmail(googleEmail);
            log.info("Google OIDC: vinculado a usuario autenticado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 3. Existe usuario con este email?
        if (googleEmail != null) {
            Optional<User> byEmail = userRepository.findByEmail(googleEmail);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                existing.setGoogleId(googleId);
                existing.setGoogleEmail(googleEmail);
                log.info("Google OIDC login: vinculado a cuenta existente por email ({})", googleEmail);
                return userRepository.save(existing);
            }
        }

        // 4. Crear cuenta nueva
        String baseUsername = (googleEmail != null)
                ? googleEmail.split("@")[0]
                : "google_" + googleId.substring(0, 8);
        String resolvedUsername = resolveUniqueUsername(baseUsername);
        String resolvedEmail = googleEmail != null
                ? googleEmail
                : resolvedUsername + "@google.oauth.placeholder";

        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(resolvedUsername);
        newUser.setEmail(resolvedEmail);
        newUser.setRole(Role.PRESENTER);
        newUser.setEmailVerified(true);
        newUser.setGoogleId(googleId);
        newUser.setGoogleEmail(googleEmail);
        newUser.setProfileCompleted(false);
        newUser.setCreatedAt(LocalDateTime.now());
        log.info("Google OIDC login: nueva cuenta creada para {} ({})", resolvedUsername, resolvedEmail);
        return userRepository.save(newUser);
    }

    /**
     * Busca al usuario actualmente autenticado en la base de datos.
     * Soporta sesiones iniciadas via form login, GitHub OAuth2 o Google OIDC.
     */
    private Optional<User> findAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof OAuth2User oauth2) {
            Object githubId = oauth2.getAttribute("id");
            if (githubId != null) {
                return userRepository.findByGithubId(githubId.toString());
            }
            Object googleId = oauth2.getAttribute("sub");
            if (googleId != null) {
                return userRepository.findByGoogleId(googleId.toString());
            }
        }
        return userRepository.findByUsername(auth.getName());
    }

    private String resolveUniqueUsername(String base) {
        if (base == null || base.isBlank())
            base = "g_user";
        String candidate = base;
        int attempt = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + "_g" + attempt++;
        }
        return candidate;
    }
}
