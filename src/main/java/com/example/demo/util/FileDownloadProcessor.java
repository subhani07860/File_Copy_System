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


//package com.example.demo.util;
//
//import com.example.demo.entity.FileMetaData;
//import com.example.demo.repository.FileDetailsStore;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.core.io.InputStreamResource;
//import org.springframework.core.io.Resource;
//import org.springframework.stereotype.Component;
//
//import javax.crypto.Cipher;
//import javax.crypto.spec.SecretKeySpec;
//import java.io.*;
//import java.nio.file.*;
//import java.util.List;
//import java.util.Optional;
//import java.util.zip.GZIPInputStream;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipOutputStream;
//import java.util.stream.Stream;
//
//@Component
//public class FileDownloadProcessor {
//
//    private final FileDetailsStore fileDetailsStore;
//    private static final int BUFFER_SIZE = 4096;
//
//    public FileDownloadProcessor(FileDetailsStore fileDetailsStore) {
//        this.fileDetailsStore = fileDetailsStore;
//    }
//
//    /** Process file only if encrypted or compressed */
//    public Resource processIfNeeded(String targetPath) throws Exception {
//        Path filePath = Paths.get(targetPath);
//        byte[] fileBytes;
//
//        if (Files.isRegularFile(filePath) && Files.exists(filePath)) {
//            fileBytes = Files.readAllBytes(filePath);
//        } else {
//            throw new FileNotFoundException("File not found or is a directory: " + targetPath);
//        }
//
//        // Decrypt if .enc
//        if (targetPath.toLowerCase().endsWith(".enc")) {
//            Optional<FileMetaData> metaOpt =
//                    fileDetailsStore.findFirstByTargetPathAndTargetFileNameOrderByCreatedAtDesc(
//                            filePath.getParent().toString(), filePath.getFileName().toString());
//
//            if (metaOpt.isEmpty() || metaOpt.get().getFileEncryptionKey() == null) {
//                throw new IllegalArgumentException("Missing encryption key for: " + targetPath);
//            }
//
//            byte[] keyBytes = java.util.Base64.getDecoder().decode(metaOpt.get().getFileEncryptionKey());
//            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
//            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
//            fileBytes = cipher.doFinal(fileBytes);
//        }
//
//        // Decompress if .gz
//        if (targetPath.toLowerCase().endsWith(".gz")) {
//            try (GZIPInputStream gzipIs = new GZIPInputStream(new ByteArrayInputStream(fileBytes));
//                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
//                byte[] buffer = new byte[BUFFER_SIZE];
//                int bytesRead;
//                while ((bytesRead = gzipIs.read(buffer)) != -1) {
//                    out.write(buffer, 0, bytesRead);
//                }
//                fileBytes = out.toByteArray();
//            }
//        }
//
//        return new ByteArrayResource(fileBytes);
//    }
//
//    /** Zip a directory */
//    public Resource downloadDirectoryAsZip(String directoryPath) throws Exception {
//        Path sourceDir = Paths.get(directoryPath);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//        try (ZipOutputStream zos = new ZipOutputStream(baos);
//             Stream<Path> paths = Files.walk(sourceDir)) {
//
//            paths.filter(Files::isRegularFile).forEach(filePath -> {
//                try {
//                    String relativeName = sourceDir.relativize(filePath).toString().replace("\\", "/");
//                    addFileToZip(zos, filePath, stripExtensions(relativeName));
//                } catch (Exception e) {
//                    throw new RuntimeException("Failed to zip file: " + filePath, e);
//                }
//            });
//        }
//        return new ByteArrayResource(baos.toByteArray());
//    }
//
//    /** Zip multiple files */
//    public Resource downloadMultipleFilesAsZip(List<String> filePaths) throws Exception {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
//            for (String pathStr : filePaths) {
//                Path path = Paths.get(pathStr);
//                addFileToZip(zos, path, stripExtensions(path.getFileName().toString()));
//            }
//        }
//        return new ByteArrayResource(baos.toByteArray());
//    }
//
//    /** Helper method to add a single file to a ZipOutputStream */
//    private void addFileToZip(ZipOutputStream zos, Path filePath, String zipEntryName) throws Exception {
//        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
//            throw new FileNotFoundException("Invalid file path: " + filePath);
//        }
//
//        Resource fileRes = (filePath.toString().toLowerCase().endsWith(".enc") || filePath.toString().toLowerCase().endsWith(".gz"))
//                ? processIfNeeded(filePath.toString())
//                : new InputStreamResource(Files.newInputStream(filePath));
//
//        zos.putNextEntry(new ZipEntry(zipEntryName));
//        try (InputStream is = fileRes.getInputStream()) {
//            byte[] buffer = new byte[BUFFER_SIZE];
//            int bytesRead;
//            while ((bytesRead = is.read(buffer)) != -1) {
//                zos.write(buffer, 0, bytesRead);
//            }
//        }
//        zos.closeEntry();
//    }
//
//    /** Remove .enc and .gz extensions */
//    private String stripExtensions(String fileName) {
//        String lowerCaseName = fileName.toLowerCase();
//        if (lowerCaseName.endsWith(".enc")) {
//            fileName = fileName.substring(0, fileName.length() - 4);
//            lowerCaseName = fileName.toLowerCase();
//        }
//        if (lowerCaseName.endsWith(".gz")) {
//            fileName = fileName.substring(0, fileName.length() - 3);
//        }
//        return fileName;
//    }
//}