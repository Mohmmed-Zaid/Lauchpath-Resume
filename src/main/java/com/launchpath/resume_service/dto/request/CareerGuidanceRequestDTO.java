package com.launchpath.resume_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CareerGuidanceRequestDTO {

    // Optional — if null uses resume targetJobTitle
    private String targetRole;

    // Optional — user location for local company suggestions
    private String location;

    // Current salary for hike calculation
    private String currentSalary;
}