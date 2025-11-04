package com.example.demo.thread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.dto.FileFilters;
import com.example.demo.entity.FileMetaData;
import com.example.demo.repository.FileDetailsStore;

public class FileDetailsUpdate implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(FileDetailsUpdate.class);

	private File sourceFile;
	private File destinationBase;
	private File sourceRootPath;
	private FileDetailsStore fileDetailsStore;
	private BigDecimal runId;
	private AtomicInteger fileCounter;
	private String activity;
	private FileFilters filters;
	private String encryptionKey;

	public FileDetailsUpdate(File sourceFile, File destinationRootPath, File sourceRootPath,
			FileDetailsStore fileDetailsStore, BigDecimal runId, AtomicInteger fileCounter, String activity,
			FileFilters filters, String encryptionKey) {
		this.sourceFile = sourceFile;
		this.destinationBase = destinationRootPath; // Store the absolute root target path
		this.sourceRootPath = sourceRootPath;
		this.fileDetailsStore = fileDetailsStore;
		this.runId = runId;
		this.fileCounter = fileCounter;
		this.activity = activity;
		this.filters = filters;
		this.encryptionKey = encryptionKey;
	}

	private static final List<Map<String, Object>> metadataList = new CopyOnWriteArrayList<>();

	@Override
	public void run() {

//			if (sourceFile.getName().equalsIgnoreCase("failtest.txt")) {
//	            throw new InterruptedException("Intentional exception for testing thread propagation!");
//	        }
		// Use the FileFilterUtility for all filtering logic
		if (FileFilterUtility.validateFile(sourceFile, filters)) {
			// Determine the final destination path based on the includeSourcePath flag

			File finalDestinationFile = null;
			if (destinationBase != null) {
				finalDestinationFile = constructDestinationPath();

				// Ensure parent directories exist for the final file
				if (finalDestinationFile.getParentFile() != null && !finalDestinationFile.getParentFile().exists()) {
					finalDestinationFile.getParentFile().mkdirs();
				}
			}

			if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {

				String sourceChecksum = null;
				String targetChecksum = null;
				String validationStatus = "N";

				MessageDigest sourceMd = null;
				MessageDigest targetMd = null;

				String defaultAlgorithm = "SHA-256";
				if (filters != null && filters.getFileValidation() != null) {
					String requestedAlgorithm = filters.getFileValidation().toUpperCase();
					if (requestedAlgorithm.equals("SHA-256") || requestedAlgorithm.equals("MD5")
							|| requestedAlgorithm.equals("SHA1")) {
						defaultAlgorithm = requestedAlgorithm;
					}
				}

				String isVersionEnabledStatus = "n";
				String encryptionStatus = (filters != null && "y".equalsIgnoreCase(filters.getFileEncryption())) ? "y"
						: "n";
				String compressionStatus = (filters != null && "y".equalsIgnoreCase(filters.getFileCompression())) ? "y"
						: "n";

				// Determine the base file name with potential extensions for
				// encryption/compression
				// Start with the name of the determined finalDestinationFile
				StringBuilder effectiveFileNameBuilder = new StringBuilder(finalDestinationFile.getName());

				// Append .enc and .gz extensions only if not already present and enabled
				if ("y".equalsIgnoreCase(encryptionStatus)
						&& !effectiveFileNameBuilder.toString().toLowerCase().endsWith(".enc")) {
					effectiveFileNameBuilder.append(".enc");
				}
				if ("y".equalsIgnoreCase(compressionStatus)
						&& !effectiveFileNameBuilder.toString().toLowerCase().endsWith(".gz")) {
					effectiveFileNameBuilder.append(".gz");
				}
				// Construct the final file path with extensions in its parent directory
				File finalDestinationFileWithExtensions = new File(finalDestinationFile.getParentFile(),
						effectiveFileNameBuilder.toString());

//				System.out.println("Determined final destination file path (with extensions): "
//						+ finalDestinationFileWithExtensions.getAbsolutePath());
				logger.info("Determined final destination file path (with extensions): {}",
						finalDestinationFileWithExtensions.getAbsolutePath());

				// Check if versioning is enabled
				if (filters != null && "y".equalsIgnoreCase(filters.getVersionEnable())) {
					if (finalDestinationFileWithExtensions.exists()) {
						String fileName = finalDestinationFileWithExtensions.getName();
						String nameWithoutExtension;
						String extension = "";
						int dotIndex = fileName.lastIndexOf('.');
						if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
							nameWithoutExtension = fileName.substring(0, dotIndex);
							extension = fileName.substring(dotIndex); // Includes the dot
						} else {
							nameWithoutExtension = fileName;
							extension = "";
						}

						LocalDateTime existingFileModifiedTime = LocalDateTime.ofInstant(
								finalDestinationFileWithExtensions.lastModified() > 0
										? new java.util.Date(finalDestinationFileWithExtensions.lastModified())
												.toInstant()
										: LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant(), // Fallback
								ZoneId.systemDefault());
						String timestamp = existingFileModifiedTime
								.format(DateTimeFormatter.ofPattern("_yyyyMMdd_HHmmss"));
						String oldFileName = nameWithoutExtension + timestamp + extension;
						File oldFile = new File(finalDestinationFileWithExtensions.getParent(), oldFileName);

//						System.out.println("Attempting to rename existing file '"
//								+ finalDestinationFileWithExtensions.getAbsolutePath() + "' to '"
//								+ oldFile.getAbsolutePath() + "' for versioning.");
						logger.info("Attempting to rename existing file '{}' to '{}' for versioning.",
								finalDestinationFileWithExtensions.getAbsolutePath(), oldFile.getAbsolutePath());

						if (finalDestinationFileWithExtensions.renameTo(oldFile)) {
//							System.out.println("Existing file renamed for versioning: " + oldFile.getName());
							logger.info("Existing file renamed for versioning: {}", oldFile.getName());
						} else {
//							System.err.println("Failed to rename existing file for versioning: "
//									+ finalDestinationFileWithExtensions.getAbsolutePath());
							logger.error("Failed to rename existing file for versioning: {}",
									finalDestinationFileWithExtensions.getAbsolutePath());
						}
					}
					isVersionEnabledStatus = "y";
					System.out.println("Versioning enabled: New file will be saved with its original name.");
				}

				String isArchived = "N";
				try (FileInputStream in = new FileInputStream(sourceFile);
						OutputStream finalOutputStream = createChainedOutputStream(
								new FileOutputStream(finalDestinationFileWithExtensions), encryptionStatus,
								compressionStatus, encryptionKey)) {

					sourceMd = MessageDigest.getInstance(defaultAlgorithm);
					targetMd = MessageDigest.getInstance(defaultAlgorithm); // For logical content checksum

					byte[] buffer = new byte[4096];
					int length;
					while ((length = in.read(buffer)) > 0) {
						finalOutputStream.write(buffer, 0, length);
						sourceMd.update(buffer, 0, length);
						targetMd.update(buffer, 0, length); // Still comparing source bytes for validationStatus
					}
					finalOutputStream.flush(); // Crucial for chained streams
					sourceChecksum = bytesToHexa(sourceMd.digest());
					targetChecksum = bytesToHexa(targetMd.digest()); // This is a checksum of source bytes, not the
																		// final target file if transformed

					if (sourceChecksum != null && sourceChecksum.equals(targetChecksum)) {
						validationStatus = "Y";
						isArchived = "Y"; // Mark as archived only if validation is successful
					}

					if (sourceFile.getName().endsWith(".txt")) {
						throw new RuntimeException("This file is not supported");
					}

					Map<String, Object> metadata = buildMetadata1(sourceFile, finalDestinationFileWithExtensions);
					// --- NEW DUPLICATE CHECK LOGIC ---
					if (filters != null && "Y".equalsIgnoreCase(filters.getRunCheckDuplicate())) {
						String targetPathForCheck = (String) metadata.get("targetPath");
						String targetFileNameForCheck = (String) metadata.get("targetfileName");

						if (targetPathForCheck != null && targetFileNameForCheck != null) {
							List<FileMetaData> existingDuplicates = fileDetailsStore
									.findAllByTargetPathAndTargetFileName(targetPathForCheck, targetFileNameForCheck);
							if (!existingDuplicates.isEmpty()) {
//								System.out.println("RUN_CHECK_DUPLICATE: Found " + existingDuplicates.size()
//										+ " existing duplicate entries for " + targetFileNameForCheck + " in "
//										+ targetPathForCheck + ". Deleting older entries.");
								logger.info(
										"RUN_CHECK_DUPLICATE: Found {} existing duplicate entries for {} in {}. Deleting older entries.",
										existingDuplicates.size(), targetFileNameForCheck, targetPathForCheck);
								fileDetailsStore.deleteAll(existingDuplicates); // Delete all previously existing
																				// duplicates
							}
						}
					}

				} catch (IOException e) {
//					System.err.println("Error copying file: " + sourceFile.getAbsolutePath() + " - " + e.getMessage());
//					e.printStackTrace();
					logger.error("Error copying file: {} - {}", sourceFile.getAbsolutePath(), e.getMessage());
					isArchived = "N";
				} catch (NoSuchAlgorithmException e) {
//					System.err.println("Checksum algorithm not found: " + e.getMessage());
//					e.printStackTrace();
					logger.error("Checksum algorithm not found: {}", e.getMessage());
					isArchived = "N";
				} catch (NoSuchPaddingException e) {
//					System.err.println("Cipher padding error: " + e.getMessage());
//					e.printStackTrace();
					logger.error("Cipher padding error: {}", e.getMessage());
					isArchived = "N";
				} catch (InvalidKeyException e) {
//					System.err.println("Invalid encryption key: " + e.getMessage());
//					e.printStackTrace();
					logger.error("Invalid encryption key: {}", e.getMessage());
					isArchived = "N";
				} catch (Exception e) {
//					System.err.println("General error during file copy: " + e.getMessage());
//					e.printStackTrace();
					logger.error("General error during file copy: {}", e.getMessage());
					isArchived = "N";
				} finally {
					Map<String, Object> metadata = buildMetadata1(sourceFile, finalDestinationFileWithExtensions);
					metadata.put("sourceChecksum", sourceChecksum);
					metadata.put("targetChecksum", targetChecksum);
					metadata.put("validationStatus", validationStatus);
					metadata.put("isVersionEnable", isVersionEnabledStatus);
					metadata.put("isEncryptionEnabled", encryptionStatus);
					metadata.put("encryptionKey", "y".equalsIgnoreCase(encryptionStatus) ? encryptionKey : null);
					metadata.put("isCompressionEnabled", compressionStatus);
					metadata.put("runId", runId);

					metadata.put("isArchived", isArchived);
//					System.out.println("isArchived initial value set to: " + isArchived);
					logger.info("isArchived initial value set to: {}", isArchived);

					metadataList.add(metadata);

					saveMetaData(metadata);

//					System.out.println("Copied From: " + sourceFile.getAbsolutePath());
//					System.out.println("Copied To  : " + finalDestinationFileWithExtensions.getAbsolutePath());
//					System.out.println("Source Checksum: " + sourceChecksum);
//					System.out.println("Target Checksum: " + targetChecksum);
//					System.out.println("Validation Status: " + validationStatus);
					logger.info("Copied From: {}", sourceFile.getAbsolutePath());
					logger.info("Copied To  : {}", finalDestinationFileWithExtensions.getAbsolutePath());
					logger.info("Source Checksum: {}", sourceChecksum);
					logger.info("Target Checksum: {}", targetChecksum);
					logger.info("Validation Status: {}", validationStatus);

				}

				if ("copyandpurge".equalsIgnoreCase(activity)) {
					if (validationStatus.equals("Y") && sourceFile.delete()) { // Only delete if validation
																				// successful
//						System.out.println("Deleted source file after successful copy and validation: "
//								+ sourceFile.getAbsolutePath());
						logger.info("Deleted source file after successful copy and validation: {}",
								sourceFile.getAbsolutePath());
					} else if (!validationStatus.equals("Y")) {
//						System.out.println("Skipping deletion of source file due to validation failure: "
//								+ sourceFile.getAbsolutePath());
						logger.warn("Skipping deletion of source file due to validation failure: {}",
								sourceFile.getAbsolutePath());
					} else {
//						System.err.println("Failed to delete file: " + sourceFile.getAbsolutePath());
						logger.error("Failed to delete file: {}", sourceFile.getAbsolutePath());
					}
				}
			} else if ("purgeonly".equalsIgnoreCase(activity)) {
				try {
					Map<String, Object> metadata = buildMetadata1(sourceFile, null);
					metadataList.add(metadata);
					saveMetaData(metadata);

					// here insted of using if-else just use delete() and log the result it helps to
					// catch the issue in thread
//					if (sourceFile.delete()) {
//						System.out.println("Deleted source file: " + sourceFile.getAbsolutePath());
//					} else {
//						System.err.println("Failed to delete file: " + sourceFile.getAbsolutePath());
//					}
					sourceFile.delete();
				} catch (Exception e) {
//					System.err.println("Error during purge operation: " + e.getMessage());
//					e.printStackTrace();
					logger.error("Error during purge operation: {}", e.getMessage());
				}
			} else if ("preview".equalsIgnoreCase(activity)) {

				Map<String, Object> metadata = buildMetadata1(sourceFile, finalDestinationFile);
				metadataList.add(metadata);
				// In preview, we might want to log the proposed destination path
//				System.out.println("Preview: " + sourceFile.getAbsolutePath() + " -> "
//						+ (finalDestinationFile != null ? finalDestinationFile.getAbsolutePath() : "N/A (no copy)"));
				logger.info("Preview: {} -> {}", sourceFile.getAbsolutePath(),
						(finalDestinationFile != null ? finalDestinationFile.getAbsolutePath() : "N/A (no copy)"));

			}

			else {
//				System.out.println("Unknown activity: " + activity);
				logger.warn("Unknown activity: {}", activity);
			}
			return;
		} else {
//			System.out.println("Skipping file due to filter criteria: " + sourceFile.getAbsolutePath());
			logger.info("Skipping file due to filter criteria: {}", sourceFile.getAbsolutePath());
		}

	}

	private OutputStream createChainedOutputStream(FileOutputStream baseOutputStream, String encryptionStatus,
			String compressionStatus, String encryptionKey)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IOException {

		OutputStream currentOutputStream = baseOutputStream;

		// Compression before encryption is generally more efficient
		if ("y".equalsIgnoreCase(compressionStatus)) {
//			System.out.println("Applying GZIP Compression.");
			logger.info("Applying GZIP Compression.");
			currentOutputStream = new GZIPOutputStream(currentOutputStream);
		}

		if ("y".equalsIgnoreCase(encryptionStatus)) {
			if (encryptionKey == null || encryptionKey.isEmpty()) {
				logger.error("Encryption key is null or empty while encryption is enabled.");
				throw new IllegalArgumentException(
						"Encryption key cannot be null or empty when encryption is enabled.");
			}
			byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
			// Using AES/ECB/PKCS5Padding is simpler for single-block encryption if key
			// management is external
			// For more robust security in real applications, consider AES/CBC/PKCS5Padding
			// with an IV
			SecretKeySpec secretKey = new SecretKeySpec(decodedKey, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
//			System.out.println("Applying AES Encryption.");
			logger.info("Applying AES Encryption.");
			currentOutputStream = new CipherOutputStream(currentOutputStream, cipher);
		}
		return currentOutputStream;
	}

	private String bytesToHexa(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private void saveMetaData(Map<String, Object> metadata) {
		if (runId != null && fileDetailsStore != null && fileCounter != null) {
			try {
				FileMetaData meta = new FileMetaData();

				meta.setFileName((String) metadata.get("fileName"));
				meta.setFilePath((String) metadata.get("FilePath"));
				meta.setSize((Long) metadata.get("Size"));
				meta.setFileSrcPath((String) metadata.get("FileSrcPath"));
				meta.setFileType((String) metadata.get("fileType"));
				meta.setAuthor((String) metadata.get("Author"));
				meta.setCreationDate((String) metadata.get("CreationDate"));
				meta.setModificationDate((String) metadata.get("ModifiedDate"));
				meta.setRunId(runId);
				meta.setCreatedAt(LocalDateTime.now().toString());
				if ("copy".equalsIgnoreCase(activity) || "copyandpurge".equalsIgnoreCase(activity)) {
					meta.setTargetPath((String) metadata.get("targetPath")); // Already processed to be parent path
					meta.setTargetFileName((String) metadata.get("targetfileName"));
					meta.setSourceChecksum((String) metadata.get("sourceChecksum"));
					meta.setTargetChecksum((String) metadata.get("targetChecksum"));
					meta.setValidationStatus((String) metadata.get("validationStatus"));
					meta.setIsVersionEnable((String) metadata.get("isVersionEnable"));
					meta.setFileEncryption((String) metadata.get("isEncryptionEnabled"));
					meta.setFileEncryptionKey((String) metadata.get("encryptionKey"));
					meta.setFileCompression((String) metadata.get("isCompressionEnabled"));
					meta.setIsArchived((String) metadata.get("isArchived"));

				} else {
					meta.setTargetPath(null);
					meta.setSourceChecksum(null);
					meta.setTargetChecksum(null);
					meta.setValidationStatus(null);
					meta.setIsVersionEnable(null);
					meta.setFileEncryption(null);
					meta.setFileEncryptionKey(null);
					meta.setFileCompression(null);
				}

				int count = fileCounter.incrementAndGet();
				String fileId = runId + "." + String.format("%03d", count);

				meta.setFileId(fileId);
				metadata.put("fileId", fileId);
				fileDetailsStore.save(meta);

			} catch (Exception e) {
//				System.err.println(
//						"Error saving metadata for file: " + metadata.get("fileName") + " - " + e.getMessage());
//				e.printStackTrace();
				logger.error("Error saving metadata for file: {} - {}", metadata.get("fileName"), e.getMessage());

			}
		} else {
//			System.out.println("Warning: Metadata will not be saved. runId, fileDetailsStore, or fileCounter is null.");
			logger.warn("Metadata will not be saved. runId, fileDetailsStore, or fileCounter is null.");
		}
	}

	private Map<String, Object> buildMetadata1(File source, File dest) {
		Map<String, Object> dMap = new HashMap<>();
		Path sourcePath = source.toPath();

		if (source.exists()) {
			try {
				BasicFileAttributes attrs = Files.readAttributes(sourcePath, BasicFileAttributes.class);
				FileOwnerAttributeView ownerAttr = Files.getFileAttributeView(sourcePath, FileOwnerAttributeView.class);
				UserPrincipal owner = (ownerAttr != null) ? ownerAttr.getOwner() : null;

				String fileName = source.getName();

				String extension = "";
				int dotIndex = fileName.lastIndexOf(".");
				if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
					extension = fileName.substring(dotIndex + 1);
				}

				dMap.put("fileName", source.getName());
				dMap.put("FilePath", source.getAbsolutePath());
				dMap.put("CreationDate", attrs.creationTime().toString());
				dMap.put("Size", source.length());
				dMap.put("FileSrcPath", source.getParent());
				dMap.put("ModifiedDate", attrs.lastModifiedTime().toString());
				dMap.put("fileType", extension);
				dMap.put("Author", owner != null ? owner.getName() : "UNKNOWN");

				if (dest != null) {
					dMap.put("targetfileName", dest.getName());
					// Set targetPath to the parent directory of the final destination file
					dMap.put("targetPath", dest.getParentFile() != null ? dest.getParentFile().getAbsolutePath()
							: dest.getAbsolutePath());
				}

			} catch (IOException e) {
//				System.err.println(
//						"Error retrieving metadata for file: " + source.getAbsolutePath() + " - " + e.getMessage());
//				e.printStackTrace();
				logger.error("Error retrieving metadata for file: {} - {}", source.getAbsolutePath(), e.getMessage());
			}
		} else {
//			System.out.println("Source file does not exist when building metadata: " + source.getAbsolutePath());
			logger.warn("Source file does not exist when building metadata: {}", source.getAbsolutePath());
		}
		return dMap;
	}

	public static void add(Map<String, Object> dto) {
		metadataList.add(dto);
	}

	public static List<Map<String, Object>> getAll() {
		return Collections.unmodifiableList(metadataList);
	}

	public static void clear() {
		metadataList.clear();
	}

	/**
	 * Constructs the destination path based on the includeSourcePath flag in
	 * FileFilters. This logic determines whether the original source directory
	 * structure is preserved under the target path.
	 *
	 * @return The calculated destination file.
	 */
	private File constructDestinationPath() {
		String finalDestinationFilePathString;
		String includeFlag = (filters != null && filters.getIncludeSourcePath() != null)
				? filters.getIncludeSourcePath().trim().toUpperCase()
				: null;

		Path sourceFilePath = sourceFile.toPath();
		Path sourceRootPathNormalized = sourceRootPath.toPath().normalize();

		// Calculate the path of the sourceFile relative to the sourceRootPath
		Path relativePathFromSourceRootToSourceFile = sourceRootPathNormalized.relativize(sourceFilePath);

		if ("Y".equals(includeFlag)) {
			// Desired for includeSourcePath = "y":
			// D:\destination\D\source\emplpoyee\Employee.java
			// This means: destinationBase + <sanitized sourceRootPath as a valid folder
			// name> + relativePathFromSourceRootToSourceFile.

			String sourceRootFolderName;
			// CORRECTED LINE: Convert File to Path first
			Path absoluteSourceRootPath = sourceRootPath.toPath().toAbsolutePath();

			if (absoluteSourceRootPath.getNameCount() == 0 && absoluteSourceRootPath.getRoot() != null) {
				// This is a root like C:\ (e.g., sourceRootPath is just the drive)
				sourceRootFolderName = absoluteSourceRootPath.getRoot().toString().replace(":", "").replace("\\", "")
						.replace("/", "");

			}

			// Extract relevant parts and sanitize to create a valid folder name segment.
			// Replace characters that are invalid in Windows folder names.
			sourceRootFolderName = absoluteSourceRootPath.toString();
			sourceRootFolderName = sourceRootFolderName.replace(":\\", "/");
			sourceRootFolderName = sourceRootFolderName.replaceAll("^_|_$", "");

			finalDestinationFilePathString = Paths.get(destinationBase.getAbsolutePath(), sourceRootFolderName,
					relativePathFromSourceRootToSourceFile.toString()).normalize().toString();
			logger.debug("Constructed path segment for source root (includeSourcePath=Y): {}", sourceRootFolderName);

//			System.out.println(
//					"DEBUG (includeSourcePath=Y) - Constructed path segment for source root: " + sourceRootFolderName);

		} else {
			// Defaulting to "N" behavior if includeSourcePath is null or invalid
//			System.out
//					.println("Warning: Invalid or missing includeSourcePath value. Defaulting to Include=N behavior.");
			logger.warn("Invalid or missing includeSourcePath value. Defaulting to Include=N behavior.");
			finalDestinationFilePathString = Paths
					.get(destinationBase.getAbsolutePath(), relativePathFromSourceRootToSourceFile.toString())
					.normalize().toString();
		}

		// Additional normalization to handle potential mixed separators or redundant
		// separators
		// Use replaceAll to handle both / and \ consistently for internal consistency
		// before File creation
		finalDestinationFilePathString = finalDestinationFilePathString.replace("\\", "/").replaceAll("/+", "/");
		// Convert back to native system separator for File constructor on Windows
		finalDestinationFilePathString = finalDestinationFilePathString.replace("/", File.separator);

		return new File(finalDestinationFilePathString);
	}

}
