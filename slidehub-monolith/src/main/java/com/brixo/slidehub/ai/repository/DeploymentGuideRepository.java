package com.brixo.slidehub.ai.repository;

import com.brixo.slidehub.ai.model.DeploymentGuide;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio MongoDB para guías de despliegue generadas por IA
 * (PLAN-EXPANSION.md Fase 5, tarea 43).
 */
public interface DeploymentGuideRepository extends MongoRepository<DeploymentGuide, String> {

    /**
     * Busca una guía existente por URL de repositorio y plataforma.
     * Usada para evitar regenerar guías ya producidas (cache).
     */
    Optional<DeploymentGuide> findByRepoUrlAndPlatform(String repoUrl, String platform);
}
