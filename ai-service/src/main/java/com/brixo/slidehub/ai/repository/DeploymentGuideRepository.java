package com.brixo.slidehub.ai.repository;

import com.brixo.slidehub.ai.model.DeploymentGuide;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Repositorio MongoDB para guias de despliegue generadas por IA
 * (Plan-Expansion.md Fase 5, tarea 43).
 */
public interface DeploymentGuideRepository extends MongoRepository<DeploymentGuide, String> {

    /**
     * Busca una guia existente por URL de repositorio y plataforma.
     * usada para evitar regenerar guias ya producidas (cache).
     */
    Optional<DeploymentGuide> findByRepoUrlAndPlataforma(String repoUrl, String platform);

}