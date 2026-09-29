# ADR-0006: This run improves existing features; PSP and automatic WhatsApp are out
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes:
  - Legacy (PF `2026-09-09-restaurant-ops-redesign-design.md`) "WhatsApp notifications: every order status change sends the customer a WhatsApp message" (L73-76), and backend gaps #5 (payment processor) and #6 (WhatsApp integration) (L302-303) for this run.
  - The frontend roadmap's "Blocked" status for in-app card payment and automatic WhatsApp (PF `RESTAURANT-OPS-ROADMAP.md` L54-55, L213-218). Both are now Out for this run, not waiting on a dependency.
- Related: ADR-0005, ADR-0007, ADR-0014, ADR-0019

## Context
- Gabriel's goal for this run is to improve existing features, with the payment gateway and automatic WhatsApp out (PF `planning/pm-tool-recommendation.md` L60; PF `planning/MASTER-PLAN.md` §3 D-6, L39). What exists is listed in MASTER-PLAN §2 (L18-28).
- The legacy concept planned in-app payment and a WhatsApp message on every status change, both as provider integrations (PF `2026-09-09-restaurant-ops-redesign-design.md` L70-76, L302-303).
- Phase 9 shipped the manual substitute:
  - `wa.me` deep links with digit-normalized phone and URL-encoded text (FE `src/utils/whatsapp.ts` L1-8).
  - Wired to the staff "Contact Customer" button in `OrderDrawer` and a KDS "Notify Customer" button (PF `RESTAURANT-OPS-ROADMAP.md` L192-196).
  - The customer-facing "Continuar no WhatsApp" link is missing (business rule 56, PF `2026-09-16-business-rules-master-en.md` L92).
  - The WhatsApp number lives in the owner's `localStorage` (FE `src/types/preferences.ts` L20).
- The backend's order `NotificationService` only logs and sleeps, with a `KAFKA_HOOK` comment (BE `src/main/java/com/enterprise/ordersuite/orders/application/service/NotificationService.java` L13-30).
- The in-app Notifications feature is still B2B demo content (business rule 88, L148; PF `planning/MASTER-PLAN.md` §2 L28).

## Decision
1. This run **improves existing features**. It adds no payment, messaging, fiscal or marketplace integration: PSP and automatic WhatsApp per D-6; fiscal, marketplaces and billing per the MASTER-PLAN §6 Out list (L95). Supporting services (object storage, email, and the pre-pilot backups and error tracking of MASTER-PLAN §5 L77) are not covered by this ADR.
2. **Out this run:**
   - A PSP or payment gateway (in-app charging of card or PIX).
   - Automatic WhatsApp messages through any provider (Meta Cloud API, Twilio-equivalent, others).
3. **Stays:** manual `wa.me` links that a person taps.
4. "Out" means not planned, designed or built this run. Bringing either item back needs a new ADR decided by Gabriel.
5. The run's full Later/Out lists are in ADR-0007 (fiscal, marketplaces and billing are also Out there).

## Consequences
- Never: add plans, contract endpoints, schemas, SDK dependencies, secrets or configuration for a PSP or a WhatsApp provider this run.
- Never: turn order status changes, or the in-process events of ADR-0019, into outbound messages this run.
- Never: build automatic WhatsApp on the backend `NotificationService` stub.
- Must: keep `wa.me` links working when the WhatsApp number moves from `localStorage` into backend restaurant settings (ADR-0001, ADR-0014).
- Must: take the storefront payment step without a PSP, per ADR-0005.
- The roadmap's two "Blocked" rows are marked Out, with this ADR as the reason (superseded-docs list, S2 banners).

## Open questions
S5 audit triage and scope-of-run page (PF `planning/MASTER-PLAN.md` §5 L76, §6 L92). Ids from the register, PF `planning/open-questions.md`.
- Is the customer-facing "Continuar no WhatsApp" link (business rule 56) in this run? Q-70.
- What is the scope of the in-app Notifications feature (business rules 88-90, PF `2026-09-16-business-rules-master-en.md` L148-150)? Q-57.
- How do storefront customers pay without a PSP (ADR-0005; MASTER-PLAN §8c L118)? Q-65.

## Sources
Legend: PF = `/mnt/project-files/`; BE = backend repo `enterprise-order-suite` @ `feature/ai-agent` (`af2634e`); FE = frontend repo `enterprise-order-suite-frontend/order-ui` @ `Claude-Assisted-Development` (`14a3cfd`).
- PF `planning/MASTER-PLAN.md` §2 (L18-28), §3 D-6 (L39), §5 (L76), §6 (L92), §8c (L118)
- PF `planning/pm-tool-recommendation.md` L60
- PF `2026-09-09-restaurant-ops-redesign-design.md` L70-76, L302-303
- PF `RESTAURANT-OPS-ROADMAP.md` L54-55, L192-196, L209-221
- PF `2026-09-16-business-rules-master-en.md` rules 56 (L92), 88-90 (L148-150)
- BE `src/main/java/com/enterprise/ordersuite/orders/application/service/NotificationService.java` L13-30
- FE `src/utils/whatsapp.ts` L1-8; `src/types/preferences.ts` L20
