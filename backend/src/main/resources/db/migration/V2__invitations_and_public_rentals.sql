-- =============================================================================
-- V2: bringing people into a building.
--
--  * invitations      — a manager adds a resident, committee member, guard or staff member by
--                       email; they accept through a one-time link and get their own login.
--  * listings         — a manager can list a flat for the building itself (no resident row), and
--                       choose to publish it on the public flats page.
--  * rental_requests  — someone from outside the building can ask for a published flat; they have
--                       no resident row until the committee approves them.
-- =============================================================================

CREATE TABLE `invitations` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`      BIGINT       NOT NULL,
  `email`            VARCHAR(150) NOT NULL,
  `name`             VARCHAR(100) NOT NULL,
  `phone`            VARCHAR(20)  NULL,
  `role`             VARCHAR(10)  NOT NULL,
  `unit_id`          BIGINT       NULL,
  `is_owner`         TINYINT(1)   NOT NULL DEFAULT 0,
  `staff_role`       VARCHAR(50)  NULL,
  `designation`      VARCHAR(100) NULL,
  -- SHA-256 of the link's token; the token itself is never stored.
  `token_hash`       CHAR(64)     NOT NULL,
  `invited_by_id`    BIGINT       NULL,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at`       DATETIME     NOT NULL,
  `accepted_at`      DATETIME     NULL,
  `accepted_user_id` BIGINT       NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invitations_token` (`token_hash`),
  KEY `ix_invitations_building` (`building_id`),
  KEY `ix_invitations_email` (`email`),
  KEY `ix_invitations_unit` (`unit_id`),
  KEY `ix_invitations_invited_by` (`invited_by_id`),
  KEY `ix_invitations_accepted_user` (`accepted_user_id`),
  CONSTRAINT `fk_invitations_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_invitations_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_invitations_invited_by` FOREIGN KEY (`invited_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_invitations_accepted_user` FOREIGN KEY (`accepted_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `listings`
  MODIFY `resident_id` BIGINT NULL,
  ADD COLUMN `listed_by_id` BIGINT NULL AFTER `resident_id`,
  ADD COLUMN `is_public` TINYINT(1) NOT NULL DEFAULT 0 AFTER `available_from`,
  ADD KEY `ix_listings_listed_by` (`listed_by_id`),
  ADD KEY `ix_listings_public` (`is_public`),
  ADD CONSTRAINT `fk_listings_listed_by` FOREIGN KEY (`listed_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL;

UPDATE `listings` l JOIN `residents` r ON r.`id` = l.`resident_id` SET l.`listed_by_id` = r.`user_id`;

ALTER TABLE `rental_requests`
  MODIFY `tenant_id` BIGINT NULL,
  ADD COLUMN `applicant_id` BIGINT NULL AFTER `tenant_id`,
  ADD COLUMN `message` TEXT NULL AFTER `status`,
  ADD KEY `ix_rental_requests_applicant` (`applicant_id`),
  ADD CONSTRAINT `fk_rental_requests_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

UPDATE `rental_requests` q JOIN `residents` r ON r.`id` = q.`tenant_id` SET q.`applicant_id` = r.`user_id`;

ALTER TABLE `rental_requests` MODIFY `applicant_id` BIGINT NOT NULL;
