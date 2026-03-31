package com.brixo.slidehub.ai.service;

import com.brixo.slidehub.ai.model.GenerateAllRequest;
import com.brixo.slidehub.ai.model.GenerateNoteRequest;
import com.brixo.slidehub.ai.model.NoteContent;
import com.brixo.slidehub.ai.model.PresenterNote;
import com.brixo.slidehub.ai.model.SlideReference;
import com.brixo.slidehub.ai.repository.PresenterNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Optional;

/**
 * Pipeline de generación de notas del presentador (PLAN-EXPANSION.md Fase 3).
 *
 * Flujo de 3 pasos:
 * 1. Análisis de imagen con Gemini Vision → descripción del slide
 * 2. Extracción de contexto técnico del repositorio con Gemini
 * 3. Generación de nota estructurada con Groq
 */
@Service
public class NotesService {

    private static final Logger log = LoggerFactory.getLogger(NotesService.class);

    /** Pausa entre slides en generate-all para no saturar las APIs. */
    private static final int INTER_SLIDE_DELAY_MS = 1500;

    private final GeminiService geminiService;
    private final GroqService groqService;
    private final PresenterNoteRepository noteRepository;
    private final WebClient imageDownloadClient;

    public NotesService(GeminiService geminiService,
            GroqService groqService,
            PresenterNoteRepository noteRepository) {
        this.geminiService = geminiService;
        this.groqService = groqService;
        this.noteRepository = noteRepository;
        // Cliente genérico para descargar imágenes desde S3 (URLs públicas)
        this.imageDownloadClient = WebClient.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    // ── Generación individual ─────────────────────────────────────────────────

    /**
     * Genera y guarda la nota del presentador para un slide.
     *
     * Pipeline:
     * <ol>
     * <li>Si hay imageData (base64) o imageUrl, analiza la imagen con Gemini Vision
     * →
     * descripción</li>
     * <li>Si hay repoUrl, extrae contexto técnico relevante con Gemini</li>
     * <li>Genera nota estructurada con Groq</li>
     * <li>Persiste en MongoDB (sobreescribe si ya existe — HU-016 §2)</li>
     * </ol>
     *
     * @param request parámetros de la solicitud
     * @return nota guardada
     */
    public PresenterNote generate(GenerateNoteRequest request) {
        log.info("Generando nota para presentación {} slide {}",
                request.presentationId(), request.slideNumber());

        // ── Paso 1: Descripción del slide ──────────────────────────────────
        String slideDescription = resolveSlideDescription(request);

        // ── Paso 2: Contexto técnico del repositorio ───────────────────────
        String repoContext = "";
        if (request.repoUrl() != null && !request.repoUrl().isBlank()) {
            repoContext = geminiService.extractRepoContext(request.repoUrl(), slideDescription);
        }

        // ── Paso 3: Generación de nota con Groq ────────────────────────────
        NoteContent noteContent = groqService.generateNote(
                repoContext, slideDescription, request.slideNumber());

        // ── Paso 4: Persistencia (upsert) ─────────────────────────────────
        return saveOrUpdate(request.presentationId(), request.slideNumber(), noteContent);
    }

    /**
     * Genera notas para todos los slides de una presentación.
     * Pausa {@value INTER_SLIDE_DELAY_MS} ms entre slides para respetar rate
     * limits.
     *
     * @param request solicitud con presentationId, repoUrl y la lista de slides
     * @return número de notas generadas exitosamente
     */
    public int generateAll(GenerateAllRequest request) {
        log.info("generate-all: {} slides para presentación {}",
                request.slides().size(), request.presentationId());

        int generated = 0;
        for (SlideReference slide : request.slides()) {
            try {
                GenerateNoteRequest noteRequest = new GenerateNoteRequest(
                        request.presentationId(),
                        slide.slideNumber(),
                        request.repoUrl(),
                        null, // imageData: null; se descargará desde imageUrl
                        slide.imageUrl(),
                        null // slideContext: null (se usará la imagen)
                );
                generate(noteRequest);
                generated++;
                log.debug("Nota generada: slide {}/{}", slide.slideNumber(), request.slides().size());

                // Pausa para respetar los rate limits de Gemini y Groq
                if (generated < request.slides().size()) {
                    Thread.sleep(INTER_SLIDE_DELAY_MS);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("generate-all interrumpido en slide {}", slide.slideNumber());
                break;
            } catch (Exception e) {
                log.error("Error en slide {} durante generate-all: {}",
                        slide.slideNumber(), e.getMessage());
                // No abortamos — continuamos con el siguiente slide
            }
        }

        log.info("generate-all completado: {}/{} notas generadas",
                generated, request.slides().size());
        return generated;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Determina la descripción textual del slide a partir de los parámetros
     * disponibles.
     * Prioridad: imagen (base64 o URL) > slideContext (texto).
     */
    private String resolveSlideDescription(GenerateNoteRequest request) {
        // Caso 1: imageData en Base64 directo
        if (request.imageData() != null && !request.imageData().isBlank()) {
            byte[] imageBytes = Base64.getDecoder().decode(request.imageData());
            String description = geminiService.analyzeSlideImage(imageBytes);
            if (!description.isBlank())
                return description;
        }

        // Caso 2: URL de imagen (S3) para descarga
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            byte[] imageBytes = downloadImage(request.imageUrl());
            if (imageBytes.length > 0) {
                String description = geminiService.analyzeSlideImage(imageBytes);
                if (!description.isBlank())
                    return description;
            }
        }

        // Fallback: slideContext provisto manualmente
        if (request.slideContext() != null && !request.slideContext().isBlank()) {
            return request.slideContext();
        }

        return "Slide " + request.slideNumber();
    }

    /**
     * Descarga los bytes de una imagen desde una URL pública (S3).
     */
    private byte[] downloadImage(String url) {
        try {
            byte[] data = imageDownloadClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            return data != null ? data : new byte[0];
        } catch (Exception e) {
            log.warn("No se pudo descargar imagen desde {}: {}", url, e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Guarda o actualiza la nota en MongoDB (upsert por presentationId +
     * slideNumber).
     * Si ya existe una nota para ese slide, sobreescribe su contenido (HU-016 §2).
     */
    private PresenterNote saveOrUpdate(String presentationId, int slideNumber,
            NoteContent content) {
        Optional<PresenterNote> existing = noteRepository.findByPresentationIdAndSlideNumber(presentationId,
                slideNumber);

        PresenterNote note = existing.orElseGet(PresenterNote::new);
        // Preserva el _id si existe, para que MongoDB haga un update en lugar de insert
        note.setPresentationId(presentationId);
        note.setSlideNumber(slideNumber);
        note.setTitle(content.title());
        note.setPoints(content.points());
        note.setSuggestedTime(content.suggestedTime());
        note.setKeyPhrases(content.keyPhrases());
        note.setDemoTags(content.demoTags());

        return noteRepository.save(note);
    }
}
