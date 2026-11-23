# ClaimIQ

ClaimIQ is a motor-claims decision-support application with a Java 21 / Spring Boot backend and a minimal Angular frontend. It supports a basic claims workflow and deterministic triage.

## Start locally

Install a Java 21 JDK and Maven 3.6.3 or later, then from `backend` run:

```powershell
mvn test
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

To run the interface, install Node.js 20.19+, 22.12+, or 24+ and npm. In a **second terminal**, from the `frontend` folder run:

```powershell
npm ci
npm start
```

Open `http://127.0.0.1:4200/`. Keep the backend running in the first terminal. The Angular development server proxies API requests to the backend; stop each server with Ctrl+C. Use the demo accounts shown on the sign-in page. To verify the frontend separately, run `npm run build` and `npm test -- --watch=false` from `frontend`.

The app listens on `127.0.0.1:8080`. The opt-in `demo` profile seeds fictional accounts `claimant@example.test` and `officer@example.test`, both with password `demo-password`. Passwords are BCrypt-hashed before storage. Seeding is idempotent by email and does not reset existing passwords. These credentials and HTTP Basic are for local demonstrations only; do not expose this configuration or use real customer data. The default profile creates no users. Unlisted routes are denied by default.

H2 stores files under `backend/data/` relative to the working directory. Hibernate's `update` is convenient for a disposable local prototype, not a migration strategy; schema changes can leave stale data. This version adds and backfills a normalized registration column at startup; keep a backup of your database before upgrading. The H2 console is disabled. Most tests use a separate in-memory database; the restart test uses a temporary file database.

## Structure and decisions

- `identity`: users, roles and authentication lookup; `security`: HTTP security, demo seed and a reusable read-policy helper.
- `claim`: motor claim, evidence metadata and activity records; `triage`: stored results and explainable findings. These are package boundaries in one deployable application, not independently deployed services.
- Claims use a version column for optimistic locking. A claimant-row write lock serializes submissions and triage-affecting changes for that claimant. The initial exposure equals the claimed amount as a provisional estimate, not a reserve or approved payout. Evidence references are metadata, not file uploads.
- A triage result can have multiple findings and a claim can have multiple results; the latest result is selected by ID. Priority is operational, not a fraud probability.
- Spring Security authenticates API calls, service methods enforce roles and claim ownership, and unlisted routes remain denied. CSRF protection remains enabled; clients need a token and its session cookie for state-changing requests. The demo configuration is not production authentication.

## Claims API

All endpoints use HTTP Basic authentication. `GET /api/v1/me` returns the authenticated user's ID and role. `GET /api/v1/csrf` returns `token` and `headerName` and establishes a session cookie; send both that cookie and the token in the named header on every POST. For example, with the app running, PowerShell can submit a fictional claim:

```powershell
$base = 'http://127.0.0.1:8080'
$credentials = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('claimant@example.test:demo-password'))
$headers = @{ Authorization = "Basic $credentials" }
$csrf = Invoke-RestMethod "$base/api/v1/csrf" -Headers $headers -SessionVariable session
$headers[$csrf.headerName] = $csrf.token
$body = @{ incidentDate = '2025-01-01'; vehicleRegistration = 'ABC123'; description = 'Rear collision'; claimedAmount = 2500.00; evidence = @(@{ type = 'DAMAGE_PHOTO'; reference = 'photo-01' }) } | ConvertTo-Json -Depth 5
Invoke-RestMethod "$base/api/v1/claims" -Method Post -Headers $headers -WebSession $session -ContentType 'application/json' -Body $body
```

| Method and path | Who | Purpose / body |
| --- | --- | --- |
| `POST /api/v1/claims` | Claimant | Submit incident date, registration, description, positive amount and optional evidence metadata. |
| `GET /api/v1/claims?page=0&size=20` | Both | Paginated claim list; claimant sees own claims and officer sees the review queue. Page is zero-based; size is 1–100. Returns `content`, `number`, `totalPages`, `totalElements`. |
| `GET /api/v1/claims/{id}` | Owner or officer | Claim details, evidence and activity history. |
| `GET /api/v1/claims/{id}/triage` | Owner or officer | Latest result; claimants see only evidence gaps and an evidence-only priority, officers see all findings and operational priority. |
| `POST /api/v1/claims/{id}/triage` | Officer | Run the rules again, including for claims created before triage was added. Requires CSRF. |
| `POST /api/v1/claims/{id}/assign` | Officer | `version`, `officerId`, optional `note`; moves a submitted claim to review. Use `/me` to find the demo officer's ID. |
| `POST /api/v1/claims/{id}/request-info` | Officer | `version`, `note`; moves a claim in review to more information required. |
| `POST /api/v1/claims/{id}/additional-info` | Owner | `version`, `note`, optional `evidence`; returns it to review. |
| `POST /api/v1/claims/{id}/decision` | Assigned officer | `version`, `decision` (`APPROVED` or `REJECTED`), `note`; requires a claim in review. |
| `POST /api/v1/claims/{id}/evidence/{evidenceId}/review` | Assigned officer | `version`, `status` (`VERIFIED` or `REJECTED`), `note`; requires a claim in review, logs activity and reruns triage. |
| `GET /api/v1/dashboard/summary` | Officer | Counts by status and provisional exposure for non-final claims. |

Use the latest returned `version` for each update. A stale version or an invalid transition returns HTTP 409; reload before retrying. Access to another claimant's claim returns 404. Claim changes and activity records are saved together. The summary groups claims by status in the database; approved and rejected claims are excluded from outstanding provisional exposure. Evidence references are metadata only. Future-incident validation uses the `Asia/Kuala_Lumpur` time zone.

## Triage rules

On submission and after additional information, the application synchronously stores a new result in the same transaction as the claim change. `GET /triage` selects the latest result by its database ID; prior results remain stored. An officer can rerun analysis for an older claim. A claim with no result yet returns 404 from the triage endpoint until analyzed.

- **Evidence gaps:** flag a missing damage photo and/or repair quotation. An unverified item counts as supplied; this does not confirm that it is genuine or sufficient. Rejected evidence does not count. Incident date, description and vehicle registration are mandatory on submission and checked by API validation.
- **Possible duplicate:** flag other claims belonging to the same claimant with the same incident date and vehicle registration, ignoring case and non-ASCII-alphanumeric characters. The normalized value is indexed; existing local records are backfilled at startup. The finding identifies the related claim and explains the matching criteria. It does not use location, time, amount, or claims from different claimants.
- **Recent vehicle claim:** flag another claim from the same claimant for the same normalized vehicle registration when the incident dates differ by 1–30 days, inclusive. The comparison works in either date order. The finding is a prompt to review whether claims are related; separate incidents are possible. It does not compare claims across different claimants. Thirty days is an MVP assumption, not a fraud threshold.
- **Repeated narrative:** flag identical descriptions of at least 40 characters after trimming, collapsing whitespace and ignoring case, when the same claimant has another claim with an incident date within 30 days. The finding is suppressed when the same pair already matches the exact-date vehicle rule. It does not detect similar wording or compare other claimants. Copying text can be innocent and needs context.
- **Priority:** `HIGH` for an exact-date possible duplicate, otherwise `MEDIUM` for a repeated narrative, recent vehicle claim or evidence gap, otherwise `NORMAL`. These are queue priorities, not fraud probabilities or decisions. An officer remains responsible for review and decisions.

Results are snapshots. Submitting or updating a claim and refreshing triage recomputes results for that claimant's claims in the affected 30-day window in the same transaction. A claimant-row lock serializes those operations, including concurrent submissions. Reconciliation can become expensive as the number of related claims grows; a durable asynchronous approach would be more appropriate at scale. Existing snapshots are not automatically recomputed after an application upgrade or rule change: an officer can open a claim and select **Refresh triage**. Findings do not change claim status. No LLM is used.

## Planned work and boundaries

An LLM summary is optional future work and must not decide eligibility, fraud or payouts. The Angular interface supports claimant submission and tracking, and officer review, assignment and decisions; it is a local demo, not a production identity system. Evidence uses text references rather than actual uploads. Currency is not modeled. Dismissal of findings and suppression of dismissed pairs remain future work.

Additional review ideas such as cross-channel submissions, file hashing, receipt matching, device patterns, policy coverage checks, payout links, provider networks and staff behavior are **not implemented**. They need corresponding reliable data, access controls, privacy decisions and tested business rules. No threshold or peer-cohort baseline is assumed. A matching evidence reference is not proof that two files are the same.

REST is intended for synchronous user actions and queries. Kafka is a future option for asynchronous events such as claim submission and decision recording. Reliable publication would require a transactional outbox, retries and idempotent consumers; no Kafka, outbox or event delivery exists here. PostgreSQL and managed migrations are possible future production choices; H2 compatibility does not prove production readiness.

The original assessment brief was not present in this workspace or attached to the referenced conversation; the scope above comes from the supplied handoff and should be checked against the brief before submission. Motor claims are a deliberate vertical slice; property claims and workload/exposure summaries remain future work.
