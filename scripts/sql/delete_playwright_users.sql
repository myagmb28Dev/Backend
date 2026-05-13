-- Reusable local-user cleanup script (kept intentionally; do not delete)
--
-- Usage examples:
--   psql ... -v ON_ERROR_STOP=1 -f scripts/sql/delete_playwright_users.sql
--     -> default pattern: 'playwright-user%@local.dev'
--
--   psql ... -v ON_ERROR_STOP=1 -v email_like="'dm-user%@local.dev'" -f scripts/sql/delete_playwright_users.sql
--     -> custom pattern
--
-- Notes:
-- - This script deletes users matched by email pattern and tries to clean FK references.
-- - It includes explicit cleanup for high-churn tables first, then generic FK cleanup.

\if :{?email_like}
\else
\set email_like 'playwright-user%@local.dev'
\endif

BEGIN;

DROP TABLE IF EXISTS target_users;
DROP TABLE IF EXISTS target_posts;
DROP TABLE IF EXISTS target_comments;

CREATE TEMP TABLE target_users AS
SELECT id
FROM users
WHERE email LIKE :'email_like';

CREATE TEMP TABLE target_posts AS
SELECT id
FROM community_posts
WHERE author_id IN (SELECT id FROM target_users);

CREATE TEMP TABLE target_comments AS
SELECT id
FROM community_comments
WHERE author_id IN (SELECT id FROM target_users)
   OR post_id IN (SELECT id FROM target_posts);

DELETE FROM reports
WHERE target_type = 'COMMUNITY_COMMENT'
  AND target_id IN (SELECT id FROM target_comments);
DELETE FROM notifications
WHERE target_type = 'COMMUNITY_COMMENT'
  AND target_id IN (SELECT id FROM target_comments);
UPDATE community_comments
SET parent_comment_id = NULL
WHERE parent_comment_id IN (SELECT id FROM target_comments);
DELETE FROM community_comments
WHERE id IN (SELECT id FROM target_comments);
DELETE FROM community_post_reactions
WHERE user_id IN (SELECT id FROM target_users)
   OR post_id IN (SELECT id FROM target_posts);
DELETE FROM community_post_votes
WHERE user_id IN (SELECT id FROM target_users)
   OR post_id IN (SELECT id FROM target_posts);
DELETE FROM community_post_images
WHERE post_id IN (SELECT id FROM target_posts);
DELETE FROM community_post_tags
WHERE post_id IN (SELECT id FROM target_posts);
DELETE FROM reports
WHERE target_type = 'COMMUNITY_POST'
  AND target_id IN (SELECT id FROM target_posts);
DELETE FROM notifications
WHERE target_type = 'COMMUNITY_POST'
  AND target_id IN (SELECT id FROM target_posts);
DELETE FROM community_posts
WHERE id IN (SELECT id FROM target_posts);

DELETE FROM user_blocks
WHERE blocker_id IN (SELECT id FROM target_users)
   OR blocked_id IN (SELECT id FROM target_users);
DELETE FROM user_follows
WHERE follower_id IN (SELECT id FROM target_users)
   OR following_id IN (SELECT id FROM target_users);
DELETE FROM user_social_accounts
WHERE user_id IN (SELECT id FROM target_users);
DELETE FROM user_fcm_tokens
WHERE user_id IN (SELECT id FROM target_users);
DELETE FROM user_notification_settings
WHERE user_id IN (SELECT id FROM target_users);
DELETE FROM notifications
WHERE user_id IN (SELECT id FROM target_users)
   OR actor_user_id IN (SELECT id FROM target_users);
DELETE FROM reports
WHERE reporter_id IN (SELECT id FROM target_users);
DELETE FROM notice_bookmarks
WHERE user_id IN (SELECT id FROM target_users);

DO $do$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name,
               c.relname AS table_name,
               a.attname AS column_name
        FROM pg_constraint co
        JOIN pg_class c ON c.oid = co.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN unnest(co.conkey) WITH ORDINALITY AS ck(attnum, ord) ON true
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ck.attnum
        WHERE co.contype = 'f'
          AND co.confrelid = 'users'::regclass
    LOOP
        EXECUTE format(
            'DELETE FROM %I.%I WHERE %I IN (SELECT id FROM target_users)',
            r.schema_name,
            r.table_name,
            r.column_name
        );
    END LOOP;
END
$do$;

DELETE FROM users
WHERE id IN (SELECT id FROM target_users);

COMMIT;

SELECT :'email_like' AS deleted_pattern,
       count(*) AS remaining_matched_users
FROM users
WHERE email LIKE :'email_like';
