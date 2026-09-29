# ADR-0014: Bounded owner customization through a typed, backend-owned settings schema
- Status: Accepted
- Date: 2026-09-29
- Decided by: Gabriel
- Supersedes: the open-ended "Restaurant-customizable" list in architecture §9 (`architecture/RESTAURANT-OPS-ARCHITECTURE.md` L171), through the architecture amendment (MASTER-PLAN §5 L73); restaurant settings stored in the owner's browser (`localStorage['preferences']`)
- Related: ADR-0001, ADR-0002, ADR-0007, ADR-0010, ADR-0018

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

## Consequences
- Restaurant-level settings move from `localStorage` to the backend settings schema when their feature is wired to the backend (order per ADR-0007).
- Per-user UI preferences (theme, font size, density, motion, language, sidebar) stay client-side. They are not restaurant settings.
- Font and layout preset are closed value sets in the schema; the frontend maps each value to its own implementation.
- Colors are stored as validated color values, never as CSS text.
- Settings hold logo and cover as URLs or storage keys, never data URLs. Whether and when an upload endpoint is built this run is Gabriel's call (Q-44); if it is, it validates on the server and reuses the object-storage pattern.
- Agents must never:
  - add a free-text styling field, CSS override, HTML block or script slot;
  - render owner-entered text as HTML;
  - store restaurant settings in browser storage, or let a public page read them from the viewer's device;
  - add a customization option that no Reviewed spec lists.

## Open questions
- Exact customization per area: MASTER-PLAN §8c (L118) "exact owner customization per area". Register: Q-66.
- Logo/cover upload endpoint (manifest `settings-images`): MASTER-PLAN §8c "logo/cover upload". Register: Q-44.
- Which `PreferencesState` fields become restaurant settings (timezone as an IANA zone per restaurant, currency, date format) and which stay per user: Tenancy & Identity contract, with the "Brazil only for now (BRL, pt-BR, IANA timezone per restaurant)?" question (MASTER-PLAN §8b L116). Register: Q-27, Q-21.
- How many brand colors, any contrast rule, the font list and the layout presets: Storefront spec (Q-66).

## Sources
- `/mnt/project-files/planning/MASTER-PLAN.md` §2 (L22, L24), §3 D-12 (L45), §5 amendment (L73), §8b (L116), §8c (L118)
- `/mnt/project-files/architecture/RESTAURANT-OPS-ARCHITECTURE.md` §9 (L167-171)
- `/mnt/project-files/2026-09-14-backend-integration-manifest.openapi.yaml` `settings-images` L997-999
- Backend `enterprise-order-suite/CLAUDE.md` L87 (`feature/ai-agent` @ `af2634e`)
- Frontend `order-ui` (`Claude-Assisted-Development` @ `14a3cfd`): `src/types/preferences.ts` L6-22; `src/features/preferences/services/preferences.service.ts` L7, L17; `src/features/storefront/components/StorefrontFeature.tsx` L22-24; `src/features/preferences/components/StorefrontSection.tsx` L19-20, L90, L107, L118
