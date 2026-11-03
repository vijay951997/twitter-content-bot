package com.ai.xbot.dto;

import com.ai.xbot.entity.enums.PostSlot;
import jakarta.validation.constraints.Pattern;

/** Both optional: slot defaults to the article's topic, language to the configured mix. */
public record DraftFromArticleRequest(PostSlot slot,
                                      @Pattern(regexp = "ta|en|mixed", message = "must be ta, en or mixed") String language) {
}
