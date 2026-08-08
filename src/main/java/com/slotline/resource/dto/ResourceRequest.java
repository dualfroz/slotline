package com.slotline.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Size(max = 64) String zoneId,
        @Min(0) Integer bufferMinutes) {

    public ResourceRequest(String name, String description) {
        this(name, description, null, null);
    }
}
