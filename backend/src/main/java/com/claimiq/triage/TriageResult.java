package com.claimiq.triage;

import com.claimiq.claim.Claim;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "triage_results")
public class TriageResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Priority priority;
    @Column(nullable = false) private Instant analyzedAt;
    @Column(nullable = false, length = 80) private String ruleVersion;
    protected TriageResult() { }
    public TriageResult(Claim claim, Priority priority, Instant analyzedAt, String ruleVersion) {
        this.claim = claim; this.priority = priority; this.analyzedAt = analyzedAt; this.ruleVersion = ruleVersion;
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public Priority getPriority() { return priority; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public String getRuleVersion() { return ruleVersion; }
}
