package com.ai.xbot.integration.news;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

/**
 * Fetches and parses an RSS or Atom feed. Feeds are the publisher's own syndication channel, so this
 * reads only what publishers choose to syndicate (titles, links, dates, short descriptions) and never
 * scrapes article pages.
 */
@Component
public class FeedClient {

    private static final Logger log = LoggerFactory.getLogger(FeedClient.class);
    static final int MAX_ITEMS = 50;
    static final int MAX_EXCERPT = 1500;

    private final HttpFetcher http;
    private final RobotsPolicy robots;

    public FeedClient(HttpFetcher http, RobotsPolicy robots) {
        this.http = http;
        this.robots = robots;
    }

    public FeedResult fetch(String feedUrl, String etag, String lastModified) {
        RobotsPolicy.Verdict verdict = robots.check(feedUrl);
        if (verdict == RobotsPolicy.Verdict.UNREACHABLE) {
            return FeedResult.failure(FeedResult.Status.NETWORK_ERROR,
                    "Could not reach the site (its robots.txt could not be read), will retry later");
        }
        if (verdict == RobotsPolicy.Verdict.DISALLOWED) {
            return FeedResult.failure(FeedResult.Status.BLOCKED_BY_ROBOTS,
                    "The site's robots.txt does not allow fetching this feed");
        }
        HttpFetcher.Response r;
        try {
            Map<String, String> headers = new HashMap<>();
            if (etag != null) headers.put("If-None-Match", etag);
            if (lastModified != null) headers.put("If-Modified-Since", lastModified);
            r = http.get(feedUrl, headers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FeedResult.failure(FeedResult.Status.NETWORK_ERROR, "Interrupted");
        } catch (Exception e) {
            return FeedResult.failure(FeedResult.Status.NETWORK_ERROR, "Could not reach the feed: " + e.getClass().getSimpleName());
        }
        if (r.status() == 304) {
            return new FeedResult(FeedResult.Status.NOT_MODIFIED, List.of(), etag, lastModified, "Not modified");
        }
        if (r.status() >= 400) {
            return FeedResult.failure(FeedResult.Status.HTTP_ERROR, "Feed returned HTTP " + r.status());
        }
        if (r.truncated()) {
            return FeedResult.failure(FeedResult.Status.TOO_LARGE, "Feed is larger than 3 MB");
        }
        try {
            List<FeedItem> items = parse(r.body());
            return new FeedResult(FeedResult.Status.OK, items, r.header("ETag"), r.header("Last-Modified"),
                    items.size() + " items");
        } catch (Exception e) {
            log.warn("Feed parse failed for {}: {}", feedUrl, e.getMessage());
            return FeedResult.failure(FeedResult.Status.PARSE_ERROR, "Not a readable RSS/Atom feed");
        }
    }

    static List<FeedItem> parse(byte[] body) throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        input.setAllowDoctypes(false); // XXE protection
        SyndFeed feed = input.build(new InputSource(new ByteArrayInputStream(body)));
        List<FeedItem> out = new ArrayList<>();
        for (SyndEntry e : feed.getEntries()) {
            if (out.size() >= MAX_ITEMS) break;
            String title = clean(e.getTitle());
            String link = e.getLink() != null ? e.getLink().trim() : null;
            if (title.isEmpty() || link == null || !(link.startsWith("http://") || link.startsWith("https://"))) continue;
            Date date = e.getPublishedDate() != null ? e.getPublishedDate() : e.getUpdatedDate();
            String excerpt = e.getDescription() == null ? "" : clean(e.getDescription().getValue());
            if (excerpt.length() > MAX_EXCERPT) excerpt = excerpt.substring(0, MAX_EXCERPT);
            out.add(new FeedItem(title, link, date == null ? null : date.toInstant(), excerpt));
        }
        return out;
    }

    /** Strips HTML from feed text. */
    static String clean(String html) {
        if (html == null) return "";
        return Jsoup.parse(html).text().replaceAll("\\s+", " ").strip();
    }
}
