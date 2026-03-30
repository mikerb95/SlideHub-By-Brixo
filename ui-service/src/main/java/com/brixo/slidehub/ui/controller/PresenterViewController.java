package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.QuickLink;
import com.brixo.slidehub.ui.service.QuickLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Controller de vistas protegidas del presentador (HU-006, HU-007).
 *
 * Fase 4: main-panel acepta presentationId para cargar quick links del sidebar
 * (PLAN-EXPANSION.md tarea 38).
 */
@Controller
public class PresenterViewController {

    @Value("${slidehub.poll.presenter.interval-ms:1500}")
    private int presenterPollIntervalMs;

    @Value("${slidehub.poll.slides.interval-ms:1000}")
    private int slidePollIntervalMs;

    private final QuickLinkService quickLinkService;

    public PresenterViewController(QuickLinkService quickLinkService) {
        this.quickLinkService = quickLinkService;
    }

    /**
     * Vista del presentador con notas y preview (HU-006).
     * Requiere rol PRESENTER o ADMIN (configurado en SecurityConfig).
     */
    @GetMapping("/presenter")
    public String presenterView(
            @RequestParam(name = "presentationId", required = false) String presentationId,
            Model model) {
        model.addAttribute("pollIntervalMs", presenterPollIntervalMs);
        model.addAttribute("presentationId", presentationId != null ? presentationId : "");
        return "presenter";
    }

    /**
     * Panel maestro para tablet 11" (HU-007).
     * Requiere rol PRESENTER o ADMIN (configurado en SecurityConfig).
     *
     * Fase 4: si se pasa presentationId, carga los quick links del sidebar.
     */
    @GetMapping("/main-panel")
    public String mainPanelView(
            @RequestParam(name = "presentationId", required = false) String presentationId,
            Model model) {
        model.addAttribute("pollIntervalMs", slidePollIntervalMs);
        model.addAttribute("presentationId", presentationId != null ? presentationId : "");

        List<QuickLink> quickLinks = List.of();
        if (presentationId != null && !presentationId.isBlank()) {
            quickLinks = quickLinkService.findByPresentation(presentationId);
        }
        model.addAttribute("quickLinks", quickLinks);
        model.addAttribute("hasQuickLinks", !quickLinks.isEmpty());
        return "main-panel";
    }

    /**
     * Tutor de deployment — genera Dockerfiles y guías de despliegue con IA
     * (PLAN-EXPANSION.md Fase 5, tarea 45).
     * Requiere rol PRESENTER o ADMIN (configurado en SecurityConfig).
     */
    @GetMapping("/deploy-tutor")
    public String deployTutorView() {
        return "deploy-tutor";
    }
}
