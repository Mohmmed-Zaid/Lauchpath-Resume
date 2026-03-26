package com.launchpath.resume_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReorderSectionRequestDTO {

    @NotNull(message = "New order is required")
    @Min(value = 0, message = "Order must be 0 or greater")
    private Integer newOrder;
}