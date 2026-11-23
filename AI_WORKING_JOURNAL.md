# AI working journal

This journal records how ClaimIQ was planned, generated with AI assistance, challenged and verified. It distinguishes requests made by the candidate from implementation choices made by the AI assistant. The system is a local prototype, and its automated findings support human review rather than determining fraud or claim outcomes.

## 23 November 2026 — Scope and architecture

**Request and decisions.** The candidate chose a backend-first motor-claims MVP using Java and Spring Boot, deterministic rules with an optional future LLM, duplicate and evidence-gap checks, and a minimal interface. The agreed direction was a modular monolith with file-backed H2, seeded demonstration users, and an Angular frontend if time permitted. Kafka was to be discussed as a future event-driven option rather than implemented in the MVP. The candidate asked for development to proceed in stages and for important decisions to be explained.

**AI proposal and reasoning.** The AI proposed six core entities, a claims activity history, role and ownership checks, and a provisional exposure initially equal to the submitted amount. The architecture favored a locally runnable system and a complete motor-claims workflow over a broader but incomplete platform. Priority would be used for work management, not as a fraud probability.

**Verification.** The implementation environment initially had neither Java nor Maven on PATH. The AI verified that the Maven project file was well-formed XML, but could not compile or run the application at that point. The original assessment brief had not yet been located in the working session.

## 23–24 November 2026 — Foundation and local setup

**Request.** The candidate requested the Spring Boot project, domain model, H2 configuration, security foundation and AI journal. After the missing tools were identified, the candidate installed Java 21 and Maven and asked the AI to recheck the setup.

**AI implementation.** The foundation used JPA entities, file-backed H2, BCrypt password hashes, opt-in fictional demo accounts, deny-by-default route security and optimistic claim versions. The AI chose separate identity, claim, triage and security packages in a single application. The development setup was local only.

**Verification.** The AI refreshed the terminal environment and confirmed that Maven was using Java 21. The first four backend tests passed. The app started with the demo profile and connected to file-backed H2; an unauthenticated request to an unopened route returned 401 and an authenticated request returned 403. The local server was stopped after the check.

## 24 November 2026 — Claims workflow

**Request.** The candidate asked to proceed to the next stage.

**AI implementation and reasoning.** The AI added claim submission and retrieval, evidence metadata, officer assignment, information requests and responses, decisions, activity records, a workload/exposure summary, validation and access control. Assignment moves a submitted claim into review; a claimant response returns a claim awaiting information to review. Updates include a version so stale edits can be rejected. The submitted amount is labeled as provisional exposure rather than an approved payout.

**Verification.** At this stage, six backend tests passed, including a multi-step workflow, role and ownership restrictions, invalid input, CSRF, stale versions and invalid transitions. A separate local smoke test obtained a CSRF token and submitted a fictional claim. Simultaneous submissions were not tested at this stage; they were tested later.

## 24 November 2026 — Deterministic triage and interface

**Request.** The candidate asked to continue without waiting for a detailed stage review.

**AI implementation.** Triage was initially added for missing evidence and potential duplicates based on the same claimant, incident date and vehicle registration. Results were stored as versioned snapshots with explanations. The AI then built a minimal Angular interface for both roles, using the backend's authentication, authorization and CSRF protections. Evidence fields store references, not uploaded files. The AI did not add an LLM or deploy the application.

**Reasoning.** Rules were chosen because they are transparent and testable. A possible duplicate is a prompt for an officer to investigate; it is not an accusation or automatic decision. A locally runnable frontend gives the assessment reviewers a way to demonstrate the workflow.

**Verification.** Seven backend tests passed after the initial triage work. The frontend production build and one frontend test passed. A browser check with a temporary in-memory database covered claimant sign-in and submission, followed by officer assignment and an information request. A later final walkthrough extended this check through claimant response and decision.

## 24 November 2026 — Candidate question about different incident dates

**Challenge.** While using the UI, the candidate observed that two claims for the same vehicle, dated eight days apart, did not produce a duplicate finding and asked why. The AI explained that the original rule required the same incident date. The candidate then requested logic for this scenario.

**Change and reasoning.** The AI added a separate recent-vehicle-claim finding for the same claimant and normalized registration when incident dates differ by 1–30 days. It receives medium priority unless a higher-priority exact-date duplicate is present. The finding states that separate incidents are possible. The 30-day window is a simple MVP choice, not a validated fraud threshold.

**Verification.** Eight backend tests passed at that stage. The added test covered an eight-day example, the 30-day boundary, 31 days, a different claimant, dates entered out of order and officer refresh.

## 24 November 2026 — Additional review ideas

**Request.** The candidate shared possible checks involving duplicate claims, identity, timing, evidence, networks and staff activity.

**AI proposal and scope decision.** The AI implemented an identical-narrative review prompt using data already collected: the same claimant, incident dates within 30 days, and at least 40 normalized characters. The finding does not establish fraud or a duplicate. The exact-date/same-vehicle duplicate rule takes precedence for the same pair. File hashing, device and payout linkage, policy coverage, price benchmarks and staff-risk rules were not implemented because the prototype lacks the necessary data and validated baselines. The 30-day and 40-character values are MVP assumptions.

**Verification.** Nine backend tests passed, including checks for repeated text, case differences, the 30-day boundary, different claimants, short descriptions and changed wording. These tests used in-memory H2.

## 24 November 2026 — External feedback and backend improvements

**Challenge and request.** The candidate shared prioritized feedback highlighting stale triage results and concurrent submissions, authorization and workflow gaps, query efficiency, pagination and evidence review. The feedback also proposed finding dismissal as a stretch feature.

**Changes made.** The AI added a claimant-row pessimistic lock and one-hop reanalysis for affected claims in the 30-day window, while keeping optimistic claim versions for updates. It restricted decisions and evidence review to the assigned officer, returned 404 for access to another claimant's claim, and limited claimants to evidence-gap findings and an evidence-only priority. It added an auditable evidence-review endpoint and interface controls, pagination, database-grouped dashboard totals, a normalized registration index with backfill for existing data, and a Kuala Lumpur clock for incident-date validation.

**Trade-offs.** Latest triage results are selected by database ID; the per-claimant lock orders the supported triage-writing operations. One-hop synchronous reconciliation remains proportional to the number of claims in the affected window and is not a production-scale event system. The suggested finding-dismissal feature was not implemented. More advanced cross-account and file checks remained outside the available data and MVP scope.

**Verification.** The final backend suite passed 13 tests, including simultaneous submissions, privacy and authorization checks, evidence rejection, pagination, dashboard exposure, and a temporary file-backed H2 restart with normalization backfill. The frontend build and its test passed. The tests do not prove production readiness or cover every possible concurrent execution.

## 24 November 2026 — Assessment brief and final verification

**Request.** The candidate asked to complete the remaining preparation and explain how to submit the assessment. The AI located the recruiter email and original brief. The brief calls for a Git repository with meaningful history, a locally runnable application and an AI working journal; it explicitly allows a scoped solution and prohibits public hosting and proprietary branding.

**Repository history.** The project had been developed before Git was initialized. A local repository was initialized and the existing MVP was recorded as an initial snapshot; subsequent documentation and journal changes were committed afterward. Earlier work was not reconstructed or backdated as separate commits. This means the Git history does not fully show the earlier development stages; this journal records those stages instead.

**Final walkthrough.** In a separate temporary in-memory database, the AI used the interface to submit a fictional claim, assign it, record evidence verification, request more information, respond as the claimant and record an officer approval. The activity trail and dashboard reflected the final status. The temporary servers were stopped after the check. The app was not publicly hosted and no email was sent.

**Review boundary.** The candidate tried the local interface and raised the different-incident-date question recorded above. A complete independent review of every generated line, additional personal challenges or overrides, and the actual total development time are not established by this journal.

## 24 November 2026 — Project documentation

**Request.** The candidate asked for the README to describe what ClaimIQ does, its benefits, scope and distinguishing features, with local setup instructions in a separate file.

**Changes and verification.** The AI rewrote the README as a project overview, moved the existing setup and demonstration instructions to `LOCAL_SETUP.md`, retained the endpoint contract in `API_REFERENCE.md`, and updated the frontend documentation links. Benefits are described as design goals and prototype capabilities rather than measured outcomes or unverified competitive claims. No application code was changed for this documentation update.

## 24 November 2026 — Architecture diagrams

**Request.** The candidate requested supporting use-case, sequence and entity-relationship documentation to explain the system's software-engineering design.

**Work and verification.** The AI compared the diagram content with the implemented entities, controllers and services, then added `ARCHITECTURE.md` with a use-case overview, lifecycle diagram, claim-submission sequence, entity relationships and design trade-offs. The four Mermaid blocks were parsed successfully with Mermaid's parser; this validates syntax, not every viewer's rendering. The README links to the new document. No application behavior was changed.
