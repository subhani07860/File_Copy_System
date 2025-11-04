package com.example.demo.serviceImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.dto.FileFilters;
import com.example.demo.dto.FolderContentResponse;
import com.example.demo.dto.FolderDetailsRequest;
import com.example.demo.repository.FileDetailsStore;
import com.example.demo.service.FileCopyService;
import com.example.demo.service.FileDownloadService;
import com.example.demo.thread.FileDetailsUpdate;
import com.example.demo.thread.FilesCopyThread;
import com.example.demo.util.FolderContentScanner;
import com.example.demo.util.LogDownloadService;

@Service
public class FileCopyServiceImpl implements FileCopyService {
	
	private static final Logger Logger = LoggerFactory.getLogger(FileCopyServiceImpl.class);
	@Autowired
	private FileDetailsStore FileDetailsStore;
	
	@Autowired
	private LogDownloadService logDownloadService;

	@Autowired
	private FileDownloadService fileDownloadService;
	@Autowired
	private FolderContentScanner folderContentScanner;

	@Autowired
	private JdbcTemplate jdbcTemplate; // for dynamic SQL operations

	@Override
	public List<Map<String, Object>> copyFiles(String sourcePath, String destinationPath, BigDecimal runId,
			String activity, FileFilters filters, String encryptionKey) {
		List<Map<String, Object>> metadataResults = executeFileOperation(sourcePath, destinationPath, runId, activity,
				filters, encryptionKey);

		return metadataResults;
	}

//	@Override
//	public List<Map<String, Object>> copyFiles(String sourcePath, String destinationPath, BigDecimal runId,
//			String activity, FileFilters filters, String encryptionKey) {
//
//		return executeFileOperation(sourcePath, destinationPath, runId, activity, filters, encryptionKey);
//	}

	@Override
	public List<Map<String, Object>> previewFiles(String sourcePath, FileFilters filters) {

		return executeFileOperation(sourcePath, null, null, "preview", filters, null);
	}

	@Override
	public List<Map<String, Object>> purgeOnly(String sourcePath, BigDecimal runId, FileFilters filters) {

		return executeFileOperation(sourcePath, null, runId, "purgeonly", filters, null);
	}

	private List<Map<String, Object>> executeFileOperation(String sourcePath, String destinationPath, BigDecimal runId,
			String activity, FileFilters filters, String encryptionKey) {
		FileDetailsUpdate.clear();

		File srcDir = new File(sourcePath);
		File destDir = (destinationPath != null) ? new File(destinationPath) : null;

		try {
			Runnable R = new FilesCopyThread(srcDir, destDir, FileDetailsStore, runId, activity, filters, encryptionKey,
					jdbcTemplate);
			Thread Tr = new Thread(R);
			Tr.start();

			Tr.join();
		} catch (InterruptedException e) {
			Logger.error("File operation thread was interrupted", e);
		} catch (Exception e) {
			Logger.error("An error occurred during file operation", e);
		}

		return FileDetailsUpdate.getAll();
	}

	@Override
	public ResponseEntity<Resource> download(List<String> targetPath) throws Exception {
		// Delegate the call to the new FileDownloadService
		// The exceptions are re-thrown to be handled by the controller (or a global
		// exception handler)
		return fileDownloadService.processDownload(targetPath);
	}

	@Override
	public FolderContentResponse getFolderDetailsByPage(FolderDetailsRequest request, int pageNumber,
			int maxResultPerPage) throws IOException {
		try {
			return folderContentScanner.scanFolderContents(request, pageNumber, maxResultPerPage);
		} catch (IllegalArgumentException | FileNotFoundException e) {
			// Map specific exceptions from scanner to your desired FolderContentResponse
			// structure
			FolderContentResponse errorResponse = new FolderContentResponse();
			errorResponse.setStatus("Input FliePath directory not found" + e.getMessage());
			// You might want to add a specific error message to the DTO
			Logger.error("Error in getFolderDetailsByPage", e);
			// Re-throw if the service should not handle the error but let the controller
			// handle it as a specific HTTP status
			throw e; // Or wrap in a custom service exception if you have one
		}
	}
	  @Override
	    public ResponseEntity<Resource> downloadLogAsZip() {
	        return logDownloadService.downloadLogAsZip();
	    }
}

//Runnable R = null;
//if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
//	R = new FilesCopyThread(srcDir, destDir, FileDetailsStore, runId, activity);
//} else if ("preview".equalsIgnoreCase(activity)) {
//	R = new FilesCopyThread(srcDir, FileDetailsStore, activity);
//} else if ("purgeonly".equalsIgnoreCase(activity)) {
//	R = new FilesCopyThread(srcDir, runId, FileDetailsStore, activity);
//} else {
//	System.out.println("unknown activity");
//}