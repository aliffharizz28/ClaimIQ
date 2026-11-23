# ClaimIQ: use cases and technical design

This document describes the implemented motor-claims prototype. Diagrams use [Mermaid](https://mermaid.js.org/) syntax, supported by GitHub and many Markdown viewers. The application runs locally; Kafka, an LLM, file uploads and payments are not implemented.

## 1. System context and use cases

Both roles use the same Angular application. The claimant can access their own claims; the officer can review the queue. These use cases describe available behavior, not separate deployed services.

```mermaid
flowchart LR
    Claimant[Claimant]
    Officer[Claims officer]

    subgraph ClaimIQ[ClaimIQ local application]
        Submit([Submit a motor claim])
        Track([Track own claim and evidence gaps])
        Respond([Provide requested information])
        Queue([View claim queue and summary])
        Assign([Assign a claim])
        Findings([Review and refresh triage findings])
        Evidence([Verify or reject evidence references])
        Request([Request information])
        Decide([Approve or reject with a note])
    end

    Claimant --> Submit
    Claimant --> Track
    Claimant --> Respond
    Officer --> Queue
    Officer --> Assign
    Officer --> Findings
    Officer --> Evidence
    Officer --> Request
    Officer --> Decide
```

| Use case | Actor and condition | Outcome |
| --- | --- | --- |
| Submit claim | Authenticated claimant; valid incident date, vehicle registration, description and positive amount | Claim is saved as `SUBMITTED`; evidence references, activity and triage results are saved in the same transaction. |
| Track and respond | Claim owner; a response requires `MORE_INFO_REQUIRED` | The claimant sees their claim and evidence gaps. A response returns the claim to `UNDER_REVIEW` and reruns triage. |
| Manage the queue | Officer | The officer sees paginated claims and a summary of status counts and provisional exposure. |
| Assign and review | Officer; assignment requires `SUBMITTED` or `UNDER_REVIEW` | The claim becomes `UNDER_REVIEW`. The assigned officer can verify or reject evidence references. |
| Request information | Officer; claim must be `UNDER_REVIEW` | A note is recorded and status becomes `MORE_INFO_REQUIRED`. |
| Record decision | Assigned officer; claim must be `UNDER_REVIEW` | The officer records `APPROVED` or `REJECTED` with a note. There is no payment or settlement processing. |

All state-changing requests require CSRF protection. Claim updates carry an expected version; conflicting versions or invalid state transitions return HTTP 409. A claimant attempting to access another claimant's claim receives 404.

## 2. Claim lifecycle

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: claimant submits
    SUBMITTED --> UNDER_REVIEW: officer assigns
    UNDER_REVIEW --> UNDER_REVIEW: officer reassigns
    UNDER_REVIEW --> MORE_INFO_REQUIRED: officer requests information
    MORE_INFO_REQUIRED --> UNDER_REVIEW: claimant responds
    UNDER_REVIEW --> APPROVED: assigned officer approves
    UNDER_REVIEW --> REJECTED: assigned officer rejects
    APPROVED --> [*]
    REJECTED --> [*]
```

`APPROVED` records an officer decision in this prototype; it does not represent settlement or payment. Evidence verification/rejection can occur while a claim is under review without changing its status.

## 3. Sequence: submitting a claim

This diagram starts after the user has signed in and the browser has obtained a CSRF token. It shows the synchronous implementation rather than a proposed event-driven system.

```mermaid
sequenceDiagram
    actor C as Claimant
    participant UI as Angular application
    participant Sec as Spring Security
    participant API as ClaimController
    participant Svc as ClaimService
    participant DB as H2 via JPA
    participant T as TriageService

    C->>UI: Enter claim and evidence references
    UI->>Sec: POST /api/v1/claims (Basic credentials, CSRF token)
    Sec->>Sec: Authenticate and validate CSRF
    Sec->>API: Forward authenticated request
    API->>API: Validate request fields
    API->>Svc: Submit claim for authenticated claimant
    Svc->>Svc: Check claimant role and start transaction
    Svc->>DB: Lock claimant row and save claim, evidence and activity
    Svc->>T: Reconcile triage for the affected window
    T->>DB: Read evidence and same-claimant candidates
    T->>DB: Save results and findings for this and affected claims
    T-->>Svc: Return after synchronous analysis
    Svc->>DB: Flush and commit transaction
    Svc-->>API: Return claim view
    API-->>UI: 201 Created with claim details
    UI-->>C: Show claim and evidence checks
```

Validation or authorization failure prevents submission. Claim and triage changes share a transaction, so a failed transaction does not leave a partially submitted claim. The claimant-row lock serializes supported triage-writing operations for that claimant. The frontend requests the triage result separately; claimants receive evidence-gap findings only, while officers can see the other findings and overall operational priority.

## 4. Entity relationship diagram

The diagram shows the principal JPA entities and relationships. `APP_USERS` represents both claimants and officers. A claim has one claimant and at most one assigned officer. A triage finding may optionally point to another claim belonging to the same claimant under the current rules.

```mermaid
erDiagram
    APP_USERS ||--o{ CLAIMS : submits
    APP_USERS o|--o{ CLAIMS : assigned_to
    APP_USERS ||--o{ CLAIM_ACTIVITIES : performs
    CLAIMS ||--o{ EVIDENCE : has
    CLAIMS ||--o{ CLAIM_ACTIVITIES : records
    CLAIMS ||--o{ TRIAGE_RESULTS : analyzed_in
    TRIAGE_RESULTS ||--o{ TRIAGE_FINDINGS : contains
    CLAIMS o|--o{ TRIAGE_FINDINGS : related_claim

    APP_USERS {
        Long id PK
        String name
        String email UK
        String password_hash
        String role
    }
    CLAIMS {
        Long id PK
        Long claimant_id FK
        Long assigned_officer_id FK
        Date incident_date
        String vehicle_registration
        String normalized_registration
        String description
        Decimal claimed_amount
        Decimal provisional_exposure
        String status
        Timestamp created_at
        Timestamp evidence_updated_at
        Long version
    }
    EVIDENCE {
        Long id PK
        Long claim_id FK
        String type
        String reference
        String verification_status
    }
    CLAIM_ACTIVITIES {
        Long id PK
        Long claim_id FK
        Long actor_id FK
        String action
        String note
        Timestamp occurred_at
    }
    TRIAGE_RESULTS {
        Long id PK
        Long claim_id FK
        String priority
        Timestamp analyzed_at
        String rule_version
    }
    TRIAGE_FINDINGS {
        Long id PK
        Long triage_result_id FK
        Long related_claim_id FK
        String type
        String explanation
    }
```

Evidence is metadata, not an uploaded document. A claim can have multiple result snapshots, and a result can contain multiple findings. The latest snapshot is selected by ID. Amounts use `BigDecimal`; currency is not currently modeled.

## 5. Engineering choices and boundaries

- **Modular monolith:** identity, security, claims and triage are package boundaries inside one Spring Boot application. This keeps the local assessment setup simple while separating responsibilities.
- **REST and future events:** REST handles the implemented synchronous actions. A future event-driven design could publish claim events via a transactional outbox and Kafka; neither exists in the current implementation.
- **Persistence:** file-backed H2 makes the demo easy to start. Hibernate schema updates and the registration backfill are prototype conveniences; production use would need a managed migration strategy and a suitable database.
- **Consistency:** optimistic claim versions prevent silent conflicting updates. A per-claimant pessimistic lock and synchronous one-hop reconciliation address the tested simultaneous-submission case; work grows with the number of claims in the affected window.
- **Human decision support:** explainable rules identify evidence gaps, same-date possible duplicates, recent same-vehicle claims and repeated narratives. Findings and priorities are review prompts, not proof of fraud, eligibility decisions or payout decisions.

See [README.md](README.md) for the project scope, [LOCAL_SETUP.md](LOCAL_SETUP.md) for a runnable walkthrough, and [API_REFERENCE.md](API_REFERENCE.md) for endpoint contracts.
