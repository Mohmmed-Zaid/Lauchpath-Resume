package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class UpdateSectionRequestDTO {

    @NotNull(message = "Content is required")
    private Map<String, Object> content;
}
