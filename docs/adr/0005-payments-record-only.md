# ADR-0005: Payments are record-only; integrate, never build
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes:
  - Legacy (PF `2026-09-09-restaurant-ops-redesign-design.md`) "Payment: required in-app (card/PIX) before confirmation for Online orders" (L70-72) and backend gap #5 "Payment processor integration" (L302).
  - The manifest's plan for a payment-processor checkout (PF `2026-09-14-backend-integration-manifest.openapi.yaml` L24-28, L976). Its `payment-sequencing` open decision (L994-996) is reframed, not closed; see Open questions.
  - Architecture §12 future "Payment Device Gateway" (L200). The amendment proposal applies the text change.
- Related: ADR-0002, ADR-0003, ADR-0004, ADR-0006, ADR-0007

## Context
- Frontend (phase 8, mock only):
  - A PIX/Card/Cash picker (FE `src/components/payment/PaymentMethodPicker.tsx` L15).
  - The storefront PIX step is a self-confirming mock (FE `src/features/storefront/components/StorefrontFeature.tsx` L155; business rule 39, PF `2026-09-16-business-rules-master-en.md` L72). No card details are collected (rule 40, L73).
  - `paymentStatus` is `Paid | Pending | PayLater`, separate from order status (FE `src/types/orders.ts` L4-5; rule 14, L33).
  - The storefront sets `PayLater` for cash and `Paid` otherwise (FE `src/features/storefront/hooks/useStorefront.ts` L75).
  - The staff modal hardcodes `PayLater` (FE `src/features/orders/components/CreateOrderModal.tsx` L76; rule 15, L34, a known bug in PF `planning/MASTER-PLAN.md` §2 L28).
- Backend: no payment concept exists (no match for "payment" under BE `src/main/java`).
- Architecture (status Proposed):
  - Payment authorization must never be shown as successful when it is not; an unreachable external system means `PENDING` (§17 L233-235).
  - Payments are listed as extension points not to build yet (§21 L271).
- Roadmap: in-app card payment was "Blocked" on a PSP (PF `RESTAURANT-OPS-ROADMAP.md` L54).
- 2026-09-29 (PF `planning/MASTER-PLAN.md` §3 D-5, L38): card terminals on 4G/5G authorize on their own. The app records the result. It integrates with existing payment, fiscal and marketplace systems and never builds them.

## Decision
1. The app **never processes, authorizes, captures, refunds or queues payment transactions**, online or offline. Card terminals authorize on their own.
2. The app **records** the payment result against the order. Payment status stays separate from order status.
3. **Integrate, never build.** When an integration with an existing payment, fiscal or marketplace system is scheduled, the app integrates with that system; it never builds one of those systems. This run integrates none of them (ADR-0006; Out list in ADR-0007).
4. Payment state in the app reflects only a recorded result. Nothing marks a payment as successful on its own (architecture §17 L235).

## Consequences
- Never: add a PSP or acquirer SDK or client, generate dynamic PIX charges, collect or store card data, or build a payment or transaction queue.
- Never: build refunds, reconciliation, fiscal document emission (NFC-e, SAT) or a marketplace order platform.
- Never: wire the self-confirming `PixWaitingMock` (FE `StorefrontFeature.tsx` L155) to the real backend as a payment confirmation. What replaces it follows from the storefront payment question below.
- Must: model payment as a record attached to the order, through the same Order Core as every other channel (ADR-0002). Money values are server-derived (D-16).
- Must: when a later ADR adds an integration, the integration writes records through the core. An unreachable external system yields a pending state, never a faked success.
- Must: remove "Payment Device Gateway" from the Edge component list (architecture amendment proposal; PF `planning/MASTER-PLAN.md` §5 L73).
- Fix the known `PayLater` bug (rule 15) in the Order Core contract or the audit triage (S5), not as a payment feature.

## Open questions
Payment topic of the Order Core / Storefront batch (PF `planning/MASTER-PLAN.md` §8c L118). Ids from the register, PF `planning/open-questions.md`.
- How do online storefront customers pay this run? For example: pay on delivery or pickup (terminal, cash), or a static PIX key with manual confirmation. Q-65.
- What does a payment record hold (method, amount, provider, authorization code/NSU, who recorded it, when)? When does `paymentStatus` become paid? Q-58, Q-55.
- Does the `Paid | Pending | PayLater` value set stay? Q-58.
- What is the payment status when a paid order is cancelled (business rule 48, PF `2026-09-16-business-rules-master-en.md` L81)? Q-56.
- Does reading results from a provider API or webhook count as an integration? If so, it is Out this run (ADR-0006). This is inferred and needs Gabriel's confirmation (Q-55 option c).
- The manifest's `payment-sequencing` (L994-996) closes when these are answered.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`); FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §2 (L28), §3 D-5 (L38), D-16 (L49), §5 (L73), §8c (L118)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §12 (L200), §17 (L233-235), §21 (L271)
- PF `2026-09-09-restaurant-ops-redesign-design.md` L70-72, L302
- PF `2026-09-14-backend-integration-manifest.openapi.yaml` L24-28, L976, L994-996
- PF `2026-09-16-business-rules-master-en.md` rules 14-15 (L33-34), 39-40 (L72-73), 48 (L81)
- PF `RESTAURANT-OPS-ROADMAP.md` L54
- BE `src/main/java` (no payment types; grep, 2026-09-29)
- FE `src/components/payment/PaymentMethodPicker.tsx` L15; `src/features/storefront/components/StorefrontFeature.tsx` L155; `src/features/storefront/hooks/useStorefront.ts` L75; `src/features/orders/components/CreateOrderModal.tsx` L76; `src/types/orders.ts` L4-5
