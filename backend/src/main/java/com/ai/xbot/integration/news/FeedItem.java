package com.ai.xbot.integration.news;

import java.time.Instant;

/**
 * One item from a feed. {@code excerpt} is the publisher's short description; it is used transiently
 * as input to analysis and is never stored.
 */
public record FeedItem(String title, String link, Instant publishedAt, String excerpt) {
}
