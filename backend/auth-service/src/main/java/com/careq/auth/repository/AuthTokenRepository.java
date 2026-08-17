package com.careq.auth.repository;

import com.careq.auth.entity.AuthToken;
import com.careq.auth.entity.AuthTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    /**
     * The verification gate: true iff the user has a pending (unused)
     * VERIFY_EMAIL token. Legacy accounts created before verification was enabled have no
     * tokens at all → false → they can sign in as before.
     */
    boolean existsByUserIdAndPurposeAndUsedAtIsNull(String userId, AuthTokenPurpose purpose);

    /**
     * Rotates a user's token for a purpose. Implemented as a bulk delete (not
     * the Spring Data derived delete) so two concurrent resends / password
     * resets can't race into an ObjectOptimisticLockingFailureException: a
     * bulk DELETE matching zero rows is a no-op, whereas the derived delete
     * loads each row and fails with "Row was updated or deleted by another
     * transaction" if a concurrent transaction already removed it.
     */
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.userId = :userId AND t.purpose = :purpose")
    void deleteByUserIdAndPurpose(@Param("userId") String userId, @Param("purpose") AuthTokenPurpose purpose);
}
