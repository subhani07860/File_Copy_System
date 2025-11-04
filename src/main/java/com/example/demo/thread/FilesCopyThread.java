package com.example.demo.thread;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.demo.dto.FileFilters;
import com.example.demo.dto.update.FolderPathFilterCriteria;
import com.example.demo.repository.FileDetailsStore;

//@Slf4j
//if we use lombok for logger use above annotation and log.info instead of logger.info
public class FilesCopyThread implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(FilesCopyThread.class);

	private File sourceFile;
	private File destinationFile = null;
	private File sourceRootPath;
	private FileDetailsStore fileDetailsStore;
	private BigDecimal runId;

	private String activity;
	private FileFilters filters;
	private String encryptionKey;
	private JdbcTemplate jdbcTemplate;

	private AtomicInteger fileCounter = new AtomicInteger(0);

	public FilesCopyThread(File srcPath, File destPath, FileDetailsStore fileDetailsStore, BigDecimal runId,
			String activity, FileFilters filters, String encryptionKey, JdbcTemplate jdbcTemplate) {

		this.sourceFile = srcPath;
		this.destinationFile = destPath;
		this.sourceRootPath = srcPath;
		this.fileDetailsStore = fileDetailsStore;
		this.runId = runId;
		this.activity = activity;
		this.filters = filters;
		this.encryptionKey = encryptionKey;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run() {

		if (!sourceFile.exists()) {
			logger.warn("Source path does not exist: {}", sourceFile.getAbsolutePath());

			return;
		}

		if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
			if (destinationFile == null) {
				logger.error("Destination path cannot be null for copy/copyandpurge activity.");
				return;
			}
			// Create the initial root destination directory if it doesn't exist
			if (!destinationFile.exists()) {
				destinationFile.mkdirs();
				logger.info("Initial Destination root folder created: {}", destinationFile.getAbsolutePath());
			}

			// Start traversal with the initial source and destination roots
			travelDirectory(sourceFile, destinationFile);
		} else if ("preview".equalsIgnoreCase(activity) || "purgeonly".equalsIgnoreCase(activity)) {

			travelDirectory(sourceFile, null);

		} else {
			logger.error("Unknown activity type: {}", activity);
		}

		if (filters != null && filters.getKbId() != null && !filters.getKbId().trim().isEmpty()) {
			insertMd(runId);
		}
	}

	/**
	 * Recursively traverses the source directory.
	 *
	 * @param currentSourceItem     The current file or directory being processed in
	 *                              the source.
	 * @param currentDestinationDir The corresponding destination directory for
	 *                              `currentSourceItem`. This helps maintain the
	 *                              relative path for directory creation during
	 *                              traversal, but the *full* destination file path
	 *                              is ultimately determined in `FileDetailsUpdate`.
	 */
	private void travelDirectory(File currentSourceItem, File currentDestinationDir) {

		if (currentSourceItem.isDirectory()) {

			// Apply folder path filters only to directories
			if (!checkFolderPathForDirectory(currentSourceItem, filters)) {
				logger.info("Skipping directory due to filter criteria: {}", currentSourceItem.getAbsolutePath());
				return;
			}

			// If currentSourceItem is a directory, then currentDestinationDir should be its
			// corresponding directory at the destination.
			// Ensure this corresponding destination directory exists.
//			if (currentDestinationDir != null && !currentDestinationDir.exists()) {
//				currentDestinationDir.mkdirs();
//			}

			File[] items = currentSourceItem.listFiles();

			if (items != null) {
				for (File item : items) {
					File nextDestinationDir = null;
					if (currentDestinationDir != null) {
						// Form the next destination directory based on the current destination
						// directory
						// and the name of the current item (which is a directory or file)
						nextDestinationDir = new File(currentDestinationDir, item.getName());
						logger.debug("Next destination directory: {}", nextDestinationDir.getAbsolutePath());
					}
					travelDirectory(item, nextDestinationDir);
				}
			}
		} else { // It's a file
			// For files, pass the original *root* destination path (`this.destinationFile`)
			// to `FileDetailsUpdate`. `FileDetailsUpdate` will handle the full path
			// construction
			// including source path inclusion logic and creating necessary parent
			// directories.

			Runnable R = new FileDetailsUpdate(currentSourceItem, this.destinationFile, this.sourceRootPath,
					fileDetailsStore, runId, fileCounter, activity, filters, encryptionKey);
			Thread subThread = new Thread(R);
			subThread.start();

			try {
				subThread.join();
			} catch (InterruptedException e) {
				logger.error("Thread interrupted: ", e);
			}

		}
	}

	private void insertMd(BigDecimal runId) {
		String kbId = filters.getKbId() != null ? filters.getKbId().trim() : null;
		String selectSQL = "SELECT * FROM file_meta_data WHERE is_archived = 'Y' AND run_id = ?";

		if (kbId == null || kbId.isEmpty()) {
			logger.error("KB ID is missing. Cannot continue insertMd()");
			return;
		}

		try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
				PreparedStatement selectStmt = conn.prepareStatement(selectSQL)) {

			selectStmt.setBigDecimal(1, runId);
			ResultSet rs = selectStmt.executeQuery();

			List<Map<String, Object>> rows = new ArrayList<>();
			ResultSetMetaData metaData = rs.getMetaData();
			int columnCount = metaData.getColumnCount();

			while (rs.next()) {
				Map<String, Object> row = new HashMap<>();
				for (int i = 1; i <= columnCount; i++) {
					row.put(metaData.getColumnName(i), rs.getObject(i));
				}
				rows.add(row);
			}

			insertMDEDMS(rows, kbId, conn); // Now pass a list instead of live ResultSet

		} catch (Exception e) {
			logger.error("Error in insertMd(): {}", e.getMessage(), e);
		}
	}

	private void insertMDEDMS(List<Map<String, Object>> rows, String kbId, Connection conn) throws SQLException {
		String tableName = "edms_filearchive_metadata_" + kbId;

		String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " LIKE file_meta_data";
		try (PreparedStatement createStmt = conn.prepareStatement(createTableSQL)) {
			createStmt.execute();
		}

		String insertSQL = "INSERT INTO " + tableName + " (file_id, file_path, creation_date, size, file_name, "
				+ "file_src_path, target_path, modification_date, file_type, author, target_file_name, "
				+ "source_checksum, target_checksum, validation_status, run_id, is_archived,is_version_enable,file_encryption,file_encryption_key,file_compression,created_at) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

		try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
			int count = 0;
			for (Map<String, Object> row : rows) {
				insertStmt.setString(1, (String) row.get("file_id"));
				insertStmt.setString(2, (String) row.get("file_path"));
				insertStmt.setString(3, (String) row.get("creation_date"));
				insertStmt.setLong(4, (Long) row.get("size"));
				insertStmt.setString(5, (String) row.get("file_name"));
				insertStmt.setString(6, (String) row.get("file_src_path"));
				insertStmt.setString(7, (String) row.get("target_path"));
				insertStmt.setString(8, (String) row.get("modification_date"));
				insertStmt.setString(9, (String) row.get("file_type"));
				insertStmt.setString(10, (String) row.get("author"));
				insertStmt.setString(11, (String) row.get("target_file_name"));
				insertStmt.setString(12, (String) row.get("source_checksum"));
				insertStmt.setString(13, (String) row.get("target_checksum"));
				insertStmt.setString(14, (String) row.get("validation_status"));
				insertStmt.setBigDecimal(15, (BigDecimal) row.get("run_id"));
				insertStmt.setString(16, (String) row.get("is_archived"));
				insertStmt.setString(17, (String) row.get("is_version_enable"));
				insertStmt.setString(18, (String) row.get("file_encryption"));
				insertStmt.setString(19, (String) row.get("file_encryption_key"));
				insertStmt.setString(20, (String) row.get("file_compression"));
				insertStmt.setString(21, (String) row.get("created_at"));

				insertStmt.addBatch();
				if (++count % 5 == 0) {
					insertStmt.executeBatch();
					logger.info("batch completed for {} records", count);
				}
			}
			insertStmt.executeBatch();
			logger.info("Inserted {} records into {}", rows.size(), tableName);
		}
	}

	/**
	 * This method checks if a given directory should be traversed based on
	 * FolderPathFilterCriteria. It's crucial for the `travelDirectory` recursion to
	 * efficiently skip entire branches. This logic is *different* from
	 * `FileFilterUtility.checkFolderPathFilters` which checks a file's parent path.
	 * Here, we check if the *current directory itself* is included/excluded.
	 */
	private boolean checkFolderPathForDirectory(File directory, FileFilters filters) {
		if (filters == null || filters.getFolderPathFilterCriteria() == null
				|| filters.getFolderPathFilterCriteria().isEmpty()) {
			return true; // No folder filters, so include by default
		}

		String currentDirectoryPath = directory.getAbsolutePath();
		String currentDirectoryPathLower = currentDirectoryPath.toLowerCase();

		List<FolderPathFilterCriteria> criteriaList = filters.getFolderPathFilterCriteria();

		boolean anyIncludeMatched = false;
		boolean hasIncludeCriteria = false;

		for (FolderPathFilterCriteria criteria : criteriaList) {
			if (!"FOLDER_PATH".equalsIgnoreCase(criteria.getParamName())) {
				continue; // Skip criteria not meant for folder paths
			}
			if (criteria.getParamValue() == null || criteria.getParamValue().trim().isEmpty()) {
				continue; // Skip empty criteria
			}

			String paramValueLower = criteria.getParamValue().toLowerCase();
			boolean currentMatch = false;

			switch (criteria.getCriteria1().toLowerCase()) {
			case "contains":
				currentMatch = currentDirectoryPathLower.contains(paramValueLower);
				break;
			case "startswith":
				currentMatch = currentDirectoryPathLower.startsWith(paramValueLower);
				break;
			case "endswith":
				currentMatch = currentDirectoryPathLower.endsWith(paramValueLower);
				break;
			default:
				logger.warn(
						"Unknown criteria1 for FolderPathFilterCriteria in directory check: {}. Defaulting to 'contains' behavior.",
						criteria.getCriteria1());
				currentMatch = currentDirectoryPathLower.contains(paramValueLower); // Fallback
				break;
			}

			if ("exclude".equalsIgnoreCase(criteria.getCriteria1())) {
				if (currentMatch) {
					logger.info("EXCLUDED directory by folder path criteria: {} -> {}", directory.getAbsolutePath(),
							criteria);
					return false; // Immediate exclusion
				}
			} else if ("include".equalsIgnoreCase(criteria.getCriteria1())) {
				hasIncludeCriteria = true;
				if (currentMatch) {
					anyIncludeMatched = true;
				}
			}
		}

		if (hasIncludeCriteria && !anyIncludeMatched) {
			logger.info("EXCLUDED directory: {} because it did not match any active include folder path criteria.",
					directory.getAbsolutePath());
			return false;
		}

		return true; // Directory is included
	}
}

//package com.example.demo.thread;
//
//import java.io.*;
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional; // Import Optional
//import java.util.concurrent.atomic.AtomicInteger;
//
//import com.example.demo.dto.FileFilters;
//import com.example.demo.dto.update.FolderPathFilterCriteria;
//import com.example.demo.repository.FileDetailsStore;
//import com.example.demo.thread.update.FileDetailsUpdate;
//
//public class FilesCopyThread implements Runnable {
//	private File sourceFile;
//	private File destinationFile = null;
//	private FileDetailsStore fileDetailsStore;
//	private BigDecimal runId;
//	private File sourceRootPath;
//	private String activity;
//	private FileFilters filters;
//	private String encryptionKey;
//
//	private AtomicInteger fileCounter = new AtomicInteger(0);
//
//	public FilesCopyThread(File srcPath, File destPath, FileDetailsStore fileDetailsStore, BigDecimal runId,
//			String activity, FileFilters filters, String encryptionKey) {
//		this.sourceFile = srcPath;
//		this.destinationFile = destPath;
//		this.sourceRootPath = srcPath;
//		this.fileDetailsStore = fileDetailsStore;
//		this.runId = runId;
//		this.activity = activity;
//		this.filters = filters;
//		this.encryptionKey = encryptionKey;
//	}
//
//	@Override
//	public void run() {
//
//		if (!sourceFile.exists()) {
//			System.out.println("Source path does not exist: " + sourceFile.getAbsolutePath());
//			return;
//		}
//
//		if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
//			if (destinationFile == null) {
//				System.out.println("Destination path cannot be null for copy/copyandpurge activity.");
//				return;
//			}
//			if (!destinationFile.exists()) {
//				destinationFile.mkdirs();
//				System.out.println("Destination folder created: " + destinationFile.getAbsolutePath());
//			}
//
//			travelDirectory(sourceFile, destinationFile);
//		} else if ("preview".equalsIgnoreCase(activity) || "purgeonly".equalsIgnoreCase(activity)) {
//
//			travelDirectory(sourceFile, null);
//		} else {
//			System.out.println("Unknown activity type: " + activity);
//		}
//
//	}
//
//	private void travelDirectory(File sourceFile2, File destinationFile2) {
//
//		// IMPORTANT: For folder inclusion/exclusion, we should check against
//		// the FolderPathFilterCriteria defined in the filters.
//		// The `isFolderIncludedByFilters` method needs to be aligned with the DTO.
//
//		// Determine the path to check against folder filters
//		String pathToCheck = sourceFile2.isDirectory()
//				? Optional.ofNullable(sourceFile2.getAbsolutePath()).map(String::toLowerCase).orElse(null)
//				: Optional.ofNullable(sourceFile2.getParentFile()).map(File::getAbsolutePath).map(String::toLowerCase)
//						.orElse(null);
//
//		if (sourceFile2.isDirectory()) {
//			// Apply folder path filters only to directories
//			if (!checkFolderPathForDirectory(sourceFile2, filters)) { // Use a specific check for directories
//				System.out.println("Skipping directory due to filter criteria: " + sourceFile2.getAbsolutePath());
//				return;
//			}
//
//			if (destinationFile2 != null && !destinationFile2.exists()) {
//				destinationFile2.mkdirs();
//			}
//
//			File[] items = sourceFile2.listFiles();
//			if (items != null) {
//				for (File item : items) {
//					File newDestDir = null;
//					if (destinationFile2 != null) {
//						newDestDir = new File(destinationFile2, item.getName());
//					}
//					travelDirectory(item, newDestDir);
//				}
//			}
//		} else { // It's a file
//			File destFile = null;
//			if (destinationFile2 != null) {
//				File parentDir = destinationFile2.getParentFile();
//
//				if (destinationFile2.isDirectory()) { // If destinationFile2 itself is intended to be a directory
//					destFile = new File(destinationFile2, sourceFile2.getName());
//				} else if (parentDir != null && !parentDir.exists()) { // If destinationFile2 is a file and its parent
//																		// doesn't exist
//					parentDir.mkdirs();
//					destFile = destinationFile2; // destinationFile2 is the actual file path
//				} else { // destinationFile2 is a file and its parent exists, or it's the root
//					destFile = destinationFile2;
//				}
//			}
//
//			// For files, the `FileDetailsUpdate` thread should perform the actual
//			// file-level filtering
//			Runnable R = new FileDetailsUpdate(sourceFile2, destFile, this.sourceRootPath, fileDetailsStore, runId,
//					fileCounter, activity, filters, encryptionKey);
//			Thread Tr = new Thread(R);
//			Tr.start();
//		}
//	}
//
//	/**
//	 * This method checks if a given directory should be traversed based on
//	 * FolderPathFilterCriteria. It's crucial for the `travelDirectory` recursion to
//	 * efficiently skip entire branches. This logic is *different* from
//	 * `FileFilterUtility.checkFolderPathFilters` which checks a file's parent path.
//	 * Here, we check if the *current directory itself* is included/excluded.
//	 */
//	private boolean checkFolderPathForDirectory(File directory, FileFilters filters) {
//		if (filters == null || filters.getFolderPathFilterCriteria() == null
//				|| filters.getFolderPathFilterCriteria().isEmpty()) {
//			return true; // No folder filters, so include by default
//		}
//
//		String currentDirectoryPath = directory.getAbsolutePath();
//		String currentDirectoryPathLower = currentDirectoryPath.toLowerCase();
//
//		List<FolderPathFilterCriteria> criteriaList = filters.getFolderPathFilterCriteria();
//
//		boolean anyIncludeMatched = false;
//		boolean hasIncludeCriteria = false;
//
//		for (FolderPathFilterCriteria criteria : criteriaList) {
//			if (!"FOLDER_PATH".equalsIgnoreCase(criteria.getParamName())) {
//				continue; // Skip criteria not meant for folder paths
//			}
//			if (criteria.getParamValue() == null || criteria.getParamValue().trim().isEmpty()) {
//				continue; // Skip empty criteria
//			}
//
//			String paramValueLower = criteria.getParamValue().toLowerCase();
//			boolean currentMatch = false;
//
//			switch (criteria.getCriteria1().toLowerCase()) {
//			case "contains":
//				currentMatch = currentDirectoryPathLower.contains(paramValueLower);
//				break;
//
//			default:
//				System.out.println("Warning: Unknown criteria2 for FolderPathFilterCriteria in directory check: "
//						+ criteria.getCriteria1());
//				continue;
//			}
//
//			if ("exclude".equalsIgnoreCase(criteria.getCriteria1())) {
//				if (currentMatch) {
//					System.out.println("EXCLUDED directory by folder path criteria: " + directory.getAbsolutePath()
//							+ " -> " + criteria);
//					return false; // Immediate exclusion
//				}
//			} else if ("include".equalsIgnoreCase(criteria.getCriteria1())) {
//				hasIncludeCriteria = true;
//				if (currentMatch) {
//					anyIncludeMatched = true;
//				}
//			}
//		}
//
//		// If there were any include criteria, and none of them matched this directory,
//		// then exclude it.
//		if (hasIncludeCriteria && !anyIncludeMatched) {
//			System.out.println("EXCLUDED directory: " + directory.getAbsolutePath()
//					+ " because it did not match any active include folder path criteria.");
//			return false;
//		}
//
//		return true; // Directory is included
//	}
//}