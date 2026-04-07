package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.exception.UserAlreadyExistsException;
import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de negocio de usuarios: registro, verificación de email (HU-001/002).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${slidehub.base-url:http://localhost:8082}")
    private String baseUrl;

    public UserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Registra un nuevo usuario local con BCrypt.
     * Envía email de verificación via Resend.
     *
     * @throws UserAlreadyExistsException si el username o email ya existe
     */
    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UserAlreadyExistsException("El nombre de usuario ya está registrado.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El email ya está registrado.");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.PRESENTER);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(verificationToken);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        log.info("Usuario registrado: {} ({})", username, email);

        sendVerificationEmail(email, verificationToken);

        return saved;
    }

    /**
     * Verifica el email del usuario usando el token de confirmación.
     * Devuelve el usuario si el token es válido; vacío si ya expiró o es
     * incorrecto.
     */
    @Transactional
    public Optional<User> verifyEmail(String token) {
        return userRepository.findByEmailVerificationToken(token)
                .map(user -> {
                    user.setEmailVerified(true);
                    user.setEmailVerificationToken(null);
                    log.info("Email verificado para usuario: {}", user.getUsername());
                    return userRepository.save(user);
                });
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    private void sendVerificationEmail(String email, String token) {
        String confirmUrl = baseUrl + "/auth/verify?token=" + token;
        String html = """
                <h2 style="font-family:sans-serif">Confirma tu cuenta en SlideHub</h2>
                <p style="font-family:sans-serif">
                    Haz clic en el enlace para activar tu cuenta:
                </p>
                <a href="%s"
                   style="background:#1f6feb;color:white;padding:10px 20px;
                          text-decoration:none;border-radius:6px;font-family:sans-serif">
                    Confirmar cuenta
                </a>
                <p style="font-family:sans-serif;color:#8b949e;font-size:0.85rem;margin-top:1rem">
                    Si no creaste esta cuenta, puedes ignorar este mensaje.
                </p>
                """.formatted(confirmUrl);

        emailService.send(email, "Confirma tu cuenta en SlideHub", html);
    }
}
