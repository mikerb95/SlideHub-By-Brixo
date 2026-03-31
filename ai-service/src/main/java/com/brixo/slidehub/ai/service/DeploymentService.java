package com.brixo.slidehub.ai.service;

import com.brixo.slidehub.ai.model.DeploymentGuide;
import com.brixo.slidehub.ai.model.RepoAnalysis;
import com.brixo.slidehub.ai.repository.DeploymentGuideRepository;
import com.brixo.slidehub.ai.repository.RepoAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta el pipeline del Tutor de Deployment (PLAN-EXPANSION.md Fase 5,
 * tarea 44).
 *
 * Flujo completo:
 * 1. analyzeRepository() → obtiene análisis técnico del repo (cache o Gemini)
 * 2. generateDockerfile() → genera Dockerfile vía Groq y actualiza RepoAnalysis
 * 3. generateDeploymentGuide() → genera guía paso a paso vía Groq y persiste en
 * {@code deployment_guides}
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private final RepoAnalysisService repoAnalysisService;
    private final RepoAnalysisRepository repoAnalysisRepository;
    private final DeploymentGuideRepository deploymentGuideRepository;
    private final GroqService groqService;

    public DeploymentService(RepoAnalysisService repoAnalysisService,
            RepoAnalysisRepository repoAnalysisRepository,
            DeploymentGuideRepository deploymentGuideRepository,
            GroqService groqService) {
        this.repoAnalysisService = repoAnalysisService;
        this.repoAnalysisRepository = repoAnalysisRepository;
        this.deploymentGuideRepository = deploymentGuideRepository;
        this.groqService = groqService;
    }

    // ── 1. Análisis de repositorio ────────────────────────────────────────────

    /**
     * Devuelve el análisis técnico del repositorio.
     * Si ya existe en MongoDB, lo devuelve del cache; si no, llama a Gemini.
     *
     * @param repoUrl URL del repositorio GitHub
     * @return análisis técnico completo
     */
    public RepoAnalysis analyzeRepository(String repoUrl) {
        return repoAnalysisService.analyze(repoUrl);
    }

    // ── 2. Generación de Dockerfile ───────────────────────────────────────────

    /**
     * Genera un Dockerfile optimizado para el proyecto usando Groq.
     * Actualiza el campo {@code dockerfile} en el {@link RepoAnalysis} guardado.
     *
     * @param repoUrl     URL del repositorio (para recuperar/actualizar el
     *                    análisis)
     * @param language    lenguaje principal detectado
     * @param framework   framework detectado
     * @param ports       puertos que expone la aplicación
     * @param environment variables de entorno requeridas
     * @return contenido del Dockerfile como texto plano
     */
    public String generateDockerfile(String repoUrl, String language, String framework,
            List<Integer> ports, List<String> environment) {
        log.info("Generando Dockerfile para {} ({}/{})", repoUrl, language, framework);

        String dockerfile = groqService.generateDockerfile(language, framework, ports, environment);

        // Persistir en RepoAnalysis para futuras consultas
        repoAnalysisRepository.findByRepoUrl(repoUrl).ifPresent(analysis -> {
            analysis.setDockerfile(dockerfile);
            repoAnalysisRepository.save(analysis);
            log.debug("Dockerfile actualizado en RepoAnalysis para {}", repoUrl);
        });

        return dockerfile;
    }

    // ── 3. Guía de despliegue ─────────────────────────────────────────────────

    /**
     * Genera (o recupera del cache) una guía de despliegue para la plataforma
     * indicada.
     *
     * Si ya existe una guía para {@code (repoUrl, platform)} en MongoDB, la
     * devuelve
     * directamente. Si no, obtiene el análisis del repo (o lo genera) y llama a
     * Groq para producir la guía.
     *
     * @param repoUrl  URL del repositorio GitHub
     * @param platform plataforma destino: "render", "vercel" o "netlify"
     * @return guía de despliegue persistida en MongoDB
     */
    @SuppressWarnings("unchecked")
    public DeploymentGuide generateDeploymentGuide(String repoUrl, String platform) {
        // ── Cache hit ──────────────────────────────────────────────────────
        Optional<DeploymentGuide> cached = deploymentGuideRepository
                .findByRepoUrlAndPlatform(repoUrl, platform);
        if (cached.isPresent()) {
            log.debug("Guía de deployment encontrada en cache: {} / {}", repoUrl, platform);
            return cached.get();
        }

        // ── Obtener análisis del repo (cache o Gemini) ─────────────────────
        RepoAnalysis analysis = repoAnalysisService.analyze(repoUrl);

        // Si el análisis no tiene Dockerfile, generarlo con Groq
        String dockerfile = analysis.getDockerfile();
        if (dockerfile == null || dockerfile.isBlank()) {
            dockerfile = groqService.generateDockerfile(
                    analysis.getLanguage(), analysis.getFramework(),
                    analysis.getPorts(), analysis.getEnvironment());
        }

        // ── Generar guía con Groq ──────────────────────────────────────────
        log.info("Generando guía de deployment para {} en {}", repoUrl, platform);
        Map<String, Object> content = groqService.generateDeploymentGuide(
                analysis.getLanguage(), analysis.getFramework(),
                platform, analysis.getEnvironment(), analysis.getDatabases());

        // ── Persistir y devolver ───────────────────────────────────────────
        DeploymentGuide guide = new DeploymentGuide();
        guide.setId(UUID.randomUUID().toString());
        guide.setRepoUrl(repoUrl);
        guide.setPlatform(platform);
        guide.setGeneratedAt(LocalDateTime.now());
        guide.setDockerfile(dockerfile);
        guide.setGuide((String) content.getOrDefault("guide", ""));
        guide.setTips((List<String>) content.getOrDefault("tips", List.of()));
        guide.setEnvironmentExample((String) content.getOrDefault("environmentExample", ""));

        DeploymentGuide saved = deploymentGuideRepository.save(guide);
        log.info("Guía de deployment guardada en MongoDB: {} / {} ({})",
                repoUrl, platform, saved.getId());
        return saved;
    }

    /**
     * Regenera la guía de despliegue ignorando el cache.
     * Útil cuando el repositorio o la configuración han cambiado.
     *
     * @param repoUrl  URL del repositorio
     * @param platform plataforma destino
     * @return nueva guía de despliegue
     */
    public DeploymentGuide regenerateDeploymentGuide(String repoUrl, String platform) {
        deploymentGuideRepository.findByRepoUrlAndPlatform(repoUrl, platform)
                .ifPresent(deploymentGuideRepository::delete);
        return generateDeploymentGuide(repoUrl, platform);
    }
}
