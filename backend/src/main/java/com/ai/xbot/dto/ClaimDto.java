package com.ai.xbot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimDto(UUID id, String text, String type, String attributedTo, String verificationStatus,
                       String verificationMethod, int independentSourceCount, List<String> supportingUrls,
                       List<String> conflictingUrls, String verificationNotes, Instant verifiedAt) {
}
