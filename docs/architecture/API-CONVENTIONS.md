# API conventions

- Status: Reviewed
- Updated: 2026-10-07
- Reviewed: Reviewed by Gabriel on 2026-10-07 (project thread, 2026-10-07T01:26Z: "can you confirmed reviewed"; the **(Claude)** rules are accepted)
- Roadmap step: S4 (`docs/roadmap.md`); gate item "API conventions + Tenancy & Identity contract are Reviewed" (MP §7)
- Applies to: every endpoint of the backend, live (`docs/api/openapi.yaml`, from Build 1) and drafted (`docs/api/drafts/`)
- Related: ADR-0001, ADR-0003 (C1-C3), ADR-0008, ADR-0009, ADR-0010, ADR-0011, ADR-0016; register Q-25, Q-26, Q-32..Q-40 (all answered); `architecture/TENANCY-AND-IDENTITY.md` (Reviewed 2026-10-07)

This page is the rulebook for request and response shapes. Contract docs (Tenancy & Identity, Menu, Order Core, Storefront) follow it and record only what is specific to their area. Where a contract needs an exception, it states the exception and the reason in its Decisions table. Rules marked **(Claude)** are Claude's defaults where the register gave no answer; Gabriel may override any of them when he reviews this page.

## 1. Who owns the shape

1. The backend owns every request and response shape (ADR-0010). Shapes change only by commits in this repo on `feature/ai-agent`.
2. Design first: a new operation is written in its area's draft in `docs/api/drafts/<area>.yaml`, then implemented, then it graduates to the live spec `docs/api/openapi.yaml` (springdoc output, guarded by the drift test). Process: `docs/api/drafts/README.md`.
3. The frontend generates its TypeScript types from the live spec, or from a draft only for mock-backed work (Q-40 a, Q-76 a). It never designs a shape.

## 2. Base path, versioning and breaking changes

1. Base path: `/api` (the servlet context path, `SERVER_CONTEXT_PATH`). Paths in drafts and in this page are written without it: `/orders` means `/api/orders`.
2. No version segment in the path (no `/v1`) this run **(Claude)**. There is one client, and a version segment can be added when an external consumer appears.
3. Breaking changes are free until the first pilot restaurant (Q-32 a; ADR-0008). Change the shape, the draft and the frontend together; no deprecation period.
4. From the first pilot restaurant on (Q-32 a): `info.version` follows semver, every change gets a changelog entry, and a breaking change needs a deprecation period. The deprecation rules are written then, not now.
5. Breaking means: removing or renaming a path, field, enum value or error code; making an optional request field required; changing a type, unit or meaning. Adding an optional request field, a response field, an enum value or an error code is not breaking. Clients must ignore unknown response fields and show a generic fallback for an unknown enum value or error code.

## 3. Paths and methods

1. Paths are lowercase, kebab-case, plural nouns: `/menu-items`, `/team/members/{id}`. Path parameters are camelCase: `{memberId}`.
2. Three path families, by caller (tenancy rules in `TENANCY-AND-IDENTITY.md` §5):

| Family | Caller | Restaurant comes from | Example |
|---|---|---|---|
| `/auth/*`, `/me/*` and restaurant paths (`/orders`, `/menu-items`, `/team/*`, `/restaurant`) | a signed-in restaurant member | the member's membership, server side; never a path, query, header or body value (Q-26 a) | `GET /orders` |
| `/public/r/{slug}/*` | anyone (customers) | the slug in the path (Q-20 a, Q-31 a) | `GET /public/r/pizzaria-do-joao/menu` |
| `/platform/*` | the platform admin only | the path (`/platform/restaurants/{restaurantId}/...`) | `POST /platform/restaurants` |

3. A restaurant path never contains a restaurant id. A request body never carries `restaurantId`; if it does, the request is rejected as unknown input (§4.4).
4. Methods:

| Method | Use | Success |
|---|---|---|
| `GET` | read; never changes state | 200 |
| `POST /things` | create | 201, body = the created resource; a repeated create returns 200 (§9) |
| `PATCH /things/{id}` | partial update of editable fields | 200, body = the full resource |
| `POST /things/{id}/<verb>` | a state transition or command (`/orders/{id}/accept`, `/team/members/{id}/deactivate`) | 200, body = the full resource |
| `DELETE /things/{id}` | remove | 204 |

5. `PUT` is not used for new operations **(Claude)**. Legacy `PUT /orders/{id}` and `PUT /products/{id}` are reshaped in their build steps.
6. State changes go through named transition endpoints, never through a `status` field in a `PATCH` **(Claude)**. Each transition has its own role rule and its own error codes, and forward-only checks (ADR-0003 C3) live in one place.
7. `PATCH` semantics: an absent field stays unchanged; `null` clears a field only where the draft marks it nullable; anything else that is `null` is `INVALID_INPUT`.

## 4. JSON

1. `application/json`, UTF-8, for every request and response except uploads (§12). Field names are camelCase.
2. Responses always include every field of the schema. An empty value is `null` (or `[]` for lists), never a missing key, so generated types stay exact **(Claude)**.
3. Booleans are adjectives without `is`: `active`, `available`.
4. Unknown request fields are rejected with 400 `INVALID_INPUT` **(Claude)**, with `errors: ["<field>: unknown field"]`. A typo by a client, or by an agent, then fails loudly instead of being ignored. Set for the whole app in Build 1 slice 6 (`spring.jackson.deserialization.fail-on-unknown-properties`).
5. Strings are trimmed by the server. An empty string after trimming counts as absent for optional fields and is `INVALID_INPUT` for required ones. Each draft states max lengths.
6. Field name suffixes carry the unit: `...Id` (ULID), `...At` (instant), `...Date` (local date), `...Cents` (money), `...Minutes` (duration), `...Url` (absolute URL).

## 5. Ids

1. Every id is a ULID: 26 characters, Crockford Base32, uppercase, sent as a string. The same string is used in the database (`char(26)`), the logs and the API (Q-35 a; ADR-0009).
2. A resource's own id is `id`; a reference is `<thing>Id` (`categoryId`, `menuItemId`).
3. The server generates ids, except where a contract lets the client supply them: orders and order lines (ADR-0003 C1). A client-supplied id must be a valid ULID, else `INVALID_INPUT`.
4. Human-facing numbers (the order number, ADR-0009) are separate fields, never the id.
5. A malformed id in a path is 400 `INVALID_INPUT`. An id that does not exist, or that belongs to another restaurant, is 404 with the same body in both cases. A request never learns whether another restaurant's resource exists.

## 6. Money

1. Money is an integer count of minor units (centavos) on the wire, `int64`, field suffix `Cents`: `priceCents: 2590` means R$ 25,90 (Q-33 a).
2. The database keeps `DECIMAL(10,2)` (Q-33 a). Conversion happens only in the persistence mapper, with `BigDecimal`, never `double`.
3. No currency field per amount: the restaurant's currency is BRL (Q-21 a) and is a restaurant attribute.
4. The server computes every derived amount (line totals, fees, order totals) from its own prices (D-16 server-side pricing); a client-sent total is ignored or rejected as each contract states. If a calculation ever yields a fraction of a centavo, it rounds half-even to the centavo **(Claude)**.
5. Negative amounts are rejected unless a contract allows them (for example a refund record).

## 7. Time

1. An instant is an ISO-8601 UTC string with `Z` and millisecond precision: `2026-10-07T14:03:21.123Z`. Suffix `At`: `createdAt`, `acceptedAt`.
2. A local date is `YYYY-MM-DD`, suffix `Date`, always in the restaurant's timezone: `businessDate` (D10, Q-28 a).
3. A local time of day is `HH:mm` (opening hours, Build 4).
4. A timezone is an IANA name: `America/Sao_Paulo`. It belongs to the restaurant (Q-28 a).
5. A duration is an integer with a unit suffix: `prepTimeMinutes`.
6. The server never formats dates for display. The frontend formats instants in the restaurant's timezone and the viewer's UI language (`TENANCY-AND-IDENTITY.md` D-13).

## 8. Enums, text and personal data

1. Enum values are SCREAMING_SNAKE: `DINE_IN`, `NEW`, `OWNER` (Q-34 a). The same values are used in the database.
2. The API returns codes and raw values, never localized text (Q-39 a). The frontend translates codes. Restaurant content (menu names, descriptions) is returned exactly as the owner typed it.
3. `message` fields in errors are English text for developers and logs; the UI never shows them.
4. Phone numbers are E.164 strings: `+5511987654321` (Q-21 a). Emails are trimmed and stored lowercase; lookups ignore case.
5. A response carries personal data (names, phones, addresses) only where the caller's role needs it, and logs never carry it (Q-52 a).

## 9. Lists and pagination

1. A paginated list returns the shape already live on `GET /users` (Q-36 a):

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 57, "totalPages": 3 }
```

2. Query parameters: `page` (0-based, default 0), `size` (default 20, max 100; larger is `INVALID_INPUT`) **(Claude: the max)**.
3. Sorting: `sort=<field>,<asc|desc>`, only for the fields the draft lists for that operation; any other field is `INVALID_INPUT`. Every list has a documented default order and ends with `id` as a tie-breaker, so pages are stable.
4. Filters are plain query parameters named after the field: `status=NEW&channel=DELIVERY`. A repeated parameter means "any of".
5. A list that is small and bounded by nature (the roles, a restaurant's categories, a menu) may return `{ "items": [ ... ] }` without the page fields. The draft says which form an operation uses. A list is never a bare JSON array.

## 10. Errors

1. Every error response is `ApiErrorResponse` (Q-37 a):

```json
{ "code": "INVALID_INPUT", "message": "name: must not be blank", "timestamp": "2026-10-07T14:03:21.123Z", "errors": ["name: must not be blank"] }
```

   - `code`: SCREAMING_SNAKE, stable, the only field clients branch on (D14).
   - `message`: English, for developers (§8.3).
   - `errors`: present only for `INVALID_INPUT`; one string per failed field, `<field>: <reason>`. `<field>` is the JSON path of the request field (`lines[2].quantity`).

2. HTTP status and generic codes. Area codes are added by each contract.

| HTTP | Code | When |
|---|---|---|
| 400 | `INVALID_INPUT` | validation failed, unknown field, malformed id, bad page or sort value |
| 400 | `MALFORMED_REQUEST` | the body is not parseable JSON. Replaces today's `BAD_REQUEST` **(Claude)** |
| 401 | `UNAUTHORIZED` | no access token, or an invalid or expired one |
| 401 | `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `INVALID_RESET_TOKEN` | auth flows (live today) |
| 403 | `FORBIDDEN` | signed in, but the role may not do this |
| 403 | `ORIGIN_NOT_ALLOWED` | cookie-borne request from an origin outside the allowlist (live today) |
| 404 | `<RESOURCE>_NOT_FOUND` | `ORDER_NOT_FOUND`, `MEMBER_NOT_FOUND`, `RESTAURANT_NOT_FOUND`; also for another restaurant's resource (§5.5) |
| 409 | `<SPECIFIC>` | a business rule refuses the change: `INVALID_STATUS_TRANSITION`, `ORDER_NOT_EDITABLE`, `LAST_OWNER`, `EMAIL_TAKEN` |
| 409 | `IDEMPOTENCY_CONFLICT` | a create reuses an id with a different payload (§11) |
| 413 | `PAYLOAD_TOO_LARGE` | an upload over the limit |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | wrong content type, or an upload format that is not accepted |
| 429 | `RATE_LIMITED` | rate limit hit (live today); a `Retry-After` header is sent |
| 500 | `INTERNAL_SERVER_ERROR` | anything unexpected; the body never contains a stack trace or SQL |

3. Business-rule refusals are 409, not 400 **(Claude)**: 400 means the request is malformed, 409 means it is well formed but the current state forbids it. Today's `PRODUCT_NOT_FOUND` answered with 400 becomes 404 when the Menu build reshapes it.
4. Every draft operation lists the codes it can return, besides the generic ones.

## 11. Idempotency and retries

1. Order creation uses the client-generated order ULID as the idempotency key (Q-38 a; ADR-0003 C1, C2):
   - a new id creates the order: 201;
   - the same id with the same payload returns the existing order: 200;
   - the same id with a different payload: 409 `IDEMPOTENCY_CONFLICT`.
   "Same payload" is defined in the Order Core contract.
2. Transitions are forward-only (ADR-0003 C3). Repeating a transition whose target state is already reached returns 200 with the current resource; a transition the state machine forbids is 409 `INVALID_STATUS_TRANSITION`.
3. No `Idempotency-Key` header exists this run. A future non-idempotent operation that needs one adds it then (Q-38 a).
4. Other creates are safe to retry by nature or say how a duplicate is refused (for example `EMAIL_TAKEN` for an invite).

## 12. Concurrency

1. No ETags or `If-Match` this run **(Claude)**. Edits of the same settings or member by two people at once are rare, and the last write wins.
2. Exceptions where state must not be overwritten are protected by the state machine (§11.2) and by row locks inside the server, not by the client.

## 13. Authentication on the wire

1. Restaurant and platform endpoints need `Authorization: Bearer <accessToken>`. The refresh token travels only in the `HttpOnly` refresh cookie, never in a body (Q-29 a: Build 1 removes the body copy and the body fallback, and makes the `Origin` check unconditional).
2. Public endpoints (`/public/*`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`) need no access token and are rate-limited.
3. Roles and the tenant rules are in `TENANCY-AND-IDENTITY.md`. Authorization lives only in `@PreAuthorize` (legacy D2).

## 14. Uploads and images

1. Uploads are `multipart/form-data` with one part named `file`. Accepted: JPEG, PNG, WebP; the server converts to WebP (today's avatar pipeline, Q-44 a). The size limit is stated per operation.
2. The database stores only the object key, under the restaurant's prefix (`restaurants/{restaurantId}/...`; user avatars under `users/{userId}/...`). Responses return an absolute `...Url`. A data URL is never stored or returned (ADR-0014).

## 15. Writing a draft operation

Every operation in a draft has:

1. an `operationId` in camelCase, verb first: `listOrders`, `acceptOrder`, `inviteMember`;
2. one tag, the contract area;
3. `x-roles`: the roles allowed (`[OWNER, MANAGER]`, `[PUBLIC]`, `[PLATFORM_ADMIN]`);
4. `x-seed`: the frontend manifest 0.4.0 path it was seeded from, or `new`;
5. its success response and every area error code it can return (§10.4);
6. request field constraints (required, max length, min and max, pattern) in the schema, so validation and generated types agree.

## Decisions

| # | Decision | Decided by | Source |
|---|---|---|---|
| 1 | Money in integer centavos, suffix `Cents`; DB `DECIMAL(10,2)` | Gabriel (Q-33 a); suffix: Claude | register |
| 2 | SCREAMING_SNAKE enums | Gabriel (Q-34 a) | register |
| 3 | ULID `char(26)` strings everywhere | Gabriel (Q-35 a) | register, ADR-0009 |
| 4 | `PagedResponse` for paginated lists; max size 100; allow-listed sort | Gabriel (Q-36 a); max and sort: Claude | register |
| 5 | `ApiErrorResponse`; business refusals are 409; `MALFORMED_REQUEST` replaces `BAD_REQUEST` | Gabriel (Q-37 a); 409 and rename: Claude | register |
| 6 | Client order ULID is the idempotency key; `IDEMPOTENCY_CONFLICT` | Gabriel (Q-38 a); code name: Claude | register, ADR-0003 |
| 7 | No localized text from the API | Gabriel (Q-39 a) | register |
| 8 | One draft per area | Gabriel (Q-40 a) | register |
| 9 | Breaking changes free until the first pilot | Gabriel (Q-32 a) | register |
| 10 | No path version; no `PUT`; transitions as `POST .../<verb>`; unknown fields rejected; all fields always present; no ETags | Claude | this page |

## Open questions

None. Reviewed by Gabriel on 2026-10-07 with every **(Claude)** rule accepted.

## Sources

- Register `planning/open-questions.md` Q-25, Q-26, Q-32..Q-40.
- Live code: `api/errors/ApiErrorResponse.java`, `identity/api/dto/PagedResponse.java`, `identity/application/UserQueryService.java` (default sort by id), `src/test/resources/application-test.yml` (context path `/api`).
- ADR-0003 C1-C3, ADR-0009, ADR-0010; `docs/api/drafts/README.md`.
