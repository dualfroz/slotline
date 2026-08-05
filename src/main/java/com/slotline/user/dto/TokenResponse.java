package com.slotline.user.dto;

public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
}
