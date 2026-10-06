# ADR-0005: Payments are record-only; integrate, never build
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel. Decision 5 (payment record, cancel, storefront payment): his register answers of 2026-10-01
- Supersedes:
  - Legacy (PF `2026-09-09-restaurant-ops-redesign-design.md`) "Payment: required in-app (card/PIX) before confirmation for Online orders" (L70-72) and backend gap #5 "Payment processor integration" (L302).
  - The manifest's plan for a payment-processor checkout (PF `2026-09-14-backend-integration-manifest.openapi.yaml` L24-28, L976). Its `payment-sequencing` open decision (L994-996) is closed by Q-55, Q-58 and Q-65 (2026-10-01); see Open questions.
  - Architecture §12 future "Payment Device Gateway" (L200). Amendment A-08 removed it, applied 2026-10-01 (Q-04).
- Related: ADR-0002, ADR-0003, ADR-0004, ADR-0006, ADR-0007, ADR-0020

## Context
- Frontend (phase 8, mock only):
  - A PIX/Card/Cash picker (FE `src/components/payment/PaymentMethodPicker.tsx` L15).
  - The storefront PIX step is a self-confirming mock (FE `src/features/storefront/components/StorefrontFeature.tsx` L155; business rule 39, PF `2026-09-16-business-rules-master-en.md` L72). No card details are collected (rule 40, L73).
  - `paymentStatus` is `Paid | Pending | PayLater`, separate from order status (FE `src/types/orders.ts` L4-5; rule 14, L33).
  - The storefront sets `PayLater` for cash and `Paid` otherwise (FE `src/features/storefront/hooks/useStorefront.ts` L75).
  - The staff modal hardcodes `PayLater` (FE `src/features/orders/components/CreateOrderModal.tsx` L76; rule 15, L34, a known bug in PF `planning/MASTER-PLAN.md` §2 L28).
- Backend: no payment concept exists (no match for "payment" under BE `src/main/java`).
- Architecture (status Proposed; Accepted with amendments on 2026-10-01, Q-04):
  - Payment authorization must never be shown as successful when it is not; an unreachable external system means `PENDING` (§17 L233-235).
  - Payments are listed as extension points not to build yet (§21 L271).
- Roadmap: in-app card payment was "Blocked" on a PSP (PF `RESTAURANT-OPS-ROADMAP.md` L54).
- 2026-09-29 (PF `planning/MASTER-PLAN.md` §3 D-5, L38): card terminals on 4G/5G authorize on their own. The app records the result. It integrates with existing payment, fiscal and marketplace systems and never builds them.

## Decision
1. The app **never processes, authorizes, captures, refunds or queues payment transactions**, online or offline. Card terminals authorize on their own.
2. The app **records** the payment result against the order. Payment status stays separate from order status.
3. **Integrate, never build.** When an integration with an existing payment, fiscal or marketplace system is scheduled, the app integrates with that system; it never builds one of those systems. This run integrates none of them (ADR-0006; Out list in ADR-0007).
4. Payment state in the app reflects only a recorded result. Nothing marks a payment as successful on its own (architecture §17 L235).
5. Decided later (Gabriel's register answers, 2026-10-01; the Order Core and Storefront contracts record the detail):
   - Q-58 a: a Payment record {id, orderId, method PIX/CARD/CASH, amount, recordedBy, recordedAt, optional provider and authorization code/NSU}; several per order. The order's `paymentStatus` is UNPAID, PAID or REFUNDED, replacing `Paid | Pending | PayLater`.
   - Q-55 a: an order becomes PAID when staff record the payment (method, amount); manual entry is the only way this run. Reading results from a provider API or webhook (option c) is an integration and stays Out this run (ADR-0006).
   - Q-56 a: a paid order can be cancelled; staff record the refund made outside the app, and the payment status becomes REFUNDED. The app records the refund; it never performs one (Decision 1).
   - Q-65 a, plus PIX prepayment: storefront customers pay on delivery or pickup (option a: card terminal or cash; the order starts UNPAID), and PIX is paid in advance at checkout (Gabriel: "payment through pix should be in advanced, if storefront selects pix, we should have a pix payment route that displays the code"). Gabriel delegated how; Claude's proposal is ADR-0020, which Gabriel accepted on 2026-10-06: a static PIX code built from the restaurant's own key, with the exact amount and the order number, and staff confirm by recording the payment. Keeping PIX at the door next to PIX prepayment was also Claude's proposal in ADR-0020; Gabriel confirmed it with the ADR. Online card payment waits for a payment-provider integration later (ADR-0006).

## Consequences
- Never: add a PSP or acquirer SDK or client, generate dynamic PIX charges, collect or store card data, or build a payment or transaction queue.
- Never: build refunds, reconciliation, fiscal document emission (NFC-e, SAT) or a marketplace order platform.
- Never: wire the self-confirming `PixWaitingMock` (FE `StorefrontFeature.tsx` L155) to the real backend as a payment confirmation. Its replacement is ADR-0020 (Accepted 2026-10-06): the customer's "Já paguei" ("I have paid") tap is never proof; only a staff-recorded payment sets PAID.
- ADR-0020 (Accepted 2026-10-06) keeps to this ADR: its static PIX code calls no PSP or bank API, so it is not the dynamic PIX charge forbidden above, and the app still processes nothing.
- Must: model payment as a record attached to the order, through the same Order Core as every other channel (ADR-0002). Money values are server-derived (D-16).
- Must: when a later ADR adds an integration, the integration writes records through the core. An unreachable external system yields a pending state, never a faked success.
- Done: "Payment Device Gateway" is removed from the Edge component list (amendment A-08, applied 2026-10-01, Q-04; PF `planning/MASTER-PLAN.md` §5 L73).
- Fix the known `PayLater` bug (rule 15) in the Order Core contract or the audit triage (S5), not as a payment feature. `PayLater` itself goes away (Q-58 a).

## Open questions
Payment topic of the Order Core / Storefront batch (PF `planning/MASTER-PLAN.md` §8c L118). Ids from the register, PF `planning/open-questions.md`.
- Q-65: answered 2026-10-01, a, plus PIX prepayment; approach delegated to Claude -> ADR-0020 (Accepted 2026-10-06). Gabriel: "a -> but payment through pix should be in advanced, if storefront selects pix, we should have a pix payment route that displays the code, confirmation on both sides (customer and restaurant), and confirms order is placed and paid, payment with card etc in storefront comes later when the app is ready and functional as this involves integrating with payment providers. Not sure if this is an actual good apporach so I will leave you to decide what makes sense now for the app but can be done later once ready."
- Q-58: answered 2026-10-01, a: Payment records, several per order; `paymentStatus` UNPAID, PAID or REFUNDED (`Paid | Pending | PayLater` goes away).
- Q-55: answered 2026-10-01, a: PAID when staff record the payment; manual entry only this run. This also confirms the inference that reading a provider API or webhook (option c) is an integration, Out this run.
- Q-56: answered 2026-10-01, a: cancel allowed; staff record the outside refund; REFUNDED (business rule 48, PF `2026-09-16-business-rules-master-en.md` L81).
- The manifest's `payment-sequencing` (L994-996) is closed by Q-55, Q-58 and Q-65 (2026-10-01).
- Pickup and PIX prepayment: Q-86, the follow-up to Gabriel's partial Q-70 answer, answered 2026-10-06, a: pickup is offered only with PIX prepayment inside a delivery zone (ADR-0020).

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`); FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §2 (L28), §3 D-5 (L38), D-16 (L49), §5 (L73), §8c (L118)
- `planning/open-questions.md` Q-55, Q-56, Q-58, Q-65 (Gabriel's answers of 2026-10-01, project thread, 2026-10-01T16:25Z); Q-86 (answered 2026-10-06, project thread, 18:12Z)
- PF `architecture/RESTAURANT-OPS-ARCHITECTURE.md` §12 (L200), §17 (L233-235), §21 (L271)
- PF `2026-09-09-restaurant-ops-redesign-design.md` L70-72, L302
- PF `2026-09-14-backend-integration-manifest.openapi.yaml` L24-28, L976, L994-996
- PF `2026-09-16-business-rules-master-en.md` rules 14-15 (L33-34), 39-40 (L72-73), 48 (L81)
- PF `RESTAURANT-OPS-ROADMAP.md` L54
- BE `src/main/java` (no payment types; grep, 2026-09-29)
- FE `src/components/payment/PaymentMethodPicker.tsx` L15; `src/features/storefront/components/StorefrontFeature.tsx` L155; `src/features/storefront/hooks/useStorefront.ts` L75; `src/features/orders/components/CreateOrderModal.tsx` L76; `src/types/orders.ts` L4-5
