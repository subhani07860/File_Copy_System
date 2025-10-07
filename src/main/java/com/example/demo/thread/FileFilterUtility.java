package com.example.demo.thread;

import com.example.demo.dto.FileFilters;
import com.example.demo.dto.update.FileNameFilterCriteria;
import com.example.demo.dto.update.FolderPathFilterCriteria;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class FileFilterUtility {

	/**
	 * Determines if a given file should be included based on the provided filters.
	 *
	 * @param file The file to check.
	 * @param filters The FileFilters object containing all filtering criteria.
	 * @return true if the file passes all active filters, false otherwise.
	 */
	public static boolean validateFile(File file, FileFilters filters) {
		System.out.println("\n--- Filtering File: " + file.getAbsolutePath() + " ---");

		if (filters == null) {
			System.out.println("  Filter object is NULL. File included by default.");
			return true;
		}

		System.out.println("  Filters object present. Checking criteria...");

		Path filePath = file.toPath();
		String fileName = file.getName(); // e.g., "document.pdf"

		final String currentNameWithoutExtension; // e.g., "document"
		final String currentFileExtension; // e.g., "pdf"

		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < fileName.length() - 1) { // Check if a dot exists and it's not the first or last
																// character
			currentNameWithoutExtension = fileName.substring(0, dotIndex);
			currentFileExtension = fileName.substring(dotIndex + 1);
		} else { // No dot, or dot at the beginning/end (e.g., ".profile", "filename.")
			currentNameWithoutExtension = fileName;
			currentFileExtension = ""; // No discernible extension
		}
		final String lowerCaseNameWithoutExtension = currentNameWithoutExtension.toLowerCase();
		final String lowerCaseFileExtension = currentFileExtension.toLowerCase();

		final String parentPath = file.getParent();
		// Convert parent path to lowercase for case-insensitive comparisons.
		// Use File.separator to handle different OS path conventions consistently.
		final String lowerCaseParentPath = Optional.ofNullable(parentPath)
				.map(p -> p.replace(File.separatorChar, '/').toLowerCase()).orElse(null);

		try {
			BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
			LocalDateTime creationTime = attrs.creationTime().toInstant().atZone(ZoneId.systemDefault())
					.toLocalDateTime();
			LocalDateTime lastModifiedTime = attrs.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())
					.toLocalDateTime();
			long fileSizeKB = file.length() / 1024; // Convert bytes to KB

			FileOwnerAttributeView ownerAttr = Files.getFileAttributeView(filePath, FileOwnerAttributeView.class);
			UserPrincipal owner = (ownerAttr != null) ? ownerAttr.getOwner() : null;
			String fileOwnerName = (owner != null) ? owner.getName() : "UNKNOWN";

			// 1. File Age/Date Filters
			if (!checkFileDateAndAgeFilters(filters, creationTime, lastModifiedTime)) {
				return false;
			}

			// 2. File Type (Extension) Filters - Uses the `fileTypes` and `fileTypeIncExc`
			if (!checkFileTypeFilters(filters, lowerCaseFileExtension)) { // Pass lowerCaseFileExtension
				return false;
			}

			// 3. Filename Filters - Uses the `fileNameFilterCriteria` list
			if (!checkFileNameFilters(filters, lowerCaseNameWithoutExtension)) {
				return false;
			}

			// 4. Folder Path Filters - Uses the `folderPathFilterCriteria` list
			if (!checkFolderPathFilters(filters, lowerCaseParentPath)) {
				return false;
			}

			// 5. File Owner Filters
			if (!checkFileOwnerFilters(filters, fileOwnerName)) {
				return false;
			}

			// 6. Size Filters
			if (!checkSizeFilters(filters, fileSizeKB)) {
				return false;
			}

		} catch (IOException e) {
			System.err.println("  Error reading attributes for filtering file: " + file.getAbsolutePath() + " - "
					+ e.getMessage());
			e.printStackTrace();
			return false;
		} catch (NullPointerException e) { // Catch potential NPE if ownerAttr.getOwner() is null and not checked
			System.err.println("  NullPointerException while processing file attributes for " + file.getAbsolutePath()
					+ ": " + e.getMessage());
			e.printStackTrace();
			return false;
		}

		System.out.println("--- File PASSED all filter criteria: " + file.getAbsolutePath() + " ---");
		return true;
	}

	// --- Filter Sub-Methods (Updated checkFileDateAndAgeFilters) ---

	private static boolean checkFileDateAndAgeFilters(FileFilters filters, LocalDateTime creationTime,
			LocalDateTime lastModifiedTime) {
		System.out.println("  Checking File Date/Age Filters. Base Attribute: " + filters.getDateFilterBaseAttribute()
				+ ", Segmentation Type: " + filters.getFileAgeSegmentationType());

		// No filter configured if base attribute is not set
		if (filters.getDateFilterBaseAttribute() == null || filters.getDateFilterBaseAttribute().trim().isEmpty()) {
			System.out.println("    No base date attribute specified. Date/Age filter skipped.");
			return true;
		}

		LocalDateTime dateToCheck;
		String baseAttributeLower = filters.getDateFilterBaseAttribute().toLowerCase();

		if ("creation".equals(baseAttributeLower)) {
			dateToCheck = creationTime;
			System.out.println("    Using Creation Date: " + creationTime);
		} else if ("modification".equals(baseAttributeLower)) {
			dateToCheck = lastModifiedTime;
			System.out.println("    Using Modification Date: " + lastModifiedTime);
		} else {
			System.out.println("    Warning: Unknown dateFilterBaseAttribute: " + filters.getDateFilterBaseAttribute()
					+ ". Date/Age filter skipped.");
			return true;
		}

		// No segmentation type configured if not set
		if (filters.getFileAgeSegmentationType() == null || filters.getFileAgeSegmentationType().trim().isEmpty()) {
			System.out.println("    No file age segmentation type specified. Date/Age filter skipped.");
			return true;
		}

		String segmentationTypeLower = filters.getFileAgeSegmentationType().toLowerCase();

		switch (segmentationTypeLower) {
		case "date_range":
			System.out.println("    Applying Absolute Date Range: From=" + filters.getAbsoluteDateRangeFrom() + ", To="
					+ filters.getAbsoluteDateRangeTo());
			if (filters.getAbsoluteDateRangeFrom() != null
					&& dateToCheck.isBefore(filters.getAbsoluteDateRangeFrom())) {
				System.out.println("    EXCLUDED: Date is before 'from' date.");
				return false;
			}
			if (filters.getAbsoluteDateRangeTo() != null && dateToCheck.isAfter(filters.getAbsoluteDateRangeTo())) {
				System.out.println("    EXCLUDED: Date is after 'to' date.");
				return false;
			}
			break;
		case "file_age":
			LocalDateTime now = LocalDateTime.now();
			System.out.println("    Applying Age Range (Relative to " + baseAttributeLower + " date):");

			// Check Days
			if (filters.getFileAgeDaysFrom() != null || filters.getFileAgeDaysTo() != null) {
				long ageDays = ChronoUnit.DAYS.between(dateToCheck, now);
				System.out.println("    Current Age (Days)=" + ageDays + ", Filter: From="
						+ filters.getFileAgeDaysFrom() + ", To=" + filters.getFileAgeDaysTo());
				if (filters.getFileAgeDaysFrom() != null && ageDays < filters.getFileAgeDaysFrom()) {
					System.out.println("    EXCLUDED: File age (days) less than 'from' days.");
					return false;
				}
				if (filters.getFileAgeDaysTo() != null && ageDays > filters.getFileAgeDaysTo()) {
					System.out.println("    EXCLUDED: File age (days) greater than 'to' days.");
					return false;
				}
			}

			// Check Months
			if (filters.getFileAgeMonthsFrom() != null || filters.getFileAgeMonthsTo() != null) {
				long ageMonths = ChronoUnit.MONTHS.between(dateToCheck, now);
				System.out.println("    Current Age (Months)=" + ageMonths + ", Filter: From="
						+ filters.getFileAgeMonthsFrom() + ", To=" + filters.getFileAgeMonthsTo());
				if (filters.getFileAgeMonthsFrom() != null && ageMonths < filters.getFileAgeMonthsFrom()) {
					System.out.println("    EXCLUDED: File age (months) less than 'from' months.");
					return false;
				}
				if (filters.getFileAgeMonthsTo() != null && ageMonths > filters.getFileAgeMonthsTo()) {
					System.out.println("    EXCLUDED: File age (months) greater than 'to' months.");
					return false;
				}
			}

			// Check Years
			if (filters.getFileAgeYearsFrom() != null || filters.getFileAgeYearsTo() != null) {
				long ageYears = ChronoUnit.YEARS.between(dateToCheck, now);
				System.out.println("    Current Age (Years)=" + ageYears + ", Filter: From="
						+ filters.getFileAgeYearsFrom() + ", To=" + filters.getFileAgeYearsTo());
				if (filters.getFileAgeYearsFrom() != null && ageYears < filters.getFileAgeYearsFrom()) {
					System.out.println("    EXCLUDED: File age (years) less than 'from' years.");
					return false;
				}
				if (filters.getFileAgeYearsTo() != null && ageYears > filters.getFileAgeYearsTo()) {
					System.out.println("    EXCLUDED: File age (years) greater than 'to' years.");
					return false;
				}
			}
			break;
		default:
			System.out.println("    Warning: Unknown fileAgeSegmentationType: " + filters.getFileAgeSegmentationType()
					+ ". Date/Age filter skipped.");
			break;
		}
		return true;
	}

	private static boolean checkFileTypeFilters(FileFilters filters, String lowerCaseFileExtension) {
		System.out.println("  Checking File Type (Extension) Filters. Current extension: " + lowerCaseFileExtension);
		List<String> fileTypes = filters.getFileTypes();
		String fileTypeIncExc = filters.getFileTypeIncExc();

		if (fileTypes != null && !fileTypes.isEmpty()) {
			boolean matches = fileTypes.stream()
					.anyMatch(ext -> ext.toLowerCase().equalsIgnoreCase(lowerCaseFileExtension));

			if ("Include".equalsIgnoreCase(fileTypeIncExc)) {
				System.out.println("    Include File Types: " + fileTypes);
				if (!matches) {
					System.out.println("    EXCLUDED: File type does not match any included type.");
					return false;
				}
			} else if ("Exclude".equalsIgnoreCase(fileTypeIncExc)) {
				System.out.println("    Exclude File Types: " + fileTypes);
				if (matches) {
					System.out.println("    EXCLUDED: File type matches an excluded type.");
					return false;
				}
			} else {
				System.out.println("    Warning: Unknown fileTypeIncExc mode: " + fileTypeIncExc
						+ ". File type filter will be ignored.");
			}
		} else {
			System.out.println("    No file type filters specified.");
		}
		return true;
	}

	private static boolean checkFileNameFilters(FileFilters filters, String lowerCaseNameWithoutExtension) {
		System.out.println("  Checking File Name Filters. Name without extension: " + lowerCaseNameWithoutExtension);
		List<FileNameFilterCriteria> fileNameCriteriaList = filters.getFileNameFilterCriteria();

		if (fileNameCriteriaList != null && !fileNameCriteriaList.isEmpty()) {
			boolean anyIncludeMatched = false;
			boolean hasIncludeCriteria = false;

			for (FileNameFilterCriteria criteria : fileNameCriteriaList) {
				// Ensure paramName is "FILE_NAME"
				if (!"FILE_NAME".equalsIgnoreCase(criteria.getParamName())) {
					System.out.println("    Warning: Invalid paramName for FileNameFilterCriteria, skipping: "
							+ criteria.getParamName());
					continue;
				}
				// Ensure paramValue is not null or empty
				if (criteria.getParamValue() == null || criteria.getParamValue().trim().isEmpty()) {
					System.out
							.println("    Warning: Empty paramValue for FileNameFilterCriteria, skipping: " + criteria);
					continue;
				}
				// Ensure criteria2 is not null or empty
				if (criteria.getCriteria2() == null || criteria.getCriteria2().trim().isEmpty()) {
					System.out
							.println("    Warning: Empty criteria2 for FileNameFilterCriteria, skipping: " + criteria);
					continue;
				}

				String paramValueLower = criteria.getParamValue().toLowerCase();
				boolean currentMatch = false;

				switch (criteria.getCriteria2().toLowerCase()) {
				case "startswith":
					currentMatch = lowerCaseNameWithoutExtension.startsWith(paramValueLower);
					break;
				case "contains":
					currentMatch = lowerCaseNameWithoutExtension.contains(paramValueLower);
					break;
				case "endswith":
					currentMatch = lowerCaseNameWithoutExtension.endsWith(paramValueLower);
					break;
				default:
					System.out.println("    Warning: Unknown criteria2 for FileNameFilterCriteria: "
							+ criteria.getCriteria2() + ", skipping.");
					continue; // Skip this criteria
				}

				if ("exclude".equalsIgnoreCase(criteria.getCriteria1())) {
					if (currentMatch) {
						System.out.println("    EXCLUDED by filename criteria: " + criteria);
						return false; // Immediate exclusion
					}
				} else if ("include".equalsIgnoreCase(criteria.getCriteria1())) {
					hasIncludeCriteria = true;
					if (currentMatch) {
						anyIncludeMatched = true; // Mark as matched, but keep checking other excludes
					}
				} else {
					System.out.println("    Warning: Unknown criteria1 for FileNameFilterCriteria: "
							+ criteria.getCriteria1() + ", skipping.");
				}
			}

			// Final check for include criteria: If there were any include rules, but none
			// matched
			if (hasIncludeCriteria && !anyIncludeMatched) {
				System.out.println("    EXCLUDED: Filename did not match any active include filename criteria.");
				return false;
			}
		} else {
			System.out.println("    No filename filters specified.");
		}
		return true;
	}

	private static boolean checkFolderPathFilters(FileFilters filters, String lowerCaseParentPath) {
		System.out.println("  Checking Folder Path Filters. Current parent path: "
				+ (lowerCaseParentPath != null ? lowerCaseParentPath : "[root]"));
		List<FolderPathFilterCriteria> folderPathCriteriaList = filters.getFolderPathFilterCriteria();

		if (folderPathCriteriaList != null && !folderPathCriteriaList.isEmpty()) {
			boolean anyIncludeMatched = false;
			boolean hasIncludeCriteria = false;

			for (FolderPathFilterCriteria criteria : folderPathCriteriaList) {
				// Ensure paramName is "FOLDER_PATH"
				if (!"FOLDER_PATH".equalsIgnoreCase(criteria.getParamName())) {
					System.out.println("    Warning: Invalid paramName for FolderPathFilterCriteria, skipping: "
							+ criteria.getParamName());
					continue;
				}
				// Ensure paramValue is not null or empty
				if (criteria.getParamValue() == null || criteria.getParamValue().trim().isEmpty()) {
					System.out.println(
							"    Warning: Empty paramValue for FolderPathFilterCriteria, skipping: " + criteria);
					continue;
				}

				String paramValueLower = criteria.getParamValue().toLowerCase();
				boolean currentMatch = false;

				// Check if the parent path matches the criteria
				if (lowerCaseParentPath == null) { // Handling root files (no parent path)
					// A root file (or its implied parent) might match a filter for "/" or ""
					currentMatch = paramValueLower.isEmpty() || "/".equals(paramValueLower)
							|| "\\".equals(paramValueLower); // Consider Windows root as well
				} else {
					// Perform a 'contains' check for the parent path
					currentMatch = lowerCaseParentPath.contains(paramValueLower);
				}

				if ("exclude".equalsIgnoreCase(criteria.getCriteria1())) {
					if (currentMatch) {
						System.out.println("    EXCLUDED by folder path criteria: " + criteria);
						return false; // Immediate exclusion
					}
				} else if ("include".equalsIgnoreCase(criteria.getCriteria1())) {
					hasIncludeCriteria = true;
					if (currentMatch) {
						anyIncludeMatched = true;
					}
				} else {
					System.out.println("    Warning: Unknown criteria1 for FolderPathFilterCriteria: "
							+ criteria.getCriteria1() + ", skipping.");
				}
			}

			// Final check for include criteria: If there were any include rules, but none
			// matched
			if (hasIncludeCriteria && !anyIncludeMatched) {
				System.out.println("    EXCLUDED: Folder path did not match any active include folder path criteria.");
				return false;
			}
		} else {
			System.out.println("    No folder path filters specified.");
		}
		return true;
	}

	private static boolean checkFileOwnerFilters(FileFilters filters, String fileOwnerName) {
		System.out.println("  Checking File Owner Filters. Current owner: " + fileOwnerName);
		List<String> includeFileOwners = filters.getIncludeFileOwner();
		List<String> excludeFileOwners = filters.getExcludeFileOwner();

		if (includeFileOwners != null && !includeFileOwners.isEmpty()) {
			System.out.println("    Include File Owners: " + includeFileOwners);
			if (includeFileOwners.stream().noneMatch(ownerCheck -> ownerCheck.equalsIgnoreCase(fileOwnerName))) {
				System.out.println("    EXCLUDED: Owner does not match any included owner.");
				return false;
			}
		} else {
			System.out.println("    No include file owners specified.");
		}

		if (excludeFileOwners != null && !excludeFileOwners.isEmpty()) {
			System.out.println("    Exclude File Owners: " + excludeFileOwners);
			if (excludeFileOwners.stream().anyMatch(ownerCheck -> ownerCheck.equalsIgnoreCase(fileOwnerName))) {
				System.out.println("    EXCLUDED: Owner matches an excluded owner.");
				return false;
			}
		} else {
			System.out.println("    No exclude file owners specified.");
		}
		return true;
	}

	private static boolean checkSizeFilters(FileFilters filters, long fileSizeKB) {
		System.out.println("  Checking Size Filters. File size (KB): " + fileSizeKB);
		if (filters.getSizeFromKB() != null) {
			if (fileSizeKB < filters.getSizeFromKB()) {
				System.out.println("    EXCLUDED: File size (" + fileSizeKB + "KB) less than min size ("
						+ filters.getSizeFromKB() + "KB).");
				return false;
			} else {
				System.out.println("    File meets min size criteria (>= " + filters.getSizeFromKB() + "KB).");
			}
		} else {
			System.out.println("    No min size specified.");
		}

		if (filters.getSizeToKB() != null) {
			if (fileSizeKB > filters.getSizeToKB()) {
				System.out.println("    EXCLUDED: File size (" + fileSizeKB + "KB) greater than max size ("
						+ filters.getSizeToKB() + "KB).");
				return false;
			} else {
				System.out.println("    File meets max size criteria (<= " + filters.getSizeToKB() + "KB).");
			}
		} else {
			System.out.println("    No max size specified.");
		}
		return true;
	}
}


