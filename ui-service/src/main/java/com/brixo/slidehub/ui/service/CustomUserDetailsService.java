package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Adaptador entre Spring Security y la tabla de usuarios en PostgreSQL
 * (CLAUDE.md §11).
 * Carga el usuario por username — Spring Security lo llama en el proceso de
 * login local.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Cuentas OAuth2-only sin contraseña local no pueden hacer login con formulario
        String passwordHash = user.getPasswordHash() != null
                ? user.getPasswordHash()
                : "{noop}__oauth2_only__"; // Spring Security no puede autenticar con este valor

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                passwordHash,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
