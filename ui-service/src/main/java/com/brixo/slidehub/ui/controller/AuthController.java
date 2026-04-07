package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.exception.UserAlreadyExistsException;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Controller de autenticación (HU-001, HU-002, HU-003).
 *
 * El POST de login local lo procesa Spring Security directamente.
 * Este controller gestiona: vistas GET, POST /register, verificación de email,
 * perfil de cuenta y vinculación de proveedores OAuth2.
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;

    public AuthController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Muestra el formulario de login.
     * Si la sesión ya está activa, redirige a /presenter (HU-001 §3).
     */
    @GetMapping("/login")
    public String loginPage(Authentication authentication,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/presenter";
        }
        if (error != null) {
            // Mensaje genérico — sin indicar qué campo falló (HU-001 §2)
            model.addAttribute("errorMessage", "Credenciales incorrectas. Inténtalo de nuevo.");
        }
        if ("oauth2".equals(error)) {
            model.addAttribute("errorMessage", "Error al iniciar sesión con el proveedor externo.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Sesión cerrada correctamente.");
        }
        return "auth/login";
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    /**
     * Muestra el formulario de registro.
     */
    @GetMapping("/register")
    public String registerPage(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/presenter";
        }
        return "auth/register";
    }

    /**
     * Procesa el registro de una nueva cuenta local (HU-002).
     * Crea el usuario, lo persiste en PostgreSQL y envía email de verificación.
     */
    @PostMapping("/register")
    public String register(@RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Las contraseñas no coinciden.");
            return "auth/register";
        }
        if (password.length() < 8) {
            model.addAttribute("errorMessage", "La contraseña debe tener al menos 8 caracteres.");
            return "auth/register";
        }
        try {
            userService.registerUser(username, email, password);
            model.addAttribute("successMessage",
                    "¡Cuenta creada! Revisa tu email (" + email
                            + ") para confirmar tu cuenta antes de iniciar sesión.");
            return "auth/register";
        } catch (UserAlreadyExistsException ex) {
            // Mensaje genérico para no revelar si fue el username o el email (seguridad)
            model.addAttribute("errorMessage", "El usuario o email ya está registrado. Prueba con otro.");
            return "auth/register";
        }
    }

    // ── Verificación de email ─────────────────────────────────────────────────

    /**
     * Confirma el email del usuario usando el token enviado por email.
     */
    @GetMapping("/verify")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("errorMessage", "Token de verificación no proporcionado.");
            return "auth/login";
        }
        Optional<User> verified = userService.verifyEmail(token);
        if (verified.isPresent()) {
            model.addAttribute("successMessage", "¡Email confirmado! Ya puedes iniciar sesión.");
        } else {
            model.addAttribute("errorMessage", "El enlace de verificación es inválido o ya fue usado.");
        }
        return "auth/login";
    }

    // ── Perfil / vinculación OAuth ────────────────────────────────────────────

    /**
     * Muestra el perfil del usuario autenticado con los proveedores vinculados
     * (PLAN-EXPANSION.md Fase 1, tarea 12).
     */
    @GetMapping("/profile")
    public String profilePage(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/auth/login";
        }

        String username = resolveUsername(authentication);
        userRepository.findByUsername(username).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("githubLinked", user.getGithubId() != null);
            model.addAttribute("googleLinked", user.getGoogleId() != null);
        });

        return "auth/profile";
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            // Para OAuth2, el atributo varía según el proveedor
            Object login = oAuth2User.getAttribute("login"); // GitHub
            Object email = oAuth2User.getAttribute("email"); // Google
            if (login != null)
                return login.toString();
            if (email != null)
                return email.toString();
        }
        return authentication.getName();
    }
}
