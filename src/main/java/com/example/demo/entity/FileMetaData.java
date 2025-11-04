package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FileMetaData {

	@Id
	private String fileId;
	@CreatedDate // This annotation instructs Spring to populate this field
    @Column(name = "created_at", updatable = false) // updatable = false prevents updates after creation
    private String createdAt;
	private BigDecimal runId;
	private String fileName;
	private String filePath;
	private String creationDate;
	private Long size;
	private String fileSrcPath;
	private String targetPath;
	private String modificationDate;
	private String fileType;
	private String author;
	private String sourceChecksum;
	private String targetChecksum;
	private String validationStatus;
	private String isVersionEnable;
	private String fileEncryption;
	private String fileEncryptionKey;
	private String fileCompression;
	private String targetFileName;
	private String isArchived;

}
