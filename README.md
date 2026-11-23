# ClaimIQ

**ClaimIQ is a motor insurance claims workspace with explainable decision support.** It brings claim submission, status tracking and officer review into one local application. Simple rules point out information that may need attention, while a claims officer remains responsible for approving or rejecting a claim.

This is an assessment prototype built around a focused motor-claims journey. It uses fictional demo data and is designed to run locally.

## The problem

A claim can pass between a claimant and a claims team over several interactions. When details are incomplete or related claims are difficult to spot, officers need to spend time bringing the information together. Claimants also need a clear view of what is happening and what information is required from them.

ClaimIQ brings those steps together: the claimant can follow the claim, and the officer can see the claim, evidence references, review prompts and activity history in one workspace.

## What ClaimIQ does

**For claimants**

- Submit a motor claim with an incident date, vehicle registration, description, amount and optional evidence references.
- View their own claims, current statuses and activity history.
- See missing-evidence prompts and provide additional information when an officer requests it.
- View the recorded approval or rejection.

**For claims officers**

- View a paginated claims queue and summary of workload and provisional exposure.
- Assign a claim, inspect its information and review evidence references.
- Request more information and record a reasoned approval or rejection.
- View explainable triage findings and manually refresh them when needed.

## How a claim moves through the system

1. A claimant submits an incident; its status becomes **Submitted**.
2. An officer assigns it and begins review; its status becomes **Under review**.
3. If needed, the officer requests information; the claimant responds and the claim returns to review.
4. The assigned officer records an approval or rejection with a note. The activity history shows the actions taken.

The dashboard presents counts by status and an **outstanding provisional exposure** total for non-final claims. The initial provisional exposure equals the amount submitted by the claimant; it is not a confirmed reserve or final payout.

## Explainable triage

ClaimIQ uses deterministic rules, not a predictive fraud model. When a claim is submitted or relevant information changes, the rules produce review findings with explanations:

- **Missing evidence:** notes whether a damage photo or repair quotation reference is missing. A reference can be unverified; it is not proof that an actual file is available or valid.
- **Possible duplicate:** flags claims from the same claimant with the same incident date and normalized vehicle registration.
- **Recent vehicle claim:** flags another claim from the same claimant for the same vehicle when incident dates are 1–30 days apart. Separate incidents are possible.
- **Repeated narrative:** flags the same claimant's matching descriptions of at least 40 characters on claims with incident dates within 30 days, after case and whitespace normalization.

An exact-date possible duplicate receives **High** operational priority. A recent-vehicle claim, repeated narrative or evidence gap receives **Medium** priority when no high-priority finding exists. No detected finding results in **Normal** priority. These labels help organize review; they are not fraud probabilities, eligibility assessments or claim decisions.

Claimants see evidence-gap prompts. Other findings and the overall officer priority are restricted to officers. The 30-day and 40-character thresholds are prototype choices, not calibrated risk thresholds. An officer can refresh triage; results saved before a rule change are not automatically updated merely by upgrading the application.

## Benefits and design focus

ClaimIQ is intended to make the local demonstration easier to follow in several ways:

- **One shared workflow:** the same Angular application supports claimant tracking and officer review, reducing the need to switch between separate views of a claim.
- **A reason behind each prompt:** officers see why a rule fired and, where relevant, which other claim it refers to. The rules do not present a black-box fraud score.
- **Human control:** automated findings guide attention; the assigned officer makes and records the final decision.
- **Traceable actions:** submission, assignment, evidence reviews, requests, responses and decisions are captured in the claim activity history.
- **A view of open work:** status counts and provisional exposure give officers a starting point for understanding their queue.
- **A compact local setup:** the modular monolith and file-backed H2 database avoid the need to run multiple services for the demonstration.

These are design goals and prototype capabilities; no measured improvement in processing time, fraud detection or business outcomes is claimed.

## What is in scope

- Motor claims and the submitted-to-decision workflow described above.
- Fictional claimant and officer demo accounts, role checks and claimant ownership checks.
- Evidence **references** with officer verification or rejection, rather than actual document storage.
- Rule-based findings, operational priority and activity history.
- A workload and provisional-exposure summary.
- Local operation, automated tests, a Git repository and an AI working journal.

## What is out of scope

- Property claims, payments, settlement processing and a true insurance reserve calculation.
- Actual file uploads, document analysis, OCR and attachment hashing.
- Cross-claimant matching, device or payout-account analysis, policy coverage checks and calibrated fraud scoring.
- Predictive AI or an LLM, automated approval or rejection, and automatic fraud determinations.
- A production identity provider, production deployment, Kafka implementation and event delivery.
- Dismissal of individual findings, large-scale reconciliation and detailed team performance reporting.

The system does not model currency, and file-backed H2 with automatic schema updates is intended for a local prototype. Further details and limitations are in the [API reference](API_REFERENCE.md) and [local setup guide](LOCAL_SETUP.md).

## Architecture

The backend is a **Java 21 / Spring Boot modular monolith** using Maven, Spring Security, Spring Data JPA and file-backed H2. It is organized into identity, security, claims and triage packages within one deployable application. The frontend is Angular and calls the backend through a REST/JSON API.

Claim updates use optimistic versions to detect conflicting edits. A claimant-row lock serializes submission and triage-affecting operations for that claimant, and affected claims in the 30-day window are reanalyzed within the same transaction. This is straightforward for the prototype, but synchronous reconciliation and claim-detail loading can become expensive as data grows.

REST handles the current user-facing operations. Kafka is a future design option for asynchronous notifications or processing; reliable event publication would need an approach such as a transactional outbox and idempotent consumers. Neither Kafka nor an outbox is implemented.

## Project documents

- [Local setup and demonstration](LOCAL_SETUP.md) — prerequisites, run commands, demo accounts and a walkthrough.
- [API reference](API_REFERENCE.md) — endpoints, request details and a sample call.
- [AI working journal](AI_WORKING_JOURNAL.md) — requests, decisions, challenges and verification during development.

The assessment covers both motor and property claims. This project deliberately implements the motor-claim slice and documents the remaining boundaries above.
