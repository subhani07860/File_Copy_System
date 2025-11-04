package com.example.demo.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import com.example.demo.dto.FileFilters;
import com.example.demo.dto.FolderContentResponse;
import com.example.demo.dto.FolderDetailsRequest;

public interface FileCopyService {

	List<Map<String, Object>> copyFiles(String sourcePath, String destinationPath, BigDecimal runId, String activity,
			FileFilters filters, String encryptionKey);

	List<Map<String, Object>> previewFiles(String sourcePath, FileFilters filters);

	List<Map<String, Object>> purgeOnly(String sourcePath, BigDecimal runId, FileFilters filters);

	ResponseEntity<Resource> download(List<String> targetPath) throws Exception;

	FolderContentResponse getFolderDetailsByPage(FolderDetailsRequest request, int pageNumber, int maxResultPerPage)
			throws IOException;

	ResponseEntity<Resource> downloadLogAsZip();

}