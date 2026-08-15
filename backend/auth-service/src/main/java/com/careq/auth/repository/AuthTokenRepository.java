package com.careq.auth.repository;

import com.careq.auth.entity.AuthToken;
import com.careq.auth.entity.AuthTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthTokenPurpose purpose);

    /**
     * The verification gate: true iff the user has a pending (unused)
     * VERIFY_EMAIL token. Legacy accounts created before Day 17 have no
     * tokens at all → false → they can sign in as before.
     */
    boolean existsByUserIdAndPurposeAndUsedAtIsNull(String userId, AuthTokenPurpose purpose);

    void deleteByUserIdAndPurpose(String userId, AuthTokenPurpose purpose);
}
