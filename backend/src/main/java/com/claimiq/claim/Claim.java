package com.claimiq.claim;

import com.claimiq.identity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims", indexes = @Index(name = "idx_claim_owner_date_registration", columnList = "claimant_id,incident_date,normalized_registration"))
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claimant_id", nullable = false) private User claimant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_officer_id") private User assignedOfficer;
    @Column(nullable = false) private LocalDate incidentDate;
    @Column(nullable = false, length = 32) private String vehicleRegistration;
    @Column(name = "normalized_registration", length = 32) private String normalizedRegistration;
    @Column(nullable = false, length = 4000) private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal claimedAmount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal provisionalExposure;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ClaimStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant evidenceUpdatedAt;
    @Version private long version;

    protected Claim() { }
    public Claim(User claimant, LocalDate incidentDate, String vehicleRegistration, String description, BigDecimal claimedAmount) {
        this.claimant = claimant; this.incidentDate = incidentDate; this.vehicleRegistration = vehicleRegistration;
        this.description = description; this.claimedAmount = claimedAmount; this.provisionalExposure = claimedAmount;
        this.status = ClaimStatus.SUBMITTED; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public User getClaimant() { return claimant; }
    public User getAssignedOfficer() { return assignedOfficer; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getVehicleRegistration() { return vehicleRegistration; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getProvisionalExposure() { return provisionalExposure; }
    public ClaimStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
    public void evidenceChanged() { evidenceUpdatedAt = Instant.now(); }
    public String getNormalizedRegistration() { return normalizedRegistration; }
    @PrePersist @PreUpdate public void normalizeRegistration() {
        normalizedRegistration = normalizeRegistration(vehicleRegistration);
    }
    public static String normalizeRegistration(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
    }
    public void assign(User officer) { this.assignedOfficer = officer; this.status = ClaimStatus.UNDER_REVIEW; }
    public void requestInfo() { this.status = ClaimStatus.MORE_INFO_REQUIRED; }
    public void provideInfo() { this.status = ClaimStatus.UNDER_REVIEW; }
    public void decide(ClaimStatus decision) { this.status = decision; }
}
