# Settings and notifications: proposal for this run (Q-24)

- Status: Reviewed
- Updated: 2026-10-06 (Reviewed; open questions answered; Q-30, Q-86 and ADR-0020 settled)
- Reviewed: Reviewed by Gabriel on 2026-10-06 (Gabriel, project thread, 2026-10-06T18:12Z: "4 - confirmed", "confirms all points as per recommended by claude", answering item 4 of Claude's 15:20Z list, which asked him to read and mark Reviewed ADR-0020, this proposal and the board runbook)
- Roadmap step: S5 (feeds the scope-of-run page); the items land in Build 1-4 (`docs/roadmap.md`)
- Related: ADR-0001, ADR-0005, ADR-0006, ADR-0007, ADR-0008, ADR-0014, ADR-0019, ADR-0020 (Accepted 2026-10-06); register Q-16, Q-17, Q-18, Q-19, Q-21, Q-22, Q-24, Q-25, Q-26, Q-27, Q-28, Q-29, Q-30, Q-31, Q-42, Q-44, Q-52, Q-54, Q-57, Q-58, Q-61, Q-64, Q-66, Q-67, Q-81, Q-86

Claude's proposal, written because Gabriel answered Q-24 (2026-10-01, project thread 2026-10-01T16:25Z) with none of the options: "Q24 - none of the options, they were added there as a mock from AI no planning, no goal, not setup for it, so we should rethink those to repurpose them for our app, because I feel like they dont really make sense. We should still have notification and settings, but it should be more in tune with out app build. So please think about this in our app context and provide the needed."

Nothing was to be built from this page before Gabriel reviewed it (ADR-0014 Open questions); he did on 2026-10-06. Rows and verdicts whose Source starts with "Claude" are Claude's proposals under the Q-24 delegation; Gabriel can change any of them at review.

## Goal
- Every restaurant member gets settings and notifications that fit a restaurant ops SaaS:
  - the owner configures what customers see;
  - the owner and manager run the team;
  - each member manages their own account and device;
  - staff hear about new orders without watching the screen.
- Every control on these screens does something real. A control with no effect is removed, not shown (business rule 72).

## Users and surfaces
Roles per Q-17 a: a platform admin role (today's env-seeded root `SUPER_ADMIN`), separate from the per-restaurant roles owner, manager and staff. Accounts are invite-only (Q-19 a).

| Screen (PT-BR label) | Route (today's, kept) | Who | Replaces |
|---|---|---|---|
| Restaurant settings ("Restaurante") | `/settings` | owner; manager for operations (A0) | the mock Security, API keys, Sessions and Danger page; the storefront and delivery-zone sections of `/preferences` |
| Team and audit log ("Equipe", "Auditoria") | `/administration/team`, `/administration/audit-log` | owner, manager | the same screens, scoped to the restaurant; `/administration/roles` folds into Team (B6) |
| My account ("Minha conta") | `/profile` | every member | the profile page, plus password and "sign out of other devices" |
| This device ("Preferências") | `/preferences` | every member | the same page without restaurant data |
| Notifications ("Notificações") | header bell + `/notifications` | every member | the B2B mock feed and the nine mock preference toggles |

The platform admin uses a restaurant-management screen (B1). Customers never see settings screens; they see the restaurant settings on the storefront.

## What exists today
Frontend: `order-ui` @ `c4a7309` (`Claude-Assisted-Development`). Backend: `feature/ai-agent` @ `c8bf761`. Frontend paths starting with `src/` are relative to `order-ui/`; other frontend paths are relative to `order-ui/src/features/`. Backend paths starting with `src/` are relative to the backend repo root; other backend paths are relative to `src/main/java/com/enterprise/ordersuite/`.

### Frontend screens and controls

| # | Screen / control | Files | State |
|---|---|---|---|
| F1 | Settings page itself. Every signed-in user can open it (no `RoleGuard`); `/security` redirects here. Its text is not translated. | `src/app/router.tsx` L114, L145-146; `src/features/settings/` (no `useTranslation`) | Mock |
| F2 | Two-factor authentication switch, on by default | `settings/components/SecurityPanel.tsx` L25-34; `settings/hooks/useSettings.ts` L9 | Mock: local state only, nothing is enforced |
| F3 | Login anomaly alerts switch | `SecurityPanel.tsx` L45-49 | Mock |
| F4 | API keys: list, New Key, Copy, Revoke | `settings/components/ApiPanel.tsx` L30-32, L52-69; `settings/constants/settings.constants.ts` L4-8 | Mock: three fake keys; New Key has no handler; Revoke only drops the key from local state |
| F5 | Active sessions: list, revoke one, "Revoke others" | `settings/components/SessionsPanel.tsx` L22-62; `settings.constants.ts` L10-15 | Mock: four fake sessions (San Francisco, London, Tokyo) |
| F6 | Danger zone: Export Account Data, Reset 2FA, Delete Account | `settings/components/DangerPanel.tsx` L23-29, L41-47, L54-93 | Mock: Request Export, Reset 2FA and Confirm Deletion have no handler ("Delete my account" only opens the typed confirmation). `settings/services/settings.service.ts` is never called. |
| F7 | Header bell feed | `src/layouts/app-layout/NotificationBell.tsx` L14; `notifications/hooks/useNotificationFeed.ts` L4-5; `notifications/constants/notifications.constants.ts` L44-73 | Mock: four B2B items ("Order #10432 shipped", "Procurement approval") |
| F8 | `/notifications`: nine toggles in three B2B groups (Logistics, System, Account), a global mute and Save | `notifications.constants.ts` L14-42; `notifications/hooks/useNotificationPreferences.ts` L4-14, L44-48 | Mock: Save discards the values and a reload resets them (business rules 88-89) |
| F9 | Profile: name, email and role (read-only) | `profile/components/ProfilePersonalInfoCard.tsx` L46-49; `profile/services/profile.service.ts` L9-12 | Works (`GET /me/profile`) |
| F10 | Profile: department, phone, country, timezone, office, bio | `ProfilePersonalInfoCard.tsx` L61-108; `profile.service.ts` L15-18 | Works (`PATCH /me/profile`), but the fields are B2B |
| F11 | Profile: avatar upload | `profile/hooks/useProfile.ts` L177; `profile.service.ts` L25-28 | Mock: resolves after 500 ms and saves nothing (business rule 71), although the backend endpoint exists |
| F12 | Profile account card: "Security: 2FA Enabled", "Account Tier", "USR-0000n" | `profile/components/ProfileAccountInfoCard.tsx` L39-52 | "Security: 2FA Enabled" is hardcoded and false; "Account Tier" shows the legacy global role; "USR-0000n" pads the legacy `Long` id. |
| F13 | Preferences: theme, font size, compact mode, dense rows, reduced motion, sidebar, language | `preferences/components/PreferencesFeature.tsx` L43-115; `preferences/services/preferences.service.ts` L7, L17 | Works, in `localStorage` |
| F14 | Preferences: timezone, date format, currency | `PreferencesFeature.tsx` L82-98 | `localStorage` only. Timezone and currency are restaurant data stored per device (Q-21 a, Q-27 a, Q-28 a); whether date format is a restaurant setting is open (D2). |
| F15 | Preferences: storefront logo, cover, brand color, WhatsApp number | `preferences/components/StorefrontSection.tsx` (WhatsApp L141-142) | `localStorage` only: customers never see them (ADR-0014 Context) |
| F16 | Preferences: delivery zones (bairro, fee, ETA, active) | `preferences/components/DeliveryZonesSection.tsx` L34; `src/types/orders.ts` L8-14 | `localStorage` only |
| F17 | Hardcoded ordering values: pickup ETA 15 min; PIX/Card/Cash picker | `storefront/constants/storefront.constants.ts` L1; `src/components/payment/PaymentMethodPicker.tsx` | Hardcoded; these become ordering settings (Q-67 a) |
| F18 | Team: list, invite (email, name, role, password-setup email), edit, change role, deactivate, reactivate, resend setup | `administration/services/team.service.ts` L15-57; `administration/components/InviteUserModal.tsx` L26, L40-41 | Works on the real backend, but deployment-wide: every user of the deployment. Changes work only for SUPER_ADMIN: the route also admits ADMIN (`src/app/router.tsx` L120), whose every change gets 403 (`AdminUsersController.java` L20-62). |
| F19 | Roles: read-only list of the global legacy roles | `administration/services/roles.service.ts` L7 | Works |
| F20 | Audit log: identity events | `administration/services/auditLog.service.ts` L7 | Works, deployment-wide |
| F21 | New-order alert | none: no sound anywhere; the KDS polls every 5 s (`kds/hooks/useKdsOrders.ts` L13); the Orders screen does not poll | Absent |

### Backend support

| Capability | Where | Relevance |
|---|---|---|
| Refresh-token rotation in families; logout revokes the current family | `auth/service/AuthenticationService.java` L117-126; `auth/domain/RefreshToken.java` L20-37 | Basis for "sign out of other devices" (C5) |
| Revoke every refresh token of a user | `auth/service/RefreshTokenService.java` L61-63, used on password reset (`auth/service/PasswordResetService.java` L192) | Sign out everywhere; deactivation (Q-29 a) |
| Refresh tokens store no device, user agent, IP or last-used time | `RefreshToken.java` L20-37 | A per-device session list would need new columns |
| Access-token lifetime defaults to 24 h | `src/main/resources/application.yml` L40 (`JWT_EXPIRATION_MS`) | Revoking refresh tokens takes full effect only when the access token expires |
| Forgot and reset password, with password history (no reuse) and revoke-all | `auth/controllers/AuthenticationController.java` L75, L82; `PasswordResetService.java` L118-192 | Reused by "change password" (C4) |
| No change-password endpoint for a signed-in user | none | Gap |
| Name edit | `identity/api/MeController.java` L28-33 (`PATCH /me`) | Profile (C1) |
| Profile fields and avatar upload/delete (object storage, WebP) | `profile/api/controller/ProfileController.java` L28-77; `profile/api/dto/UpdateProfileRequest.java` L3-10 | Profile; the same pipeline serves logo, cover and item photos (Q-44 a) |
| User admin: create with password-setup email, role, deactivate, reactivate, update; user list; identity audit | `identity/api/AdminUsersController.java` L20-65 (all `SUPER_ADMIN`); `identity/api/dto/UsersController.java` L20, L29; `identity/api/IdentityAuditController.java` L19 | Team and invite-only (Q-19 a) |
| Public signup | `AuthenticationController.java` L36; FE `src/app/router.tsx` L40 | Removed by Q-19 a |
| Transactional email (password setup, reset) | `notifications/service/EmailService.java` and implementations | Invites; never order-event emails (ADR-0006) |
| Order notification stub: `@Async`, only logs and sleeps | `orders/application/service/NotificationService.java` L13-31 | Replaced by the feed listener (ADR-0019) |
| Restaurant, membership, settings, 2FA, API-key or notification tables | none | none exist |

## In scope
Columns: Build = the build step that delivers the item (ADR-0007). Contract = the contract doc that specifies it: T&I = Tenancy & Identity (S4); OC = Order Core; SF = Storefront; Menu.

### A. Restaurant settings (`/settings`, owner and manager)
Q-30 a (Gabriel, 2026-10-06) confirms the Build column for A2-A5: Build 1 creates the restaurant, the typed settings schema and the timezone; the other settings land in Build 4.

| # | Requirement | Build | Contract | Acceptance criteria (testable) | Source |
|---|---|---|---|---|---|
| A1 | General: restaurant name (store name, editable). Read-only: the slug with the full storefront link and a copy button; the IANA timezone (set at creation, Q-28 a; read-only for the owner is Claude's default; the T&I contract says whether the platform admin can correct it); currency BRL. Storefront default language (editable). | 1 (language: 4) | T&I | Values come from the backend; another restaurant's owner never reads them (cross-tenant test); the slug cannot be changed; the copied link is an absolute URL. | Q-27 a, Q-28 a, Q-31 a, Q-21 a, Q-67 a; business rule 81 |
| A2 | Ordering: an open/closed switch with weekly opening hours, pickup on/off, delivery on/off, minimum delivery order, pickup ETA and accepted payment methods. These replace F17's hardcoded values. | 4 | SF | A change shows on the storefront on another device; the server rejects invalid values. Pickup eligibility follows Q-86 a: pickup only with PIX prepayment inside a delivery zone. | Q-67 a; ADR-0014 |
| A3 | Delivery zones: bairro, fee, ETA, active (today's shape, F16), moved to the backend | 4 | SF | A zone the owner adds appears in another device's checkout. | Q-27 a; ADR-0014 |
| A4 | Storefront appearance: logo and cover (uploaded), one brand color with computed text contrast, one font from a list, one layout preset. WhatsApp number. | 4 | SF | Only storage keys or URLs are stored, never data URLs; there is no free CSS or HTML field; `wa.me` links keep working. | Q-66 a, Q-44 a, Q-27 a; ADR-0014, ADR-0006 |
| A5 | Payments: the PIX key (and its type) plus the merchant name and city for the static PIX code. Owner only. ADR-0020 (Accepted 2026-10-06) says which key types are accepted and where the merchant name and city come from. | 4 | SF | PIX prepayment stays hidden unless PIX is accepted and a key is set; every key change is written to the audit log (B5). | ADR-0020 points 2 and 7 and Consequences |
| A6 | Menu presentation (category order, item photos) stays on the Menu screen, not in Settings. | 2 | Menu | Covered by the Menu contract. | Q-66 a, Q-44 a |

- A0, permission default (Claude; the T&I contract settles it):
  - the owner edits everything;
  - the manager edits A2 and A3 (day-to-day operations);
  - staff have no settings access;
  - the PIX key and the WhatsApp number are owner-only, because they route money and customer messages;
  - the platform admin reads everything (Q-18 b).

### B. Team and access (owner, manager, platform admin)

| # | Requirement | Build | Contract | Acceptance criteria (testable) | Source |
|---|---|---|---|---|---|
| B1 | The platform admin creates a restaurant (name, slug, timezone) and invites its owner by email. This reuses today's create-user and password-setup email. Public signup is removed. | 1 | T&I | `/auth/register` and `/register` are gone; the invited owner sets a password and lands in their restaurant. | Q-19 a, Q-31 a, Q-28 a |
| B2 | Team list scoped to the restaurant: members with role and status. | 1 | T&I | The owner of R1 never sees R2's members (cross-tenant test). | Q-16 a, Q-25 a, Q-26 |
| B3 | Invite a member (email, name, role) through today's invite dialog and password-setup email. The owner invites managers and staff; the manager invites staff. | 1 | T&I | A manager's attempt to invite an owner gets 403. | Claude (Q-24 delegation); Q-17 a; Q-19 a: the owner invites staff; the manager invites staff: Claude default, the T&I contract settles it (A0) |
| B4 | Change role, deactivate, reactivate. Deactivation and role or membership changes revoke the member's refresh tokens. | 1 | T&I | After deactivation the member's next refresh gets 401; the screen says the member's access ends when their current access token expires (Rule 8). | Q-29 a |
| B5 | Restaurant audit log: invites, role changes, deactivations, and changes to the PIX key and WhatsApp number. This is today's screen, scoped to the restaurant. | 1 (settings events: 4) | T&I, SF | Each event shows who, what and when; R2's events never appear in R1. | Claude (Q-24 delegation): the PIX-key and WhatsApp events; Q-25 a (identity audit log in scope) |
| B6 | The Roles page folds into Team: the role descriptions appear in the invite and change-role dialogs. With three fixed roles there is nothing to configure. | 1 | T&I | No `/administration/roles` route; each role has a one-line description in the dialogs. | Claude (Q-24 delegation); Q-17 a |

### C. My account (`/profile`, every member)

| # | Requirement | Build | Contract | Acceptance criteria (testable) | Source |
|---|---|---|---|---|---|
| C1 | Profile: name (editable), phone (optional), email (read-only). Remove department, office, bio, country and timezone (open question 3: yes, 2026-10-06). | 1 | T&I | The name saves through the backend; the removed fields are gone from the API and the UI. | `PATCH /me`; Q-28 a (the timezone belongs to the restaurant); ADR-0008 |
| C2 | Avatar upload made real against `POST /me/profile/avatar`. | 1 | T&I | After a reload the photo is still there. | Business rule 71 |
| C3 | The account card shows restaurant, role and member-since. Remove "2FA Enabled", "Account Tier" and "USR-0000n". | 1 | T&I | No hardcoded security claim remains. | F12; business rule 72 |
| C4 | Change password: current and new password. Same password rules and no-reuse history as reset; on success, the user's other devices are signed out. New endpoint (open question 2: yes, 2026-10-06). | 1 | T&I | A wrong current password is rejected with a SCREAMING_SNAKE error code (D14); a reused password is rejected; the other devices' refresh gets 401. | `PasswordResetService.java` L161-192 (reuse) |
| C5 | "Sign out of other devices": revokes every refresh-token family of the user except the current one. There is no device list. | 1 | T&I | The current session continues; a refresh from another browser gets 401; the screen says the other devices stop when their current access expires. | Claude (Q-24 delegation; replaces F5); mechanism `RefreshTokenService.java` L53-63; Q-29 a covers only revocation on deactivation and role/membership change |

- Forgot password stays on the login page (it exists today).
- The T&I contract sets the access-token lifetime. The 24 h default bounds how fast C4, C5 and B4 take full effect.

### D. This device (`/preferences`, every member)
These preferences stay in browser storage: they belong to the user or device (Q-27 a; ADR-0014 Consequences).

| # | Requirement | Build | Contract | Acceptance criteria (testable) | Source |
|---|---|---|---|---|---|
| D1 | Keep theme, font size, compact mode, dense rows, reduced motion, sidebar and UI language. | 1 (cleanup) | none | Unchanged behavior. | Q-27 a |
| D2 | Remove timezone and currency from this page: they are restaurant data. Date format: the T&I contract decides whether it is a restaurant setting or follows the language. | 1 | T&I | Displayed dates use the restaurant's timezone. | Q-21 a, Q-27 a, Q-28 a; ADR-0014 Open questions |
| D3 | The storefront, WhatsApp and delivery-zone sections move to Restaurant settings when their backend fields exist. Until then they stay as they are. | 4 | SF | No restaurant setting is read from the viewer's device after Build 4. | ADR-0014 Consequences; Q-30 |
| D4 | "New-order sound" switch, on by default, per device (E3). | 3 | none | The switch's value survives a reload on that device only. | Claude (Q-24 delegation); part of E3, not a Q-57 mute setting; device storage per Q-27 a |

### E. Notifications (every member of the restaurant)

| # | Requirement | Build | Contract | Acceptance criteria (testable) | Source |
|---|---|---|---|---|---|
| E1 | Restaurant feed. An after-commit listener on order events writes one entry per event: new order (number, channel, source, total, plus an "unpaid PIX" tag, ADR-0020) and order cancelled (number, who cancelled). Entries hold no customer name, phone or address. Proposed retention: 30 days. | 3 | OC | An order created in R1 shows in R1's feed only; a rolled-back create writes no entry. | Claude (Q-24 delegation): the 30-day retention and the entry fields; Q-57 a, Q-64 a; ADR-0019; Q-52 a (data minimization) |
| E2 | The bell and `/notifications` show the feed, newest first; tapping an entry opens the order. The unread dot counts entries newer than this device's last view, kept in device storage. The feed is polled. It replaces F7 and F8. | 3 | OC | No B2B text remains; entries appear without a page reload. | Q-57 a (per-user read state waits), Q-61 a; business rule 90 |
| E3 | New-order alert on the Orders and KDS screens: when polling returns a NEW order not seen before, play a short sound and highlight the card. Browsers block sound until the page is touched once, so the screen shows an "Ativar som" button until then. Frontend only; the Orders screen gains the KDS polling. | 3 | OC (the order list must make new orders detectable) | With the sound on, a new storefront order is heard within one polling interval. The KDS runs under a staff login. | Claude (Q-24 delegation); F21; constraints: polling (Q-61 a), KDS staff login (Q-54 a) |
| E4 | A "customer says PIX paid" entry from the "Já paguei" tap (ADR-0020; open question 1: yes, 2026-10-06). It is information, not payment state. | 4 | SF | The entry never changes `paymentStatus`. | ADR-0020 point 3, Must |
| E5 | No outbound messages: order events never send email, WhatsApp or push. Transactional auth emails (invite, reset) stay. | all | OC | No email or messaging call in any order-event listener. | ADR-0006 Consequences; ADR-0019 |

## Later and Out
Verdicts for today's mock controls and for nearby ideas. Verdicts are Claude's, in this app's context; Gabriel can change any of them at review.

| Item (today) | Verdict this run | Reason |
|---|---|---|
| 2FA switch (F2) and Reset 2FA (F6) | Remove; Later | Real 2FA (TOTP) needs enrolment, recovery codes, an admin reset path and lockout rules: new scope (ADR-0006 Decision 1). A switch that does nothing is a false promise (business rule 72). Accounts are invite-only, with password history and login rate limits. Proposed for the S5 NFR page as a pre-pilot candidate, first for the platform admin, who reads every restaurant (Q-18 b), then for owners. |
| Login anomaly alerts (F3) | Remove; Later | The backend keeps no device or IP history (`RefreshToken.java` L20-37); storing IPs is personal data to govern (LGPD); outbound alert emails are new scope. The restaurant audit log (B5) covers what an owner needs: who changed the team, the PIX key or the WhatsApp number. |
| API keys (F4) | Remove; Out this run | No external consumer exists (ADR-0019) and integrations are Out (ADR-0006, ADR-0007). Keys come with the first integration's own ADR and auth design. |
| Session list with device, IP and location (F5) | Replace with C5; list Later | C5 gives the useful action now. A list needs new token metadata, which is personal data. |
| Export account data (F6) | Remove | LGPD data-subject requests (access, portability, deletion) are handled as the S5 NFR page defines, not by a self-service button. |
| Delete account (F6) | Remove self-service | Deactivation (B4) removes a staff member and keeps order history, payment records (`recordedBy`, Q-58 a) and the audit trail intact. Deleting the owner account would orphan a restaurant. Closing a restaurant is a platform-admin action. Erasure requests follow the S5 NFR page. |
| Notification category toggles and global mute (F8) | Remove | The categories are B2B (business rule 88); notification mute settings wait (Q-57 a). D4 only turns the E3 sound on or off on one device; it mutes no feed entries. |
| Per-user notification history and read state | Later | Q-57 a |
| Browser or OS desktop notifications, web push | Later | Web push needs a push service and a server channel; this run polls (Q-61 a). Local browser notifications from an open, polling page add a permission prompt and repeat the E3 sound alert. Later (Claude). |
| Email or WhatsApp messages on order events | Out | ADR-0006 |
| Low-stock notifications | Gone | Stock counts are dropped (Q-42 c) |
| Waiter-call and bill-request notifications | Later | They come with comandas (Q-81 a; business rule 88) |
| Owner-editable slug | Out this run | Q-31 a |
| Ownership transfer, several restaurants per user | Later | Q-16 a, Q-22 a |

## Rules
1. Every control on these screens has a real effect on the backend or on the device; a control without one is removed (business rule 72; Claude's proposal answering Gabriel's Q-24 request to "rethink those to repurpose them for our app").
2. Restaurant settings live in the backend, scoped to the restaurant and validated on the server, never in browser storage. Device preferences stay on the device (ADR-0001, ADR-0014; Q-27 a).
3. Endpoints used by restaurant members (settings, team, feed) take no restaurant id: the restaurant comes from the signed-in member's membership (Q-26 a, decided by Claude 2026-10-01). Platform-admin endpoints (B1, and reads under Q-18 b) name the restaurant explicitly. The T&I contract defines them, and restaurant roles can never reach them.
4. Authorization lives only in `@PreAuthorize` (legacy D2, kept). The T&I contract sets which role edits what (A0).
5. Feed entries are written only after commit and never trigger an outbound message (ADR-0019, ADR-0006).
6. Feed entries carry no customer personal data (Q-52 a).
7. The "Já paguei" entry never changes payment state (ADR-0020 Must).
8. Screen text claims only what the backend enforces. For example, C5 and B4 say when access actually stops (business rule 72).

## Acceptance scenario
Given restaurant R1 with owner Ana, manager Bruno and staff Carla, and restaurant R2 with its own owner.
- When Ana adds the bairro "Centro" with a fee in Restaurant settings, then a customer's checkout on another phone offers Centro with that fee.
- When a storefront order arrives while Carla's KDS has the sound on, then the KDS plays the alert, and R1's feed shows "Novo pedido #12" in the bell for Ana, Bruno and Carla.
- When Ana deactivates Bruno, then Bruno's next refresh gets 401, and R1's audit log records who did it and when.
- When R2's owner opens Settings, the feed or the audit log, then nothing from R1 appears.

## Open questions
Local to this proposal. Answered 2026-10-06 (Gabriel, project thread, 2026-10-06T18:12Z: "confirms all points as per recommended by claude"): each takes the recommended answer, yes.
1. Notifications: should the feed add a "customer says PIX paid" entry, so staff know to check the bank (E4)? yes / no. **Recommended: yes.** Answered: yes.
2. My account: should Build 1 add "change password" (current and new password, C4)? Without it, a signed-in user can change their password only through the "forgot password" email. yes / no. **Recommended: yes.** Answered: yes.
3. Profile: should the B2B fields (department, office, bio, country, timezone) be removed, keeping name, phone, email and photo (C1)? yes / no. **Recommended: yes.** Answered: yes.

What this proposal depends on (no new register rows):
- Q-30 (answered 2026-10-06, a): Build placement of A1's language and of A2-A5, B5's settings events and D3.
- Q-86 (answered 2026-10-06, a): pickup eligibility in A2.
- ADR-0020 (Accepted 2026-10-06): A5, E4 and E1's "unpaid PIX" tag.
- T&I contract: access-token lifetime (today 24 h, `application.yml` L40; the JWT filter does not re-check `active`). It bounds B4, C4 and C5.

## Sources
- Gabriel's Q-24 answer, project thread, 2026-10-01T16:25Z (quoted above); `planning/open-questions.md` rows Q-16..Q-31, Q-42, Q-44, Q-52, Q-54, Q-57, Q-58, Q-61, Q-64, Q-66, Q-67, Q-81, Q-86; Gabriel's review and answers, project thread, 2026-10-06T18:12Z
- ADR-0001, ADR-0005, ADR-0006 (Decision 1, Consequences), ADR-0007 (Later, Out), ADR-0008, ADR-0014 (Decision, Consequences, Open questions), ADR-0019 (Decision 6, Consequences), ADR-0020 (Accepted 2026-10-06)
- FE business rules master `order-ui/docs/superpowers/specs/2026-09-16-business-rules-master-en.md`: rules 71, 72, 81, 88, 89, 90
- FE `order-ui` @ `c4a7309`: paths in "What exists today"
- BE `feature/ai-agent` @ `c8bf761`: paths in "What exists today"; `src/main/resources/application.yml` L40; `security/jwt/JwtAuthenticationFilter.java` L47-75 (no `active` re-check)
