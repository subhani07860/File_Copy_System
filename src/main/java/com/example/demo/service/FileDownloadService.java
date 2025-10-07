package com.example.demo.service;

import com.example.demo.util.FileDownloadProcessor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

@Service
public class FileDownloadService {

    private final FileDownloadProcessor fileDownloadProcessor;

    public FileDownloadService(FileDownloadProcessor fileDownloadProcessor) {
        this.fileDownloadProcessor = fileDownloadProcessor;
    }

    /**
     * Handles single file, multiple files, or directory download in one API
     */
    public ResponseEntity<Resource> processDownload(List<String> paths) throws Exception {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("No paths provided");
        }

        Resource resource;
        String downloadName;

        Path path = Paths.get(paths.get(0)); // Only get the first path for the initial check

        if (Files.isDirectory(path) || paths.size() > 1) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Logic for zipping a directory or multiple files
                if (Files.isDirectory(path)) {
                    try (Stream<Path> filePaths = Files.walk(path)) {
                        filePaths.filter(Files::isRegularFile).forEach(filePath -> {
                            try {
                                String relativeName = path.relativize(filePath).toString().replace("\\", "/");
                                String zipEntryName = stripExtensions(relativeName);

                                Resource fileResource = getResourceForPath(filePath);
                                addFileToZip(zos, fileResource, zipEntryName);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to zip file: " + filePath, e);
                            }
                        });
                    }
                    downloadName = path.getFileName() + ".zip";
                } else { // Handles multiple files (paths.size() > 1)
                    for (String pathStr : paths) {
                        Path filePath = Paths.get(pathStr);
                        String zipEntryName = stripExtensions(filePath.getFileName().toString());

                        Resource fileResource = getResourceForPath(filePath);
                        addFileToZip(zos, fileResource, zipEntryName);
                    }
                    downloadName = Paths.get(paths.get(0)).getParent().getFileName() + ".zip";
                }
            }
            resource = new ByteArrayResource(baos.toByteArray());
        } else { // Handles a single regular file
            Path filePath = Paths.get(paths.get(0));
            downloadName = filePath.getFileName().toString();
            resource = getResourceForPath(filePath);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /** Helper method to get the resource based on file type. */
    private Resource getResourceForPath(Path filePath) throws Exception {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".enc") || fileName.endsWith(".gz")) {
            return fileDownloadProcessor.processIfNeeded(filePath.toString());
        } else {
            return new ByteArrayResource(Files.readAllBytes(filePath));
        }
    }

    /** Helper method to add a single file to a ZipOutputStream */
    private void addFileToZip(ZipOutputStream zos, Resource fileRes, String zipEntryName) throws Exception {
        zos.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream is = fileRes.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
        zos.closeEntry();
    }

    /** Remove .enc and .gz extensions */
    private String stripExtensions(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        if (lowerCaseName.endsWith(".enc")) {
            fileName = fileName.substring(0, fileName.length() - 4);
            lowerCaseName = fileName.toLowerCase();
        }
        if (lowerCaseName.endsWith(".gz")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        return fileName;
    }
}

//package com.example.demo.service;
//
//import com.example.demo.util.FileDownloadProcessor;
//import org.springframework.core.io.ByteArrayResource;
//import org.springframework.core.io.Resource;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//
//import java.nio.file.*;
//import java.util.List;
//
//@Service
//public class FileDownloadService {
//
//    private final FileDownloadProcessor fileDownloadProcessor;
//
//    public FileDownloadService(FileDownloadProcessor fileDownloadProcessor) {
//        this.fileDownloadProcessor = fileDownloadProcessor;
//    }
//
//    /**
//     * Handles single file, multiple files, or directory download in one API
//     */
//    public ResponseEntity<Resource> processDownload(List<String> paths) throws Exception {
//        if (paths == null || paths.isEmpty()) {
//            throw new IllegalArgumentException("No paths provided");
//        }
//
//        Resource resource;
//        String downloadName;
//
//        if (paths.size() == 1) {
//            Path path = Paths.get(paths.get(0));
//
//            if (Files.isDirectory(path)) {
//                resource = fileDownloadProcessor.downloadDirectoryAsZip(path.toString());
//                downloadName = path.getFileName() + ".zip"; 
//            } else if (Files.isRegularFile(path)) {
//                if (path.toString().toLowerCase().endsWith(".enc") || path.toString().toLowerCase().endsWith(".gz")) {
//                    resource = fileDownloadProcessor.processIfNeeded(path.toString());
//                } else {
//                    resource = new ByteArrayResource(Files.readAllBytes(path));
//                }
//                downloadName = path.getFileName().toString(); 
//            } else {
//                throw new IllegalArgumentException("Invalid path: " + path);
//            }
//
//        } else {
//           
//            resource = fileDownloadProcessor.downloadMultipleFilesAsZip(paths);
//            downloadName = "download_file.zip"; 
//        }
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
//                .contentType(MediaType.APPLICATION_OCTET_STREAM)
//                .body(resource);
//    }
//}
