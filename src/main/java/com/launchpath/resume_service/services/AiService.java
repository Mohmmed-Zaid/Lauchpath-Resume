package com.launchpath.resume_service.services;


import com.launchpath.resume_service.entity.AtsResult;
import com.launchpath.resume_service.enums.AiProvider;
import com.launchpath.resume_service.exception.AiServiceException;
import com.launchpath.resume_service.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final UserServiceClient userServiceClient;
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${ai.deepseek.base-url}")
    private String deepseekBaseUrl;

    @Value("${ai.deepseek.model}")
    private String deepseekModel;

    @Value("${ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${ai.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.model}")
    private String geminiModel;

    // ══════════════════════════════════════════════════════════
    // PROVIDER ROUTING
    // FREE tier → DeepSeek
    // PAID tier → Gemini 1.5 Flash
    // ══════════════════════════════════════════════════════════

    private AiProvider selectProvider(Long userId) {
        try {
            Integer remainingCredits = userServiceClient
                    .getRemainingAtsCredits(userId)
                    .getData();

            // If remaining > 3 and active subscription — use Gemini
            // Simple heuristic: FREE has max 3 credits
            // TODO: Phase 3 — add explicit isPaidPlan check via Feign
            if (remainingCredits != null && remainingCredits > 3) {
                return AiProvider.GEMINI;
            }
        } catch (Exception e) {
            log.warn("Could not determine provider, defaulting to DeepSeek");
        }
        return AiProvider.DEEPSEEK;
    }

    // ══════════════════════════════════════════════════════════
    // ATS ANALYSIS — Main Entry Point
    // ══════════════════════════════════════════════════════════

    /**
     * Full ATS analysis pipeline:
     * 1. Select AI provider based on plan
     * 2. Consume ATS credit BEFORE calling AI (atomic)
     * 3. Call AI with resume + optional job description
     * 4. Parse response into AtsResult
     * 5. Refund credit if AI call fails
     */
    public AtsResult analyzeResume(String resumeText,
                                   String jobDescription,
                                   Long userId) {
        log.info("ATS analysis - userId: {}", userId);

        AiProvider provider = selectProvider(userId);
        log.info("Selected provider: {} for userId: {}", provider, userId);

        // Consume credit BEFORE API call
        // If AI fails → refund in catch block
        userServiceClient.consumeAtsCredit(userId);

        try {
            String prompt = buildAtsPrompt(resumeText, jobDescription);
            String rawResponse = callAiProvider(provider, prompt);
            AtsResult result = parseAtsResponse(rawResponse, provider,
                    jobDescription);

            log.info("ATS analysis complete - score: {}, userId: {}",
                    result.getOverallScore(), userId);
            return result;

        } catch (Exception e) {
            log.error("ATS analysis failed - userId: {}, refunding credit", userId);
            // Refund credit — user not penalized for AI failure
            try {
                userServiceClient.refundAtsCredit(userId);
            } catch (Exception refundEx) {
                log.error("Credit refund failed - userId: {}", userId, refundEx);
            }
            throw new AiServiceException(
                    "AI analysis failed. Your credit has been refunded. " +
                            "Please try again."
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    // RESUME TEXT PARSING — Called by ParserService
    // ══════════════════════════════════════════════════════════

    /**
     * Parses raw extracted text into structured resume sections.
     * Uses DeepSeek — cheaper, parsing doesn't need premium AI.
     * Returns Map of sectionType → structured content.
     */
    public Map<String, Object> parseResumeText(String rawText, Long userId) {
        log.info("Parsing resume text - userId: {}", userId);

        String prompt = buildParsePrompt(rawText);

        // Always use DeepSeek for parsing — no credit consumed
        String rawResponse = callDeepSeek(prompt);

        return parseStructuredSections(rawResponse);
    }

    // ══════════════════════════════════════════════════════════
    // AI SUGGESTIONS — ATS improvement tips
    // ══════════════════════════════════════════════════════════

    public List<String> generateSuggestions(String resumeText,
                                            String jobDescription,
                                            Long userId) {
        log.info("Generating suggestions - userId: {}", userId);

        AiProvider provider = selectProvider(userId);
        String prompt = buildSuggestionsPrompt(resumeText, jobDescription);

        try {
            String rawResponse = callAiProvider(provider, prompt);
            return parseSuggestions(rawResponse);
        } catch (Exception e) {
            log.error("Suggestion generation failed - userId: {}", userId, e);
            throw new AiServiceException(
                    "Failed to generate suggestions. Please try again."
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE — AI PROVIDER CALLS
    // ══════════════════════════════════════════════════════════

    private String callAiProvider(AiProvider provider, String prompt) {
        return switch (provider) {
            case DEEPSEEK -> callDeepSeek(prompt);
            case GEMINI   -> callGemini(prompt);
        };
    }

    private String callDeepSeek(String prompt) {
        log.debug("Calling DeepSeek API");

        Map<String, Object> requestBody = Map.of(
                "model", deepseekModel,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are an expert ATS resume analyzer " +
                                        "and career coach. Always respond in JSON."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 2000
        );

        try {
            Map<String, Object> response = webClientBuilder
                    .baseUrl(deepseekBaseUrl)
                    .build()
                    .post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + deepseekApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return extractContentFromResponse(response, "deepseek");

        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            throw new AiServiceException("DeepSeek API error: " + e.getMessage());
        }
    }

    private String callGemini(String prompt) {
        log.debug("Calling Gemini API");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 2000
                )
        );

        try {
            Map<String, Object> response = webClientBuilder
                    .baseUrl(geminiBaseUrl)
                    .build()
                    .post()
                    .uri("/v1beta/models/" + geminiModel
                            + ":generateContent?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return extractContentFromResponse(response, "gemini");

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new AiServiceException("Gemini API error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> response,
                                              String provider) {
        try {
            if ("deepseek".equals(provider)) {
                List<Map<String, Object>> choices =
                        (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> message =
                        (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            } else {
                List<Map<String, Object>> candidates =
                        (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> content =
                        (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts =
                        (List<Map<String, Object>>) content.get("parts");
                return (String) parts.get(0).get("text");
            }
        } catch (Exception e) {
            throw new AiServiceException(
                    "Failed to parse AI response from " + provider
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE — PROMPT BUILDERS
    // ══════════════════════════════════════════════════════════

    private String buildAtsPrompt(String resumeText, String jobDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this resume for ATS compatibility. ");
        prompt.append("Return ONLY valid JSON with this exact structure:\n");
        prompt.append("{\n");
        prompt.append("  \"overallScore\": <0-100>,\n");
        prompt.append("  \"metricScores\": {\n");
        prompt.append("    \"keywords\": <0-20>,\n");
        prompt.append("    \"actionVerbs\": <0-15>,\n");
        prompt.append("    \"achievements\": <0-20>,\n");
        prompt.append("    \"formatting\": <0-15>,\n");
        prompt.append("    \"completeness\": <0-15>,\n");
        prompt.append("    \"grammar\": <0-15>\n");
        prompt.append("  },\n");
        prompt.append("  \"suggestions\": [\"suggestion1\", \"suggestion2\"],\n");
        prompt.append("  \"matchedKeywords\": [\"keyword1\", \"keyword2\"],\n");
        prompt.append("  \"missingKeywords\": [\"keyword1\", \"keyword2\"]\n");
        prompt.append("}\n\n");
        prompt.append("RESUME:\n").append(resumeText);

        if (jobDescription != null && !jobDescription.isBlank()) {
            prompt.append("\n\nJOB DESCRIPTION:\n").append(jobDescription);
            prompt.append("\n\nCompare resume against this job description. ");
            prompt.append("Identify matching and missing keywords.");
        }

        return prompt.toString();
    }

    private String buildParsePrompt(String rawText) {
        return "Extract resume information from this text and return ONLY " +
                "valid JSON with these sections:\n" +
                "{\n" +
                "  \"personalInfo\": {\"name\": \"\", \"email\": \"\", " +
                "\"phone\": \"\", \"location\": \"\", \"linkedin\": \"\", " +
                "\"github\": \"\"},\n" +
                "  \"summary\": \"\",\n" +
                "  \"experience\": [{\"company\": \"\", \"role\": \"\", " +
                "\"startDate\": \"\", \"endDate\": \"\", " +
                "\"description\": \"\", \"bullets\": []}],\n" +
                "  \"education\": [{\"institution\": \"\", \"degree\": \"\", " +
                "\"field\": \"\", \"startDate\": \"\", \"endDate\": \"\", " +
                "\"gpa\": \"\"}],\n" +
                "  \"skills\": [{\"category\": \"\", \"skills\": []}],\n" +
                "  \"projects\": [{\"name\": \"\", \"description\": \"\", " +
                "\"techStack\": [], \"url\": \"\"}]\n" +
                "}\n\n" +
                "RESUME TEXT:\n" + rawText;
    }

    private String buildSuggestionsPrompt(String resumeText,
                                          String jobDescription) {
        String base = "Generate 10 specific, actionable suggestions to " +
                "improve this resume for ATS systems. " +
                "Return ONLY a JSON array of strings:\n" +
                "[\"suggestion1\", \"suggestion2\", ...]\n\n" +
                "RESUME:\n" + resumeText;

        if (jobDescription != null && !jobDescription.isBlank()) {
            base += "\n\nJOB DESCRIPTION:\n" + jobDescription;
        }
        return base;
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE — RESPONSE PARSERS
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private AtsResult parseAtsResponse(String rawResponse,
                                       AiProvider provider,
                                       String jobDescription) {
        try {
            // Strip markdown code blocks if AI wraps in ```json
            String clean = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            Map<String, Object> parsed = mapper.readValue(clean, Map.class);

            Integer overallScore = (Integer) parsed.get("overallScore");
            Map<String, Integer> metricScores =
                    (Map<String, Integer>) parsed.get("metricScores");
            List<String> suggestions =
                    (List<String>) parsed.get("suggestions");
            List<String> matchedKeywords =
                    (List<String>) parsed.get("matchedKeywords");
            List<String> missingKeywords =
                    (List<String>) parsed.get("missingKeywords");

            return AtsResult.builder()
                    .resultId(UUID.randomUUID().toString())
                    .provider(provider)
                    .analyzedAt(LocalDateTime.now())
                    .jobDescription(jobDescription)
                    .overallScore(overallScore != null ? overallScore : 0)
                    .metricScores(metricScores != null
                            ? metricScores : new HashMap<>())
                    .suggestions(suggestions != null
                            ? suggestions : new ArrayList<>())
                    .matchedKeywords(matchedKeywords != null
                            ? matchedKeywords : new ArrayList<>())
                    .missingKeywords(missingKeywords != null
                            ? missingKeywords : new ArrayList<>())
                    .rawAiResponse(rawResponse)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse ATS response: {}", e.getMessage());
            throw new AiServiceException(
                    "Failed to parse AI response. Please try again."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseSuggestions(String rawResponse) {
        try {
            String clean = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            return mapper.readValue(clean, List.class);
        } catch (Exception e) {
            log.error("Failed to parse suggestions: {}", e.getMessage());
            return List.of("Could not parse suggestions. Please try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStructuredSections(String rawResponse) {
        try {
            String clean = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            return mapper.readValue(clean, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse structured sections: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}