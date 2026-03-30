package com.brixo.slidehub.ui.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Servicio de upload de diapositivas a Amazon S3 (CLAUDE.md §9.5.2).
 *
 * Los archivos nunca se guardan en el filesystem local de Render (es efímero).
 * Todo va a S3 y se devuelve la URL pública del objeto.
 */
@Service
public class SlideUploadService {

    private static final Logger log = LoggerFactory.getLogger(SlideUploadService.class);

    private final S3Client s3;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    public SlideUploadService(S3Client s3) {
        this.s3 = s3;
    }

    /**
     * Sube un archivo a S3 y devuelve su URL pública.
     *
     * @param key         clave S3 (e.g. "slides/pres-uuid/1.png")
     * @param data        bytes del archivo
     * @param contentType MIME type (e.g. "image/png")
     * @return URL pública del objeto en S3
     */
    public String upload(String key, byte[] data, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
        String url = "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
        log.info("Archivo subido a S3: {} ({})", key, contentType);
        return url;
    }

    /**
     * Elimina un archivo de S3.
     *
     * @param key clave S3 del archivo a eliminar
     */
    public void delete(String key) {
        s3.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());
        log.info("Archivo eliminado de S3: {}", key);
    }

    /**
     * Construye la clave S3 para un slide de una presentación.
     *
     * @param presentationId ID único de la presentación
     * @param slideNumber    número del slide (1-based)
     * @return clave S3: "slides/{presentationId}/{slideNumber}.png"
     */
    public String buildSlideKey(String presentationId, int slideNumber) {
        return "slides/%s/%d.png".formatted(presentationId, slideNumber);
    }
}
