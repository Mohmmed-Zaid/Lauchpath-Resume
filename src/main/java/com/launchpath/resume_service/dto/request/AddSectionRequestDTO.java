package com.launchpath.resume_service.dto.request;

import com.launchpath.resume_service.enums.SectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AddSectionRequestDTO {

    @NotNull(message = "Section type is required")
    private SectionType type;

    @NotBlank(message = "Section title is required")
    private String title;

    // Initial content — can be empty map
    private Map<String, Object> content;
}
