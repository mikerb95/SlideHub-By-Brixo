package com.brixo.slidehub.ui.config;

import com.brixo.slidehub.ui.service.CustomOAuth2UserService;
import com.brixo.slidehub.ui.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de ui-service (CLAUDE.md §11).
 *
 * Fase 1: auth local (BCrypt + PostgreSQL) + OAuth2 (GitHub, Google).
 * Estrategia de autorización según AGENTS.md §2.2.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final CustomUserDetailsService userDetailsService;
        private final CustomOAuth2UserService oAuth2UserService;

        public SecurityConfig(CustomUserDetailsService userDetailsService,
                        CustomOAuth2UserService oAuth2UserService) {
                this.userDetailsService = userDetailsService;
                this.oAuth2UserService = oAuth2UserService;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                LoginUrlAuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/auth/login");
                loginEntryPoint.setFavorRelativeUris(true);

                http
                                .authenticationProvider(authenticationProvider())
                                .authorizeHttpRequests(auth -> auth
                                                // Vistas públicas (HU-005, HU-011, HU-012, HU-013)
                                                .requestMatchers("/slides", "/remote", "/demo", "/showcase").permitAll()
                                                // Join de reunión por QR + comandos en remoto (públicos pero
                                                // tokenizados)
                                                .requestMatchers(
                                                                "/api/presentations/*/meeting/join-options",
                                                                "/api/presentations/*/meeting/join",
                                                                "/api/presentations/*/meeting/assignment-check",
                                                                "/api/presentations/*/meeting/help",
                                                                "/api/presentations/*/meeting/assist/audio")
                                                .permitAll()
                                                .requestMatchers("/api/presentations/*/slides").permitAll()
                                                // Auth pública — incluye rutas OAuth2 de Spring Security
                                                .requestMatchers("/auth/**", "/oauth2/**", "/login/oauth2/**")
                                                .permitAll()
                                                // Assets estáticos de slides
                                                .requestMatchers("/presentation/**").permitAll()
                                                // Importación y gestión de presentaciones — requiere PRESENTER o ADMIN
                                                .requestMatchers("/presentations/**").hasAnyRole("PRESENTER", "ADMIN")
                                                .requestMatchers("/api/presentations/**")
                                                .hasAnyRole("PRESENTER", "ADMIN")
                                                // Polling de dispositivos (pasa por gateway, llega como /api/**)
                                                .requestMatchers("/api/**").permitAll()
                                                // Recursos estáticos (CSS, JS, imágenes)
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/slides/**")
                                                .permitAll()
                                                // Panel del presentador, main panel y tutor de deployment — requiere
                                                // PRESENTER
                                                // o ADMIN
                                                .requestMatchers("/presenter", "/main-panel", "/deploy-tutor")
                                                .hasAnyRole("PRESENTER", "ADMIN")
                                                // Perfil del usuario — requiere estar autenticado
                                                .requestMatchers("/auth/profile").authenticated()
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(loginEntryPoint))
                                // Login local con formulario
                                .formLogin(form -> form
                                                .loginPage("/auth/login")
                                                .loginProcessingUrl("/auth/login")
                                                .defaultSuccessUrl("/presenter", true)
                                                .failureUrl("/auth/login?error=true")
                                                .permitAll())
                                // Login OAuth2 (GitHub y Google)
                                .oauth2Login(oauth -> oauth
                                                .loginPage("/auth/login")
                                                .defaultSuccessUrl("/presenter", true)
                                                .failureUrl("/auth/login?error=oauth2")
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(oAuth2UserService)))
                                .logout(logout -> logout
                                                .logoutUrl("/auth/logout")
                                                .logoutSuccessUrl("/auth/login?logout=true")
                                                .invalidateHttpSession(true));
                return http.build();
        }

        /**
         * Provider que conecta Spring Security con CustomUserDetailsService + BCrypt.
         * Inyección explícita para evitar la auto-configuración de Spring Security
         * que podría interferir con la coexistencia OAuth2 + form login.
         *
         * Spring Security 6.x: DaoAuthenticationProvider requiere UserDetailsService en
         * constructor.
         */
        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder());
                return provider;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
