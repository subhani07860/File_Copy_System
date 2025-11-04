package com.example.demo.repository;

import java.math.BigDecimal;
import java.util.List;
//import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.FileMetaData;

@Repository
public interface FileDetailsStore extends JpaRepository<FileMetaData, String> {

	List<FileMetaData> findAllByTargetPathAndTargetFileName(String targetPath, String targetFileName);

	// Optional<FileMetaData> findByTargetPathAndTargetFileName(String targetPath,
	// String targetFileName);
	Optional<FileMetaData> findFirstByTargetPathAndTargetFileNameOrderByCreatedAtDesc(String targetPath,
			String targetFileName);

	List<FileMetaData> findAllByFileIdAndRunId(String fileId, BigDecimal runId);

}
