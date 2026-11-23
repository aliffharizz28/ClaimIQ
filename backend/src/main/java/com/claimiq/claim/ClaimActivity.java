package com.claimiq.claim;

import com.claimiq.identity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "claim_activities")
public class ClaimActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id", nullable = false) private User actor;
    @Column(nullable = false, length = 80) private String action;
    @Column(length = 2000) private String note;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    protected ClaimActivity() { }
    public ClaimActivity(Claim claim, User actor, String action, String note) {
        this.claim = claim; this.actor = actor; this.action = action; this.note = note; this.occurredAt = Instant.now();
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getNote() { return note; }
    public Instant getOccurredAt() { return occurredAt; }
}
