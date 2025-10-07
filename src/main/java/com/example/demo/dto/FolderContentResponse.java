package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.util.List;

/**
 * DTO for the response of folder content details. This class is used to
 * structure the response data for folder contents, including file and folder
 * details, pagination information, and status.
 */
@Data
public class FolderContentResponse {
	private int totalNoRecords;
	private String status;
	@JsonProperty("FILE_DETAILS")
	private List<FileDetail> fileDetails;
	private int pageNumber;
	private int noOfPages;
	@JsonProperty("FOLDER_DETAILS")
	private List<FolderDetail> folderDetails;
	private int maxResultPerPage;
	@JsonProperty("FOLDER_COUNT")
	private int folderCount;

	// Constructors, getters, and setters
	public FolderContentResponse() {
	}

}