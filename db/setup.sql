-- One-time local dev setup for Nibash.
--
-- Run this ONCE as a MySQL admin (root). In MySQL Workbench: File → Open SQL Script → Execute.
-- From a terminal:  mysql -u root -p < db/setup.sql
--
-- It creates the app database, a dedicated least-privilege app user, and a separate schema for
-- integration tests. Flyway creates the tables on first boot — do not create them here.

CREATE DATABASE IF NOT EXISTS `nibash`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `nibash_test`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Change this password and mirror it in backend/.env before deploying anywhere real.
CREATE USER IF NOT EXISTS 'nibash'@'localhost' IDENTIFIED BY 'nibash_dev_password';
CREATE USER IF NOT EXISTS 'nibash'@'127.0.0.1' IDENTIFIED BY 'nibash_dev_password';

GRANT ALL PRIVILEGES ON `nibash`.*      TO 'nibash'@'localhost';
GRANT ALL PRIVILEGES ON `nibash`.*      TO 'nibash'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `nibash_test`.* TO 'nibash'@'localhost';
GRANT ALL PRIVILEGES ON `nibash_test`.* TO 'nibash'@'127.0.0.1';

FLUSH PRIVILEGES;

SELECT 'Nibash databases and app user created.' AS status;
