package com.claimiq.triage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageFindingRepository extends JpaRepository<TriageFinding, Long> {
    List<TriageFinding> findByTriageResultIdOrderByIdAsc(Long triageResultId);
}
