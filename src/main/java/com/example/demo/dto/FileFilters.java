package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.demo.dto.update.FileNameFilterCriteria;
import com.example.demo.dto.update.FolderPathFilterCriteria;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileFilters {
	
	private String runCheckDuplicate;
	private String includeSourcePath;
	
	
    private String dateFilterBaseAttribute; // "creation" or "modification"

    // New field to specify segmentation type
    private String fileAgeSegmentationType; // "FILE_AGE" or "DATE_RANGE"

    // Used if fileAgeSegmentationType is "DATE_RANGE"
    private LocalDateTime absoluteDateRangeFrom;
    private LocalDateTime absoluteDateRangeTo;

    // Used if fileAgeSegmentationType is "FILE_AGE"
    private Long fileAgeDaysFrom;
    private Long fileAgeDaysTo;
    private Long fileAgeMonthsFrom;
    private Long fileAgeMonthsTo;
    private Long fileAgeYearsFrom;
    private Long fileAgeYearsTo;

    // --- File Type (Extension) Filters ---
    private List<String> fileTypes; // List of extensions, e.g., ["txt", "pdf"]
    private String fileTypeIncExc; // "Include" or "Exclude"

    // --- Filename Filters (using DTO) ---
    private List<FileNameFilterCriteria> fileNameFilterCriteria;

    // --- Folder Path Filters (using DTO) ---
    private List<FolderPathFilterCriteria> folderPathFilterCriteria;

    private Long sizeFromKB;
    private Long sizeToKB;

    private String fileEncryption; // .enc ("y" / "n")
    private String versionEnable; // ("y" / "n")

    private String fileValidation; // e.g., "SHA-256", "MD5"
    private String fileCompression; // .gz ("y" / "n")

    private List<String> includeFileOwner;
    private List<String> excludeFileOwner;
	
    private String kbId;
   
}