package com.slotline.user.dto;

import com.slotline.user.Role;

public record UserResponse(Long id, String username, Role role, Long organizationId) {
}
