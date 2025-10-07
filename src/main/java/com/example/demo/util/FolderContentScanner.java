package com.example.demo.util;

import com.example.demo.dto.FileDetail;
import com.example.demo.dto.FolderDetail;
import com.example.demo.dto.FolderContentResponse;
import com.example.demo.dto.FolderDetailsRequest;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class FolderContentScanner {

	public FolderContentResponse scanFolderContents(FolderDetailsRequest request, int pageNumber, int maxResultPerPage)
			throws IOException, IllegalArgumentException {
		String folderPathString = request.getFolderPath();
		if (folderPathString == null || folderPathString.trim().isEmpty()) {
			throw new IllegalArgumentException("Folder path cannot be null or empty.");
		}

		File folder = new File(folderPathString);

		List<FileDetail> allFileDetails = new ArrayList<>();
		List<FolderDetail> allFolderDetails = new ArrayList<>();

		File[] filesAndFolders = folder.listFiles();

		if (filesAndFolders != null) {
			for (File item : filesAndFolders) {
				if (item.isFile()) {
					FileDetail fileDetail = new FileDetail();
					fileDetail.setId(item.getAbsolutePath());
					fileDetail.setTargetFileName(item.getName());
					fileDetail.setSize(item.length());
					fileDetail.setLastModifiedDate(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
							.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.lastModified())));
					allFileDetails.add(fileDetail);
				} else if (item.isDirectory()) {
					FolderDetail folderDetail = new FolderDetail();
					folderDetail.setFolderName(item.getName());
					folderDetail.setFolderPath(item.getAbsolutePath() + File.separator);

					long subDirectoryCount = 0;
					File[] subItems = item.listFiles();
					if (subItems != null) {
						for (File subItem : subItems) {
							if (subItem.isDirectory()) {
								subDirectoryCount++;
							}
						}
					}
					folderDetail.setFolderSize(subDirectoryCount);

					allFolderDetails.add(folderDetail);
				}
			}
		}

		int totalRecords = allFileDetails.size();
		int calculatedNoOfPages;
		List<FileDetail> paginatedFileDetails = new ArrayList<>();

		calculatedNoOfPages = (int) Math.ceil((double) totalRecords / maxResultPerPage);
		if (calculatedNoOfPages == 0 && totalRecords > 0) {
			calculatedNoOfPages = 1;
		} else if (totalRecords == 0) {
			calculatedNoOfPages = 0;
			pageNumber = 0;
		}

		if (pageNumber < 1 && totalRecords > 0) {
			pageNumber = 1;
		} else if (pageNumber > calculatedNoOfPages && calculatedNoOfPages > 0) {
			pageNumber = calculatedNoOfPages;
		} else if (totalRecords == 0) {
			pageNumber = 0;
		}

		int startIndex = (pageNumber > 0 ? pageNumber - 1 : 0) * maxResultPerPage;
		int endIndex = Math.min(startIndex + maxResultPerPage, totalRecords); // Ensure endIndex does not exceed
																				// totalRecords

		if (startIndex < endIndex) {
			paginatedFileDetails = allFileDetails.subList(startIndex, endIndex);
		}

		FolderContentResponse response = new FolderContentResponse();
		response.setStatus("SUCCESS");
		response.setTotalNoRecords(totalRecords);
		response.setFileDetails(paginatedFileDetails);
		response.setFolderDetails(allFolderDetails);
		response.setPageNumber(pageNumber);
		response.setNoOfPages(calculatedNoOfPages);
		response.setMaxResultPerPage(maxResultPerPage);
		response.setFolderCount(allFolderDetails.size());

		return response;
	}
}
