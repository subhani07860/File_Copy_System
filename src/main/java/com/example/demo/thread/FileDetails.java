//package com.example.demo.thread;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.math.BigDecimal;
//import java.io.OutputStream;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.attribute.BasicFileAttributes;
//import java.nio.file.attribute.FileOwnerAttributeView;
//import java.nio.file.attribute.UserPrincipal;
//import java.security.InvalidKeyException;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.time.temporal.ChronoUnit;
//import java.util.Base64;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.CopyOnWriteArrayList;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.zip.GZIPOutputStream;
//
//import javax.crypto.Cipher;
//import javax.crypto.CipherOutputStream;
//import javax.crypto.NoSuchPaddingException;
//import javax.crypto.spec.SecretKeySpec;
//
//import com.example.demo.dto.FileFilters;
//import com.example.demo.entity.FileMetaData;
//import com.example.demo.repository.FileDetailsStore;
//
//public class FileDetails implements Runnable {
//
//	private File source;
//	private File destination;
//	private FileDetailsStore fileDetailsStore;
//	private BigDecimal runId;
//	private AtomicInteger fileCounter;
//	private String activity;
//	private FileFilters filters;
//	private String encryptionKey;
//
//	public FileDetails(File source, File destination, FileDetailsStore fileDetailsStore, BigDecimal runId,
//			AtomicInteger fileCounter, String activity, FileFilters filters, String encryptionKey) {
//		super();
//		this.source = source;
//		this.destination = destination;
//		this.fileDetailsStore = fileDetailsStore;
//		this.runId = runId;
//		this.fileCounter = fileCounter;
//		this.activity = activity;
//		this.filters = filters;
//		this.encryptionKey = encryptionKey;
//	}
//
//	private static final List<Map<String, Object>> metadataList = new CopyOnWriteArrayList<>();
//	// Static list to hold metadata for all files processed in this run
//	
//	@Override
//	public void run() {
//
//		if (!isFileIncludedByFilters(source, filters)) { // If it returns false, then skip
//			System.out.println("Skipping file due to filter criteria: " + source.getAbsolutePath());
//			return; // This crucial line stops further processing (copy/purge/preview)
//		}
//
//		// --- File Copy/Purge/Preview Logic (Your Existing Code) ---
//		if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
//
//			String sourceChecksum = null;
//			String targetChecksum = null;
//			String validationStatus = "N";
//
//			MessageDigest sourceMd = null;
//			MessageDigest targetMd = null;
//
//			// Using SHA-256 as the default for better security
//			String defaultAlgorithm = "SHA-256";
//			if (filters != null && filters.getFileValidation() != null) {
//				String requestedAlgorithm = filters.getFileValidation().toUpperCase();
//				if (requestedAlgorithm.equals("SHA-256") || requestedAlgorithm.equals("MD5")
//						|| requestedAlgorithm.equals("SHA1")) {
//					defaultAlgorithm = requestedAlgorithm;
//				}
//			}
//
//			File finalDestinationFile = destination;
//			String isVersionEnabledStatus = "n";
//
//			String encryptionStatus = (filters != null && "y".equalsIgnoreCase(filters.getFileEncryption())) ? "y"
//					: "n";
//			String compressionStatus = (filters != null && "y".equalsIgnoreCase(filters.getFileCompression())) ? "y"
//					: "n";
//
//			StringBuilder effectiveFileNameBuilder = new StringBuilder(destination.getName());
//
//			// Apply encryption/compression extensions based on status
//			if ("y".equalsIgnoreCase(encryptionStatus)) {
//				effectiveFileNameBuilder.append(".enc");
//				System.out.println("Encryption enabled: File will be saved as " + effectiveFileNameBuilder);
//			} else if ("y".equalsIgnoreCase(compressionStatus)) { // 'else if' ensures mutual exclusivity
//				effectiveFileNameBuilder.append(".gz");
//				System.out.println("Compression enabled: File will be saved as " + effectiveFileNameBuilder);
//			}
//
//			finalDestinationFile = new File(destination.getParent(), effectiveFileNameBuilder.toString());
//			System.out.println("Determined final destination file name: " + finalDestinationFile.getName());
//			
//			// Check if versioning is enabled
//			if (filters != null && "y".equalsIgnoreCase(filters.getVersionEnable())) {
//				if (finalDestinationFile.exists()) {
//					String fileName = finalDestinationFile.getName();
//					String nameWithoutExtension;
//					String extension = "";
//					int dotIndex = fileName.lastIndexOf('.');
//					if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
//						nameWithoutExtension = fileName.substring(0, dotIndex);
//						extension = fileName.substring(dotIndex); // Includes the dot
//					} else {
//						nameWithoutExtension = fileName;
//						extension = "";
//					}
//
//					LocalDateTime existingFileModifiedTime = LocalDateTime.ofInstant(
//							finalDestinationFile.lastModified() > 0
//									? new java.util.Date(finalDestinationFile.lastModified()).toInstant()
//									: LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(), // Fallback if
//																										// lastModified
//																										// is 0
//							ZoneId.systemDefault());
//					String timestamp = existingFileModifiedTime.format(DateTimeFormatter.ofPattern("_yyyyMMdd_HHmmss"));
//					String oldFileName = nameWithoutExtension + timestamp + extension;
//					File oldFile = new File(finalDestinationFile.getParent(), oldFileName);
//
//					System.out.println("Attempting to rename existing file '" + finalDestinationFile.getAbsolutePath()
//							+ "' to '" + oldFile.getAbsolutePath() + "' for versioning.");
//
//					if (finalDestinationFile.renameTo(oldFile)) {
//						System.out.println("Existing file renamed for versioning: " + oldFile.getName());
//					}
//				}
//
//				isVersionEnabledStatus = "y";
//				System.out.println("Versioning enabled: New file will be saved with its original name.");
//			}
//			
//			try (FileInputStream in = new FileInputStream(source);
//					OutputStream finalOutputStream = createChainedOutputStream(
//							new FileOutputStream(finalDestinationFile), encryptionStatus, compressionStatus,
//							encryptionKey)) {
//				
//				// Initialize MessageDigest for checksum calculation
//				sourceMd = MessageDigest.getInstance(defaultAlgorithm);
//				targetMd = MessageDigest.getInstance(defaultAlgorithm);
//
//				byte[] buffer = new byte[4096];
//				int length;
//				while ((length = in.read(buffer)) > 0) {
//					finalOutputStream.write(buffer, 0, length);
//					sourceMd.update(buffer, 0, length);
//					targetMd.update(buffer, 0, length);
//				}
//				// Ensure all data is flushed to the output stream
//				sourceChecksum = bytesToHexa(sourceMd.digest());
//				targetChecksum = bytesToHexa(targetMd.digest());
//
//				if (sourceChecksum != null && sourceChecksum.equals(targetChecksum)) {
//					validationStatus = "Y";
//				}
//				// Build metadata for the copied file
//				Map<String, Object> metadata = buildMetadata1(source, finalDestinationFile);
//				
//				metadata.put("sourceChecksum", sourceChecksum);
//				metadata.put("targetChecksum", targetChecksum);
//				metadata.put("validationStatus", validationStatus);
//				metadata.put("isVersionEnable", isVersionEnabledStatus);
//				metadata.put("isEncryptionEnabled", encryptionStatus);
//				metadata.put("encryptionKey", "y".equalsIgnoreCase(encryptionStatus) ? encryptionKey : null);
//				metadata.put("isCompressionEnabled", compressionStatus);
//
//				metadataList.add(metadata);
//				saveMetaData(metadata);
//
//				System.out.println("Copied From: " + source.getAbsolutePath());
//				System.out.println("Copied To  : " + finalDestinationFile.getAbsolutePath());
//
//				System.out.println("Source Checksum: " + sourceChecksum);
//				System.out.println("Target Checksum: " + targetChecksum);
//				System.out.println("Validation Status: " + validationStatus);
//
//			} catch (IOException e) {
//				System.out.println("Error copying file: " + source.getAbsolutePath());
//				e.printStackTrace();
//			} catch (NoSuchAlgorithmException e) {
//				System.out.println("Checksum algorithm not found: " + e.getMessage());
//				e.printStackTrace();
//			} catch (NoSuchPaddingException e) {
//				System.out.println("Cipher padding error: " + e.getMessage());
//				e.printStackTrace();
//			} catch (InvalidKeyException e) {
//				System.out.println("Invalid encryption key: " + e.getMessage());
//				e.printStackTrace();
//			}
//
//			if ("copyandpurge".equalsIgnoreCase(activity)) {
//				if (source.delete()) {
//					System.out.println("Deleted source file: " + source.getAbsolutePath());
//				} else {
//					System.out.println("Failed to delete file: " + source.getAbsolutePath());
//				}
//			}
//		} else if ("purgeonly".equalsIgnoreCase(activity)) {
//			Map<String, Object> metadata = buildMetadata1(source, null);
//			metadataList.add(metadata);
//			saveMetaData(metadata);
//			if (source.delete()) {
//				System.out.println("Deleted source file: " + source.getAbsolutePath());
//			} else {
//				System.out.println("Failed to delete file: " + source.getAbsolutePath());
//			}
//		} else if ("preview".equalsIgnoreCase(activity)) {
//			Map<String, Object> metadata = buildMetadata1(source, null);
//			metadataList.add(metadata);
//		} else {
//			System.out.println("Unknown activity: " + activity);
//		}
//	}
//
//	private OutputStream createChainedOutputStream(FileOutputStream baseOutputStream, String encryptionStatus,
//			String compressionStatus, String encryptionKey)
//			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IOException {
//
//		if ("y".equalsIgnoreCase(encryptionStatus)) {
//			if (encryptionKey == null || encryptionKey.isEmpty()) {
//				throw new IllegalArgumentException(
//						"Encryption key cannot be null or empty when encryption is enabled.");
//			}
//			byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
//			SecretKeySpec secretKey = new SecretKeySpec(decodedKey, "AES");
//			Cipher cipher = Cipher.getInstance("AES");
//			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
//			return new CipherOutputStream(baseOutputStream, cipher);
//		} else if ("y".equalsIgnoreCase(compressionStatus)) {
//			return new GZIPOutputStream(baseOutputStream);
//		} else {
//			return baseOutputStream;
//		}
//	}
//
//	private boolean isFileIncludedByFilters(File file, FileFilters filters) {
//		System.out.println("--- Filtering File: " + file.getAbsolutePath() + " ---");
//
//		if (filters == null) {
//			System.out.println("Filter object is NULL. File included by default.");
//			return true; // No filters specified, include all files
//		}
//
//		System.out.println("Filters object present. Checking criteria...");
//
//		Path filePath = file.toPath();
//		String fileName = file.getName();
//
//		// --- Extract nameWithoutExtension 
//		final String currentNameWithoutExtension;
//		final String currentFileExtension;
//
//		int dotIndex = fileName.lastIndexOf('.');
//		if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
//			currentNameWithoutExtension = fileName.substring(0, dotIndex);
//			currentFileExtension = fileName.substring(dotIndex + 1);
//		}  else { // dotIndex == fileName.length() - 1 (e.g., "archive.")
//			currentNameWithoutExtension = fileName.substring(0, dotIndex);
//			currentFileExtension = "";
//		}
//		// Convert to lowercase for case-insensitive filename comparisons
//		final String lowerCaseNameWithoutExtension = currentNameWithoutExtension.toLowerCase();
//
//		final String parentPath = file.getParent(); // This will be null for root files
//		System.out.println("FileName: " + fileName + ", NameWithoutExtension: " + currentNameWithoutExtension
//				+ ", Extension: " + currentFileExtension + ", ParentPath: " + parentPath);
//		System.out.println("LowerCaseNameWithoutExtension for filtering: " + lowerCaseNameWithoutExtension);
//
//		try {
//			BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
//			LocalDateTime creationTime = attrs.creationTime().toInstant().atZone(ZoneId.systemDefault())
//					.toLocalDateTime();
//			LocalDateTime lastModifiedTime = attrs.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())
//					.toLocalDateTime();
//			long fileSizeKB = file.length() / 1024; // Convert bytes to KB
//
//			FileOwnerAttributeView ownerAttr = Files.getFileAttributeView(filePath, FileOwnerAttributeView.class);
//			UserPrincipal owner = (ownerAttr != null) ? ownerAttr.getOwner() : null;
//			String fileOwnerName = (owner != null) ? owner.getName() : "UNKNOWN";
//
//			// --- File Age/Date Filters ---
//			System.out.println("Checking File Age/Date Filters. Segmentation: " + filters.getFileAgeFileSegmentation());
//			if (filters.getFileAgeFileSegmentation() != null
//					&& !filters.getFileAgeFileSegmentation().trim().isEmpty()) {
//				switch (filters.getFileAgeFileSegmentation().toLowerCase()) {
//				case "creation":
//					System.out.println("  Creation Date Filter: From=" + filters.getCreationDateFrom() + ", To="
//							+ filters.getCreationDateTo() + ", FileCreation=" + creationTime);
//					if (filters.getCreationDateFrom() != null && creationTime.isBefore(filters.getCreationDateFrom())) {
//						System.out.println("  EXCLUDED: Creation date before 'from' date.");
//						return false;
//					}
//					if (filters.getCreationDateTo() != null && creationTime.isAfter(filters.getCreationDateTo())) {
//						System.out.println("  EXCLUDED: Creation date after 'to' date.");
//						return false;
//					}
//					break;
//				case "modified":
//					System.out.println("  Modified Date Filter: From=" + filters.getModifiedDateFrom() + ", To="
//							+ filters.getModifiedDateTo() + ", FileModified=" + lastModifiedTime);
//					if (filters.getModifiedDateFrom() != null
//							&& lastModifiedTime.isBefore(filters.getModifiedDateFrom())) {
//						System.out.println("  EXCLUDED: Modification date before 'from' date.");
//						return false;
//					}
//					if (filters.getModifiedDateTo() != null && lastModifiedTime.isAfter(filters.getModifiedDateTo())) {
//						System.out.println("  EXCLUDED: Modification date after 'to' date.");
//						return false;
//					}
//					break;
//				case "age":
//					LocalDateTime now = LocalDateTime.now();
//					long ageDays = ChronoUnit.DAYS.between(creationTime, now);
//					System.out.println("  Age Filter: From=" + filters.getFileAgeDaysFrom() + ", To="
//							+ filters.getFileAgeDaysTo() + ", FileAgeDays=" + ageDays);
//					if (filters.getFileAgeDaysFrom() != null && ageDays < filters.getFileAgeDaysFrom()) {
//						System.out.println("  EXCLUDED: File age less than 'from' days.");
//						return false;
//					}
//					if (filters.getFileAgeDaysTo() != null && ageDays > filters.getFileAgeDaysTo()) {
//						System.out.println("  EXCLUDED: File age greater than 'to' days.");
//						return false;
//					}
//					break;
//				default:
//					System.out.println("  Warning: Unknown fileAgeFileSegmentation type: "
//							+ filters.getFileAgeFileSegmentation() + ". Filter will be ignored.");
//					break;
//				}
//			} else {
//				System.out.println("  File Age/Date Filters not specified or empty.");
//			}
//
//			// --- Extension Filters ---
//			System.out.println("Checking Extension Filters. Current extension: " + currentFileExtension);
//			List<String> excludeExtensions = filters.getExcludeExtension();
//			if (excludeExtensions != null && !excludeExtensions.isEmpty()) {
//				System.out.println("  Exclude Extensions: " + excludeExtensions);
//				if (excludeExtensions.stream().anyMatch(ext -> ext.equalsIgnoreCase(currentFileExtension))) {
//					System.out.println("  EXCLUDED: Matches exclude extension.");
//					return false;
//				}
//			} else {
//				System.out.println("  No exclude extensions specified.");
//			}
//
//			List<String> includeExtensions = filters.getIncludeExtension();
//			if (includeExtensions != null && !includeExtensions.isEmpty()) {
//				System.out.println("  Include Extensions: " + includeExtensions);
//				if (includeExtensions.stream().noneMatch(ext -> ext.equalsIgnoreCase(currentFileExtension))) {
//					System.out.println("  EXCLUDED: Does not match any include extension.");
//					return false;
//				}
//			} else {
//				System.out.println("  No include extensions specified.");
//			}
//
//		
//			boolean fileNameMatched = false; // True if the file matched ANY active 'include' filename filter
//			
//			boolean hasAnyIncludePatternConfigured = false;
//
//			System.out.println(
//					"Checking File Name Filters. Segmentation types: " + filters.getFileNameFileSegmentation());
//			if (filters.getFileNameFileSegmentation() != null && !filters.getFileNameFileSegmentation().isEmpty()) {
//
//				for (String segmentationType : filters.getFileNameFileSegmentation()) {
//					System.out.println("  Processing segmentation type: " + segmentationType);
//					switch (segmentationType.toLowerCase()) { // Ensure this is lowercase to match filter config
//					case "startswith":
//						if (filters.getIncludeFileStartsWith() != null
//								&& !filters.getIncludeFileStartsWith().isEmpty()) {
//							hasAnyIncludePatternConfigured = true; // Yes, we have include patterns to check against
//							System.out.println("    Include StartsWith: " + filters.getIncludeFileStartsWith());
//							if (filters.getIncludeFileStartsWith().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::startsWith)) {
//								fileNameMatched = true; // File matched an include filter
//								System.out.println("    MATCHED: Includes StartsWith.");
//							}
//						} else {
//							System.out.println("    No include StartsWith patterns.");
//						}
//						if (filters.getExcludeFileStartsWith() != null
//								&& !filters.getExcludeFileStartsWith().isEmpty()) {
//							System.out.println("    Exclude StartsWith: " + filters.getExcludeFileStartsWith());
//							if (filters.getExcludeFileStartsWith().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::startsWith)) {
//								System.out.println("    EXCLUDED: Matches exclude StartsWith.");
//								return false; // Immediate exit on exclude match
//							}
//						} else {
//							System.out.println("    No exclude StartsWith patterns.");
//						}
//						break;
//					case "contains":
//						if (filters.getIncludeFileContains() != null && !filters.getIncludeFileContains().isEmpty()) {
//							hasAnyIncludePatternConfigured = true;
//							System.out.println("    Include Contains: " + filters.getIncludeFileContains());
//							if (filters.getIncludeFileContains().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::contains)) {
//								fileNameMatched = true;
//								System.out.println("    MATCHED: Includes Contains.");
//							}
//						} else {
//							System.out.println("    No include Contains patterns.");
//						}
//						if (filters.getExcludeFileContains() != null && !filters.getExcludeFileContains().isEmpty()) {
//							System.out.println("    Exclude Contains: " + filters.getExcludeFileContains());
//							if (filters.getExcludeFileContains().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::contains)) {
//								System.out.println("    EXCLUDED: Matches exclude Contains.");
//								return false;
//							}
//						} else {
//							System.out.println("    No exclude Contains patterns.");
//						}
//						break;
//					case "endswith":
//						if (filters.getIncludeFileEndsWith() != null && !filters.getIncludeFileEndsWith().isEmpty()) {
//							hasAnyIncludePatternConfigured = true;
//							System.out.println("    Include EndsWith: " + filters.getIncludeFileEndsWith());
//							if (filters.getIncludeFileEndsWith().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::endsWith)) {
//								fileNameMatched = true;
//								System.out.println("    MATCHED: Includes EndsWith.");
//							}
//						} else {
//							System.out.println("    No include EndsWith patterns.");
//						}
//						if (filters.getExcludeFileEndsWith() != null && !filters.getExcludeFileEndsWith().isEmpty()) {
//							System.out.println("    Exclude EndsWith: " + filters.getExcludeFileEndsWith());
//							if (filters.getExcludeFileEndsWith().stream()
//									.anyMatch(lowerCaseNameWithoutExtension::endsWith)) {
//								System.out.println("    EXCLUDED: Matches exclude EndsWith.");
//								return false;
//							}
//						} else {
//							System.out.println("    No exclude EndsWith patterns.");
//						}
//						break;
//					default:
//						System.out.println("  Warning: Unknown fileNameFileSegmentation type: " + segmentationType);
//						break;
//					}
//				}
//
//				
//				System.out.println("  Final filename filter check: hasAnyIncludePatternConfigured="
//						+ hasAnyIncludePatternConfigured + ", fileNameMatched=" + fileNameMatched);
//				if (hasAnyIncludePatternConfigured && !fileNameMatched) {
//					System.out.println("  EXCLUDED: Filename did not match any active include filter patterns.");
//					return false;
//				}
//			} else {
//				System.out.println("  Filename filters not specified or empty.");
//			}
//
//			// --- Folder Path Filters ---
//			System.out.println("Checking Folder Path Filters. Current parent path: " + parentPath);
//			List<String> excludeFolderPath = filters.getExcludeFolderPath();
//			if (excludeFolderPath != null && !excludeFolderPath.isEmpty()) {
//				System.out.println("  Exclude Folder Paths: " + excludeFolderPath);
//				if (parentPath != null && excludeFolderPath.stream()
//						.anyMatch(path -> parentPath.toLowerCase().contains(path.toLowerCase()))) {
//					System.out.println("  EXCLUDED: Matches exclude folder path.");
//					return false;
//				} else if (parentPath == null && excludeFolderPath.stream().anyMatch(String::isEmpty)) { 
//					System.out.println(
//							"  EXCLUDED: Exclude folder path includes empty string and parent is null (root).");
//					return false;
//				}
//			} else {
//				System.out.println("  No exclude folder paths specified.");
//			}
//
//			List<String> includeFolderPath = filters.getIncludeFolderPath();
//			if (includeFolderPath != null && !includeFolderPath.isEmpty()) {
//				System.out.println("  Include Folder Paths: " + includeFolderPath);
//				
//				if (parentPath == null) {
//					if (!includeFolderPath.stream().anyMatch(String::isEmpty)) { // Only allow if empty string is in
//						// includes for root
//						System.out.println("  EXCLUDED: Root file and no empty string in include folder paths.");
//						return false;
//					}
//				} else { // Not a root file
//					boolean matched = includeFolderPath.stream()
//							.anyMatch(path -> parentPath.toLowerCase().contains(path.toLowerCase()));
//					if (!matched) {
//						System.out.println("  EXCLUDED: Folder path does not match any include folder path.");
//						return false;
//					}
//				}
//			} else {
//				System.out.println("  No include folder paths specified.");
//			}
//
//			// --- File Owner Filters ---
//			System.out.println("Checking File Owner Filters. Current owner: " + fileOwnerName);
//			List<String> includeFileOwners = filters.getIncludeFileOwner();
//			if (includeFileOwners != null && !includeFileOwners.isEmpty()) {
//				System.out.println("  Include File Owners: " + includeFileOwners);
//				if (includeFileOwners.stream().noneMatch(ownerCheck -> ownerCheck.equalsIgnoreCase(fileOwnerName))) {
//					System.out.println("  EXCLUDED: Owner does not match any include owner.");
//					return false;
//				}
//			} else {
//				System.out.println("  No include file owners specified.");
//			}
//
//			List<String> excludeFileOwners = filters.getExcludeFileOwner();
//			if (excludeFileOwners != null && !excludeFileOwners.isEmpty()) {
//				System.out.println("  Exclude File Owners: " + excludeFileOwners);
//				if (excludeFileOwners.stream().anyMatch(ownerCheck -> ownerCheck.equalsIgnoreCase(fileOwnerName))) {
//					System.out.println("  EXCLUDED: Owner matches exclude owner.");
//					return false;
//				}
//			} else {
//				System.out.println("  No exclude file owners specified.");
//			}
//
//			// --- Size Filters ---
//			System.out.println("Checking Size Filters. File size (KB): " + fileSizeKB);
//			if (filters.getSizeFromKB() != null && fileSizeKB < filters.getSizeFromKB()) {
//				System.out.println("  EXCLUDED: File size less than min size (" + filters.getSizeFromKB() + "KB).");
//				return false;
//			} else {
//				System.out.println("  No min size specified or file meets criteria.");
//			}
//			if (filters.getSizeToKB() != null && fileSizeKB > filters.getSizeToKB()) {
//				System.out.println("  EXCLUDED: File size greater than max size (" + filters.getSizeToKB() + "KB).");
//				return false;
//			} else {
//				System.out.println("  No max size specified or file meets criteria.");
//			}
//
//		} catch (IOException e) {
//			System.err.println(
//					"Error reading attributes for filtering file: " + file.getAbsolutePath() + " - " + e.getMessage());
//			e.printStackTrace();
//			return false;
//			
//		} catch (NullPointerException e) { // Catch potential NPE if ownerAttr.getOwner() is null and not checked
//			System.err.println("NullPointerException while processing file attributes for " + file.getAbsolutePath()
//					+ ": " + e.getMessage());
//			e.printStackTrace();
//			return false;
//		}
//
//		System.out.println("File PASSED all filter criteria: " + file.getAbsolutePath());
//		return true; // File passed all active filters
//	}
//
//	private String bytesToHexa(byte[] bytes) {
//		StringBuilder sb = new StringBuilder();
//		for (byte b : bytes) {
//			sb.append(String.format("%02x", b));
//		}
//		return sb.toString();
//	}
//
//	private void saveMetaData(Map<String, Object> metadata) {
//		if (runId != null && fileDetailsStore != null && fileCounter != null) {
//			try {
//				FileMetaData meta = new FileMetaData();
//
//				meta.setFileName((String) metadata.get("fileName"));
//				meta.setFilePath((String) metadata.get("FilePath"));
//				meta.setSize((Long) metadata.get("Size"));
//				meta.setFileSrcPath((String) metadata.get("FileSrcPath"));
//				meta.setFileType((String) metadata.get("fileType"));
//				meta.setAuthor((String) metadata.get("Author"));
//				meta.setCreationDate((String) metadata.get("CreationDate")); 
//				meta.setModificationDate((String) metadata.get("ModifiedDate")); 
//				meta.setRunId(runId);
//
//				if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
//					meta.setTargetPath((String) metadata.get("targetPath"));
//					meta.setSourceChecksum((String) metadata.get("sourceChecksum"));
//					meta.setTargetChecksum((String) metadata.get("targetChecksum"));
//					meta.setValidationStatus((String) metadata.get("validationStatus"));
//					meta.setIsVersionEnable((String) metadata.get("isVersionEnable"));
//					meta.setFileEncryption((String) metadata.get("isEncryptionEnabled"));
//					meta.setFileEncryptionKey((String) metadata.get("encryptionKey"));
//					meta.setFileCompression((String) metadata.get("isCompressionEnabled"));
//
//				} else {
//					
//					meta.setTargetPath(null);
//					meta.setSourceChecksum(null);
//					meta.setTargetChecksum(null);
//					meta.setValidationStatus(null);
//					meta.setIsVersionEnable(null);
//					meta.setFileEncryption(null);
//					meta.setFileEncryptionKey(null);
//					meta.setFileCompression(null);
//				}
//
//				int count = fileCounter.incrementAndGet();
//				String fileId = runId + "." + String.format("%03d", count);
//				meta.setFileId(fileId);
//
//				fileDetailsStore.save(meta);
//
//			} catch (Exception e) {
//				System.err.println(
//						"Error saving metadata for file: " + metadata.get("fileName") + " - " + e.getMessage());
//				e.printStackTrace();
//			}
//		} else {
//			System.out.println("Warning: Metadata will not be saved. runId, fileDetailsStore, or fileCounter is null.");
//		}
//	}
//
//	
//	
//	private Map<String, Object> buildMetadata1(File source, File dest) { // Build metadata for a single file
//		Map<String, Object> dMap = new HashMap<>();
//		Path sourcePath = source.toPath();
//
//		if (source.exists()) {
//			try {
//				BasicFileAttributes attrs = Files.readAttributes(sourcePath, BasicFileAttributes.class);
//				FileOwnerAttributeView ownerAttr = Files.getFileAttributeView(sourcePath, FileOwnerAttributeView.class);
//				UserPrincipal owner = (ownerAttr != null) ? ownerAttr.getOwner() : null;
//
//				String fileName = source.getName();
//
//				String extension = "";
//				int dotIndex = fileName.lastIndexOf(".");
//				if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
//					extension = fileName.substring(dotIndex + 1);
//				}
//
//				dMap.put("fileName", source.getName());
//				dMap.put("FilePath", source.getAbsolutePath());
//				dMap.put("CreationDate", attrs.creationTime().toString());
//				dMap.put("Size", source.length());
//				dMap.put("FileSrcPath", source.getParent());
//
//				dMap.put("ModifiedDate", attrs.lastModifiedTime().toString()); 
//				dMap.put("fileType", extension);
//				dMap.put("Author",owner.getName());
//
//				if (dest != null) {
//					dMap.put("targetfileName", dest.getName());
//					dMap.put("targetPath", dest.getAbsolutePath());
//				}
//
//			} catch (IOException e) {
//				System.err.println(
//						"Error retrieving metadata for file: " + source.getAbsolutePath() + " - " + e.getMessage());
//				e.printStackTrace();
//			}
//		} else {
//			System.out.println("Source file does not exist when building metadata: " + source.getAbsolutePath());
//		}
//		return dMap;
//	}
//
//	public static void add(Map<String, Object> dto) {
//		metadataList.add(dto);
//	}
//
//	public static List<Map<String, Object>> getAll() {
//		return Collections.unmodifiableList(metadataList);
//	}
//
//	public static void clear() {
//		metadataList.clear();
//	}
//}