package com.launchpath.resume_service.feign.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResponseDTO<T> {
    private boolean success;
    private String message;
    private T data;
}
