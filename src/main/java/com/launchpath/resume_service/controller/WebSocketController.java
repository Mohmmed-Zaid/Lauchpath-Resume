package com.launchpath.resume_service.controller;


import com.launchpath.resume_service.dto.request.SaveResumeRequestDTO;
import com.launchpath.resume_service.dto.response.ResumeDetailResponseDTO;
import com.launchpath.resume_service.entity.Resume;
import com.launchpath.resume_service.mapper.ResumeMapper;
import com.launchpath.resume_service.services.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final SimpMessagingTemplate messagingTemplate;


    // ══════════════════════════════════════════════════════════
    // AUTO SAVE VIA WEBSOCKET
    //
    // Client sends to:   /app/resume/{resumeId}/save
    // Server broadcasts: /topic/resume/{resumeId}
    //
    // Flow:
    // 1. User types in editor
    // 2. Frontend debounces 2 seconds
    // 3. Sends sections via WebSocket
    // 4. Server auto-saves to MongoDB
    // 5. Broadcasts "saved ✓" back to all clients
    //    on same resume (multi-tab support)
    // ══════════════════════════════════════════════════════════

    @MessageMapping("/resume/{resumeId}/save")
    @SendTo("/topic/resume/{resumeId}")
    public ResumeDetailResponseDTO handleAutoSave(
            @DestinationVariable String resumeId,
            SaveResumeRequestDTO request) {

        log.debug("WebSocket auto-save - resumeId: {}", resumeId);

        // Extract userId from WebSocket session
        // Phase 3: JWT extracted from WebSocket handshake header
        // For now: userId sent in request body
        Long userId = extractUserIdFromRequest(request);

        Resume saved = resumeService.autoSave(
                resumeId,
                userId,
                request.getSections()
        );

        log.debug("WebSocket save complete - resumeId: {}", resumeId);

        // Broadcast to all subscribers of this resume
        // Handles multi-tab scenario
        return resumeMapper.toDetailDTO(saved, userId);
    }

    // ══════════════════════════════════════════════════════════
    // EDITOR LOCK — notify others someone is editing
    // Client sends to: /app/resume/{resumeId}/editing
    // Broadcasts:      /topic/resume/{resumeId}/status
    // ══════════════════════════════════════════════════════════

    @MessageMapping("/resume/{resumeId}/editing")
    @SendTo("/topic/resume/{resumeId}/status")
    public String handleEditing(
            @DestinationVariable String resumeId,
            String userId) {

        log.debug("User {} editing resume {}", userId, resumeId);
        return "{ \"status\": \"editing\", \"userId\": \"" + userId + "\" }";
    }

    // ══════════════════════════════════════════════════════════
    // EDITOR DISCONNECT — release lock
    // Called when browser tab closes
    // ══════════════════════════════════════════════════════════

    @MessageMapping("/resume/{resumeId}/disconnect")
    @SendTo("/topic/resume/{resumeId}/status")
    public String handleDisconnect(
            @DestinationVariable String resumeId,
            String userId) {

        log.info("User {} disconnected from resume {}", userId, resumeId);

        // Force unlock
        resumeService.forceUnlock(resumeId);

        return "{ \"status\": \"disconnected\", \"userId\": \"" + userId + "\" }";
    }

    // Stub — Phase 3: extract from JWT in WebSocket handshake
    private Long extractUserIdFromRequest(SaveResumeRequestDTO request) {
        return 1L;
    }
}
