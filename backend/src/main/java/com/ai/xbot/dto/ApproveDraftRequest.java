package com.ai.xbot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * @param version             the draft version the user reviewed; approval fails if the draft changed since
 * @param publishAt           when to publish (ignored when publishNow is true); null = the draft's proposed time
 * @param publishNow          publish as soon as the publisher runs (within a minute)
 * @param acknowledgeWarnings must be true when the draft has risk flags, unverified claims or human edits
 */
public record ApproveDraftRequest(@NotNull Long version, Instant publishAt, boolean publishNow,
                                  boolean acknowledgeWarnings, @Size(max = 500) String comment) {
}
