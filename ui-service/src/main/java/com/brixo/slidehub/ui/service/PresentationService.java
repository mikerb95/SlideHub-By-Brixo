package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.DriveFile;
import com.brixo.slidehub.ui.model.DriveFolder;
import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.Slide;
import com.brixo.slidehub.ui.model.SlideInfo;
import com.brixo.slidehub.ui.model.SourceType;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de negocio para importar y listar presentaciones (PLAN-EXPANSION.md
 * Fase 2).
 *
 * Flujos soportados:
 * - Importación desde Google Drive: descarga imágenes y las sube a S3.
 * - Upload manual: recibe MultipartFile[], los sube directamente a S3.
 *
 * En ambos casos las imágenes van a S3; el filesystem local de Render nunca se
 * usa.
 */
@Service
public class PresentationService {

    private static final Logger log = LoggerFactory.getLogger(PresentationService.class);

    private final PresentationRepository presentationRepository;
    private final GoogleDriveService googleDriveService;
    private final SlideUploadService slideUploadService;
    private final WebClient imageDownloadClient;

    public PresentationService(PresentationRepository presentationRepository,
            GoogleDriveService googleDriveService,
            SlideUploadService slideUploadService) {
        this.presentationRepository = presentationRepository;
        this.googleDriveService = googleDriveService;
        this.slideUploadService = slideUploadService;
        this.imageDownloadClient = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    // ── Listado de presentaciones ─────────────────────────────────────────────

    /**
     * Lista todas las presentaciones de un usuario, ordenadas por fecha de creación
     * desc.
     */
    public List<Presentation> listPresentations(String userId) {
        return presentationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Obtiene una presentación específica asegurando que pertenece al usuario.
     */
    public Optional<Presentation> getPresentation(String userId, String presentationId) {
        return presentationRepository.findByIdAndUserId(presentationId, userId);
    }

    public List<SlideInfo> getSlidesForPlayback(String presentationId) {
        return presentationRepository.findById(presentationId)
                .map(presentation -> presentation.getSlides().stream()
                        .sorted((a, b) -> Integer.compare(a.getNumber(), b.getNumber()))
                        .map(slide -> new SlideInfo(slide.getNumber(), slide.getFilename(), slide.getS3Url()))
                        .toList())
                .orElse(List.of());
    }

    // ── Importación desde Google Drive ────────────────────────────────────────

    /**
     * Lista las carpetas accesibles del Drive del usuario.
     *
     * @param accessToken OAuth2 access token del usuario
     */
    public List<DriveFolder> listDriveFolders(String accessToken) {
        return googleDriveService.listFolders(accessToken);
    }

    /**
     * Lista las imágenes disponibles dentro de una carpeta de Drive.
     *
     * @param folderId    ID de la carpeta en Drive
     * @param accessToken OAuth2 access token del usuario
     */
    public List<DriveFile> listDriveImages(String folderId, String accessToken) {
        return googleDriveService.listImagesInFolder(folderId, accessToken);
    }

    /**
     * Crea una presentación importando imágenes desde Google Drive.
     *
     * Cada imagen se descarga de Drive, se sube a S3 con la clave
     * {@code slides/{presentationId}/{slideNumber}.png} y se persiste una entidad
     * {@link Slide} por imagen.
     *
     * @param user              usuario propietario
     * @param name              nombre de la presentación
     * @param description       descripción (puede ser null)
     * @param driveFolderId     ID de la carpeta de Drive
     * @param driveFolderName   nombre de la carpeta (solo para mostrar en UI)
     * @param repoUrl           URL del repositorio GitHub (puede ser null, usada en
     *                          Fase 3)
     * @param googleAccessToken token OAuth2 de Google del usuario
     * @return presentación creada con todos sus slides
     */
    @Transactional
    public Presentation createFromDrive(User user,
            String name,
            String description,
            String driveFolderId,
            String driveFolderName,
            String repoUrl,
            String googleAccessToken) {
        List<DriveFile> images = googleDriveService.listImagesInFolder(driveFolderId, googleAccessToken);
        if (images.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontraron imágenes en la carpeta de Drive: " + driveFolderId);
        }

        Presentation presentation = buildPresentation(user, name, description, repoUrl,
                SourceType.DRIVE, driveFolderId, driveFolderName);
        presentationRepository.save(presentation);

        for (int i = 0; i < images.size(); i++) {
            DriveFile driveFile = images.get(i);
            int slideNumber = i + 1;

            byte[] imageBytes = googleDriveService.downloadImage(driveFile.id(), googleAccessToken);
            if (imageBytes.length == 0) {
                log.warn("Imagen vacía o error al descargar: {} ({})", driveFile.name(), driveFile.id());
                continue;
            }

            String s3Key = "slides/%s/%d.png".formatted(presentation.getId(), slideNumber);
            String contentType = resolveContentType(driveFile.mimeType());
            String s3Url = slideUploadService.upload(s3Key, imageBytes, contentType);

            Slide slide = buildSlide(presentation, slideNumber, slideNumber + ".png",
                    driveFile.id(), s3Url);
            presentation.getSlides().add(slide);
        }

        Presentation saved = presentationRepository.save(presentation);
        log.info("Presentación creada desde Drive: {} ({} slides)", saved.getId(), saved.getSlides().size());
        return saved;
    }

    /**
     * Crea una presentación a partir de archivos subidos manualmente.
     *
     * Los archivos se ordenan por nombre antes de asignarles número de slide.
     * Cada archivo se sube a S3 con la clave
     * {@code slides/{presentationId}/{n}.png}.
     *
     * @param user        usuario propietario
     * @param name        nombre de la presentación
     * @param description descripción (puede ser null)
     * @param repoUrl     URL del repositorio GitHub (puede ser null)
     * @param files       archivos de imagen recibidos vía multipart/form-data
     * @return presentación creada con todos sus slides
     */
    @Transactional
    public Presentation createFromUpload(User user,
            String name,
            String description,
            String repoUrl,
            List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un archivo para crear la presentación.");
        }

        Presentation presentation = buildPresentation(user, name, description, repoUrl,
                SourceType.UPLOAD, null, null);
        presentationRepository.save(presentation);

        // Ordenar por nombre original para mantener un orden predecible
        List<MultipartFile> sorted = files.stream()
                .sorted((a, b) -> {
                    String na = a.getOriginalFilename() != null ? a.getOriginalFilename() : "";
                    String nb = b.getOriginalFilename() != null ? b.getOriginalFilename() : "";
                    return na.compareToIgnoreCase(nb);
                })
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            MultipartFile file = sorted.get(i);
            int slideNumber = i + 1;

            byte[] imageBytes;
            try {
                imageBytes = file.getBytes();
            } catch (IOException e) {
                log.error("Error leyendo archivo {}: {}", file.getOriginalFilename(), e.getMessage());
                continue;
            }

            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : slideNumber + ".png";
            String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
            String s3Key = "slides/%s/%d.png".formatted(presentation.getId(), slideNumber);
            String s3Url = slideUploadService.upload(s3Key, imageBytes, contentType);

            Slide slide = buildSlide(presentation, slideNumber, slideNumber + ".png",
                    null, s3Url);
            presentation.getSlides().add(slide);
        }

        Presentation saved = presentationRepository.save(presentation);
        log.info("Presentación creada desde upload: {} ({} slides)", saved.getId(), saved.getSlides().size());
        return saved;
    }

    @Transactional
    public Map<String, Object> createQuickSlide(String userId,
            String presentationId,
            String title,
            String bodyText) {
        Presentation presentation = presentationRepository.findByIdAndUserId(presentationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Presentación no encontrada o sin permisos."));

        String effectiveTitle = title != null && !title.isBlank() ? title.trim() : "Nuevo slide";
        String effectiveBody = bodyText != null ? bodyText.trim() : "";

        int nextSlideNumber = presentation.getSlides().stream()
                .mapToInt(Slide::getNumber)
                .max()
                .orElse(0) + 1;

        Color primaryColor = detectPrimaryColor(presentation);
        byte[] imageBytes = renderQuickSlideImage(effectiveTitle, effectiveBody, primaryColor);

        String key = slideUploadService.buildSlideKey(presentation.getId(), nextSlideNumber);
        String s3Url = slideUploadService.upload(key, imageBytes, "image/png");

        Slide slide = buildSlide(presentation, nextSlideNumber, nextSlideNumber + ".png", null, s3Url);
        presentation.getSlides().add(slide);
        presentation.setUpdatedAt(LocalDateTime.now());
        presentationRepository.save(presentation);

        return Map.of(
                "success", true,
                "slideNumber", nextSlideNumber,
                "s3Url", s3Url,
                "primaryColor", "#%02X%02X%02X".formatted(primaryColor.getRed(), primaryColor.getGreen(),
                        primaryColor.getBlue()));
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Presentation buildPresentation(User user,
            String name,
            String description,
            String repoUrl,
            SourceType sourceType,
            String driveFolderId,
            String driveFolderName) {
        Presentation p = new Presentation();
        p.setId(UUID.randomUUID().toString());
        p.setUser(user);
        p.setName(name);
        p.setDescription(description);
        p.setRepoUrl(repoUrl);
        p.setSourceType(sourceType);
        p.setDriveFolderId(driveFolderId);
        p.setDriveFolderName(driveFolderName);
        LocalDateTime now = LocalDateTime.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }

    private Slide buildSlide(Presentation presentation,
            int number,
            String filename,
            String driveFileId,
            String s3Url) {
        Slide slide = new Slide();
        slide.setId(UUID.randomUUID().toString());
        slide.setPresentation(presentation);
        slide.setNumber(number);
        slide.setFilename(filename);
        slide.setDriveFileId(driveFileId);
        slide.setS3Url(s3Url);
        slide.setUploadedAt(LocalDateTime.now());
        return slide;
    }

    /**
     * Determina el content-type para S3 basado en el MIME type reportado por Drive.
     */
    private String resolveContentType(String mimeType) {
        if (mimeType == null)
            return "image/png";
        return switch (mimeType) {
            case "image/jpeg" -> "image/jpeg";
            case "image/gif" -> "image/gif";
            case "image/webp" -> "image/webp";
            default -> "image/png";
        };
    }

    private Color detectPrimaryColor(Presentation presentation) {
        if (presentation.getSlides().isEmpty()) {
            return new Color(38, 87, 173);
        }

        String firstSlideUrl = presentation.getSlides().stream()
                .filter(slide -> slide.getS3Url() != null && !slide.getS3Url().isBlank())
                .map(Slide::getS3Url)
                .findFirst()
                .orElse(null);

        if (firstSlideUrl == null) {
            return new Color(38, 87, 173);
        }

        try {
            byte[] data = imageDownloadClient.get()
                    .uri(firstSlideUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (data == null || data.length == 0) {
                return new Color(38, 87, 173);
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                return new Color(38, 87, 173);
            }

            int stepX = Math.max(1, image.getWidth() / 60);
            int stepY = Math.max(1, image.getHeight() / 60);

            int[] bins = new int[64];
            int[] sumR = new int[64];
            int[] sumG = new int[64];
            int[] sumB = new int[64];

            for (int y = 0; y < image.getHeight(); y += stepY) {
                for (int x = 0; x < image.getWidth(); x += stepX) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int bin = ((r / 64) * 16) + ((g / 64) * 4) + (b / 64);
                    bins[bin]++;
                    sumR[bin] += r;
                    sumG[bin] += g;
                    sumB[bin] += b;
                }
            }

            int bestBin = 0;
            for (int i = 1; i < bins.length; i++) {
                if (bins[i] > bins[bestBin]) {
                    bestBin = i;
                }
            }

            if (bins[bestBin] == 0) {
                return new Color(38, 87, 173);
            }

            return new Color(
                    Math.min(255, sumR[bestBin] / bins[bestBin]),
                    Math.min(255, sumG[bestBin] / bins[bestBin]),
                    Math.min(255, sumB[bestBin] / bins[bestBin]));
        } catch (Exception ex) {
            log.warn("No se pudo detectar color primario para presentación {}: {}", presentation.getId(),
                    ex.getMessage());
            return new Color(38, 87, 173);
        }
    }

    private byte[] renderQuickSlideImage(String title, String bodyText, Color primaryColor) {
        try {
            int width = 1600;
            int height = 900;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(primaryColor);
            graphics.fillRect(0, 0, width, height);

            Color overlayColor = new Color(
                    Math.max(0, primaryColor.getRed() - 25),
                    Math.max(0, primaryColor.getGreen() - 25),
                    Math.max(0, primaryColor.getBlue() - 25),
                    180);
            graphics.setColor(overlayColor);
            graphics.fillRoundRect(80, 100, width - 160, height - 200, 36, 36);

            Color textColor = resolveTextColor(primaryColor);
            graphics.setColor(textColor);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 72));
            graphics.drawString(title, 130, 240);

            graphics.setFont(new Font("SansSerif", Font.PLAIN, 40));
            List<String> lines = wrapText(graphics, bodyText, width - 260);
            int y = 330;
            for (String line : lines) {
                graphics.drawString(line, 130, y);
                y += 56;
                if (y > height - 120) {
                    break;
                }
            }

            graphics.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo renderizar la diapositiva rápida.", ex);
        }
    }

    private Color resolveTextColor(Color background) {
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue())
                / 255.0;
        return luminance > 0.5 ? new Color(18, 24, 33) : new Color(245, 247, 250);
    }

    private List<String> wrapText(Graphics2D graphics, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of("Sin descripción.");
        }

        FontMetrics fontMetrics = graphics.getFontMetrics();
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fontMetrics.stringWidth(candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
            } else {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                }
                line = new StringBuilder(word);
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
