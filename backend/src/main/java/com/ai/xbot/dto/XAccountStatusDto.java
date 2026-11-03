package com.ai.xbot.dto;

import java.time.Instant;

/** Connection state for the dashboard. Never contains tokens. */
public record XAccountStatusDto(boolean appConfigured, boolean encryptionKeyConfigured, boolean connected,
                                String status, String username, Instant connectedSince, String missing) {
}
