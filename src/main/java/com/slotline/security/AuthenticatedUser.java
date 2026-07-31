package com.slotline.security;

import com.slotline.user.Role;

public record AuthenticatedUser(Long id, String username, Role role, Long organizationId) {
}
