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

At the time of writing the highest is `V19__Add_Avatar_Key_To_User_Profile.sql`.

## Never edit an applied migration

Flyway checksums every migration it has run. Editing one breaks startup in every environment
that already applied it — including any teammate's local database and CI. The fix is always
a **new** version, never a correction in place.

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

(The mapping above is the one migration phase 4 needs; confirm the target names against
`docs/contracts/backend-integration-manifest.openapi.yaml` before writing it.)

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
