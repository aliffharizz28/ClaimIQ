package com.claimiq.triage;

import com.claimiq.claim.Claim;
import jakarta.persistence.*;

@Entity
@Table(name = "triage_findings")
public class TriageFinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "triage_result_id", nullable = false) private TriageResult triageResult;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private FindingType type;
    @Column(nullable = false, length = 2000) private String explanation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "related_claim_id") private Claim relatedClaim;
    protected TriageFinding() { }
    public TriageFinding(TriageResult triageResult, FindingType type, String explanation, Claim relatedClaim) {
        this.triageResult = triageResult; this.type = type; this.explanation = explanation; this.relatedClaim = relatedClaim;
    }
    public Long getId() { return id; }
    public TriageResult getTriageResult() { return triageResult; }
    public FindingType getType() { return type; }
    public String getExplanation() { return explanation; }
    public Claim getRelatedClaim() { return relatedClaim; }
}
