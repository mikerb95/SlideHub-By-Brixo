package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.DriveFile;
import com.brixo.slidehub.ui.model.DriveFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cliente para Google Drive REST API v3 (PLAN-EXPANSION.md Fase 2, tarea 16).
 *
 * Integración HTTP pura con WebClient — sin SDK oficial de Google.
 * Requiere que el usuario haya vinculado su cuenta de Google (Fase 1).
 */
@Service
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String DRIVE_API_BASE = "https://www.googleapis.com/drive/v3";

    private final WebClient driveClient;

    public GoogleDriveService() {
        this.driveClient = WebClient.builder()
                .baseUrl(DRIVE_API_BASE)
                .build();
    }

    /**
     * Lista las carpetas del Google Drive del usuario.
     *
     * @param accessToken OAuth2 access token del usuario
     * @return lista de carpetas disponibles
     */
    public List<DriveFolder> listFolders(String accessToken) {
        String query = URLEncoder.encode(
                "mimeType='application/vnd.google-apps.folder' and trashed=false",
                StandardCharsets.UTF_8);

        try {
            Map<?, ?> response = driveClient.get()
                    .uri("/files?q={q}&fields=files(id,name)&orderBy=name",
                            "mimeType='application/vnd.google-apps.folder' and trashed=false")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null)
                return Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> files = (List<Map<String, String>>) response.get("files");
            if (files == null)
                return Collections.emptyList();

            return files.stream()
                    .map(f -> new DriveFolder(f.get("id"), f.get("name")))
                    .toList();
        } catch (Exception e) {
            log.error("Error listando carpetas de Drive: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Lista los archivos de imagen dentro de una carpeta de Drive.
     *
     * @param folderId    ID de la carpeta en Drive
     * @param accessToken OAuth2 access token del usuario
     * @return lista de archivos de imagen, ordenados por nombre
     */
    public List<DriveFile> listImagesInFolder(String folderId, String accessToken) {
        String query = "'" + folderId + "' in parents "
                + "and (mimeType contains 'image/') "
                + "and trashed=false";
        try {
            Map<?, ?> response = driveClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/files")
                            .queryParam("q", query)
                            .queryParam("fields", "files(id,name,mimeType)")
                            .queryParam("orderBy", "name")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null)
                return Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> files = (List<Map<String, String>>) response.get("files");
            if (files == null)
                return Collections.emptyList();

            return files.stream()
                    .map(f -> new DriveFile(f.get("id"), f.get("name"), f.get("mimeType")))
                    .toList();
        } catch (Exception e) {
            log.error("Error listando imágenes en carpeta {}: {}", folderId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Descarga el contenido binario de un archivo de Drive.
     *
     * @param fileId      ID del archivo en Drive
     * @param accessToken OAuth2 access token del usuario
     * @return bytes del archivo, o array vacío si hay error
     */
    public byte[] downloadImage(String fileId, String accessToken) {
        try {
            byte[] data = driveClient.get()
                    .uri("/files/{fileId}?alt=media", fileId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            return data != null ? data : new byte[0];
        } catch (Exception e) {
            log.error("Error descargando archivo {} de Drive: {}", fileId, e.getMessage());
            return new byte[0];
        }
    }
}
