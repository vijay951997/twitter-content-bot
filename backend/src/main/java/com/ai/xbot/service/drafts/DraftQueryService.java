package com.ai.xbot.service.drafts;

import com.ai.xbot.dto.DraftDto;
import com.ai.xbot.dto.PageDto;
import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.PostSource;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.repository.GeneratedPostRepository;
import com.ai.xbot.repository.PostSourceRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftQueryService {

    private final GeneratedPostRepository posts;
    private final PostSourceRepository sources;
    private final DraftMapper mapper;
    private final com.ai.xbot.repository.PublishingQueueRepository queue;
    private final com.ai.xbot.repository.PublishedPostRepository published;
    private final com.ai.xbot.repository.PostApprovalRepository approvals;
    private final com.ai.xbot.repository.UserRepository users;
    private final com.ai.xbot.repository.ConnectedAccountRepository accounts;

    public DraftQueryService(GeneratedPostRepository posts, PostSourceRepository sources, DraftMapper mapper,
                             com.ai.xbot.repository.PublishingQueueRepository queue,
                             com.ai.xbot.repository.PublishedPostRepository published,
                             com.ai.xbot.repository.PostApprovalRepository approvals,
                             com.ai.xbot.repository.UserRepository users,
                             com.ai.xbot.repository.ConnectedAccountRepository accounts) {
        this.posts = posts;
        this.sources = sources;
        this.mapper = mapper;
        this.queue = queue;
        this.published = published;
        this.approvals = approvals;
        this.users = users;
        this.accounts = accounts;
    }

    private DraftDto enrich(GeneratedPost p, List<String> urls) {
        DraftDto d = mapper.toDto(p, urls);
        var entries = queue.findByPostIdOrderByCreatedAtDesc(p.getId());
        var pub = published.findByPostId(p.getId());
        if (entries.isEmpty() && pub.isEmpty()) return d;
        var q = entries.isEmpty() ? null : entries.get(0);
        var approval = q == null ? null : approvals.findById(q.getApprovalId()).orElse(null);
        String approver = approval == null ? null
                : users.findById(approval.getDecidedBy()).map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()).orElse(null);
        String xUrl = pub.map(pp -> accounts.findById(pp.getConnectedAccountId())
                .map(a -> "https://x.com/" + (a.getProviderUsername() == null ? "i" : a.getProviderUsername()) + "/status/" + pp.getXPostId())
                .orElse("https://x.com/i/status/" + pp.getXPostId())).orElse(null);
        return d.withPublishing(new DraftDto.Publishing(q == null ? null : q.getStatus().name(),
                q == null ? null : q.getScheduledAt(), q == null ? 0 : q.getAttemptCount(), q == null ? null : q.getLastError(),
                pub.map(com.ai.xbot.entity.PublishedPost::getXPostId).orElse(null), xUrl,
                pub.map(com.ai.xbot.entity.PublishedPost::getPublishedAt).orElse(null),
                approver, approval == null ? null : approval.getDecidedAt()));
    }

    @Transactional(readOnly = true)
    public PageDto<DraftDto> list(PostStatus status, int page, int size) {
        PageRequest req = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)));
        Page<GeneratedPost> result = status == null
                ? posts.findAllByOrderByCreatedAtDesc(req)
                : posts.findByStatusOrderByCreatedAtDesc(status, req);
        Map<UUID, List<String>> urls = urlsFor(result.getContent().stream().map(GeneratedPost::getId).toList());
        List<DraftDto> items = result.getContent().stream()
                .map(p -> enrich(p, urls.getOrDefault(p.getId(), List.of()))).toList();
        return new PageDto<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DraftDto get(UUID id) {
        GeneratedPost p = posts.findById(id).orElseThrow(() -> new NotFoundException("Draft not found"));
        return enrich(p, urlsFor(List.of(id)).getOrDefault(id, List.of()));
    }

    private Map<UUID, List<String>> urlsFor(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        for (PostSource ps : sources.findByPostIdIn(ids)) {
            List<String> l = out.computeIfAbsent(ps.getPostId(), k -> new ArrayList<>());
            if (!l.contains(ps.getSourceUrl())) l.add(ps.getSourceUrl());
        }
        return out;
    }
}
