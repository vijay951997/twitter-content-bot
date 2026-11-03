package com.ai.xbot.repository;

import com.ai.xbot.entity.ConnectedAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, UUID> {

    Optional<ConnectedAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** The account posts go out as: the most recently updated non-revoked X account. */
    @Query("select a from ConnectedAccount a where a.provider = 'X' and a.status <> 'REVOKED' order by a.updatedAt desc")
    List<ConnectedAccount> findCurrent();

    default Optional<ConnectedAccount> current() {
        return findCurrent().stream().findFirst();
    }
}
