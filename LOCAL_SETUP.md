# Local setup and demonstration

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

## Demonstration walkthrough

1. Sign in as `claimant@example.test`, password `demo-password`. Submit a claim with a past incident date, registration, description and positive amount. Add a damage photo reference such as `photo-01`, and leave the quotation reference empty. The application tracks references only; it does not upload files.
2. Sign out and sign in as `officer@example.test` with the same password. Open the claim and choose **Assign to me**. Inspect the evidence-gap finding. Enter an evidence review note and choose **Verify** if demonstrating the evidence status control. Enter a review note and choose **Request information**.
3. Sign in again as the claimant. Open the claim, enter a response and a quotation reference such as `quote-01`, and choose **Send information**. The claim returns to review and the evidence-gap result updates.
4. Sign in as the officer, open the claim and record an approval or rejection with a note. Check the activity history and dashboard. The final decision is made by the officer; this prototype does not implement payment or settlement.
5. To demonstrate a possible duplicate separately, submit two claims as the same claimant for the same vehicle and incident date. Open either claim as the officer to see the finding. Claimants see evidence gaps, while other triage findings are officer-only.

With a fresh database the app starts without claims; demo accounts are seeded. Existing local claims persist between runs. A first Maven build and npm installation require access to their dependency registries.

The app listens on `127.0.0.1:8080`. The opt-in `demo` profile seeds fictional accounts `claimant@example.test` and `officer@example.test`, both with password `demo-password`. Passwords are BCrypt-hashed before storage. Seeding is idempotent by email and does not reset existing passwords. These credentials and HTTP Basic are for local demonstrations only; do not expose this configuration or use real customer data. The default profile creates no users. Unlisted routes are denied by default.

H2 stores files under `backend/data/` relative to the working directory. Hibernate's `update` is convenient for a disposable local prototype, not a migration strategy; schema changes can leave stale data. This version adds and backfills a normalized registration column at startup; keep a backup of your database before upgrading. The H2 console is disabled. Most tests use a separate in-memory database; the restart test uses a temporary file database.


