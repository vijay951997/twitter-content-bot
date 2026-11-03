package com.ai.xbot.dto;

import java.util.UUID;

public record CurrentUserDto(UUID id, String username, String displayName, String role) {
}
