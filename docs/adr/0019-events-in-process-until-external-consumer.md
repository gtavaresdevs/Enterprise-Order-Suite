# ADR-0019: Status history and in-process events; outbox and core-module split deferred
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (accepted Claude's proposal; Q-03 rows 3.5-3.6, 2026-09-29). Decision 6: his register answers of 2026-10-01
- Supersedes: as this-run work: architecture review gap #1 "one core module deployed twice" (`planning/pm-tool-recommendation.md` L113), gap #3 "event log + outbox with an idempotency key per event" (L115), and revised-order row 3 "domain events + outbox", "Runs where: Core module" (L125)
- Related: ADR-0002, ADR-0003, ADR-0004, ADR-0006, ADR-0007, ADR-0009

## Context
- Architecture §14 (`architecture/RESTAURANT-OPS-ARCHITECTURE.md` L206-215): Edge-cloud sync is asynchronous, idempotent, retryable. §23 (L294-298): `OrderApplicationService.createOrder(...)` holds core order behavior; storefront, waiter, POS and tablet adapters call it.
- pm-tool gap #1 (L113): one plain-Java Order/Menu module deployed in the cloud and on the Edge. Gap #3 (L115): sync replays domain events through an outbox with a per-event idempotency key.
- This run nothing outside the app consumes events:
  - the Edge is not built (D-3, ADR-0003);
  - PSP and automatic WhatsApp are out (D-6, ADR-0006);
  - the KDS reads the same order stream by polling every 5 s (`order-ui/src/features/kds/constants/kds.constants.ts` L3; track-order reuses it, `src/features/track-order/hooks/useTrackOrder.ts` L23);
  - analytics runs in the app.
- Backend today (`feature/ai-agent` @ `af2634e`):
  - `OrderService` writes an `OrderHistory` row per transition (`orders/application/service/OrderService.java` L72, L164, L256) and calls `NotificationService.sendOrderUpdateNotification` (L73, L165).
  - `NotificationService` is an `@Async` method that only logs and sleeps 1 s, with a `KAFKA_HOOK` comment (`orders/application/service/NotificationService.java` L13-31).
  - `order_history` exists (V12) without a foreign key to `orders`.
  - No domain events, outbox or idempotency keys exist.
- One Gradle project: `settings.gradle` holds only `rootProject.name = 'enterprise-order-suite'`. Layering per module is api → application → domain → persistence (`CLAUDE.md` L67).
- MASTER-PLAN §4 (L61, L62) proposes both deferrals.

## Decision
Accepted (Gabriel, 2026-09-29; Q-03 rows 3.5-3.6, "3 Yes"). Proposed by Claude in MASTER-PLAN §4 L61-62.
1. **Status history.** Order Core records every status change as a history row written in the same transaction as the change.
2. **In-process events.** The Order Core application service publishes in-process Spring application events for side effects inside the app. Listeners with side effects run after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`), so a rolled-back change emits nothing.
3. **No outbox this run.** No outbox table, message broker, Kafka, event store or per-event idempotency key. The outbox is added, in its own ADR, when the first external consumer is scheduled (Edge sync, a WhatsApp provider, a marketplace, a payment or fiscal integration).
4. **No core-module split this run.** Order and Menu rules stay in the existing `domain` and `application` packages of their modules, free of web types (no Spring MVC, servlet, controller DTO or `/public` adapter types). A separate Gradle module is extracted when the Edge build is scheduled.
5. Idempotent order creation on the client ULID (ADR-0003, ADR-0004, ADR-0009) does not depend on an outbox.
6. **Decided later (Gabriel, 2026-10-01; "Q33  - Q64- Recommended", and Q-80 below):**
   - Q-64 a: confirms points 1-2. Order events are Spring application events published after commit, written alongside the status history; their names and payloads are defined in the Order Core contract.
   - Q-57 a: the first in-process consumer is a small per-restaurant notifications feed built from order events (new order, cancellation). It replaces the B2B demo content of the Notifications feature. Per-user history and mute settings wait.
   - Q-61 a: the KDS and order tracking keep polling this run (the KDS polls every 5 s). No server push (SSE or WebSocket).
   - Q-47 a: the order status set is NEW, PREPARING, READY, COMPLETED, plus CANCELLED. Q-34 a: enum values on the wire are SCREAMING_SNAKE.
   - Q-80 b: "Q80 - b when I decide, for now we focus only on making sure everything is funcional but keeping in mind we will have edge later and that its optional paid feature". The Edge, and with it the outbox (point 3) and the core-module split (point 4), is built when Gabriel decides, as an optional paid feature. Until then the work makes everything functional and keeps the Edge-ready constraints (ADR-0003).

## Consequences
- Agents must route every order status change through the Order Core application service, which writes history and publishes the event.
- Controllers and storefront/public adapters stay thin and call the application service (architecture §23).
- In-process events are not durable: an event is lost if the process stops after commit. Anything that must not be lost is written in the transaction (history row) or waits for the outbox ADR.
- The `NotificationService` stub and its `KAFKA_HOOK` comment are replaced by an after-commit listener that feeds the per-restaurant notifications feed (Q-57 a). The Order Core contract settles the event names and payloads (Q-64 a).
- The ADR-0009 baseline adds the missing foreign key from status history to orders.
- A rule "domain/application import no web types" (ArchUnit) lands with the first code it guards (MASTER-PLAN §4 L64).
- Agents must never:
  - add Kafka, a broker, an outbox or an event store this run;
  - put order rules in controllers or in channel-specific services (the `StorefrontService.createOrder()` anti-pattern, architecture L298);
  - import web types into `domain` or `application`;
  - create a separate Gradle core module before the Edge build is scheduled.

## Open questions
- Q-03 rows 3.5-3.6 (these §4 simplifications): answered 2026-09-29, accepted.
- Q-64, Q-47, Q-34: answered 2026-10-01, a (Decision 6). Event names and payloads are written in the Order Core contract (MASTER-PLAN §8c L118).
- Q-61: answered 2026-10-01, a: the KDS and track-order keep polling this run (Decision 6).
- Q-57: answered 2026-10-01, a: a small per-restaurant notifications feed from in-process order events (Decision 6). Notification settings and screens: Claude's Q-24 proposal `docs/planning/proposals/settings-and-notifications.md` (Draft), awaiting Gabriel's review.
- Q-80: answered 2026-10-01, b: the Edge build (the outbox trigger for Edge sync) is Gabriel's call, as an optional paid feature (Decision 6). Any other external consumer (a WhatsApp provider, a marketplace, a payment or fiscal integration) is Out this run (ADR-0006, ADR-0007) and brings the outbox ADR with it when scheduled.
- Reviewer note: MASTER-PLAN §8a Q3 (L114) names "docs folder, Plane off the critical path, Edge reduced to a constraints ADR, packages deferred" but not the outbox or core-module deferrals (§4 L61-62). The register should make sure Gabriel's Q-03 answer explicitly covers these rows, or ask them separately. Addressed: the register's Q-03 lists all twelve §4 rows; these are rows 3.5 and 3.6 (PF `planning/open-questions.md`).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-3 (L36), D-6 (L39), §4 outbox row (L61), core-module row (L62), ArchUnit row (L64), §8a Q3 (L114), §8c (L118), §8d (L120)
- `planning/open-questions.md` Q-24, Q-34, Q-47, Q-57, Q-61, Q-64, Q-80: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z)
- `/mnt/project-files/architecture/RESTAURANT-OPS-ARCHITECTURE.md` §14 (L206-215), §23 (L294-298)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L113, L115, L125
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `settings.gradle`; `CLAUDE.md` L67, L83; `orders/application/service/OrderService.java` L72-73, L164-165, L256; `orders/application/service/NotificationService.java` L13-31; `db/migration/V12__create_order_history_table.sql` L1-12
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `src/features/kds/constants/kds.constants.ts` L3; `src/features/track-order/hooks/useTrackOrder.ts` L23
