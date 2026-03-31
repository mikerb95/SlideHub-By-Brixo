package com.brixo.slidehub.ai.model;

import java.util.List;

/**
 * Contenido estructurado de una nota del presentador, tal como lo devuelve
 * Groq.
 *
 * Used by GroqService para parsear la respuesta JSON del LLM.
 */
public record NoteContent(
        String title,
        List<String> points,
        String suggestedTime,
        List<String> keyPhrases,
        List<String> demoTags) {
}
