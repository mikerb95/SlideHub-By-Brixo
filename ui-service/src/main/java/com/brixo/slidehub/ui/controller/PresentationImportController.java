package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.DriveFile;
import com.brixo.slidehub.ui.model.DriveFolder;
import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.PresentationSummary;
import com.brixo.slidehub.ui.model.SlideInfo;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.PresentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller de importación y gestión de presentaciones (PLAN-EXPANSION.md Fase
 * 2).
 *
 * Expone dos grupos de endpoints:
 * - MVC: /presentations/** → vistas Thymeleaf para el panel de control
 * - REST: /api/presentations/** → JSON consumido por fetch() en el frontend
 *
 * El acceso a todos estos endpoints requiere rol PRESENTER o ADMIN
 * (configurado en SecurityConfig).
 */
@Controller
public class PresentationImportController {

    private static final Logger log = LoggerFactory.getLogger(PresentationImportController.class);

    private final PresentationService presentationService;
    private final UserRepository userRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public PresentationImportController(PresentationService presentationService,
            UserRepository userRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        this.presentationService = presentationService;
        this.userRepository = userRepository;
        this.authorizedClientService = authorizedClientService;
    }

    // ── Vista MVC ─────────────────────────────────────────────────────────────

    /**
     * Vista principal de importación de presentaciones.
     * Carga la lista de presentaciones del usuario para mostrarla en la misma
     * página.
     */
    @GetMapping("/presentations/import")
    public String importPage(Authentication authentication, Model model) {
        User user = resolveUser(authentication);
        List<PresentationSummary> presentations = presentationService
                .listPresentations(user.getId())
                .stream()
                .map(PresentationSummary::from)
                .toList();
        model.addAttribute("presentations", presentations);
        model.addAttribute("hasGoogleToken", hasGoogleToken(authentication));
        return "presentations/import";
    }

    // ── API JSON ──────────────────────────────────────────────────────────────

    /**
     * Lista las presentaciones del usuario en formato JSON.
     */
    @GetMapping("/api/presentations")
    @ResponseBody
    public ResponseEntity<List<PresentationSummary>> listPresentations(Authentication authentication) {
        User user = resolveUser(authentication);
        List<PresentationSummary> list = presentationService
                .listPresentations(user.getId())
                .stream()
                .map(PresentationSummary::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/api/presentations/{id}/slides")
    @ResponseBody
    public ResponseEntity<?> listSlidesForPlayback(@PathVariable String id) {
        List<SlideInfo> slides = presentationService.getSlidesForPlayback(id);
        if (slides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "presentationId", id,
                "totalSlides", slides.size(),
                "slides", slides));
    }

    /**
     * Lista las carpetas de Google Drive del usuario.
     * Requiere que el usuario haya iniciado sesión con Google (token OAuth2
     * disponible).
     */
    @GetMapping("/api/presentations/drive/folders")
    @ResponseBody
    public ResponseEntity<?> listDriveFolders(Authentication authentication) {
        String accessToken = resolveGoogleAccessToken(authentication);
        if (accessToken == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No hay token de Google. Inicia sesión con Google para acceder a Drive."));
        }
        List<DriveFolder> folders = presentationService.listDriveFolders(accessToken);
        return ResponseEntity.ok(Map.of("folders", folders));
    }

    /**
     * Lista las imágenes disponibles en una carpeta de Google Drive.
     */
    @GetMapping("/api/presentations/drive/folders/{folderId}/images")
    @ResponseBody
    public ResponseEntity<?> listDriveImages(@PathVariable String folderId,
            Authentication authentication) {
        String accessToken = resolveGoogleAccessToken(authentication);
        if (accessToken == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No hay token de Google disponible."));
        }
        List<DriveFile> images = presentationService.listDriveImages(folderId, accessToken);
        return ResponseEntity.ok(Map.of("images", images));
    }

    /**
     * Crea una presentación importando imágenes desde Google Drive.
     *
     * @param name            nombre de la presentación
     * @param description     descripción (opcional)
     * @param driveFolderId   ID de la carpeta de Drive
     * @param driveFolderName nombre de la carpeta (solo para mostrar)
     * @param repoUrl         URL del repositorio GitHub (opcional, para Fase 3)
     */
    @PostMapping("/api/presentations/create-from-drive")
    @ResponseBody
    public ResponseEntity<?> createFromDrive(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String driveFolderId,
            @RequestParam(required = false, defaultValue = "") String driveFolderName,
            @RequestParam(required = false) String repoUrl,
            Authentication authentication) {

        String accessToken = resolveGoogleAccessToken(authentication);
        if (accessToken == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No hay token de Google disponible."));
        }

        try {
            User user = resolveUser(authentication);
            Presentation presentation = presentationService.createFromDrive(
                    user, name, description, driveFolderId, driveFolderName, repoUrl, accessToken);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "presentationId", presentation.getId(),
                    "totalSlides", presentation.getSlides().size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error importando presentación desde Drive: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al importar la presentación: " + e.getMessage()));
        }
    }

    /**
     * Crea una presentación a partir de archivos subidos manualmente.
     *
     * @param name        nombre de la presentación
     * @param description descripción (opcional)
     * @param repoUrl     URL del repositorio GitHub (opcional)
     * @param files       archivos de imagen (multipart/form-data)
     */
    @PostMapping("/api/presentations/create-from-upload")
    @ResponseBody
    public ResponseEntity<?> createFromUpload(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String repoUrl,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {

        try {
            User user = resolveUser(authentication);
            Presentation presentation = presentationService.createFromUpload(
                    user, name, description, repoUrl, files);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "presentationId", presentation.getId(),
                    "totalSlides", presentation.getSlides().size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creando presentación desde upload: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al procesar los archivos: " + e.getMessage()));
        }
    }

    @PostMapping("/api/presentations/{presentationId}/quick-slide")
    @ResponseBody
    public ResponseEntity<?> createQuickSlide(
            @PathVariable String presentationId,
            @RequestParam String title,
            @RequestParam(required = false) String body,
            Authentication authentication) {
        try {
            User user = resolveUser(authentication);
            return ResponseEntity.ok(presentationService.createQuickSlide(
                    user.getId(),
                    presentationId,
                    title,
                    body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error creando quick slide para {}: {}", presentationId, ex.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Resuelve el {@link User} JPA autenticado a partir del {@link Authentication}.
     * Busca por email o nombre de usuario según el tipo de autenticación.
     */
    private User resolveUser(Authentication authentication) {
        String identifier = authentication.getName();
        Optional<User> user = userRepository.findByEmail(identifier);
        return user.orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado: " + identifier));
    }

    /**
     * Extrae el access token de Google OAuth2 del contexto de seguridad.
     * Devuelve {@code null} si el usuario no ha iniciado sesión con Google
     * o si el token ya no está disponible.
     */
    private String resolveGoogleAccessToken(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            // Login local — no tiene token de Google
            return null;
        }
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName());
        if (client == null || client.getAccessToken() == null) {
            return null;
        }
        return client.getAccessToken().getTokenValue();
    }

    /**
     * Indica si el usuario tiene un token de Google activo (para condicionar la
     * UI).
     */
    private boolean hasGoogleToken(Authentication authentication) {
        return resolveGoogleAccessToken(authentication) != null;
    }
}
