# Architecture: non-functional requirements (this run)

- Status: Draft
- Updated: 2026-10-07
- Reviewed: not yet
- Roadmap step: S5 (`docs/roadmap.md`); each requirement names the build that meets it
- Related: ADR-0001, ADR-0004, ADR-0005, ADR-0008, ADR-0013; register Q-18, Q-23, Q-32, Q-36, Q-52, Q-60, Q-61, Q-69, Q-79; `architecture/API-CONVENTIONS.md`, `architecture/TENANCY-AND-IDENTITY.md`, `planning/run-scope.md`

Rows marked **(Claude)** are Claude's proposals; Q-52 a asked Claude to propose the retention periods here. Gabriel may change any of them at review. Everything else cites a decision. This is not legal advice: the LGPD reading is Claude's and should be checked with a lawyer before the first pilot.

## Goal
Keep the run safe and cheap: protect customer and staff data from day one, keep tenants apart, and list what must exist before a first pilot restaurant without building it now (no pilot is planned this run, Q-23 b).

## Environments (this run)
| Environment | What | Status |
|---|---|---|
| Local | Docker Compose: PostgreSQL and MinIO (`pgsty/minio`, Q-85 a); `./gradlew bootRun`; frontend `yarn dev` | Exists |
| CI | GitHub Actions: backend `./gradlew test` (Testcontainers); frontend `yarn lint && yarn build && yarn test` | Exists (S3) |
| Cloud agent sessions | Same checks as CI; SessionStart reports whether the full test suite can run | Exists (S3) |
| Staging, production | None this run. Hosting and domain are chosen before the first pilot, with app and API on the same registrable domain (Q-79 a) | Later |

## Privacy and LGPD
### Personal data held
| Data | Whose | Where | Purpose |
|---|---|---|---|
| Name, phone, delivery address on each order (snapshot) | Customer | Order | Fulfil and track the order; the phone is also the manual tracking credential (Q-51 a, Q-69 a) |
| Name, email, phone (optional), avatar, password hash, membership | Staff member | User, membership | Sign-in, team management (T&I D-18) |
| Audit events (who, what, when) | Staff member | Audit log | Accountability for team, settings and PIX key changes (proposal B5) |
| Sign-in records (time, IP, user agent) | Staff member | Refresh tokens, identity audit | Session security |
| PIX key (may be a CPF, phone or email) | Owner | Restaurant setting | Static PIX code (ADR-0020 point 7) |

### Roles (Claude)
The restaurant decides why it collects its customers' data, so it is the controller of that data; the platform processes it on the restaurant's behalf (operator). For staff accounts and its own audit and sign-in records, the platform is the controller. The terms of service and privacy notice say so before the first pilot.

### Requirements
| # | Requirement | Build | Acceptance criteria | Source |
|---|---|---|---|---|
| P-1 | Data minimization: the storefront asks only for name, phone and, for delivery, the structured address; no CPF, email or birth date | Build 4 | The order-create schema has no other personal field | Q-51 a, Q-68 a |
| P-2 | Privacy notice at checkout: a short pt-BR text with a link to the full notice, shown before the order is placed | Build 4 | Checkout shows the notice; the full text is a restaurant-independent page (wording drafted before the pilot) | Q-52 a |
| P-3 | **(Claude)** Customer name, phone and address on an order are anonymized 12 months after the order's `businessDate`. Totals, lines, status history and payment records stay | Job built in Build 3 | A scheduled job replaces the three fields with a fixed marker; an IT test with a `MutableClock` proves it; analytics totals do not change | Q-52 a |
| P-4 | **(Claude)** Public tracking by number + phone works only while the order is open and for 7 days after it ends; the ULID link follows the same window | Build 4 | Lookup outside the window returns 404, rate-limited like any miss | Q-69 a |
| P-5 | **(Claude)** Audit events are kept 2 years, then deleted | Job built in Build 1 | Job + IT test as in P-3 | Q-52 a; rule 62 |
| P-6 | **(Claude)** Sign-in records (time, IP) are kept at least 6 months (Marco Civil da Internet, Art. 15) and at most 12 months | Build 1 | The existing token cleanup keeps rows for 6 months after expiry, then deletes them | Claude |
| P-7 | **(Claude)** Application logs carry ids, not personal data: no customer phone, address or name, no tokens or passwords; logs kept 30 days once hosted | Build 1 (log rule), Later (retention) | A log-capture test on order create finds no phone or name | Claude |
| P-8 | Data-subject requests (access, correction, deletion) are handled by hand: the platform admin receives them by email and answers within 15 days; no self-service export or delete button | Process, before pilot | Documented in the privacy notice | Proposal F6; LGPD Art. 19 |
| P-9 | Platform admin full read access is for support only | Build 1 | As T&I defines; **(Claude)** an audit event per platform-admin read of a restaurant is a pre-pilot candidate (Q-18 b chose full read, not logged) | Q-18 b |

## Security
| # | Requirement | Build | Source |
|---|---|---|---|
| S-1 | Every restaurant-owned row is scoped and fails closed; one cross-tenant test per endpoint; ArchUnit tenant rule | Build 1+ | ADR-0001; Build 1 acceptance |
| S-2 | `/public/*` never reuses an admin-scoped query; a leak test per public endpoint; public lookups rate-limited | Build 1+ | LR-4; Q-69 a |
| S-3 | Access token 15 min in memory; refresh cookie HttpOnly, `Secure` (only local dev opts out), 30-day sliding; revoked on deactivation and role or membership change | Build 1 | T&I (Reviewed 2026-10-07); Q-29 a; D12 |
| S-4 | Secrets only in environment variables, never in git; `.env.example` lists the names | Exists | CLAUDE.md |
| S-5 | Authorization only in `@PreAuthorize` | Exists | D2 |
| S-6 | The app never processes card data or calls a bank; PIX keys are validated by format only | Build 4 | ADR-0005, ADR-0020 |
| S-7 | Repos are public: no restaurant, customer or order data, secrets or IPs in issues, commits or seed data | Exists | ADR-0021 |

## Reliability and offline
| # | Requirement | Build | Source |
|---|---|---|---|
| R-1 | Order create is idempotent on the client-generated id; retries never duplicate an order | Build 3 | Q-38 a, ADR-0004 |
| R-2 | Connection lost: the app says so, keeps unsent orders on the device and retries; nothing is shown as sent before the server confirms | Build 3 | Q-60 a, ADR-0004 |
| R-3 | KDS and tracking refresh by polling (KDS every 5 s today) | Build 3-4 | Q-61 a |
| R-4 | List endpoints are paginated with a capped page size | Build 1+ | Q-36 a; API-CONVENTIONS |
| R-5 | No availability target this run (no production) | Later | Q-23 b |

## Observability
| # | Requirement | Build | Source |
|---|---|---|---|
| O-1 | The restaurant id and a request id are in every log line of a request | Build 1 | Build 1 acceptance |
| O-2 | `org.springframework.security` logs at `INFO` outside local dev (today `DEBUG`: `src/main/resources/application.yml` L93) | Build 1 | superseded-docs §5 |
| O-3 | Error tracking (a hosted service) | Pre-pilot | MASTER-PLAN §5 |

## Usability and i18n
- pt-BR is the default, EN kept; every money value goes through `formatCurrency` in BRL; the API returns codes, not localized text (Q-21 a, Q-39 a).
- Storefront and tracking work on current mobile Chrome and Safari; back office on current desktop browsers **(Claude)**.

## Pre-pilot checklist (not built this run)
Moved here from `planning/superseded-docs.md` §5 and the settings proposal. Each is done before the first pilot restaurant (Q-32 a marks the same moment for API stability).
- Hosting and domain chosen; app and API on the same registrable domain (Q-79 a); `CORS_ALLOWED_ORIGINS` set to the real origins.
- Behind a proxy: `SERVER_FORWARD_HEADERS_STRATEGY=native` and trusted `server.tomcat.remoteip.internal-proxies`.
- Daily PostgreSQL backups off the host, kept 30 days, with one restore tested **(Claude)**; object storage (images) versioned or backed up the same way.
- Error tracking (O-3) and log retention (P-7).
- `SUPER_ADMIN_EMAIL` set per environment; the seeded super-admin password rotated; the verification user `phase5-verify-test-delete-me@example.com` deleted.
- The rate limiter is in-memory per instance: a shared store before running more than one instance.
- Real 2FA (TOTP), first for the platform admin, then owners (proposal F2).
- Terms of service and the full privacy notice (P-2, P-8), checked by a lawyer.
- From then on: semver, changelog, deprecation rules, no more Flyway re-baseline (Q-32 a).

## Open questions
- The **(Claude)** rows above are proposals; Gabriel accepts or changes them by reviewing this page. Blocks: Build 1 (P-5, P-6, P-7) and Build 3 (P-3).

## Sources
- MASTER-PLAN §5 ("Short NFR page"); `planning/superseded-docs.md` §5; register answers cited above; LGPD (Lei 13.709/2018) Art. 19; Marco Civil da Internet (Lei 12.965/2014) Art. 15.
