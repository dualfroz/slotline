package com.slotline.resource.dto;

import com.slotline.resource.ResourceGroup;

import java.time.Instant;

public record ResourceGroupResponse(Long id, String name, Long parentGroupId, Instant createdAt) {

    public static ResourceGroupResponse from(ResourceGroup group) {
        return new ResourceGroupResponse(group.getId(), group.getName(), group.getParentGroupId(),
                group.getCreatedAt());
    }
}
