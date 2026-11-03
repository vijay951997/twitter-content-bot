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

    public DraftQueryService(GeneratedPostRepository posts, PostSourceRepository sources, DraftMapper mapper) {
        this.posts = posts;
        this.sources = sources;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageDto<DraftDto> list(PostStatus status, int page, int size) {
        PageRequest req = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)));
        Page<GeneratedPost> result = status == null
                ? posts.findAllByOrderByCreatedAtDesc(req)
                : posts.findByStatusOrderByCreatedAtDesc(status, req);
        Map<UUID, List<String>> urls = urlsFor(result.getContent().stream().map(GeneratedPost::getId).toList());
        List<DraftDto> items = result.getContent().stream()
                .map(p -> mapper.toDto(p, urls.getOrDefault(p.getId(), List.of()))).toList();
        return new PageDto<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DraftDto get(UUID id) {
        GeneratedPost p = posts.findById(id).orElseThrow(() -> new NotFoundException("Draft not found"));
        return mapper.toDto(p, urlsFor(List.of(id)).getOrDefault(id, List.of()));
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
