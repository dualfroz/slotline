package com.slotline.resource.dto;

import com.slotline.resource.Resource;

import java.time.Instant;

public record ResourceResponse(
        Long id,
        Long organizationId,
        Long groupId,
        String name,
        String description,
        String zoneId,
        int bufferMinutes,
        Instant createdAt) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getOrganizationId(),
                resource.getGroupId(),
                resource.getName(),
                resource.getDescription(),
                resource.getZoneId(),
                resource.getBufferMinutes(),
                resource.getCreatedAt());
    }
}
