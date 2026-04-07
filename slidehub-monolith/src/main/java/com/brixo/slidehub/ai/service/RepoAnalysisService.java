package com.brixo.slidehub.ai.service;

import com.brixo.slidehub.ai.model.RepoAnalysis;
import com.brixo.slidehub.ai.repository.RepoAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de análisis de repositorios GitHub con Gemini (PLAN-EXPANSION.md
 * Fase 3,
 * tarea 33).
 *
 * Cachea los resultados en MongoDB para evitar llamadas repetidas a la API.
 * El análisis incluye: lenguaje, framework, tecnologías, build system,
 * arquitectura,
 * hints de deployment y Dockerfile candidato.
 */
@Service
public class RepoAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RepoAnalysisService.class);

    private final GeminiService geminiService;
    private final GitHubRepoContextService gitHubRepoContextService;
    private final RepoAnalysisRepository repository;
    private final ObjectMapper objectMapper;

    public RepoAnalysisService(GeminiService geminiService,
            GitHubRepoContextService gitHubRepoContextService,
            RepoAnalysisRepository repository,
            ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.gitHubRepoContextService = gitHubRepoContextService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Analiza un repositorio y devuelve su análisis técnico.
     *
     * Si ya existe un análisis en MongoDB, lo devuelve sin llamar a Gemini.
     * Si no, llama a Gemini, parsea la respuesta, la guarda y la devuelve.
     *
     * @param repoUrl URL del repositorio GitHub
     * @return análisis técnico del repositorio
     */
    public RepoAnalysis analyze(String repoUrl) {
        Optional<GitHubRepoContextService.RepositorySnapshot> snapshotOpt = gitHubRepoContextService
                .resolveSnapshot(repoUrl);

        String normalizedRepoUrl = snapshotOpt
                .map(GitHubRepoContextService.RepositorySnapshot::normalizedRepoUrl)
                .orElse(repoUrl);

        Optional<RepoAnalysis> cached = repository.findByRepoUrl(normalizedRepoUrl);
        if (cached.isPresent()) {
            RepoAnalysis cachedAnalysis = cached.get();
            if (snapshotOpt.isEmpty()) {
                log.debug("Análisis de repo encontrado en cache (sin snapshot): {}", normalizedRepoUrl);
                return cachedAnalysis;
            }

            String incomingHead = snapshotOpt.get().headCommitSha();
            if (incomingHead != null && incomingHead.equals(cachedAnalysis.getSourceCommitSha())) {
                log.debug("Cache hit por commit SHA para {}: {}", normalizedRepoUrl, incomingHead);
                return cachedAnalysis;
            }

            log.info("Cache inválida por cambio de commit en {}. previo={}, nuevo={}",
                    normalizedRepoUrl, cachedAnalysis.getSourceCommitSha(), incomingHead);
        }

        // ── Cache miss — llamar a Gemini ───────────────────────────────────
        log.info("Analizando repositorio con Gemini: {}", normalizedRepoUrl);
        String rawJson = geminiService.analyzeRepoRaw(
                normalizedRepoUrl,
                snapshotOpt.map(GitHubRepoContextService.RepositorySnapshot::contextText).orElse(null));

        RepoAnalysis analysis = parseGeminiResponse(rawJson, normalizedRepoUrl);
        analysis.setRepoUrl(normalizedRepoUrl);
        snapshotOpt.ifPresent(snapshot -> {
            analysis.setSourceBranch(snapshot.defaultBranch());
            analysis.setSourceCommitSha(snapshot.headCommitSha());
        });
        analysis.setAnalyzedAt(LocalDateTime.now());

        RepoAnalysis saved = repository.save(analysis);
        log.info("Análisis de repo guardado en MongoDB: {} ({})", normalizedRepoUrl, saved.getId());
        return saved;
    }

    /**
     * Fuerza un nuevo análisis ignorando la cache.
     * Útil cuando el repositorio ha cambiado significativamente.
     *
     * @param repoUrl URL del repositorio
     * @return nuevo análisis técnico
     */
    public RepoAnalysis reanalyze(String repoUrl) {
        // Eliminar análisis previo si existe
        String normalizedRepoUrl = normalizeRepoUrl(repoUrl);
        repository.findByRepoUrl(normalizedRepoUrl).ifPresent(repository::delete);
        return analyze(repoUrl);
    }

    public Optional<RepoAnalysis> findByRepoUrl(String repoUrl) {
        return repository.findByRepoUrl(normalizeRepoUrl(repoUrl));
    }

    public void updateDockerfile(String repoUrl, String dockerfile) {
        findByRepoUrl(repoUrl).ifPresent(analysis -> {
            analysis.setDockerfile(dockerfile);
            repository.save(analysis);
            log.debug("Dockerfile actualizado en RepoAnalysis para {}", analysis.getRepoUrl());
        });
    }

    public String normalizeRepoUrl(String repoUrl) {
        return gitHubRepoContextService.resolveSnapshot(repoUrl)
                .map(GitHubRepoContextService.RepositorySnapshot::normalizedRepoUrl)
                .orElse(repoUrl);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Parsea el JSON devuelto por Gemini en un objeto {@link RepoAnalysis}.
     * Si el parsing falla, devuelve un RepoAnalysis con datos parciales y loguea el
     * error.
     */
    private RepoAnalysis parseGeminiResponse(String rawJson, String repoUrl) {
        RepoAnalysis analysis = new RepoAnalysis();

        try {
            JsonNode root = objectMapper.readTree(rawJson);

            analysis.setLanguage(textOrDefault(root, "language", "Desconocido"));
            analysis.setFramework(textOrDefault(root, "framework", "Desconocido"));
            analysis.setTechnologies(toStringList(root.path("technologies")));
            analysis.setBuildSystem(textOrDefault(root, "buildSystem", "Desconocido"));
            analysis.setPorts(toIntegerList(root.path("ports")));
            analysis.setEnvironment(toStringList(root.path("environment")));
            analysis.setDatabases(toStringList(root.path("databases")));
            analysis.setSummary(textOrDefault(root, "summary", ""));
            analysis.setStructure(textOrDefault(root, "structure", ""));
            analysis.setDeploymentHints(textOrDefault(root, "deploymentHints", ""));
            analysis.setDockerfile(textOrDefault(root, "dockerfile", ""));

        } catch (Exception e) {
            log.error("Error parseando respuesta de Gemini para repo {}: {}", repoUrl, e.getMessage());
            log.debug("JSON recibido: {}", rawJson);
            // Datos parciales: al menos guardamos el raw como summary
            analysis.setLanguage("Desconocido");
            analysis.setFramework("Desconocido");
            analysis.setTechnologies(List.of());
            analysis.setBuildSystem("Desconocido");
            analysis.setPorts(List.of());
            analysis.setEnvironment(List.of());
            analysis.setDatabases(List.of());
            analysis.setSummary("Error al parsear análisis: " + rawJson);
            analysis.setStructure("");
            analysis.setDeploymentHints("");
            analysis.setDockerfile("");
        }

        return analysis;
    }

    private java.util.List<Integer> toIntegerList(JsonNode arrayNode) {
        java.util.List<Integer> list = new ArrayList<>();
        if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return list;
        }
        for (JsonNode item : arrayNode) {
            if (item.isNumber())
                list.add(item.asInt());
        }
        return list;
    }

    private String textOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asString(defaultValue);
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return list;
        }
        for (JsonNode item : arrayNode) {
            if (item.isString())
                list.add(item.asString());
        }
        return list;
    }
}
