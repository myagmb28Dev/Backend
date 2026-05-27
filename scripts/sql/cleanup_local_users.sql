BEGIN;

DROP TABLE IF EXISTS target_users;
DROP TABLE IF EXISTS target_posts;
DROP TABLE IF EXISTS target_comments;
DROP TABLE IF EXISTS target_notices;
DROP TABLE IF EXISTS target_rooms;
DROP TABLE IF EXISTS target_messages;

CREATE TEMP TABLE target_users AS
SELECT id
FROM users
WHERE email LIKE '%@local.dev';

CREATE TEMP TABLE target_posts AS
SELECT id
FROM community_posts
WHERE author_id IN (SELECT id FROM target_users);

CREATE TEMP TABLE target_comments AS
SELECT id
FROM community_comments
WHERE author_id IN (SELECT id FROM target_users)
   OR post_id IN (SELECT id FROM target_posts);

CREATE TEMP TABLE target_notices AS
SELECT id
FROM pet_notices
WHERE author_id IN (SELECT id FROM target_users);

CREATE TEMP TABLE target_rooms AS
SELECT id
FROM notice_chat_rooms
WHERE owner_user_id IN (SELECT id FROM target_users)
   OR guest_user_id IN (SELECT id FROM target_users)
   OR notice_id IN (SELECT id FROM target_notices);

CREATE TEMP TABLE target_messages AS
SELECT id
FROM notice_chat_messages
WHERE room_id IN (SELECT id FROM target_rooms)
   OR sender_user_id IN (SELECT id FROM target_users);

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

DELETE FROM reports
WHERE target_type = 'NOTICE_CHAT_ROOM'
  AND target_id IN (SELECT id FROM target_rooms);

DELETE FROM notifications
WHERE target_type = 'NOTICE_CHAT_ROOM'
  AND target_id IN (SELECT id FROM target_rooms);

DELETE FROM reports
WHERE target_type = 'NOTICE_CHAT_MESSAGE'
  AND target_id IN (SELECT id FROM target_messages);

DELETE FROM notifications
WHERE target_type = 'NOTICE_CHAT_MESSAGE'
  AND target_id IN (SELECT id FROM target_messages);

DELETE FROM notice_chat_room_participant_states
WHERE room_id IN (SELECT id FROM target_rooms)
   OR user_id IN (SELECT id FROM target_users);

DELETE FROM notice_chat_read_receipts
WHERE room_id IN (SELECT id FROM target_rooms)
   OR reader_user_id IN (SELECT id FROM target_users);

DELETE FROM notice_chat_message_images
WHERE message_id IN (SELECT id FROM target_messages);

UPDATE notice_chat_messages
SET reply_to_message_id = NULL
WHERE room_id IN (SELECT id FROM target_rooms);

DELETE FROM notice_chat_messages
WHERE id IN (SELECT id FROM target_messages)
   OR room_id IN (SELECT id FROM target_rooms);

DELETE FROM notice_chat_rooms
WHERE id IN (SELECT id FROM target_rooms);

DELETE FROM notice_bookmarks
WHERE user_id IN (SELECT id FROM target_users)
   OR notice_id IN (SELECT id FROM target_notices);

DELETE FROM reports
WHERE target_type = 'PET_NOTICE'
  AND target_id IN (SELECT id FROM target_notices);

DELETE FROM notifications
WHERE target_type = 'PET_NOTICE'
  AND target_id IN (SELECT id FROM target_notices);

DELETE FROM pet_notice_images
WHERE notice_id IN (SELECT id FROM target_notices);

DELETE FROM pet_notices
WHERE id IN (SELECT id FROM target_notices);

-- Admin FK order guard:
-- admin_auth_challenges.session_id -> admin_sessions.id
-- so delete challenges before sessions to avoid FK violations.
DELETE FROM admin_auth_challenges
WHERE user_id IN (SELECT id FROM target_users)
   OR session_id IN (
     SELECT id
     FROM admin_sessions
     WHERE user_id IN (SELECT id FROM target_users)
   );

DELETE FROM admin_sessions
WHERE user_id IN (SELECT id FROM target_users);

DO $$
DECLARE r record;
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
$$;

DELETE FROM users
WHERE id IN (SELECT id FROM target_users);

COMMIT;

SELECT count(*) AS remaining_local_users
FROM users
WHERE email LIKE '%@local.dev';
