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

    @Value("${ai.groq.api-key}")
    private String groqApiKey;

    @Value("${ai.groq.base-url}")
    private String groqBaseUrl;

    @Value("${ai.groq.free.model}")
    private String groqFreeModel;

    @Value("${ai.groq.pro.model}")
    private String groqProModel;

    // ══════════════════════════════════════════════════════
    // PROVIDER ROUTING
    // ══════════════════════════════════════════════════════

    private String selectModel(Long userId) {
        try {
            Integer remainingCredits = userServiceClient
                    .getRemainingAtsCredits(userId)
                    .getData();
            if (remainingCredits != null && remainingCredits > 3) {
                return groqProModel;
            }
        } catch (Exception e) {
            log.warn("Could not determine credits, defaulting to free model");
        }
        return groqFreeModel;
    }

    // ══════════════════════════════════════════════════════
    // ATS ANALYSIS
    // ══════════════════════════════════════════════════════

    public AtsResult analyzeResume(String resumeText,
                                   String jobDescription,
                                   Long userId) {
        log.info("ATS analysis - userId: {}", userId);

        userServiceClient.consumeAtsCredit(userId);

        try {
            String prompt = buildAtsPrompt(resumeText, jobDescription);
            String rawResponse = callGroq(userId, prompt);
            AtsResult result = parseAtsResponse(rawResponse, jobDescription);

            log.info("ATS analysis complete - score: {}, userId: {}",
                    result.getOverallScore(), userId);
            return result;

        } catch (Exception e) {
            log.error("ATS analysis failed - userId: {}, refunding credit", userId);
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

    // ══════════════════════════════════════════════════════
    // RESUME TEXT PARSING
    // ══════════════════════════════════════════════════════

    public Map<String, Object> parseResumeText(String rawText, Long userId) {
        log.info("Parsing resume text - userId: {}", userId);
        String prompt = buildParsePrompt(rawText);
        String rawResponse = callGroq(userId, prompt);
        return parseStructuredSections(rawResponse);
    }

    // ══════════════════════════════════════════════════════
    // ATS SUGGESTIONS
    // ══════════════════════════════════════════════════════

    public List<String> generateSuggestions(String resumeText,
                                            String jobDescription,
                                            Long userId) {
        log.info("Generating suggestions - userId: {}", userId);
        String prompt = buildSuggestionsPrompt(resumeText, jobDescription);
        try {
            String rawResponse = callGroq(userId, prompt);
            return parseSuggestions(rawResponse);
        } catch (Exception e) {
            log.error("Suggestion generation failed - userId: {}", userId, e);
            throw new AiServiceException(
                    "Failed to generate suggestions. Please try again."
            );
        }
    }

    // ══════════════════════════════════════════════════════
    // GROQ API CALL — single method for everything
    // ══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private String callGroq(Long userId, String prompt) {
        String model = selectModel(userId);
        log.debug("Calling Groq - model: {}", model);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are an expert ATS resume analyzer " +
                                        "and career coach. Always respond in JSON."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,
                "max_tokens", 2000,
                "response_format", Map.of("type", "json_object")
        );

        try {
            Map<String, Object> response = webClientBuilder
                    .baseUrl(groqBaseUrl)
                    .build()
                    .post()
                    .uri("/chat/completions")   // ✅ correct
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + groqApiKey)
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

        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            throw new AiServiceException("Groq API error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // PROMPT BUILDERS
    // ══════════════════════════════════════════════════════

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
                "Return ONLY a JSON object with key 'suggestions' as array:\n" +
                "{\"suggestions\": [\"suggestion1\", \"suggestion2\", ...]}\n\n" +
                "RESUME:\n" + resumeText;

        if (jobDescription != null && !jobDescription.isBlank()) {
            base += "\n\nJOB DESCRIPTION:\n" + jobDescription;
        }
        return base;
    }

    // ══════════════════════════════════════════════════════
    // RESPONSE PARSERS
    // ══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private AtsResult parseAtsResponse(String rawResponse,
                                       String jobDescription) {
        try {
            String clean = cleanJson(rawResponse);
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
                    .provider(AiProvider.GROQ_FREE_MODEL)
                    .analyzedAt(LocalDateTime.now())
                    .jobDescription(jobDescription)
                    .overallScore(overallScore != null ? overallScore : 0)
                    .metricScores(metricScores != null ? metricScores : new HashMap<>())
                    .suggestions(suggestions != null ? suggestions : new ArrayList<>())
                    .matchedKeywords(matchedKeywords != null ? matchedKeywords : new ArrayList<>())
                    .missingKeywords(missingKeywords != null ? missingKeywords : new ArrayList<>())
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
            String clean = cleanJson(rawResponse);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            // Groq returns JSON object (response_format json_object),
            // so wrap in object if needed
            Map<String, Object> parsed = mapper.readValue(clean, Map.class);
            Object suggestions = parsed.get("suggestions");
            if (suggestions instanceof List) {
                return (List<String>) suggestions;
            }
            return List.of("Could not parse suggestions. Please try again.");
        } catch (Exception e) {
            log.error("Failed to parse suggestions: {}", e.getMessage());
            return List.of("Could not parse suggestions. Please try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStructuredSections(String rawResponse) {
        try {
            String clean = cleanJson(rawResponse);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(clean, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse structured sections: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String cleanJson(String response) {
        if (response == null) return "{}";
        return response
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }
}