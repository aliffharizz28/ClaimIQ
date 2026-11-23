package com.claimiq.claim;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimActivityRepository extends JpaRepository<ClaimActivity, Long> {
    List<ClaimActivity> findByClaimIdOrderByIdAsc(Long claimId);
}
