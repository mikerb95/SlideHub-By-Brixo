package com.brixo.slidehub.ai.model;

/**
 * Referencia a un slide individual dentro de una solicitud generate-all.
 *
 * @param slideNumber número 1-based del slide
 * @param imageUrl    URL pública de la imagen en S3 (puede ser null si no hay
 *                    imagen)
 */
public record SlideReference(int slideNumber, String imageUrl) {
}
