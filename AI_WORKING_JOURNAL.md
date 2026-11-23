# AI working journal

Record actual work and distinguish AI proposals from decisions reviewed by the developer. Future entries should include the request, proposal, accepted or challenged decisions, reasoning, and verification.

## 2026-11-23 — Planning context

- The user selected Java/Spring Boot, a modular monolith, motor claims, deterministic evidence-gap and potential-duplicate checks, an optional LLM, a backend-first approach and Kafka as design rather than required implementation. The prior conversation proposed H2, seeded users, an activity trail and an initial provisional exposure based on the claim amount.
- The plan states that automated findings support an officer; they do not establish fraud or decide a claim. The original brief was not attached to this session, so its requirements were not independently verified.

## 2026-11-23 — Stage 1 implementation

- Request: inspect the workspace, implement the Stage 1 foundation, document it, and run available verification without starting claims APIs or publishing.
- AI implementation choices: separate package boundaries, JPA entities with foreign keys and optimistic locking, file-backed H2, BCrypt demo users under an opt-in profile, deny-by-default HTTP security and a reusable ownership policy. Demo users are local examples, not production identity management.
- Reasoning: these choices keep startup and the model small while making authorization and concurrency concerns visible. The developer has not yet reviewed or accepted the generated implementation; no review, challenge or commit is claimed.
- Verification: `mvn test` was attempted but could not start because Maven was not found. Java was also absent from PATH and the common installation locations checked. The POM was parsed successfully as well-formed XML, which does not establish that dependencies resolve or the code compiles. Tests, application startup, authentication and database persistence remain unverified at runtime. No commits were created; the workspace was not a Git repository.

## 2026-11-24 — Tooling and Stage 1 verification

- The user installed Java and Maven and asked for the setup to be checked and any remaining work handled.
- The running Codex process initially had an outdated PATH. Reloading the Windows machine and user environment made Microsoft OpenJDK 21.0.13.1 and Maven 3.10.2 available; Maven reported Java 21.
- `mvn test` succeeded: four tests passed, with no failures or errors. They cover demo users, access policy, default route denial and a JPA claim persistence case using in-memory H2.
- Started the application with the demo profile. It connected to file-backed H2 and started on localhost port 8080. An unauthenticated request to the unopened claims route returned 401; an authenticated demo claimant received 403, as expected for the deny-by-default configuration. The application was stopped after the check.
- No claims API or triage engine was implemented, and no commits were made. Maven is currently installed under Downloads, which should be retained or moved and its PATH updated before that directory is cleaned.

## 2026-11-24 — Stage 2 claims workflow

- Request: move on to the next stage after Stage 1 verification.
- Implemented authenticated REST claim submission and retrieval, evidence metadata, officer assignment, information requests and responses, officer decisions, activity history, an officer workload/exposure summary and a current-user endpoint. Added request validation, role and ownership checks at the service boundary, CSRF token access, and version checks backed by JPA optimistic locking.
- Decisions: assignment moves a submitted claim into review; only a claim under review can receive an information request or decision; a claimant response returns it to review. A decision requires an officer note. Provisional exposure remains the submitted amount, and the summary excludes final claims. A version is required on each update to expose stale edits. These are implementation choices for the MVP, pending developer review.
- Verification: `mvn -B test` passed all six tests, including a multi-step workflow, cross-user denial, role restrictions, invalid amount, CSRF absence, stale version and invalid transitions. Tests use in-memory H2. A live local smoke test with a separate in-memory database retrieved a CSRF token and session cookie, then submitted a fictional claim successfully. The server was stopped afterward. Simultaneous racing transactions and a file-backed Stage 2 restart were not separately tested.
- Stage 3 triage rules, frontend and deployment remain out of scope. No commits were created. The original assessment brief is still unavailable in this workspace.

## 2026-11-24 — Stage 3 deterministic triage

- Request: proceed directly to Stage 3 without waiting for a user review.
- Implemented synchronous triage at submission and after additional information, stored versioned result snapshots, owner/officer retrieval and officer-initiated refresh for older claims. Rules identify missing damage photos or repair quotations and possible duplicates with the same claimant, incident date and normalized registration. Duplicate findings receive HIGH priority, evidence gaps MEDIUM, and no findings NORMAL. No automated claim decision or LLM was added.
- Decision: results are snapshots rather than a background event system. Existing results are not automatically refreshed when a later claim is submitted; concurrent submissions can miss each other. Officers can rerun analysis, but this is not a guarantee of duplicate detection. This trade-off keeps the assessment scope small and needs consideration if the system is expanded.
- Verification: `mvn -B test` succeeded with seven tests and no failures. The new integration test covers gaps, a duplicate with case/spacing differences, a different claimant, normal priority, access restrictions and reanalysis following new evidence. Tests use in-memory H2; no simultaneous-submission or file-backed Stage 3 restart test was performed. No user code review or commit is claimed.

## 2026-11-24 — Stage 4 Angular interface

- Request: continue after Stage 3. Built a minimal local Angular interface for the existing claims API: demo sign-in, claimant submission and tracking, officer queue and summary, triage findings, assignment, information requests and responses, and decisions. Evidence is entered as references, not uploaded files. The frontend uses the backend session and CSRF token; the development proxy keeps API requests on the same browser origin.
- Decision: kept the existing backend authorization and workflow as the authority, held demo credentials in memory, and avoided publishing or adding an LLM. The UI only assigns an officer to themselves; the backend also supports assigning a different officer by ID. The developer has not reviewed the implementation.
- Verification: `npm run build` succeeded and `npm test -- --watch=false` passed one test. Using a temporary in-memory backend, browser testing verified claimant sign-in, fictional claim submission and evidence-gap findings, followed by officer sign-in, assignment and an information request. The claimant response and final decision were not re-tested in the browser; their backend paths were covered by the existing integration suite. Local servers were stopped after testing. No commits or deployment were made.

## 2026-11-24 — Recent vehicle claim review rule

- Request: flag the user's example of two claims for the same vehicle on different nearby dates as a separate review concern.
- Implemented a `RECENT_VEHICLE_CLAIM` finding for the same claimant and normalized registration with different incident dates 1–30 days apart, in either date order. This produces MEDIUM priority unless a higher-priority exact-date duplicate is also present. The explanation says separate incidents are possible. The 30-day window and same-claimant restriction are MVP choices; they are not evidence of fraud. The same-claimant restriction also avoids exposing another claimant's claim ID through the claimant-facing result.
- Existing analyses remain snapshots; an officer can refresh each claim after restarting the backend. Bumped rule version to `motor-v2`.
- Verification: `mvn -B test` passed all eight tests. The added integration test covers an eight-day example, exactly 30 days, 31 days, a different claimant, out-of-order dates and officer refresh; the existing exact-date duplicate test also passed. Testing used in-memory H2. No user data was modified and no commit was created.

## 2026-11-24 — Additional review ideas

- The user supplied potential duplicate, identity, timing, document and staff-side checks. Implemented one bounded rule supported by existing data: identical incident narratives of at least 40 characters after case and whitespace normalization, on claims for the same claimant within 30 incident-date days. It creates a MEDIUM-priority review prompt, except that it does not duplicate an existing exact-date same-vehicle finding for the same pair. The text explicitly says repetition does not establish duplication or fraud. The 30-day and 40-character thresholds are MVP design choices, not calibrated fraud thresholds.
- Did not implement file hashes, cross-channel matching, device or identity linkage, payout comparisons, policy coverage, price benchmarks, merchant networks, staff-risk signals or other ideas: the current application does not collect the needed data, and the user did not provide validated rules or baselines. The same-claimant scope avoids exposing other users' claim IDs in claimant-visible findings. Exact text matching does not detect paraphrases.
- Verification: `mvn -B test` passed nine tests. The added integration test covers case-insensitive repeated text, the 30-day boundary, different claimants, outside-window matches, short descriptions and changed wording; existing triage and workflow tests passed. Tests used in-memory H2; no user claims or live database were altered. The user has not reviewed this implementation; no commit was made.

## 2026-11-24 — Review feedback: consistency, access, and queries

- The user shared a prioritized review. Implemented claimant-row pessimistic locking for submission, triage refresh, additional information and evidence review. These paths recompute results for the affected claimant's 30-day window in one transaction without recursive propagation; claim updates still use optimistic versions. Latest results continue to be selected by ID, which orders snapshots written under the claimant lock; switching to wall-clock time was not needed for this model.
- Restricted decisions and evidence review to the assigned officer; added an auditable evidence verification/rejection endpoint and interface controls. Unauthorized access to another claimant's claim or triage now returns 404. Claimant triage responses contain evidence gaps and an evidence-only priority; other findings and overall priority remain officer-only. Registration normalization now removes non-ASCII-alphanumeric characters and uses an indexed column; a startup backfill populates the new column for existing data. Validation uses a configurable Clock bean fixed to Asia/Kuala_Lumpur.
- Added paginated claim lists and UI navigation, and database grouping for dashboard counts/exposure. Kept narrative comparisons and one-hop reconciliation within the bounded per-claimant date window; these still do work proportional to matching window size, and list views still fetch evidence and activity per claim. These changes improve the prototype but do not establish production scalability. Finding disposition/dismissal was not implemented.
- Verification: `mvn -B test` passed 13 tests, including simultaneous submissions with findings on both claims, unauthorized officer actions, evidence rejection, claimant visibility, pagination, dashboard totals and a temporary file-backed H2 restart from a schema without the normalized column. `npm run build` and `npm test -- --watch=false` passed (one frontend test). The new interface controls were not browser-tested in this turn. No user database was changed during tests and no commit or deployment was made. The user must restart the backend to load the changes; the local schema will be updated/backfilled at startup.
