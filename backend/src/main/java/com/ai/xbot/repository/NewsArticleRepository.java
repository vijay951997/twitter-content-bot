package com.ai.xbot.repository;

import com.ai.xbot.entity.NewsArticle;
import com.ai.xbot.entity.enums.AnalysisStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {

    boolean existsByOriginalUrl(String originalUrl);

    @Query("""
            select a from NewsArticle a
            where (:status is null or a.analysisStatus = :status)
              and (:relevantOnly = false or a.relevant = true)
              and (:sourceId is null or a.sourceId = :sourceId)
            order by coalesce(a.publishedAt, a.retrievedAt) desc""")
    Page<NewsArticle> search(AnalysisStatus status, boolean relevantOnly, UUID sourceId, Pageable pageable);

    /** Analysed articles from other sources in a time window — candidates for corroboration. */
    @Query("""
            select a from NewsArticle a
            where a.analysisStatus = com.ai.xbot.entity.enums.AnalysisStatus.ANALYSED
              and a.sourceId <> :excludeSource
              and coalesce(a.publishedAt, a.retrievedAt) between :from and :to""")
    List<NewsArticle> findCorroborationCandidates(UUID excludeSource, Instant from, Instant to);

    @Query("""
            select a from NewsArticle a
            where a.analysisStatus = com.ai.xbot.entity.enums.AnalysisStatus.NEW and a.relevant = true
            order by coalesce(a.publishedAt, a.retrievedAt) desc""")
    List<NewsArticle> findNewRelevant(Pageable pageable);

    @Query("""
            select a from NewsArticle a
            where a.analysisStatus = com.ai.xbot.entity.enums.AnalysisStatus.ANALYSED
              and a.topicCategory in :categories
              and coalesce(a.publishedAt, a.retrievedAt) >= :since
              and not exists (select 1 from PostSource ps where ps.articleId = a.id)
            order by coalesce(a.publishedAt, a.retrievedAt) desc""")
    List<NewsArticle> findUnusedAnalysed(Collection<String> categories, Instant since, Pageable pageable);

    long countByAnalysisStatus(AnalysisStatus status);
}
