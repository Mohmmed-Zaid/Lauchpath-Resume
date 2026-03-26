package com.launchpath.resume_service.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.launchpath.resume_service.exception.FileUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword"
    );

    // ══════════════════════════════════════════════════════════
    // UPLOAD
    // ══════════════════════════════════════════════════════════

    /**
     * Uploads resume file to Cloudinary.
     * Returns map with: publicId, url, format, bytes
     * Only called after validation passes.
     */
    public Map<String, Object> uploadResumeFile(MultipartFile file,
                                                Long userId) {
        log.info("Uploading file - userId: {}, filename: {}, size: {}",
                userId, file.getOriginalFilename(), file.getSize());

        validateFile(file);

        try {
            String publicId = "resumes/" + userId + "/" + UUID.randomUUID();

            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "raw", // raw = non-image files
                            "folder", "launchpath/resumes",
                            "use_filename", true,
                            "unique_filename", true,
                            "tags", List.of("resume", "user_" + userId)
                    )
            );

            log.info("File uploaded to Cloudinary - publicId: {}",
                    result.get("public_id"));
            return result;

        } catch (IOException e) {
            log.error("Cloudinary upload failed - userId: {}", userId, e);
            throw new FileUploadException(
                    "Failed to upload file. Please try again."
            );
        }
    }

    /**
     * Uploads generated PDF/DOCX export to Cloudinary.
     * Called after export generation succeeds.
     */
    public Map<String, Object> uploadGeneratedFile(byte[] fileBytes,
                                                   String resumeId,
                                                   String format,
                                                   Long userId) {
        log.info("Uploading generated {} - resumeId: {}", format, resumeId);

        try {
            String publicId = "exports/" + userId + "/" + resumeId
                    + "_" + format + "_" + System.currentTimeMillis();

            Map<String, Object> result = cloudinary.uploader().upload(
                    fileBytes,
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "raw",
                            "folder", "launchpath/exports",
                            "tags", List.of("export", format, "user_" + userId)
                    )
            );

            log.info("Generated file uploaded - publicId: {}",
                    result.get("public_id"));
            return result;

        } catch (IOException e) {
            log.error("Export upload failed - resumeId: {}", resumeId, e);
            throw new FileUploadException(
                    "Failed to upload generated file."
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════

    public void deleteFile(String cloudinaryPublicId) {
        log.info("Deleting file from Cloudinary: {}", cloudinaryPublicId);
        try {
            cloudinary.uploader().destroy(
                    cloudinaryPublicId,
                    ObjectUtils.asMap("resource_type", "raw")
            );
            log.info("File deleted: {}", cloudinaryPublicId);
        } catch (IOException e) {
            log.error("Cloudinary delete failed: {}", cloudinaryPublicId, e);
            // Don't throw — log and continue
            // Orphaned files cleaned up by scheduled job (Phase 3)
        }
    }

    // ══════════════════════════════════════════════════════════
    // VALIDATION
    // ══════════════════════════════════════════════════════════

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileUploadException(
                    "File too large. Maximum size is 10MB"
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new FileUploadException(
                    "Invalid file type. Only PDF and DOCX files are allowed"
            );
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new FileUploadException("Invalid filename");
        }
    }
}
