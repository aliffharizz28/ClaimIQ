# ClaimIQ working instructions

- Keep the assessment small and understandable: Java 21, Spring Boot, Maven, modular monolith, motor claims first.
- Work stage by stage. Do not implement future stages unless requested. Preserve the user's files and do not publish or deploy.
- Claimants may access only their own claims; officers have review access. Enforce authorization at service boundaries and test it when implementing APIs. Protect state-changing browser requests against CSRF.
- Rules should explain evidence gaps and possible duplicates. They support human review and must not claim fraud or automatically decide eligibility or payouts. An LLM is optional; Kafka is design only unless explicitly requested.
- Use fictional data. Never commit real credentials, customer data, generated database files, or build outputs.
- Update AI_WORKING_JOURNAL.md with actual prompts, decisions, challenges, and verification; never invent user approval, tests, reviews, or commits. Explain important decisions in plain language.
