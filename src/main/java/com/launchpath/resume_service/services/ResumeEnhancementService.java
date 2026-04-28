package com.launchpath.resume_service.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchpath.resume_service.dto.request.*;
import com.launchpath.resume_service.dto.response.CareerChatResponseDTO;
import com.launchpath.resume_service.dto.response.CareerGuidanceResponseDTO;
import com.launchpath.resume_service.dto.response.OptimizeResumeResponseDTO;
import com.launchpath.resume_service.dto.response.RewriteSectionResponseDTO;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.entity.ResumeSection;
import com.launchpath.resume_service.enums.ResumeStatus;
import com.launchpath.resume_service.enums.SectionType;
import com.launchpath.resume_service.exception.AiServiceException;
import com.launchpath.resume_service.exception.ResumeLimitExceededException;
import com.launchpath.resume_service.feign.UserServiceClient;
import com.launchpath.resume_service.repo.ResumeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeEnhancementService {

    private final ResumeService resumeService;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final ResumeRepository resumeRepository;

    @Value("${ai.groq.api-key}")
    private String groqApiKey;

    @Value("${ai.groq.base-url}")
    private String groqBaseUrl;

    @Value("${ai.groq.free.model}")
    private String groqFreeModel;

    @Value("${ai.groq.pro.model}")
    private String groqProModel;

    // ══════════════════════════════════════════════════════
    // OPTIMIZE RESUME
    // ══════════════════════════════════════════════════════

    public OptimizeResumeResponseDTO optimizeResume(
            String resumeId,
            Long userId,
            OptimizeResumeRequestDTO request) {

        log.info("Optimizing resume - id: {}, userId: {}", resumeId, userId);

        Resume resume = resumeService.getResumeById(resumeId, userId);

        userServiceClient.consumeAtsCredit(userId);

        try {
            String resumeText = extractResumeText(resume);
            String targetRole = request.getTargetJobTitle() != null
                    ? request.getTargetJobTitle()
                    : resume.getTargetJobTitle();

            String prompt = buildOptimizePrompt(
                    resumeText, targetRole, request.getJobDescription()
            );

            String rawResponse = callGroq(userId, prompt);
            return parseOptimizeResponse(rawResponse, resume, targetRole);

        } catch (Exception e) {
            try {
                userServiceClient.refundAtsCredit(userId);
            } catch (Exception refundEx) {
                log.error("Refund failed for userId: {}", userId);
            }
            log.error("Resume optimization failed: {}", e.getMessage());
            throw new AiServiceException(
                    "Resume optimization failed. Credit refunded."
            );
        }
    }

    // ══════════════════════════════════════════════════════
    // REWRITE SECTIONS FOR JOB
    // ══════════════════════════════════════════════════════

    public RewriteSectionResponseDTO rewriteForJob(
            String resumeId,
            Long userId,
            RewriteSectionRequestDTO request) {

        log.info("Rewriting resume for job - id: {}, userId: {}",
                resumeId, userId);

        Resume resume = resumeService.getResumeById(resumeId, userId);

        userServiceClient.consumeAtsCredit(userId);

        try {
            String resumeText = extractResumeText(resume);

            String prompt = buildRewritePrompt(
                    resumeText,
                    request.getJobDescription(),
                    request.getSectionId()
            );

            String rawResponse = callGroq(userId, prompt);
            return parseRewriteResponse(
                    rawResponse, resume, request.getJobDescription()
            );

        } catch (Exception e) {
            try {
                userServiceClient.refundAtsCredit(userId);
            } catch (Exception refundEx) {
                log.error("Refund failed for userId: {}", userId);
            }
            log.error("Section rewrite failed: {}", e.getMessage());
            throw new AiServiceException(
                    "Section rewrite failed. Credit refunded."
            );
        }
    }

    // ══════════════════════════════════════════════════════
    // CAREER GUIDANCE
    // ══════════════════════════════════════════════════════

    public CareerGuidanceResponseDTO getCareerGuidance(
            String resumeId,
            Long userId,
            CareerGuidanceRequestDTO request) {

        log.info("Career guidance - resumeId: {}, userId: {}",
                resumeId, userId);

        Resume resume = resumeService.getResumeById(resumeId, userId);

        String targetRole = request.getTargetRole() != null
                ? request.getTargetRole()
                : resume.getTargetJobTitle() != null
                ? resume.getTargetJobTitle()
                : "Software Engineer";

        String location = request.getLocation() != null
                ? request.getLocation()
                : "India";

        String resumeText = extractResumeText(resume);

        String prompt = buildCareerGuidancePrompt(
                resumeText, targetRole, location, request.getCurrentSalary()
        );

        try {
            String rawResponse = callGroq(userId, prompt);
            return parseCareerGuidanceResponse(
                    rawResponse, resumeId, targetRole
            );
        } catch (Exception e) {
            log.error("Career guidance failed: {}", e.getMessage());
            throw new AiServiceException(
                    "Career guidance temporarily unavailable. Please try again."
            );
        }
    }

    // ══════════════════════════════════════════════════════
    // AI RESUME GENERATOR
    // ══════════════════════════════════════════════════════

    public Resume generateResumeFromText(
            Long userId,
            GenerateResumeRequestDTO request) {

        log.info("Generating resume from text - userId: {}", userId);

        long currentCount = resumeRepository
                .countByUserIdAndStatusNot(
                        String.valueOf(userId),
                        ResumeStatus.ARCHIVED
                );

        Boolean canCreate = userServiceClient
                .canCreateMoreResumes(userId, currentCount)
                .getData();

        if (Boolean.FALSE.equals(canCreate)) {
            throw new ResumeLimitExceededException(
                    "Resume limit reached. Please upgrade your plan."
            );
        }

        String prompt = buildGeneratePrompt(request);
        String rawResponse = callGroq(userId, prompt);

        Map<String, Object> generatedSections =
                parseGeneratedResume(rawResponse);

        // ✅ Fix 1 — correct type, no semicolon
        List<ResumeSection> sections = buildSectionsFromGenerated(
                generatedSections, request
        );

        return resumeService.createResumeFromParsed(
                userId,
                request.getTargetRole() + " Resume",
                request.getTemplateId(),
                sections
        );
    }

    // ══════════════════════════════════════════════════════
    // CAREER GUIDANCE CHAT
    // ══════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════
// CAREER GUIDANCE CHAT
// ══════════════════════════════════════════════════════

    // ✅ FIXED — CareerChatRequestDTO not CareerGuidanceRequestDTO
    public CareerChatResponseDTO careerChat(
            String resumeId,
            Long userId,
            CareerChatRequestDTO request) {  // ← CHANGED THIS LINE

        log.info("Career chat - resumeId: {}, userId: {}",
                resumeId, userId);

        Resume resume = resumeService.getResumeById(resumeId, userId);
        String resumeText = extractResumeText(resume);

        List<Map<String, Object>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", """
                You are a career coach with deep knowledge of the
                tech industry in India. You have access to the user's
                resume and provide specific, actionable advice.
                
                USER'S RESUME:
                %s
                
                TARGET ROLE: %s
                
                Rules:
                - Give specific salary figures for Indian market
                - Name real companies hiring in India
                - Give specific skill recommendations with timelines
                - Be encouraging but realistic
                - Keep responses concise and actionable
                """.formatted(
                        resumeText,
                        resume.getTargetJobTitle() != null
                                ? resume.getTargetJobTitle()
                                : "Software Engineer"
                )
        ));

        // ✅ Now works — CareerChatRequestDTO has getConversationHistory()
        if (request.getConversationHistory() != null) {
            request.getConversationHistory().forEach(msg ->
                    messages.add(Map.of(
                            "role", msg.get("role"),
                            "content", msg.get("content")
                    ))
            );
        }

        // ✅ Now works — CareerChatRequestDTO has getMessage()
        messages.add(Map.of(
                "role", "user",
                "content", request.getMessage()
        ));

        String aiReply = callGroqChat(userId, messages);

        List<Map<String, String>> updatedHistory = new ArrayList<>();

        if (request.getConversationHistory() != null) {
            updatedHistory.addAll(request.getConversationHistory());
        }
        updatedHistory.add(Map.of(
                "role", "user",
                "content", request.getMessage()
        ));
        updatedHistory.add(Map.of(
                "role", "assistant",
                "content", aiReply
        ));

        return CareerChatResponseDTO.builder()
                .reply(aiReply)
                .resumeId(resumeId)
                .updatedHistory(updatedHistory)
                .build();
    }

    // ══════════════════════════════════════════════════════
    // GROQ API CALLS
    // ══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String callGroq(Long userId, String prompt) {
        String model = groqFreeModel;
        try {
            Integer credits = userServiceClient
                    .getRemainingAtsCredits(userId)
                    .getData();
            if (credits != null && credits > 3) {
                model = groqProModel;
            }
        } catch (Exception e) {
            log.warn("Could not determine plan, using free model");
        }

        log.debug("Calling Groq model: {}", model);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are an expert resume writer " +
                                        "and career coach. Always respond " +
                                        "with valid JSON only."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 4000,
                "response_format", Map.of("type", "json_object")
        );

        Map response = webClientBuilder
                .baseUrl(groqBaseUrl)
                .build()
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + groqApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message =
                (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    @SuppressWarnings("unchecked")
    private String callGroqChat(Long userId,
                                List<Map<String, Object>> messages) {
        String model = groqFreeModel;
        try {
            Integer credits = userServiceClient
                    .getRemainingAtsCredits(userId)
                    .getData();
            if (credits != null && credits > 3) {
                model = groqProModel;
            }
        } catch (Exception e) {
            log.warn("Could not determine plan, using free model");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        Map response = webClientBuilder
                .baseUrl(groqBaseUrl)
                .build()
                .post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + groqApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message =
                (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    // ══════════════════════════════════════════════════════
    // PROMPT BUILDERS
    // ══════════════════════════════════════════════════════

    private String buildOptimizePrompt(String resumeText,
                                       String targetRole,
                                       String jobDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Optimize this resume and return JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"improvedSections\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"sectionTitle\": \"Experience\",\n");
        prompt.append("      \"improvedContent\": {},\n");
        prompt.append("      \"changes\": [\"what was changed and why\"]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"summary\": \"overall improvement summary\",\n");
        prompt.append("  \"bulletsImproved\": 5\n");
        prompt.append("}\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Use strong action verbs\n");
        prompt.append("- Add metrics where possible (%, numbers)\n");
        prompt.append("- Make bullets specific and measurable\n");
        prompt.append("- Remove weak phrases like 'worked on', 'helped'\n\n");
        prompt.append("TARGET ROLE: ").append(targetRole).append("\n\n");
        prompt.append("RESUME:\n").append(resumeText);

        if (jobDescription != null && !jobDescription.isBlank()) {
            prompt.append("\n\nJOB DESCRIPTION:\n").append(jobDescription);
        }

        return prompt.toString();
    }

    private String buildRewritePrompt(String resumeText,
                                      String jobDescription,
                                      String sectionId) {
        return """
                Rewrite this resume to match the job description. Return JSON:
                {
                  "rewrittenSections": [
                    {
                      "sectionTitle": "Experience",
                      "rewrittenContent": {},
                      "keywordsAdded": ["keyword1", "keyword2"]
                    }
                  ],
                  "keywordsAdded": ["all", "added", "keywords"],
                  "matchScoreBefore": 40,
                  "matchScoreAfter": 75
                }
                
                Rules:
                - Add relevant keywords from JD naturally
                - Rewrite bullets to highlight relevant experience
                - Keep truthful — don't invent experience
                - Focus on transferable skills
                
                %s
                
                JOB DESCRIPTION:
                %s
                
                RESUME:
                %s
                """.formatted(
                sectionId != null
                        ? "FOCUS ON SECTION: " + sectionId
                        : "REWRITE ALL RELEVANT SECTIONS",
                jobDescription,
                resumeText
        );
    }

    private String buildCareerGuidancePrompt(String resumeText,
                                             String targetRole,
                                             String location,
                                             String currentSalary) {
        return """
                Analyze this resume and provide career guidance. Return JSON:
                {
                  "currentLevel": "FRESHER/JUNIOR/MID/SENIOR",
                  "skillGaps": [
                    {
                      "skill": "Kubernetes",
                      "importance": "HIGH",
                      "salaryImpact": "30-40%% salary hike",
                      "resources": ["https://kubernetes.io/docs"],
                      "timeToLearn": "4-6 weeks"
                    }
                  ],
                  "nextCareerMoves": ["Move 1", "Move 2"],
                  "companySuggestions": [
                    {
                      "name": "Company Name",
                      "type": "product/service/startup",
                      "location": "%s",
                      "salaryRange": "10-15 LPA",
                      "whyGoodFit": "reason",
                      "applyUrl": "https://careers.company.com"
                    }
                  ],
                  "interviewQuestions": ["Question 1", "Question 2"],
                  "currentSalaryRange": "6-8 LPA",
                  "potentialSalaryRange": "12-18 LPA after skill gaps filled",
                  "careerAdvice": "specific actionable advice paragraph"
                }
                
                TARGET ROLE: %s
                LOCATION: %s
                CURRENT SALARY: %s
                
                RESUME:
                %s
                
                Be specific about salary hikes for Indian market.
                """.formatted(
                location,
                targetRole,
                location,
                currentSalary != null ? currentSalary : "not specified",
                resumeText
        );
    }

    private String buildGeneratePrompt(GenerateResumeRequestDTO req) {
        return """
                Generate a professional resume and return JSON:
                {
                  "summary": "professional summary paragraph",
                  "experience": [
                    {
                      "company": "Company Name",
                      "role": "Job Title",
                      "startDate": "Jan 2022",
                      "endDate": "Present",
                      "bullets": ["Strong bullet 1", "Strong bullet 2"]
                    }
                  ],
                  "education": [
                    {
                      "institution": "University Name",
                      "degree": "B.Tech",
                      "field": "Computer Science",
                      "startDate": "2018",
                      "endDate": "2022",
                      "gpa": "8.5"
                    }
                  ],
                  "skills": {
                    "technical": ["Java", "Spring Boot"],
                    "soft": ["Leadership", "Communication"]
                  },
                  "projects": [
                    {
                      "name": "Project Name",
                      "description": "What it does",
                      "techStack": ["Java", "MySQL"],
                      "bullets": ["Achievement 1", "Achievement 2"]
                    }
                  ]
                }
                
                PERSON DETAILS:
                Name: %s
                Target Role: %s
                Experience Level: %s
                Location: %s
                
                RAW EXPERIENCE:
                %s
                
                RAW EDUCATION:
                %s
                
                SKILLS:
                %s
                
                PROJECTS:
                %s
                
                Rules:
                - Write strong action verb bullets with metrics
                - Generate professional summary if not provided
                - Make experience sound impressive but truthful
                - Add relevant keywords for %s role
                """.formatted(
                req.getFullName(),
                req.getTargetRole(),
                req.getExperienceLevel() != null
                        ? req.getExperienceLevel() : "FRESHER",
                req.getLocation() != null ? req.getLocation() : "India",
                req.getExperiences() != null
                        ? req.getExperiences() : List.of("No experience yet"),
                req.getEducations() != null
                        ? req.getEducations() : List.of(),
                req.getSkills() != null ? req.getSkills() : List.of(),
                req.getProjects() != null ? req.getProjects() : List.of(),
                req.getTargetRole()
        );
    }

    // ══════════════════════════════════════════════════════
    // RESPONSE PARSERS
    // ══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private OptimizeResumeResponseDTO parseOptimizeResponse(
            String rawResponse,
            Resume resume,
            String targetRole) {
        try {
            String clean = cleanJson(rawResponse);
            Map<String, Object> parsed = objectMapper.readValue(
                    clean, new TypeReference<>() {}
            );

            List<Map<String, Object>> sections =
                    (List<Map<String, Object>>) parsed.getOrDefault(
                            "improvedSections", List.of()
                    );

            List<OptimizeResumeResponseDTO.ImprovedSectionDTO> improved =
                    sections.stream()
                            .map(s -> OptimizeResumeResponseDTO
                                    .ImprovedSectionDTO.builder()
                                    .sectionTitle(
                                            (String) s.get("sectionTitle")
                                    )
                                    .improvedContent(
                                            (Map<String, Object>)
                                                    s.getOrDefault(
                                                            "improvedContent", Map.of()
                                                    )
                                    )
                                    .changes(
                                            (List<String>) s.getOrDefault(
                                                    "changes", List.of()
                                            )
                                    )
                                    .build())
                            .toList();

            return OptimizeResumeResponseDTO.builder()
                    .resumeId(resume.getId())
                    .targetJobTitle(targetRole)
                    .improvedSections(improved)
                    .summary((String) parsed.getOrDefault(
                            "summary", "Resume optimized successfully"
                    ))
                    .bulletsImproved((Integer) parsed.getOrDefault(
                            "bulletsImproved", 0
                    ))
                    .build();

        } catch (Exception e) {
            throw new AiServiceException(
                    "Failed to parse optimization response"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private RewriteSectionResponseDTO parseRewriteResponse(
            String rawResponse,
            Resume resume,
            String jobDescription) {
        try {
            String clean = cleanJson(rawResponse);
            Map<String, Object> parsed = objectMapper.readValue(
                    clean, new TypeReference<>() {}
            );

            List<Map<String, Object>> sections =
                    (List<Map<String, Object>>) parsed.getOrDefault(
                            "rewrittenSections", List.of()
                    );

            List<RewriteSectionResponseDTO.RewrittenSectionDTO> rewritten =
                    sections.stream()
                            .map(s -> RewriteSectionResponseDTO
                                    .RewrittenSectionDTO.builder()
                                    .sectionTitle(
                                            (String) s.get("sectionTitle")
                                    )
                                    .rewrittenContent(
                                            (Map<String, Object>)
                                                    s.getOrDefault(
                                                            "rewrittenContent", Map.of()
                                                    )
                                    )
                                    .build())
                            .toList();

            return RewriteSectionResponseDTO.builder()
                    .resumeId(resume.getId())
                    .jobDescription(jobDescription)
                    .rewrittenSections(rewritten)
                    .keywordsAdded(
                            (List<String>) parsed.getOrDefault(
                                    "keywordsAdded", List.of()
                            )
                    )
                    .matchScoreBefore((Integer) parsed.getOrDefault(
                            "matchScoreBefore", 0
                    ))
                    .matchScoreAfter((Integer) parsed.getOrDefault(
                            "matchScoreAfter", 0
                    ))
                    .build();

        } catch (Exception e) {
            throw new AiServiceException(
                    "Failed to parse rewrite response"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private CareerGuidanceResponseDTO parseCareerGuidanceResponse(
            String rawResponse,
            String resumeId,
            String targetRole) {
        try {
            String clean = cleanJson(rawResponse);
            Map<String, Object> parsed = objectMapper.readValue(
                    clean, new TypeReference<>() {}
            );

            List<Map<String, Object>> gapsList =
                    (List<Map<String, Object>>) parsed.getOrDefault(
                            "skillGaps", List.of()
                    );

            List<CareerGuidanceResponseDTO.SkillGapDTO> skillGaps =
                    gapsList.stream()
                            .map(g -> CareerGuidanceResponseDTO
                                    .SkillGapDTO.builder()
                                    .skill((String) g.get("skill"))
                                    .importance((String) g.getOrDefault(
                                            "importance", "MEDIUM"
                                    ))
                                    .salaryImpact((String) g.getOrDefault(
                                            "salaryImpact", "Increases salary"
                                    ))
                                    .resources(
                                            (List<String>) g.getOrDefault(
                                                    "resources", List.of()
                                            )
                                    )
                                    .timeToLearn((String) g.getOrDefault(
                                            "timeToLearn", "4-6 weeks"
                                    ))
                                    .build())
                            .toList();

            List<Map<String, Object>> companiesList =
                    (List<Map<String, Object>>) parsed.getOrDefault(
                            "companySuggestions", List.of()
                    );

            List<CareerGuidanceResponseDTO.CompanySuggestionDTO> companies =
                    companiesList.stream()
                            .map(c -> CareerGuidanceResponseDTO
                                    .CompanySuggestionDTO.builder()
                                    .name((String) c.get("name"))
                                    .type((String) c.getOrDefault(
                                            "type", "product"
                                    ))
                                    .location((String) c.getOrDefault(
                                            "location", "India"
                                    ))
                                    .salaryRange((String) c.getOrDefault(
                                            "salaryRange", "Market rate"
                                    ))
                                    .whyGoodFit((String) c.getOrDefault(
                                            "whyGoodFit", ""
                                    ))
                                    .applyUrl((String) c.getOrDefault(
                                            "applyUrl", ""
                                    ))
                                    .build())
                            .toList();

            return CareerGuidanceResponseDTO.builder()
                    .resumeId(resumeId)
                    .targetRole(targetRole)
                    .currentLevel((String) parsed.getOrDefault(
                            "currentLevel", "JUNIOR"
                    ))
                    .skillGaps(skillGaps)
                    .nextCareerMoves(
                            (List<String>) parsed.getOrDefault(
                                    "nextCareerMoves", List.of()
                            )
                    )
                    .companySuggestions(companies)
                    .interviewQuestions(
                            (List<String>) parsed.getOrDefault(
                                    "interviewQuestions", List.of()
                            )
                    )
                    .currentSalaryRange((String) parsed.getOrDefault(
                            "currentSalaryRange", "Not specified"
                    ))
                    .potentialSalaryRange((String) parsed.getOrDefault(
                            "potentialSalaryRange", "Not specified"
                    ))
                    .careerAdvice((String) parsed.getOrDefault(
                            "careerAdvice", ""
                    ))
                    .build();

        } catch (Exception e) {
            throw new AiServiceException(
                    "Failed to parse career guidance response"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGeneratedResume(String rawResponse) {
        try {
            String clean = cleanJson(rawResponse);
            return objectMapper.readValue(
                    clean, new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            throw new AiServiceException(
                    "Failed to parse generated resume"
            );
        }
    }

    // ══════════════════════════════════════════════════════
    // SECTION BUILDER
    // ══════════════════════════════════════════════════════

    // ✅ Fix 1 — correct return type List<ResumeSection>
    private List<ResumeSection> buildSectionsFromGenerated(
            Map<String, Object> generated,
            GenerateResumeRequestDTO req) {

        List<ResumeSection> sections = new ArrayList<>();
        int order = 0;

        // Personal Info
        Map<String, Object> personalInfo = new HashMap<>();
        personalInfo.put("name", req.getFullName());
        personalInfo.put("email", req.getEmail());
        personalInfo.put("phone", req.getPhone());
        personalInfo.put("location", req.getLocation());

        sections.add(buildSection(
                "PERSONAL_INFO", "Personal Information",
                personalInfo, order++
        ));

        // Summary
        String summary = (String) generated.getOrDefault(
                "summary",
                req.getSummary() != null ? req.getSummary() : ""
        );
        sections.add(buildSection(
                "SUMMARY", "Professional Summary",
                Map.of("text", summary), order++
        ));

        // Experience
        sections.add(buildSection(
                "EXPERIENCE", "Work Experience",
                Map.of("items",
                        generated.getOrDefault("experience", List.of())
                ),
                order++
        ));

        // Education
        sections.add(buildSection(
                "EDUCATION", "Education",
                Map.of("items",
                        generated.getOrDefault("education", List.of())
                ),
                order++
        ));

        // Skills
        sections.add(buildSection(
                "SKILLS", "Skills",
                (Map<String, Object>) generated.getOrDefault(
                        "skills", Map.of()
                ),
                order++
        ));

        // Projects
        sections.add(buildSection(
                "PROJECTS", "Projects",
                Map.of("items",
                        generated.getOrDefault("projects", List.of())
                ),
                order
        ));

        return sections;
    }

    // ✅ Fix 2 — correct package: entity not document
    private ResumeSection buildSection(String type,
                                       String title,
                                       Map<String, Object> content,
                                       int order) {
        return ResumeSection.builder()
                .sectionId(UUID.randomUUID().toString())
                .type(SectionType.valueOf(type))
                .title(title)
                .order(order)
                .isVisible(true)
                .content(content)
                .build();
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════

    private String extractResumeText(Resume resume) {
        StringBuilder text = new StringBuilder();
        resume.getSections().forEach(section -> {
            if (Boolean.TRUE.equals(section.getIsVisible())
                    && section.getContent() != null
                    && !section.getContent().isEmpty()) {
                text.append(section.getTitle()).append(":\n");
                text.append(section.getContent().toString()).append("\n\n");
            }
        });
        return text.toString().trim();
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        return response
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }
}
