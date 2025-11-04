package com.example.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class LogDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LogDownloadService.class);

    // configurable log file path (use application.properties if available)
    private static final String LOG_DIRECTORY = "logs";
    private static final String LOG_FILE_NAME = "application.log";

    /**
     * Compresses the log file into a .zip and returns it as a downloadable Resource.
     */
    public ResponseEntity<Resource> downloadLogAsZip() {
        Path logPath = Paths.get(LOG_DIRECTORY, LOG_FILE_NAME);
        if (!Files.exists(logPath)) {
            logger.error("Log file not found at: {}", logPath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        // Create zipped file
        Path zipPath = logPath.resolveSibling(LOG_FILE_NAME.replace(".log", ".zip"));

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)));
             BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(logPath))) {

            ZipEntry entry = new ZipEntry(logPath.getFileName().toString());
            zos.putNextEntry(entry);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }

            zos.closeEntry();
            logger.info("Log file successfully compressed to {}", zipPath.toAbsolutePath());

        } catch (IOException e) {
            logger.error("Error while creating log zip file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }

        // Return zipped file as downloadable response
        try {
            Resource resource = new InputStreamResource(Files.newInputStream(zipPath));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipPath.getFileName())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(zipPath))
                    .body(resource);
        } catch (IOException e) {
            logger.error("Failed to read zipped log file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
