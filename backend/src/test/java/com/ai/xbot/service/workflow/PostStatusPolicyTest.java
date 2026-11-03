package com.ai.xbot.service.workflow;

import static com.ai.xbot.entity.enums.PostStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.xbot.entity.GeneratedPost;
import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.entity.enums.PostStatus;
import com.ai.xbot.exception.InvalidStateTransitionException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PostStatusPolicyTest {

    @Test
    @DisplayName("Happy path: generated -> review -> approved -> scheduled -> publishing -> published")
    void happyPath() {
        GeneratedPost post = new GeneratedPost("text", "h", ContentLanguage.ENGLISH, "GOVERNANCE", null, null);
        post.transitionTo(UNDER_REVIEW);
        post.transitionTo(APPROVED);
        post.transitionTo(SCHEDULED);
        post.transitionTo(PUBLISHING);
        post.transitionTo(PUBLISHED);
        assertThat(post.getStatus()).isEqualTo(PUBLISHED);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = {"SCHEDULED", "PUBLISHING", "PUBLISHED"})
    @DisplayName("Negative #1: an unapproved draft can never reach the publishing path")
    void generatedDraftCannotSkipApproval(PostStatus target) {
        assertThat(PostStatusPolicy.canTransition(GENERATED, target)).isFalse();
        assertThat(PostStatusPolicy.canTransition(UNDER_REVIEW, target)).isFalse();
    }

    @Test
    @DisplayName("Only SCHEDULED can enter PUBLISHING")
    void onlyScheduledEntersPublishing() {
        Set<PostStatus> sources = EnumSet.noneOf(PostStatus.class);
        for (PostStatus s : PostStatus.values()) {
            if (PostStatusPolicy.canTransition(s, PUBLISHING)) sources.add(s);
        }
        assertThat(sources).containsExactly(SCHEDULED);
    }

    @Test
    @DisplayName("Only APPROVED (or a previously approved FAILED post) can be SCHEDULED")
    void onlyApprovedCanBeScheduled() {
        Set<PostStatus> sources = EnumSet.noneOf(PostStatus.class);
        for (PostStatus s : PostStatus.values()) {
            if (PostStatusPolicy.canTransition(s, SCHEDULED)) sources.add(s);
        }
        assertThat(sources).containsExactlyInAnyOrder(APPROVED, FAILED);
    }

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    @DisplayName("Negative #3: a rejected post can never move anywhere")
    void rejectedIsTerminal(PostStatus target) {
        assertThat(PostStatusPolicy.canTransition(REJECTED, target)).isFalse();
    }

    @Test
    @DisplayName("Negative #4: a published post cannot be published again")
    void publishedCannotRepublish() {
        assertThat(PostStatusPolicy.isTerminal(PUBLISHED)).isTrue();
        assertThat(PostStatusPolicy.canTransition(PUBLISHED, PUBLISHING)).isFalse();
    }

    @Test
    @DisplayName("Negative #2: editing an approved or scheduled post sends it back to review")
    void editAfterApprovalInvalidates() {
        assertThat(PostStatusPolicy.editInvalidatesApproval(APPROVED)).isTrue();
        assertThat(PostStatusPolicy.editInvalidatesApproval(SCHEDULED)).isTrue();
        assertThat(PostStatusPolicy.canTransition(APPROVED, UNDER_REVIEW)).isTrue();
        assertThat(PostStatusPolicy.canTransition(SCHEDULED, UNDER_REVIEW)).isTrue();
        assertThat(PostStatusPolicy.editInvalidatesApproval(GENERATED)).isFalse();
    }

    @Test
    @DisplayName("Posts that are publishing or finished cannot be edited")
    void notEditableOncePublishing() {
        assertThat(PostStatusPolicy.isEditable(PUBLISHING)).isFalse();
        assertThat(PostStatusPolicy.isEditable(PUBLISHED)).isFalse();
        assertThat(PostStatusPolicy.isEditable(REJECTED)).isFalse();
    }

    @Test
    @DisplayName("Entity rejects an illegal transition and keeps its status")
    void entityEnforcesPolicy() {
        GeneratedPost post = new GeneratedPost("text", "h", ContentLanguage.TAMIL, "POLITICS", null, null);
        assertThatThrownBy(() -> post.transitionTo(PUBLISHING))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("GENERATED")
                .hasMessageContaining("PUBLISHING");
        assertThat(post.getStatus()).isEqualTo(GENERATED);
    }

    @Test
    @DisplayName("Every status has a rule defined")
    void everyStatusCovered() {
        for (PostStatus s : PostStatus.values()) {
            assertThat(PostStatusPolicy.allowedFrom(s)).isNotNull();
        }
    }
}
