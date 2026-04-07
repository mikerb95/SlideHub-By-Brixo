package com.brixo.slidehub.ui.model;

import java.util.List;

/**
 * Resultado de explorar una carpeta de Google Drive.
 * Contiene subcarpetas y archivos de imagen por separado.
 */
public record DriveContents(List<DriveFolder> folders, List<DriveFile> files) {
}
