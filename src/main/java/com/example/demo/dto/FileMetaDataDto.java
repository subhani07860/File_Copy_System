package com.example.demo.dto;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FileMetaDataDto {
	
	   
	private String FilePath;
	private String CreationDate;
	private Long Size;
	private String FileName;
	private String FileSrcPath;
	private String targetPath;
	private String ModifiedDate;
	private String file_type;
	private String Author;
	private String targetFileName;
	private String sourceChecksum; 
    private String targetChecksum; 
    private String validationStatus;
	
}
