package com.launchpath.resume_service.repo;

import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.enums.ResumeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {

    // ── Basic Finders ─────────────────────────────────────────

    // All resumes for a user — dashboard list
    // Excludes ARCHIVED by default
    List<Resume> findByUserIdAndStatusNot(String userId, ResumeStatus status);

    // All resumes including archived — admin view
    List<Resume> findByUserId(String userId);

    // Paginated — for users with many resumes
    Page<Resume> findByUserIdAndStatusNot(
            String userId, ResumeStatus status, Pageable pageable
    );

    // Single resume by id + userId
    // Always include userId in query — prevents user A reading user B's resume
    Optional<Resume> findByIdAndUserId(String id, String userId);

    // Count active resumes — used for plan limit check
    // canCreateMoreResumes() calls this before creating new resume
    long countByUserIdAndStatusNot(String userId, ResumeStatus status);

    // Find by template — used when template is deleted
    // need to know which resumes used it
    List<Resume> findByTemplateId(String templateId);

    // Check title uniqueness per user
    boolean existsByUserIdAndTitle(String userId, String title);

    // ── Status Queries ────────────────────────────────────────

    List<Resume> findByUserIdAndStatus(String userId, ResumeStatus status);

    // ── MongoDB @Query ────────────────────────────────────────
    // Use when derived method name becomes too long
    // ? 0, ?1 = positional parameters

    // Find resumes that have at least one ATS result
    // $exists checks if array field exists and is not empty
    @Query("{ 'userId': ?0, 'atsResults': { $exists: true, $not: { $size: 0 } } }")
    List<Resume> findResumesWithAtsResults(String userId);

    @Query("{ 'userId': ?0, 'targetJobTitle': { $regex: ?1, $options: 'i' } }")
    List<Resume> findByUserIdAndTargetJobTitleContaining(
            String userId, String jobTitle
    );

    // Find resumes currently locked (being edited via WebSocket)
    @Query("{ 'isLocked': true, 'lockedBy': ?0 }")
    List<Resume> findLockedResumesByUser(String userId);

    // Get only specific fields — projection
    // Avoids loading entire sections/versions array for list views
    // Returns lightweight resume objects for dashboard
    @Query(value = "{ 'userId': ?0, 'status': { $ne: 'ARCHIVED' } }",
            fields = "{ 'id': 1, 'title': 1, 'status': 1, 'templateId': 1, " +
                    "'targetJobTitle': 1, 'currentVersion': 1, " +
                    "'createdAt': 1, 'updatedAt': 1 }")
    List<Resume> findResumesSummaryByUserId(String userId);

    // Latest ATS score for a resume
    // Gets resume with only atsResults field populated
    @Query(value = "{ '_id': ?0 }",
            fields = "{ 'atsResults': 1 }")
    Optional<Resume> findAtsResultsById(String resumeId);
}
