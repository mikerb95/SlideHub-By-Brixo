package com.brixo.slidehub.ui.config;

import com.brixo.slidehub.ui.service.UserActivityTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserActivityTrackingFilter extends OncePerRequestFilter {

    private final UserActivityTracker userActivityTracker;

    public UserActivityTrackingFilter(UserActivityTracker userActivityTracker) {
        this.userActivityTracker = userActivityTracker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }

        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/slides/")
                || path.startsWith("/presentation/")
                || path.startsWith("/actuator/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return;
        }

        userActivityTracker.markActivityNow();
    }
}
