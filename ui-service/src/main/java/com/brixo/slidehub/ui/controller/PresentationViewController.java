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
 * Controller que sirve las vistas públicas de presentación (HU-005, HU-004,
 * HU-010, HU-012).
 *
 * Fase 4: /remote acepta presentationId para cargar quick links en el bottom
 * sheet (PLAN-EXPANSION.md tarea 39).
 */
@Controller
public class PresentationViewController {

    @Value("${slidehub.poll.slides.interval-ms:1000}")
    private int slidePollIntervalMs;

    @Value("${slidehub.poll.demo.interval-ms:800}")
    private int demoPollIntervalMs;

    private final QuickLinkService quickLinkService;

    public PresentationViewController(QuickLinkService quickLinkService) {
        this.quickLinkService = quickLinkService;
    }

    /**
     * Redirección raíz a showcase (HU-012).
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/showcase";
    }

    /**
     * Vista del proyector/TV (HU-005) — polling a /api/slide para sincronización.
     */
    @GetMapping("/slides")
    public String slidesView(
            @RequestParam(name = "presentationId", required = false) String presentationId,
            Model model) {
        model.addAttribute("pollIntervalMs", slidePollIntervalMs);
        model.addAttribute("presentationId", presentationId != null ? presentationId : "");
        return "slides";
    }

    /**
     * Control remoto para smartphone (HU-004).
     * Fase 4: carga quick links si se pasa presentationId.
     */
    @GetMapping("/remote")
    public String remoteView(
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
        return "remote";
    }

    /**
     * Pantalla dual slides/iframe (HU-010, HU-011) — polling a /api/demo.
     */
    @GetMapping("/demo")
    public String demoView(
            @RequestParam(name = "presentationId", required = false) String presentationId,
            Model model) {
        model.addAttribute("pollIntervalMs", demoPollIntervalMs);
        model.addAttribute("presentationId", presentationId != null ? presentationId : "");
        return "demo";
    }

    /**
     * Landing page pública (HU-012).
     */
    @GetMapping("/showcase")
    public String showcaseView() {
        return "showcase";
    }
}
