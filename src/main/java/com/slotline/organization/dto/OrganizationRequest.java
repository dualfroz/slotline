package com.slotline.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(min = 2, max = 64) @Pattern(regexp = "[a-z0-9-]+",
                message = "slug must be lowercase letters, digits or hyphens") String slug) {
}
