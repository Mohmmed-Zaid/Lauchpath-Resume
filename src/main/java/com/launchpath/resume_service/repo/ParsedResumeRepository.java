package com.launchpath.resume_service.repo;


import com.launchpath.resume_service.entity.ParsedResume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParsedResumeRepository extends MongoRepository<ParsedResume, String> {

    // Find by user — check if user has a pending parsed resume
    Optional<ParsedResume> findByUserId(String userId);

    // Find by cloudinary file ID
    Optional<ParsedResume> findByCloudinaryFileId(String cloudinaryFileId);

    // Find expired records — cleanup job
    List<ParsedResume> findByExpiresAtBefore(LocalDateTime now);

    // Check if user already has a parsed resume waiting
    boolean existsByUserId(String userId);

    // Delete by userId — when user confirms/rejects the parsed data
    void deleteByUserId(String userId);
}