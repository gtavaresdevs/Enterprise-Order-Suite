# ADR-0014: Bounded owner customization through a typed, backend-owned settings schema
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel. Decision details of 2026-10-01: his register answers (Q-21, Q-27, Q-44, Q-66, Q-67); the PIX key setting is part of ADR-0020 (Accepted 2026-10-06)
- Supersedes: the open-ended "Restaurant-customizable" list in architecture §9 (`architecture/RESTAURANT-OPS-ARCHITECTURE.md` L171), through the architecture amendment (MASTER-PLAN §5 L73); restaurant settings stored in the owner's browser (`localStorage['preferences']`)
- Related: ADR-0001, ADR-0002, ADR-0007, ADR-0010, ADR-0018, ADR-0020

## Context
- D-12 (MASTER-PLAN L45), worked out by Gabriel and Claude in conversation: owner customization is bounded (logo, cover, brand colors, a font from a list, a layout preset); no custom CSS/HTML; settings are a typed, backend-owned schema; what each area allows is decided in its spec; menu config builds on phases 1-3.
- Architecture §9 (L171) lists as restaurant-customizable: brand name, logo, cover image, colors, typography, storefront layout, menu presentation, product images, descriptions, categories, promotional content.
- Frontend today (`Claude-Assisted-Development` @ `14a3cfd`):
  - `PreferencesState` (`order-ui/src/types/preferences.ts` L6-22) mixes per-user UI preferences (theme, fontSize, compactMode, denseTable, reducedMotion, language, sidebarNavigation) with restaurant data (timezone, dateFormat, currency, storefrontLogo, storefrontCover, storefrontBrandColor, whatsappNumber, deliveryZones).
  - It is saved in `localStorage` (`src/features/preferences/services/preferences.service.ts` L7, L17).
  - The storefront reads branding and zones from the viewer's own preferences (`src/features/storefront/components/StorefrontFeature.tsx` L22-24), so customers on another device never see them (MASTER-PLAN §2 L24).
  - Brand color is a free `<input type="color">` (`src/features/preferences/components/StorefrontSection.tsx` L118); logo and cover come from file inputs as data URLs (L19-20, L90, L107).
- Manifest 0.4.0 `settings-images` is open: logo/cover need an upload endpoint before `/restaurant-settings` can return real URLs (`order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` L997-999). The backend already has S3-compatible object storage and an avatar upload pipeline (`enterprise-order-suite/CLAUDE.md` L87).
- Menu configuration exists from frontend phases 1-3: categories, items with sizes/add-ons, the 86 toggle (MASTER-PLAN §2 L22).

## Decision
Accepted (D-12).
- Owners customize the storefront only through bounded choices: logo, cover image, brand colors, one font from a fixed list, one layout preset from a fixed list.
- No custom CSS, HTML, JavaScript or other free-form markup, anywhere.
- Restaurant settings are a typed schema owned by the backend and published in the contract (ADR-0010). They are stored per restaurant, validated on the server, and read by public pages from the backend.
- What each area allows beyond this list is decided in that area's spec (Storefront, Menu, Order Core).
- Menu configuration builds on frontend phases 1-3; it is not restarted.
- Decided later (Gabriel, 2026-10-01; register answers "Q21 - a", "Q27- Recommended", "Q33  - Q64- Recommended", "Q66-69 - recommended"):
  - Q-66 a: exactly what an owner customizes this run. Storefront: logo, cover, one brand color (text contrast computed), one font from a list of about five, one of two or three layout presets. Menu: category order and item photos. Nothing else this run.
  - Q-44 a: images are uploaded this run for menu items, logo and cover, through one upload pattern that reuses the avatar pipeline (object storage, WebP). This closes the manifest's `settings-images`.
  - Q-27 a: restaurant settings are the IANA timezone, currency, storefront default language, branding, WhatsApp number, delivery zones and the ordering settings (Q-67). Theme, font size, sidebar, dense tables and UI language belong to the user or device.
  - Q-67 a: the ordering settings, each one typed field: store name, weekly opening hours with an open/closed switch, pickup on/off, delivery on/off, minimum delivery order, accepted payment methods, pickup ETA.
  - Q-21 a: Brazil only for now: BRL, pt-BR default (EN kept), Brazilian address, +55 phone numbers, an IANA timezone per restaurant; fields stay extensible.
- Accepted with ADR-0020 (Gabriel, 2026-10-06; proposed by Claude under his Q-65 delegation, 2026-10-01): the restaurant's PIX key (and its type) is one more typed restaurant setting, used to generate the storefront's static PIX code for prepayment. The code also carries a merchant name and city; ADR-0020 says where they come from and which key types are accepted (Claude's decisions under Gabriel's 2026-10-06 delegation).

## Consequences
- Restaurant-level settings move from `localStorage` to the backend settings schema when their feature is wired to the backend (order per ADR-0007).
- Per-user UI preferences (theme, font size, density, motion, language, sidebar) stay client-side. They are not restaurant settings (Q-27 a).
- Font and layout preset are closed value sets in the schema; the frontend maps each value to its own implementation.
- Colors are stored as validated color values, never as CSS text.
- Settings hold logo and cover as URLs or storage keys, never data URLs. The upload endpoint is built this run (Q-44 a, 2026-10-01): it validates on the server and reuses the avatar pipeline (object storage, WebP), for logo, cover and menu item photos.
- The PIX key (ADR-0020) is restaurant data like any other setting: stored per restaurant, validated on the server, never kept in browser storage. It is Build 4 work (ADR-0007, Q-30 a).
- Agents must never:
  - add a free-text styling field, CSS override, HTML block or script slot;
  - render owner-entered text as HTML;
  - store restaurant settings in browser storage, or let a public page read them from the viewer's device;
  - add a customization option that no Reviewed spec lists.

## Open questions
- Q-66: answered 2026-10-01, a: the bounded list in Decision. The exact font list and the layout presets are named in the Storefront contract.
- Q-44: answered 2026-10-01, a: one upload pattern for menu item photos, logo and cover, reusing the avatar pipeline.
- Q-27: answered 2026-10-01, a: the restaurant/user settings split in Decision. Q-21: answered 2026-10-01, a: Brazil only for now. The Tenancy & Identity contract records the schema detail (for example whether date format is a restaurant setting).
- Q-67: answered 2026-10-01, a: all seven ordering settings become restaurant settings.
- Q-30: answered 2026-10-06, a: Build 1 creates the restaurant, the typed settings schema and the timezone; branding, ordering settings and delivery zones land in Build 4.
- Q-24: answered 2026-10-01 with none of the options. Gabriel: "Q24 - none of the options, they were added there as a mock from AI no planning, no goal, not setup for it, so we should rethink those to repurpose them for our app, because I feel like they dont really make sense. We should still have notification and settings, but it should be more in tune with out app build. So please think about this in our app context and provide the needed." Claude's proposal for settings and notification screens that fit this app: `docs/planning/proposals/settings-and-notifications.md`, Reviewed by Gabriel on 2026-10-06. Nothing is built from it before he reviews it.
- PIX key setting: ADR-0020 (Accepted 2026-10-06).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §2 (L22, L24), §3 D-12 (L45), §5 amendment (L73), §8b (L116), §8c (L118)
- `planning/open-questions.md` Q-21, Q-24, Q-27, Q-30, Q-44, Q-66, Q-67: Gabriel's answers of 2026-10-01 (project thread, 2026-10-01T16:25Z); Q-30 on 2026-10-06 (project thread, 18:12Z); ADR-0020
- `/mnt/project-files/architecture/RESTAURANT-OPS-ARCHITECTURE.md` §9 (L167-171)
- `/mnt/project-files/2026-09-14-backend-integration-manifest.openapi.yaml` `settings-images` L997-999
- Backend `enterprise-order-suite/CLAUDE.md` L87 (`feature/ai-agent` @ `af2634e`)
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `src/types/preferences.ts` L6-22; `src/features/preferences/services/preferences.service.ts` L7, L17; `src/features/storefront/components/StorefrontFeature.tsx` L22-24; `src/features/preferences/components/StorefrontSection.tsx` L19-20, L90, L107, L118
