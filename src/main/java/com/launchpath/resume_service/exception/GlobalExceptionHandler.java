package com.launchpath.resume_service.exception;

import com.launchpath.resume_service.dto.response.ApiResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation errors — @Valid failed
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });

        log.warn("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error("Validation failed: " + errors));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleRateLimit(
            RateLimitExceededException ex) {
        log.warn("Rate limit: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Resume / Template / Version not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleNotFound(
            ResourceNotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Accessing another user's resume
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleUnauthorized(
            UnauthorizedAccessException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Resume count exceeded plan limit
    @ExceptionHandler(ResumeLimitExceededException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleResumeLimit(
            ResumeLimitExceededException ex) {
        log.warn("Resume limit: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // File too large, wrong type, empty
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleFileUpload(
            FileUploadException ex) {
        log.warn("File upload error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Spring multipart size limit exceeded
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleMaxSize(
            MaxUploadSizeExceededException ex) {
        log.warn("File too large: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(
                        "File too large. Maximum allowed size is 10MB"
                ));
    }

    // Tika extraction failed
    @ExceptionHandler(FileParseException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleFileParse(
            FileParseException ex) {
        log.warn("File parse error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // DeepSeek / Gemini failed
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleAiService(
            AiServiceException ex) {
        log.error("AI service error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // PDF / DOCX generation failed
    @ExceptionHandler(ExportException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleExport(
            ExportException ex) {
        log.error("Export error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Business rule violations
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleIllegalState(
            IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Bad input
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    // Missing query param
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.warn("Missing param: {}", ex.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(
                        "Required parameter missing: " + ex.getParameterName()
                ));
    }

    // Wrong type in path variable
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {}", ex.getName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDTO.error(
                        "Invalid value for: " + ex.getName()
                ));
    }

    // Feign client failure — user-service down
    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleFeignException(
            feign.FeignException ex) {
        log.error("Feign error - status: {}, message: {}",
                ex.status(), ex.getMessage());

        if (ex.status() == 402) {
            return ResponseEntity
                    .status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ApiResponseDTO.error(
                            "Credit limit reached. Please upgrade your plan."
                    ));
        }
        if (ex.status() == 404) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDTO.error("User not found"));
        }
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponseDTO.error(
                        "User service unavailable. Please try again."
                ));
    }

    // Catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error(
                        "Something went wrong. Please try again later."
                ));
    }
}
