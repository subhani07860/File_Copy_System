
package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// This DTO maps to the "FILE_DETAILS" array elements
public class FileDetail {
	private String id;
	private String targetFileName;
	private long size;
	private String lastModifiedDate;
	// private String accessTier;

}