# ADR-0019: Status history and in-process events; outbox and core-module split deferred
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel (accepted Claude's proposal; Q-03 rows 3.5-3.6, 2026-09-29)
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

## Consequences
- Agents must route every order status change through the Order Core application service, which writes history and publishes the event.
- Controllers and storefront/public adapters stay thin and call the application service (architecture §23).
- In-process events are not durable: an event is lost if the process stops after commit. Anything that must not be lost is written in the transaction (history row) or waits for the outbox ADR.
- What replaces the `NotificationService` stub and its `KAFKA_HOOK` comment is settled in the Order Core contract; a listener is the extension point.
- The ADR-0009 baseline adds the missing foreign key from status history to orders.
- A rule "domain/application import no web types" (ArchUnit) lands with the first code it guards (MASTER-PLAN §4 L64).
- Agents must never:
  - add Kafka, a broker, an outbox or an event store this run;
  - put order rules in controllers or in channel-specific services (the `StorefrontService.createOrder()` anti-pattern, architecture L298);
  - import web types into `domain` or `application`;
  - create a separate Gradle core module before the Edge build is scheduled.

## Open questions
- Q-03 rows 3.5-3.6 (these §4 simplifications): answered 2026-09-29, accepted.
- Event names and payloads, order status set and casing: Order Core contract (MASTER-PLAN §8c L118). Register: Q-64, Q-47, Q-34.
- Whether the KDS and track-order keep polling or get server push: Order Core contract. Register: Q-61.
- What counts as "an external consumer is scheduled" (the outbox trigger) and the Edge build criteria: MASTER-PLAN §8d (L120). Register: Q-80.
- Reviewer note: MASTER-PLAN §8a Q3 (L114) names "docs folder, Plane off the critical path, Edge reduced to a constraints ADR, packages deferred" but not the outbox or core-module deferrals (§4 L61-62). The register should make sure Gabriel's Q-03 answer explicitly covers these rows, or ask them separately. Addressed: the register's Q-03 lists all twelve §4 rows; these are rows 3.5 and 3.6 (PF `planning/open-questions.md`).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §3 D-3 (L36), D-6 (L39), §4 outbox row (L61), core-module row (L62), ArchUnit row (L64), §8a Q3 (L114), §8c (L118), §8d (L120)
- `/mnt/project-files/architecture/RESTAURANT-OPS-ARCHITECTURE.md` §14 (L206-215), §23 (L294-298)
- `/mnt/project-files/planning/pm-tool-recommendation.md` L113, L115, L125
- Backend `enterprise-order-suite` (`feature/ai-agent` @ `af2634e`): `settings.gradle`; `CLAUDE.md` L67, L83; `orders/application/service/OrderService.java` L72-73, L164-165, L256; `orders/application/service/NotificationService.java` L13-31; `db/migration/V12__create_order_history_table.sql` L1-12
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `src/features/kds/constants/kds.constants.ts` L3; `src/features/track-order/hooks/useTrackOrder.ts` L23
