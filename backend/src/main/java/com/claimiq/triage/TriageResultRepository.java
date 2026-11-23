package com.claimiq.triage;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageResultRepository extends JpaRepository<TriageResult, Long> {
    Optional<TriageResult> findFirstByClaimIdOrderByIdDesc(Long claimId);
}
