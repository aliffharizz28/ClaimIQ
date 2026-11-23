# API reference

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


