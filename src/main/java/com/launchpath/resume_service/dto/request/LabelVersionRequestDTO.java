package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LabelVersionRequestDTO {

    @NotBlank(message = "Label is required")
    @Size(max = 50, message = "Label must be under 50 characters")
    private String label;
}