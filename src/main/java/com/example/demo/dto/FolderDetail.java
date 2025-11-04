package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
//This DTO maps to the "FOLDER_DETAILS" array elements
public class FolderDetail {
	private String folderName;
	private String folderPath;
	private long folderSize;

//	// Constructors,constructor with fields
//	public FolderDetail() {
//	}
//
//	public FolderDetail(String folderName, String folderPath, long folderSize) {
//		super();
//		this.folderName = folderName;
//		this.folderPath = folderPath;
//		this.folderSize = folderSize;
//	}

}