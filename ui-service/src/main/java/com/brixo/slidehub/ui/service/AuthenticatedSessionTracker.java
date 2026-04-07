package com.brixo.slidehub.ui.service;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthenticatedSessionTracker implements HttpSessionListener, HttpSessionAttributeListener {

    private static final String SECURITY_CONTEXT_ATTRIBUTE = "SPRING_SECURITY_CONTEXT";

    private final Set<String> authenticatedSessionIds = ConcurrentHashMap.newKeySet();

    public boolean hasAuthenticatedSessions() {
        return !authenticatedSessionIds.isEmpty();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        if (session != null) {
            authenticatedSessionIds.remove(session.getId());
        }
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        if (!SECURITY_CONTEXT_ATTRIBUTE.equals(event.getName())) {
            return;
        }
        updateSessionStatus(event.getSession(), event.getValue());
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        if (!SECURITY_CONTEXT_ATTRIBUTE.equals(event.getName())) {
            return;
        }
        Object newValue = event.getSession().getAttribute(SECURITY_CONTEXT_ATTRIBUTE);
        updateSessionStatus(event.getSession(), newValue);
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        if (!SECURITY_CONTEXT_ATTRIBUTE.equals(event.getName())) {
            return;
        }
        authenticatedSessionIds.remove(event.getSession().getId());
    }

    private void updateSessionStatus(HttpSession session, Object value) {
        if (session == null) {
            return;
        }

        if (!isAuthenticatedContext(value)) {
            authenticatedSessionIds.remove(session.getId());
            return;
        }

        authenticatedSessionIds.add(session.getId());
    }

    private boolean isAuthenticatedContext(Object value) {
        if (!(value instanceof SecurityContext context)) {
            return false;
        }

        Authentication authentication = context.getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return !(authentication instanceof AnonymousAuthenticationToken);
    }
}