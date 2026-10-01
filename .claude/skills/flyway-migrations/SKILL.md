---
name: flyway-migrations
description: Use when adding or changing a database migration, or changing a JPA entity's mapped schema - covers version numbering, keeping entity and schema in sync under ddl-auto validate, and renaming an enum without losing data.
---

# Flyway migrations in this backend

Migrations run automatically on boot, and in tests against a real Postgres container. A
broken migration does not fail one test — it fails the entire integration suite, because
nothing can start.

## Numbering

`src/main/resources/db/migration/V<N>__Description.sql`.

**List the directory and take the next free integer. Never assume it.** Two branches that
both guessed the same number collide, and the collision surfaces as a checksum error in
whichever environment runs second.

```bash
ls src/main/resources/db/migration/ | sort -V | tail -1
```

At the time of writing (2026-09-29) the highest is `V21__Refresh_Token_Families.sql`.

## Never edit an applied migration

Flyway checksums every migration it has run. Editing one breaks startup in every environment
that already applied it — including any teammate's local database and CI. The fix is always
a **new** version, never a correction in place.

**One exception, before launch only** (ADR-0009): V1-V21 are replaced once by a new baseline
that creates the target schema (ULID keys, the restaurant id on restaurant-owned tables, the
missing foreign keys). It is planned for Build 1 (Tenant foundation), as one dedicated,
reviewed change, and only while no environment holds data that must be kept; every local
database is dropped and recreated afterwards. Outside that re-baseline, and always after
launch, this rule applies without exception.

## Entity and schema ship together

`application.yml` sets `ddl-auto: validate`. Hibernate compares every entity against its
table at startup and **refuses to boot** if they disagree. There is no partial failure mode:
the application either matches the schema or does not run.

So the entity change and its migration go in the **same commit**. A commit that contains only
one of the two is broken by construction, and it breaks for whoever checks it out, not for you.

## Seeds are idempotent

`ON CONFLICT ... DO NOTHING`. Precedent: `V15__seed_super_admin.sql`. Migrations run against
databases that may already hold the row.

## Renaming an enum value is a data migration

**Not for pre-launch legacy data.** There is no production data (ADR-0008): the legacy
`orders` statuses are replaced by the Order Core contract, not migrated, and no data
migration or compatibility shim is written for legacy B2B data. The technique below applies
once real data exists.

Enums are persisted as strings — `@Enumerated(EnumType.STRING)` on
`orders/domain/Order.java:25` — so the stored values are the **Java constant names**. Renaming
a constant does not change the rows; it just makes every existing row unreadable by the new
code.

Three steps, in order:

1. **Add** the new value set alongside the old (widen the constraint, do not swap it).
2. **Backfill** with an explicit mapping that covers *every* old value:

```sql
UPDATE orders SET status = CASE status
    WHEN 'PENDING'    THEN 'NEW'
    WHEN 'PROCESSING' THEN 'PREPARING'
    WHEN 'SHIPPED'    THEN 'READY'
    WHEN 'DELIVERED'  THEN 'COMPLETED'
    WHEN 'CANCELLED'  THEN 'CANCELLED'
END;
```

3. **Drop** the old constraint once no row holds an old value.

Never leave a row holding a value the Java enum no longer has — it deserializes to an
exception on read, and only for the rows that happen to be old.

(The mapping above only illustrates the technique; it is not a planned migration. Target
enum names come from the backend-owned contract in `docs/api/drafts/` (ADR-0010), never from
the frozen `docs/contracts/` snapshot.)

## New-architecture rules

Only what is Accepted so far:

- **New tables get a ULID primary key** (ADR-0009). Never `IDENTITY`, `SERIAL`/`BIGSERIAL` or
  a database sequence for a primary key; the application generates the id, and foreign keys
  reference ULIDs. The column type is `char(26)` Crockford Base32, the same string in the
  database, logs and API (Q-35 a, 2026-10-01; ADR-0009 Decision 6).
- **Restaurant-owned tables carry the restaurant id** (ADR-0001). Whether child rows (for
  example order lines) carry it directly, and how queries are scoped, come from the Tenancy &
  Identity contract, which is not written yet. Do not invent a mechanism.

## Before you write

State what you found: the current highest version, the table and entity involved, and whether
any existing row would violate the new constraint. A migration that fails halfway leaves the
database in a state someone has to repair by hand.

## Commands

```bash
./gradlew flywayMigrate
./gradlew flywayInfo
./gradlew test            # what actually proves it
```

`build.gradle` reads `.env` directly to configure the Flyway plugin, outside Spring's context.

Verification is `./gradlew test`: the integration suite boots against a real Postgres
container, so it is the only thing that proves the migration and the entity agree.
