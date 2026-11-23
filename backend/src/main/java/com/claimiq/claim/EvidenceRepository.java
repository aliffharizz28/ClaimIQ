package com.claimiq.claim;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findByClaimIdOrderByIdAsc(Long claimId);
}
