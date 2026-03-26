package com.launchpath.resume_service.dto.request;

import com.launchpath.resume_service.entity.ResumeSection;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveResumeRequestDTO {

    @NotNull(message = "Sections are required")
    private List<ResumeSection> sections;

    // USER = manual save, AUTO_SAVE = WebSocket debounce
    private String savedBy = "USER";
}
