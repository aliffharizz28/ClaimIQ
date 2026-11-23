package com.claimiq.claim;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByClaimantIdOrderByIdDesc(Long claimantId);
    List<Claim> findAllByOrderByIdDesc();
    Page<Claim> findByClaimantId(Long claimantId, Pageable pageable);
    @Query("select c.status as status, count(c) as count, sum(c.provisionalExposure) as exposure from Claim c group by c.status")
    List<StatusTotals> summarize();
    interface StatusTotals { ClaimStatus getStatus(); long getCount(); BigDecimal getExposure(); }
    List<Claim> findByClaimantIdAndIncidentDateBetweenAndIdNotOrderByIdAsc(Long claimantId, LocalDate start, LocalDate end, Long id);
    List<Claim> findByNormalizedRegistrationIsNull();
    List<Claim> findByClaimantIdAndNormalizedRegistrationAndIncidentDateBetweenAndIdNotOrderByIdAsc(Long claimantId, String registration, LocalDate start, LocalDate end, Long id);
}
