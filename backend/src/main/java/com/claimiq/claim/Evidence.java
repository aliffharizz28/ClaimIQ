package com.claimiq.claim;

import jakarta.persistence.*;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private EvidenceType type;
    @Column(nullable = false, length = 500) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private VerificationStatus verificationStatus;
    protected Evidence() { }
    public Evidence(Claim claim, EvidenceType type, String reference) {
        this.claim = claim; this.type = type; this.reference = reference; this.verificationStatus = VerificationStatus.UNVERIFIED;
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public EvidenceType getType() { return type; }
    public String getReference() { return reference; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
}
