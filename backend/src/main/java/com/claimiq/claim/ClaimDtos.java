package com.claimiq.claim;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ClaimDtos {
    private ClaimDtos() { }
    public record EvidenceInput(@NotNull EvidenceType type, @NotBlank @Size(max=500) String reference) { }
    public record Submit(@NotNull @PastOrPresent LocalDate incidentDate,
                         @NotBlank @Size(max=32) String vehicleRegistration,
                         @NotBlank @Size(max=4000) String description,
                         @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(integer=17, fraction=2) BigDecimal claimedAmount,
                         @Size(max=20) List<@NotNull @Valid EvidenceInput> evidence) { }
    public record Action(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=2000) String note) { }
    public record Assign(@NotNull @PositiveOrZero Long version, @NotNull @Positive Long officerId, @Size(max=1600) String note) { }
    public record Decision(@NotNull @PositiveOrZero Long version, @NotNull ClaimStatus decision, @NotBlank @Size(max=2000) String note) { }
    public record EvidenceReview(@NotNull @PositiveOrZero Long version, @NotNull VerificationStatus status, @NotBlank @Size(max=1900) String note) { }
    public record AdditionalInfo(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=2000) String note,
                                 @Size(max=20) List<@NotNull @Valid EvidenceInput> evidence) { }
    public record EvidenceView(Long id, EvidenceType type, String reference, VerificationStatus verificationStatus) { }
    public record ActivityView(Long id, String actor, String action, String note, Instant occurredAt) { }
    public record ClaimView(Long id, Long claimantId, Long assignedOfficerId, LocalDate incidentDate,
                            String vehicleRegistration, String description, BigDecimal claimedAmount,
                            BigDecimal provisionalExposure, ClaimStatus status, Instant createdAt, long version,
                            List<EvidenceView> evidence, List<ActivityView> activities) { }
}
