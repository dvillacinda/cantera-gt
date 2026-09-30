ALTER TABLE "users"
    ADD COLUMN "first_name" varchar(100),
    ADD COLUMN "last_name" varchar(100);

UPDATE "users" u
SET "first_name" = COALESCE(
        (SELECT p."first_name" FROM "players" p WHERE p."user_id" = u."user_id"),
        (SELECT c."first_name" FROM "coaches" c WHERE c."user_id" = u."user_id"),
        u."username"),
    "last_name" = COALESCE(
        (SELECT p."last_name" FROM "players" p WHERE p."user_id" = u."user_id"),
        (SELECT c."last_name" FROM "coaches" c WHERE c."user_id" = u."user_id"),
        u."username");

ALTER TABLE "users"
    ALTER COLUMN "first_name" SET NOT NULL,
    ALTER COLUMN "last_name" SET NOT NULL;

ALTER TABLE "players"
    DROP COLUMN "first_name",
    DROP COLUMN "last_name";

ALTER TABLE "coaches"
    DROP COLUMN "first_name",
    DROP COLUMN "last_name";

COMMENT ON COLUMN "users"."first_name" IS 'Given name synchronized with the Keycloak user profile';
COMMENT ON COLUMN "users"."last_name" IS 'Family name synchronized with the Keycloak user profile';
