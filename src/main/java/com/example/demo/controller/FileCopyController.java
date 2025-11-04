package com.example.demo.controller;

import com.example.demo.dto.CopyRequest;
import com.example.demo.dto.FileFilters;
import com.example.demo.dto.FolderContentResponse;
import com.example.demo.dto.FolderDetailsRequest;
import com.example.demo.service.EncryptionKeyGenerator;
import com.example.demo.service.FileCopyService;
import com.example.demo.service.nextRunIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FileCopyController {

	private static final Logger Logger = LoggerFactory.getLogger(FileCopyController.class);
	@Autowired
	private FileCopyService fileCopyService;

	@Autowired
	private nextRunIdGenerator nextRunIdGenerator;

	@PostMapping("/copy")
	public ResponseEntity<Map<String, Object>> copyFiles(@RequestBody CopyRequest request) { // Object reference
																								// variable

		String srcPath = request.getSourcepath();
		String destPath = request.getDestinationpath();

		String activity = request.getActivity();
		FileFilters filters = request.getFilters();

		BigDecimal runId = nextRunIdGenerator.generateNextRunId();

		if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
			if (srcPath == null || destPath == null) {
				return ResponseEntity.badRequest()
						.body(Map.of("error", "Source and destination must be provided for copy activities."));

			}
		} else {
			return ResponseEntity.badRequest().body(
					Map.of("error", "Invalid activity type for /copy endpoint. Expected 'copy' or 'copyandpurge'."));
		}

		String encryptionKey = null;

		String isEncryptionEnabled = (filters != null && "y".equalsIgnoreCase(filters.getFileEncryption()) ? "y" : "n");

		if ("y".equalsIgnoreCase(isEncryptionEnabled)) {
			encryptionKey = EncryptionKeyGenerator.generateKey();
			Logger.info("Generated encryption key for run {}: {}", runId, encryptionKey);
		}

		List<Map<String, Object>> fileDetails = fileCopyService.copyFiles(srcPath, destPath, runId, activity, filters,
				encryptionKey);

		Map<String, Object> response = new HashMap<>();
		response.put("runId", runId);
		response.put("activity", activity);

		String message = "Files operation completed successfully.";
		if ("copyandpurge".equalsIgnoreCase(activity)) {
			message = "Files copied successfully. Source files will be purged after successful copy.";
		} else if ("copy".equalsIgnoreCase(activity)) {
			message = "Files copied successfully.";
		}
		response.put("message", message);
		if ("copyandpurge".equalsIgnoreCase(activity)) {
			response.put("data", fileDetails);
		}

		return ResponseEntity.ok(response);
	}

	@PostMapping("/preview")
//	@GetMapping("/preview")
	public ResponseEntity<Map<String, Object>> previewFiles(@RequestBody CopyRequest request) {
		String sourcePath = request.getSourcepath();
		FileFilters filters = request.getFilters();

		if (sourcePath == null) {
			return ResponseEntity.badRequest().body(null);
		}

		List<Map<String, Object>> fileDetails = fileCopyService.previewFiles(sourcePath, filters);
		Map<String, Object> response = new HashMap<>();
		response.put("data", fileDetails);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/purge")
//	public ResponseEntity<Map<String, Object>> purgeOnly(@RequestBody Map<String, String> request) {
	public ResponseEntity<Map<String, Object>> purgeOnly(@RequestBody CopyRequest request) {
		// String sourcePath = request.get("sourcePath");
		String sourcePath = request.getSourcepath();
		FileFilters filters = request.getFilters();

		if (sourcePath == null) {
			Logger.error("sourcePath must provide to PurgeOnly ");
		}
		BigDecimal runId = nextRunIdGenerator.generateNextRunId();
		fileCopyService.purgeOnly(sourcePath, runId, filters);
		Map<String, Object> response = new HashMap<>();
		response.put("runId", runId);
		response.put("message", "All files are purged successfully");
		return ResponseEntity.ok(response);
	}

	@GetMapping("/download")
	public ResponseEntity<Resource> downloadContent(@RequestParam List<String> targetPath) throws Exception {

		return fileCopyService.download(targetPath);
	}

	@PostMapping("/getfolderdetailsbypage/{pageNumber}")
	public ResponseEntity<FolderContentResponse> getFolderDetailsByPage(@PathVariable int pageNumber,
			@RequestParam(value = "maxResultPerPage", defaultValue = "100") int maxResultPerPage,
			@RequestBody FolderDetailsRequest request) {
		if (pageNumber <= 0 || maxResultPerPage <= 0) {
			FolderContentResponse errorResponse = new FolderContentResponse();
			errorResponse.setStatus("ERROR");
			return ResponseEntity.badRequest().body(errorResponse);
		}

		try {
			Path path = Paths.get(request.getFolderPath());
			if (!Files.exists(path)) {
				throw new FileNotFoundException("Directory not found");
			}
			if (!Files.isDirectory(path)) {
				throw new IllegalArgumentException("Not a directory");
			}

			FolderContentResponse response = fileCopyService.getFolderDetailsByPage(request, pageNumber,
					maxResultPerPage);
			return ResponseEntity.ok(response);
		} catch (IOException e) {
			FolderContentResponse errorResponse = new FolderContentResponse();
			errorResponse.setStatus("There is no such type of file/folder");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
		}
	}

	@GetMapping("/download-logs")
	public ResponseEntity<Resource> downloadLogs() {
		return fileCopyService.downloadLogAsZip();
	}

}