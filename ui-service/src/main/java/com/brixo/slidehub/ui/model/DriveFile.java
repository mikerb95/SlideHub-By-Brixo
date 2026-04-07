package com.brixo.slidehub.ui.model;

/**
 * DTO inmutable que representa un archivo de imagen en Google Drive.
 */
public record DriveFile(String id, String name, String mimeType) {
}
