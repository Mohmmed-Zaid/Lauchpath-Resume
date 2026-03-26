package com.launchpath.resume_service.services;

import com.launchpath.resume_service.entity.ParsedResume;
import com.launchpath.resume_service.exception.FileParseException;
import com.launchpath.resume_service.repo.ParsedResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserService {

    private final ParsedResumeRepository parsedResumeRepository;
    private final AiService aiService;

    private final Tika tika = new Tika();

    // ══════════════════════════════════════════════════════════
    // PARSE UPLOADED FILE
    // ══════════════════════════════════════════════════════════

    /**
     * Full parse pipeline:
     * 1. Extract raw text via Apache Tika
     * 2. Send raw text to AI for structured section extraction
     * 3. Store result in parsed_resumes collection (TTL 24h)
     * 4. Return structured sections for user confirmation
     */
    public ParsedResume parseUploadedFile(MultipartFile file,
                                          String cloudinaryFileId,
                                          Long userId) {
        log.info("Parsing uploaded file - userId: {}, file: {}",
                userId, file.getOriginalFilename());

        // Delete any existing parsed resume for this user
        parsedResumeRepository.deleteByUserId(String.valueOf(userId));

        // 1. Extract raw text using Tika
        String rawText = extractText(file);
        log.debug("Extracted {} chars from file", rawText.length());

        // 2. AI parses raw text into structured sections
        Map<String, Object> parsedSections = new HashMap<>();
        boolean isParsed = false;
        String parseError = null;

        try {
            parsedSections = aiService.parseResumeText(rawText, userId);
            isParsed = true;
            log.info("AI parsing successful for userId: {}", userId);
        } catch (Exception e) {
            log.error("AI parsing failed for userId: {}", userId, e);
            parseError = e.getMessage();
            // Still save with raw text — user can edit manually
        }

        // 3. Save parsed result with 24h TTL
        ParsedResume parsed = ParsedResume.builder()
                .userId(String.valueOf(userId))
                .cloudinaryFileId(cloudinaryFileId)
                .originalFileName(file.getOriginalFilename())
                .rawText(rawText)
                .parsedSections(parsedSections)
                .isParsed(isParsed)
                .parseError(parseError)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        ParsedResume saved = parsedResumeRepository.save(parsed);
        log.info("ParsedResume saved - id: {}", saved.getId());
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // GET PARSED RESULT
    // ══════════════════════════════════════════════════════════

    public ParsedResume getParsedResume(Long userId) {
        return parsedResumeRepository
                .findByUserId(String.valueOf(userId))
                .orElseThrow(() -> new FileParseException(
                        "No parsed resume found. Please upload a file first."
                ));
    }

    // ══════════════════════════════════════════════════════════
    // CLEANUP
    // ══════════════════════════════════════════════════════════

    // Called after user confirms parsed sections → resume created
    public void deleteParsedResume(Long userId) {
        log.info("Deleting parsed resume for userId: {}", userId);
        parsedResumeRepository.deleteByUserId(String.valueOf(userId));
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private String extractText(MultipartFile file) {
        try {
            String text = tika.parseToString(file.getInputStream());
            if (text == null || text.isBlank()) {
                throw new FileParseException(
                        "Could not extract text from file. " +
                                "Ensure the file is not scanned/image-only."
                );
            }
            return text.trim();
        } catch (IOException | TikaException e) {
            log.error("Tika extraction failed: {}", e.getMessage());
            throw new FileParseException(
                    "Failed to read file content: " + e.getMessage()
            );
        }
    }
}
