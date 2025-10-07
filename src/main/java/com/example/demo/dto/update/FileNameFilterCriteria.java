package com.example.demo.dto.update;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileNameFilterCriteria {
    private String paramName; 
    private String paramValue; 
    private String criteria1; 
    private String criteria2;
}
