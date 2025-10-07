package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CopyRequest {
	private String sourcepath;
	private String destinationpath;
	private String activity;

	private FileFilters filters;

}