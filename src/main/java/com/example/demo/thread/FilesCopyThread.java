package com.example.demo.thread;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.demo.dto.FileFilters;
import com.example.demo.dto.update.FolderPathFilterCriteria;
import com.example.demo.repository.FileDetailsStore;

public class FilesCopyThread implements Runnable {
	private File sourceFile;
	private File destinationFile = null; // This now represents the initial root destination path
	private File sourceRootPath; // Added to store the initial source root path
	private FileDetailsStore fileDetailsStore;
	private BigDecimal runId;

	private String activity;
	private FileFilters filters;
	private String encryptionKey;

	private AtomicInteger fileCounter = new AtomicInteger(0);

	public FilesCopyThread(File srcPath, File destPath, FileDetailsStore fileDetailsStore, BigDecimal runId,
			String activity, FileFilters filters, String encryptionKey) {

		this.sourceFile = srcPath;
		this.destinationFile = destPath; // Initialize destinationFile with the initial destPath
		this.sourceRootPath = srcPath; // Initialize sourceRootPath with the initial srcPath
		this.fileDetailsStore = fileDetailsStore;
		this.runId = runId;
		this.activity = activity;
		this.filters = filters;
		this.encryptionKey = encryptionKey;
	}

	@Override
	public void run() {

		if (!sourceFile.exists()) {
			System.out.println("Source path does not exist: " + sourceFile.getAbsolutePath());
			return;
		}

		if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
			if (destinationFile == null) {
				System.out.println("Destination path cannot be null for copy/copyandpurge activity.");
				return;
			}
			// Create the initial root destination directory if it doesn't exist
			if (!destinationFile.exists()) {
				destinationFile.mkdirs();
				System.out.println("Initial Destination root folder created: " + destinationFile.getAbsolutePath());
			}

			// Start traversal with the initial source and destination roots
			travelDirectory(sourceFile, destinationFile);
		} else if ("preview".equalsIgnoreCase(activity) || "purgeonly".equalsIgnoreCase(activity)) {

			travelDirectory(sourceFile, null);

		} else {
			System.out.println("Unknown activity type: " + activity);
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
				System.out.println("Skipping directory due to filter criteria: " + currentSourceItem.getAbsolutePath());
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

				e.printStackTrace();
			}

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
				System.out.println("Warning: Unknown criteria1 for FolderPathFilterCriteria in directory check: "
						+ criteria.getCriteria1() + ". Defaulting to 'contains' behavior.");
				currentMatch = currentDirectoryPathLower.contains(paramValueLower); // Fallback
				break;
			}

			if ("exclude".equalsIgnoreCase(criteria.getCriteria1())) {
				if (currentMatch) {
					System.out.println("EXCLUDED directory by folder path criteria: " + directory.getAbsolutePath()
							+ " -> " + criteria);
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
			System.out.println("EXCLUDED directory: " + directory.getAbsolutePath()
					+ " because it did not match any active include folder path criteria.");
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