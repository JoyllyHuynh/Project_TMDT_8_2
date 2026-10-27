-- Align users with the User entity without modifying existing user rows.
-- Run manually against the existing comicworm database.
-- This migration only adds missing nullable users columns; it does not touch orders.

USE comicworm;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'account_status_reason';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN account_status_reason TEXT NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'account_status_changed_at';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN account_status_changed_at DATETIME NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'seller_requested_at';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN seller_requested_at DATETIME NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'seller_request_status';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN seller_request_status VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'seller_request_note';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN seller_request_note TEXT NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @column_exists
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'seller_request_resolved_at';
SET @migration_sql = IF(
    @column_exists = 0,
    'ALTER TABLE users ADD COLUMN seller_request_resolved_at DATETIME NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
