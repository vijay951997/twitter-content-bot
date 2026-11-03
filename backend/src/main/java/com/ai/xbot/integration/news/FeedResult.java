package com.ai.xbot.integration.news;

import java.util.List;

public record FeedResult(Status status, List<FeedItem> items, String etag, String lastModified, String message) {

    public enum Status { OK, NOT_MODIFIED, BLOCKED_BY_ROBOTS, HTTP_ERROR, NETWORK_ERROR, PARSE_ERROR, TOO_LARGE }

    public boolean success() { return status == Status.OK || status == Status.NOT_MODIFIED; }

    static FeedResult failure(Status status, String message) {
        return new FeedResult(status, List.of(), null, null, message);
    }
}
