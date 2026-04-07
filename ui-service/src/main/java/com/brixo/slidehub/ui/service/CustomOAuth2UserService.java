package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa el login via GitHub y Google (PLAN-EXPANSION.md Fase 1, tareas 9-12).
 *
 * Estrategia "merge by email":
 * 1. Si ya existe usuario con ese githubId/googleId → actualiza tokens.
 * 2. Si existe usuario con mismo email → vincula proveedor a esa cuenta.
 * 3. Si no existe → crea cuenta nueva con rol PRESENTER.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        try {
            User user = switch (registrationId) {
                case "github" -> processGithubUser(request, oauth2User);
                case "google" -> processGoogleUser(oauth2User);
                default -> throw new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_provider"),
                        "Proveedor OAuth2 no soportado: " + registrationId);
            };

            String nameAttribute = request.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName();

            return new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                    oauth2User.getAttributes(),
                    nameAttribute);
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error procesando usuario OAuth2 ({})): {}", registrationId, ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oauth2_processing_error"), ex.getMessage(), ex);
        }
    }

    // ── GitHub ────────────────────────────────────────────────────────────────

    private User processGithubUser(OAuth2UserRequest request, OAuth2User oauth2User) {
        String githubId = String.valueOf(oauth2User.getAttribute("id"));
        String githubUsername = oauth2User.getAttribute("login");
        String email = oauth2User.getAttribute("email"); // puede ser null (email privado)
        String accessToken = request.getAccessToken().getTokenValue();

        // 1. ¿Existe usuario con este githubId?
        Optional<User> byGithubId = userRepository.findByGithubId(githubId);
        if (byGithubId.isPresent()) {
            User existing = byGithubId.get();
            existing.setGithubUsername(githubUsername);
            existing.setGithubAccessToken(accessToken);
            log.debug("GitHub login: usuario existente vinculado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 2. ¿Existe usuario con este email?
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                existing.setGithubId(githubId);
                existing.setGithubUsername(githubUsername);
                existing.setGithubAccessToken(accessToken);
                log.info("GitHub login: vinculado a cuenta existente por email ({})", email);
                return userRepository.save(existing);
            }
        }

        // 3. Crear cuenta nueva
        String resolvedUsername = resolveUniqueUsername(githubUsername, "gh");
        String resolvedEmail = email != null ? email : resolvedUsername + "@github.oauth.placeholder";

        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(resolvedUsername);
        newUser.setEmail(resolvedEmail);
        newUser.setRole(Role.PRESENTER);
        newUser.setEmailVerified(email != null); // solo verificado si GitHub lo proveyó
        newUser.setGithubId(githubId);
        newUser.setGithubUsername(githubUsername);
        newUser.setGithubAccessToken(accessToken);
        newUser.setCreatedAt(LocalDateTime.now());
        log.info("GitHub login: nueva cuenta creada para {} ({})", resolvedUsername, resolvedEmail);
        return userRepository.save(newUser);
    }

    // ── Google ────────────────────────────────────────────────────────────────

    private User processGoogleUser(OAuth2User oauth2User) {
        String googleId = oauth2User.getAttribute("sub");
        String googleEmail = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        // 1. ¿Existe usuario con este googleId?
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User existing = byGoogleId.get();
            existing.setGoogleEmail(googleEmail);
            log.debug("Google login: usuario existente vinculado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 2. ¿Existe usuario con este email?
        if (googleEmail != null) {
            Optional<User> byEmail = userRepository.findByEmail(googleEmail);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                existing.setGoogleId(googleId);
                existing.setGoogleEmail(googleEmail);
                log.info("Google login: vinculado a cuenta existente por email ({})", googleEmail);
                return userRepository.save(existing);
            }
        }

        // 3. Crear cuenta nueva
        String baseUsername = (googleEmail != null)
                ? googleEmail.split("@")[0]
                : "google_" + googleId.substring(0, 8);
        String resolvedUsername = resolveUniqueUsername(baseUsername, "g");
        String resolvedEmail = googleEmail != null
                ? googleEmail
                : resolvedUsername + "@google.oauth.placeholder";

        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(resolvedUsername);
        newUser.setEmail(resolvedEmail);
        newUser.setRole(Role.PRESENTER);
        newUser.setEmailVerified(true); // Google siempre verifica el email
        newUser.setGoogleId(googleId);
        newUser.setGoogleEmail(googleEmail);
        newUser.setCreatedAt(LocalDateTime.now());
        log.info("Google login: nueva cuenta creada para {} ({})", resolvedUsername, resolvedEmail);
        return userRepository.save(newUser);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * Genera un username único: si 'base' ya está ocupado, prueba 'base_<prefix>N'.
     */
    private String resolveUniqueUsername(String base, String prefix) {
        if (base == null || base.isBlank())
            base = prefix + "_user";
        String candidate = base;
        int attempt = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + "_" + prefix + attempt++;
        }
        return candidate;
    }
}
