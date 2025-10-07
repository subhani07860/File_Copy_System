
package com.example.demo.dto;

import lombok.Data;

@Data
// This DTO maps to the "FILE_DETAILS" array elements
public class FileDetail {
    private String id; 
    private String targetFileName; 
    private long size;
    private String lastModifiedDate; 
   // private String accessTier;

    // Constructors, getters, and setters
    public FileDetail() {}

//	public FileDetail(String id, String targetFileName, long size, String lastModifiedDate, String accessTier) {
    public FileDetail(String id, String targetFileName, long size, String lastModifiedDate) {
		super();
		this.id = id;
		this.targetFileName = targetFileName;
		this.size = size;
		this.lastModifiedDate = lastModifiedDate;
		//this.accessTier = accessTier;
	}


}