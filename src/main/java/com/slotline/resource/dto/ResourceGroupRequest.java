package com.slotline.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceGroupRequest(
        @NotBlank @Size(max = 128) String name,
        Long parentGroupId) {
}
