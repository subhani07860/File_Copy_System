package com.example.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
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

	// Store logs on D: drive
	private static final String LOG_DIRECTORY = "D:\\file-transfer-logs";
	private static final String LOG_FILE_NAME = "application.log";

	/**
	 * Compresses the log file in-memory and streams it as a downloadable response.
	 * No zip file will be saved permanently on disk.
	 */
	public ResponseEntity<Resource> downloadLogAsZip() {
		Path logPath = Paths.get(LOG_DIRECTORY, LOG_FILE_NAME);

		if (!Files.exists(logPath)) {
			logger.error("Log file not found at: {}", logPath.toAbsolutePath());
			return ResponseEntity.notFound().build();
		}

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ZipOutputStream zos = new ZipOutputStream(baos);
				InputStream logStream = Files.newInputStream(logPath)) {

			// Add log file to zip
			ZipEntry entry = new ZipEntry(LOG_FILE_NAME);
			zos.putNextEntry(entry);

			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = logStream.read(buffer)) != -1) {
				zos.write(buffer, 0, bytesRead);
			}

			zos.closeEntry();
			zos.finish(); // finalize zip

			byte[] zipBytes = baos.toByteArray();
			ByteArrayResource resource = new ByteArrayResource(zipBytes);

			String zipFileName = LOG_FILE_NAME.replace(".log", "_" + System.currentTimeMillis() + ".zip");
			logger.info("Log file compressed in memory and ready for download: {}", zipFileName);

			return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFileName)
					.contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(zipBytes.length).body(resource);

		} catch (IOException e) {
			logger.error("Error while streaming log zip file: {}", e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
}
