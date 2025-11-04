package com.example.demo.util;

import com.example.demo.entity.FileMetaData;
import com.example.demo.repository.FileDetailsStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@Component
public class FileDownloadProcessor {

    private final FileDetailsStore fileDetailsStore;
    private static final int BUFFER_SIZE = 4096;

    public FileDownloadProcessor(FileDetailsStore fileDetailsStore) {
        this.fileDetailsStore = fileDetailsStore;
    }

    /** Only process & return if file is .enc or .gz, else throw exception */
    public Resource processIfNeeded(String targetPath) throws Exception {
        Path filePath = Paths.get(targetPath);

        if (!Files.isRegularFile(filePath) || !Files.exists(filePath)) {
            throw new FileNotFoundException("File not found or is a directory: " + targetPath);
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        boolean processed = false;

        // Decrypt if .enc
        if (targetPath.toLowerCase().endsWith(".enc")) {
            Optional<FileMetaData> metaOpt =
                    fileDetailsStore.findFirstByTargetPathAndTargetFileNameOrderByCreatedAtDesc(
                            filePath.getParent().toString(), filePath.getFileName().toString());

            if (metaOpt.isEmpty() || metaOpt.get().getFileEncryptionKey() == null) {
                throw new IllegalArgumentException("Missing encryption key for: " + targetPath);
            }

            byte[] keyBytes = java.util.Base64.getDecoder().decode(metaOpt.get().getFileEncryptionKey());
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            fileBytes = cipher.doFinal(fileBytes);
            processed = true;
        }

        // Decompress if .gz
        if (targetPath.toLowerCase().endsWith(".gz")) {
            try (GZIPInputStream gzipIs = new GZIPInputStream(new ByteArrayInputStream(fileBytes));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = gzipIs.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                fileBytes = out.toByteArray();
                processed = true;
            }
        }

        if (!processed) {
            throw new IllegalArgumentException("File is not encrypted (.enc) or compressed (.gz): " + targetPath);
        }

        return new ByteArrayResource(fileBytes);
    }
}


