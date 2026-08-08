-- Nibash baseline schema (spec §7).
-- Conventions: BIGINT auto-increment PKs, utf8mb4, InnoDB, money as DECIMAL(10,2) unless noted.
-- Enum-typed columns are VARCHAR with the widths given in the spec; the enum is enforced in the
-- application layer (matching the original Django behaviour).

-- ---------------------------------------------------------------- identity

CREATE TABLE `users` (
  `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
  `name`                    VARCHAR(100) NOT NULL,
  `email`                   VARCHAR(150) NOT NULL,
  `phone`                   VARCHAR(20)  NULL,
  `password_hash`           VARCHAR(255) NOT NULL,
  `role`                    VARCHAR(10)  NOT NULL DEFAULT 'resident',
  `is_listed`               TINYINT(1)   NOT NULL DEFAULT 1,
  `dob`                     DATE         NULL,
  `national_id`             VARCHAR(50)  NULL,
  `avatar_path`             VARCHAR(255) NULL,
  `address`                 VARCHAR(255) NULL,
  `bio`                     TEXT         NULL,
  `emergency_contact_phone` VARCHAR(20)  NULL,
  `is_staff`                TINYINT(1)   NOT NULL DEFAULT 0,
  `is_superuser`            TINYINT(1)   NOT NULL DEFAULT 0,
  `is_active`               TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `auth_tokens` (
  `key`     VARCHAR(40) NOT NULL,
  `user_id` BIGINT      NOT NULL,
  `created` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`key`),
  UNIQUE KEY `uq_auth_tokens_user` (`user_id`),
  CONSTRAINT `fk_auth_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- buildings & occupancy

CREATE TABLE `buildings` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `name`               VARCHAR(150) NOT NULL,
  `address`            VARCHAR(255) NOT NULL,
  `developer_id`       BIGINT       NULL,
  `primary_contact_id` BIGINT       NULL,
  `year_built`         SMALLINT     NULL,
  `num_floors`         INT          NULL,
  `total_units`        INT          NULL,
  `website`            VARCHAR(255) NULL,
  `amenities_json`     JSON         NULL,
  `photo_path`         VARCHAR(255) NULL,
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_buildings_developer` (`developer_id`),
  KEY `ix_buildings_primary_contact` (`primary_contact_id`),
  CONSTRAINT `fk_buildings_developer` FOREIGN KEY (`developer_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_buildings_primary_contact` FOREIGN KEY (`primary_contact_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `units` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT        NOT NULL,
  `unit_number` VARCHAR(50)   NOT NULL,
  `floor`       INT           NULL,
  `type`        VARCHAR(10)   NOT NULL DEFAULT '1BHK',
  `size_sqft`   DECIMAL(10,2) NULL,
  `price`       DECIMAL(12,2) NULL,
  `status`      VARCHAR(10)   NOT NULL DEFAULT 'available',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_units_building_number` (`building_id`, `unit_number`),
  CONSTRAINT `fk_units_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `residents` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT     NOT NULL,
  `building_id` BIGINT     NOT NULL,
  `unit_id`     BIGINT     NULL,
  `is_owner`    TINYINT(1) NOT NULL DEFAULT 0,
  `opt_in`      TINYINT(1) NOT NULL DEFAULT 1,
  `start_date`  DATE       NULL,
  `end_date`    DATE       NULL,
  `created_at`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_residents_user_building` (`user_id`, `building_id`),
  KEY `ix_residents_building` (`building_id`),
  KEY `ix_residents_unit` (`unit_id`),
  CONSTRAINT `fk_residents_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_residents_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_residents_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- services, vendors, reviews

CREATE TABLE `services` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `name`      VARCHAR(100) NOT NULL,
  `parent_id` BIGINT       NULL,
  PRIMARY KEY (`id`),
  KEY `ix_services_parent` (`parent_id`),
  CONSTRAINT `fk_services_parent` FOREIGN KEY (`parent_id`) REFERENCES `services` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vendors` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `service_id`   BIGINT       NOT NULL,
  `building_id`  BIGINT       NULL,          -- NULL = global vendor, visible to every building
  `name`         VARCHAR(150) NOT NULL,
  `contact_info` VARCHAR(255) NULL,
  `rating`       DECIMAL(2,1) NULL,
  `latitude`     DECIMAL(9,6) NULL,
  `longitude`    DECIMAL(9,6) NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_vendors_building` (`building_id`),
  KEY `ix_vendors_service` (`service_id`),
  CONSTRAINT `fk_vendors_service` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_vendors_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reviews` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `vendor_id`   BIGINT   NOT NULL,
  `resident_id` BIGINT   NOT NULL,
  `rating`      SMALLINT NOT NULL,
  `comment`     TEXT     NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_reviews_vendor` (`vendor_id`),
  KEY `ix_reviews_resident` (`resident_id`),
  CONSTRAINT `fk_reviews_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_reviews_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- finance

CREATE TABLE `bill_types` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL,
  `description` TEXT         NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_bill_types_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoices` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `invoice_number` VARCHAR(50)   NOT NULL,
  `resident_id`    BIGINT        NOT NULL,
  `building_id`    BIGINT        NOT NULL,
  `bill_type_id`   BIGINT        NULL,
  `amount`         DECIMAL(10,2) NOT NULL,
  `due_date`       DATE          NOT NULL,
  `status`         VARCHAR(8)    NOT NULL DEFAULT 'pending',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_invoices_number` (`invoice_number`),
  KEY `ix_invoices_due_date` (`due_date`),
  KEY `ix_invoices_status` (`status`),
  KEY `ix_invoices_building` (`building_id`),
  KEY `ix_invoices_resident` (`resident_id`),
  CONSTRAINT `fk_invoices_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoices_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoices_bill_type` FOREIGN KEY (`bill_type_id`) REFERENCES `bill_types` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice_items` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `invoice_id`      BIGINT        NOT NULL,
  `description`     VARCHAR(255)  NOT NULL,
  `quantity`        DECIMAL(10,2) NOT NULL DEFAULT 1.00,
  `unit_price`      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `tax_amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `total_amount`    DECIMAL(10,2) NOT NULL,
  `utility_bill_id` INT           NULL,       -- intentionally NOT a foreign key (spec §15.2)
  PRIMARY KEY (`id`),
  KEY `ix_invoice_items_invoice` (`invoice_id`),
  CONSTRAINT `fk_invoice_items_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payments` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `invoice_id`     BIGINT        NOT NULL,
  `resident_id`    BIGINT        NOT NULL,
  `amount`         DECIMAL(10,2) NOT NULL,
  `payment_date`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `method`         VARCHAR(50)   NOT NULL,
  `transaction_id` VARCHAR(100)  NULL,
  PRIMARY KEY (`id`),
  KEY `ix_payments_date` (`payment_date`),
  KEY `ix_payments_invoice` (`invoice_id`),
  KEY `ix_payments_resident` (`resident_id`),
  CONSTRAINT `fk_payments_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payments_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `expenses` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `building_id`   BIGINT        NOT NULL,
  `category`      VARCHAR(100)  NOT NULL,
  `amount`        DECIMAL(10,2) NOT NULL,
  `description`   TEXT          NULL,
  `date`          DATE          NOT NULL,
  `receipt_path`  VARCHAR(255)  NULL,
  `created_by_id` BIGINT        NOT NULL,
  `vendor_id`     BIGINT        NULL,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_expenses_building_date` (`building_id`, `date`),
  KEY `ix_expenses_created_by` (`created_by_id`),
  KEY `ix_expenses_vendor` (`vendor_id`),
  CONSTRAINT `fk_expenses_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expenses_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expenses_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- notices

CREATE TABLE `notices` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`   BIGINT       NOT NULL,
  `title`         VARCHAR(200) NOT NULL,
  `body`          TEXT         NOT NULL,
  `is_pinned`     TINYINT(1)   NOT NULL DEFAULT 0,
  `publish_date`  DATETIME     NOT NULL,
  `expiry_date`   DATETIME     NULL,
  `created_by_id` BIGINT       NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_notices_building` (`building_id`),
  KEY `ix_notices_created_by` (`created_by_id`),
  CONSTRAINT `fk_notices_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_notices_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- staffing

CREATE TABLE `staff` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       NULL,
  `name`           VARCHAR(100) NOT NULL,
  `role`           VARCHAR(50)  NOT NULL,
  `designation`    VARCHAR(100) NULL,
  `qualifications` TEXT         NULL,
  `building_id`    BIGINT       NOT NULL,
  `contact_info`   VARCHAR(100) NULL,
  PRIMARY KEY (`id`),
  KEY `ix_staff_building` (`building_id`),
  KEY `ix_staff_user` (`user_id`),
  CONSTRAINT `fk_staff_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_staff_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `attendance` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT,
  `staff_id`      BIGINT   NOT NULL,
  `checkin_time`  DATETIME NOT NULL,
  `checkout_time` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `ix_attendance_staff_checkin` (`staff_id`, `checkin_time`),
  CONSTRAINT `fk_attendance_staff` FOREIGN KEY (`staff_id`) REFERENCES `staff` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- visitors

CREATE TABLE `appointments` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`    BIGINT       NOT NULL,
  `resident_id`    BIGINT       NOT NULL,
  `visitor_name`   VARCHAR(100) NOT NULL,
  `visitor_phone`  VARCHAR(20)  NOT NULL,
  `scheduled_time` DATETIME     NOT NULL,
  `approved`       TINYINT(1)   NOT NULL DEFAULT 0,
  `qr_token`       VARCHAR(64)  NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_appointments_qr_token` (`qr_token`),
  KEY `ix_appointments_building_time` (`building_id`, `scheduled_time`),
  KEY `ix_appointments_resident` (`resident_id`),
  CONSTRAINT `fk_appointments_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_appointments_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `visitors` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `appointment_id` BIGINT      NOT NULL,
  `checkin_time`   DATETIME    NULL,
  `checkout_time`  DATETIME    NULL,
  `status`         VARCHAR(12) NOT NULL DEFAULT 'pending',
  `handled_by_id`  BIGINT      NULL,
  PRIMARY KEY (`id`),
  KEY `ix_visitors_appointment` (`appointment_id`),
  KEY `ix_visitors_handled_by` (`handled_by_id`),
  CONSTRAINT `fk_visitors_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_visitors_handled_by` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- tickets

CREATE TABLE `tickets` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`       BIGINT       NOT NULL,
  `resident_id`       BIGINT       NOT NULL,
  `category`          VARCHAR(100) NOT NULL,
  `description`       TEXT         NOT NULL,
  `status`            VARCHAR(12)  NOT NULL DEFAULT 'open',
  `priority`          VARCHAR(6)   NOT NULL DEFAULT 'medium',
  `assigned_to_id`    BIGINT       NULL,
  `service_vendor_id` BIGINT       NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `closed_at`         DATETIME     NULL,
  PRIMARY KEY (`id`),
  KEY `ix_tickets_building_status` (`building_id`, `status`),
  KEY `ix_tickets_assigned_to` (`assigned_to_id`),
  KEY `ix_tickets_resident` (`resident_id`),
  KEY `ix_tickets_vendor` (`service_vendor_id`),
  CONSTRAINT `fk_tickets_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tickets_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tickets_assigned_to` FOREIGN KEY (`assigned_to_id`) REFERENCES `staff` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_tickets_vendor` FOREIGN KEY (`service_vendor_id`) REFERENCES `vendors` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ticket_images` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `ticket_id`  BIGINT       NOT NULL,
  `image_path` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_ticket_images_ticket` (`ticket_id`),
  CONSTRAINT `fk_ticket_images_ticket` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- bookable resources

CREATE TABLE `resources` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(150) NOT NULL,
  `capacity`    INT          NOT NULL DEFAULT 1,
  `location`    VARCHAR(100) NULL,
  `building_id` BIGINT       NOT NULL,
  `type`        VARCHAR(50)  NULL,
  PRIMARY KEY (`id`),
  KEY `ix_resources_building` (`building_id`),
  CONSTRAINT `fk_resources_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bookings` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT       NOT NULL,
  `resident_id` BIGINT       NOT NULL,
  `start_time`  DATETIME     NOT NULL,
  `end_time`    DATETIME     NOT NULL,
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'pending',
  `purpose`     VARCHAR(150) NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_bookings_resource_window` (`resource_id`, `start_time`, `end_time`),
  KEY `ix_bookings_resource_start` (`resource_id`, `start_time`),
  KEY `ix_bookings_resident_start` (`resident_id`, `start_time`),
  CONSTRAINT `fk_bookings_resource` FOREIGN KEY (`resource_id`) REFERENCES `resources` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bookings_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- polls

CREATE TABLE `polls` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`   BIGINT       NOT NULL,
  `question`      VARCHAR(255) NOT NULL,
  `created_by_id` BIGINT       NOT NULL,
  `start_date`    DATETIME     NOT NULL,
  `end_date`      DATETIME     NULL,
  PRIMARY KEY (`id`),
  KEY `ix_polls_building` (`building_id`),
  KEY `ix_polls_created_by` (`created_by_id`),
  CONSTRAINT `fk_polls_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_polls_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `options` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `poll_id`     BIGINT       NOT NULL,
  `option_text` VARCHAR(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_options_poll` (`poll_id`),
  CONSTRAINT `fk_options_poll` FOREIGN KEY (`poll_id`) REFERENCES `polls` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `votes` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `poll_id`     BIGINT   NOT NULL,
  `option_id`   BIGINT   NOT NULL,
  `resident_id` BIGINT   NOT NULL,
  `voted_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_votes_poll_resident` (`poll_id`, `resident_id`),   -- one vote per resident
  KEY `ix_votes_option` (`option_id`),
  KEY `ix_votes_resident` (`resident_id`),
  CONSTRAINT `fk_votes_poll` FOREIGN KEY (`poll_id`) REFERENCES `polls` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_votes_option` FOREIGN KEY (`option_id`) REFERENCES `options` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_votes_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- documents

CREATE TABLE `documents` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`    BIGINT       NOT NULL,
  `title`          VARCHAR(200) NOT NULL,
  `file_path`      VARCHAR(255) NOT NULL,
  `version`        INT          NOT NULL DEFAULT 1,
  `mime_type`      VARCHAR(120) NULL,
  `parent_id`      BIGINT       NULL,
  `is_active`      TINYINT(1)   NOT NULL DEFAULT 1,
  `uploaded_by_id` BIGINT       NOT NULL,
  `uploaded_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_documents_building` (`building_id`),
  KEY `ix_documents_parent` (`parent_id`),
  KEY `ix_documents_uploaded_by` (`uploaded_by_id`),
  CONSTRAINT `fk_documents_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_documents_parent` FOREIGN KEY (`parent_id`) REFERENCES `documents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_documents_uploaded_by` FOREIGN KEY (`uploaded_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `document_acl_users` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT     NOT NULL,
  `user_id`     BIGINT     NOT NULL,
  `can_view`    TINYINT(1) NOT NULL DEFAULT 1,
  `can_edit`    TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_acl_users` (`document_id`, `user_id`),
  KEY `ix_document_acl_users_user` (`user_id`),
  CONSTRAINT `fk_document_acl_users_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_document_acl_users_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `document_acl_roles` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT      NOT NULL,
  `role`        VARCHAR(10) NOT NULL,
  `can_view`    TINYINT(1)  NOT NULL DEFAULT 1,
  `can_edit`    TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_document_acl_roles` (`document_id`, `role`),
  CONSTRAINT `fk_document_acl_roles_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `document_audit_logs` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT     NOT NULL,
  `user_id`     BIGINT     NOT NULL,
  `event_type`  VARCHAR(8) NOT NULL,
  `event_time`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_document_audit_document` (`document_id`),
  KEY `ix_document_audit_user` (`user_id`),
  CONSTRAINT `fk_document_audit_document` FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_document_audit_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- emergencies & intercom

CREATE TABLE `emergencies` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT       NOT NULL,
  `resident_id` BIGINT       NOT NULL,
  `latitude`    DECIMAL(9,6) NULL,
  `longitude`   DECIMAL(9,6) NULL,
  `timestamp`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_emergencies_building_time` (`building_id`, `timestamp`),
  KEY `ix_emergencies_resident` (`resident_id`),
  CONSTRAINT `fk_emergencies_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_emergencies_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `intercom_devices` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT       NOT NULL,
  `device_name` VARCHAR(100) NOT NULL,
  `ip_address`  VARCHAR(45)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_intercom_devices_building_ip` (`building_id`, `ip_address`),
  CONSTRAINT `fk_intercom_devices_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `intercom_logs` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `device_id`  BIGINT      NOT NULL,
  `event_type` VARCHAR(50) NOT NULL,
  `timestamp`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `details`    TEXT        NULL,
  PRIMARY KEY (`id`),
  KEY `ix_intercom_logs_device_time` (`device_id`, `timestamp`),
  CONSTRAINT `fk_intercom_logs_device` FOREIGN KEY (`device_id`) REFERENCES `intercom_devices` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- chat

CREATE TABLE `chat_rooms` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL,
  `is_public`   TINYINT(1)   NOT NULL DEFAULT 1,
  `building_id` BIGINT       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_chat_rooms_building` (`building_id`),
  CONSTRAINT `fk_chat_rooms_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_members` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `room_id`     BIGINT   NOT NULL,
  `resident_id` BIGINT   NOT NULL,
  `joined_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_room_members` (`room_id`, `resident_id`),
  KEY `ix_room_members_resident` (`resident_id`),
  CONSTRAINT `fk_room_members_room` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_room_members_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `messages` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `room_id`     BIGINT   NOT NULL,
  `resident_id` BIGINT   NOT NULL,
  `content`     TEXT     NOT NULL,
  `sent_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_messages_room_sent` (`room_id`, `sent_at`),
  KEY `ix_messages_resident` (`resident_id`),
  CONSTRAINT `fk_messages_room` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_messages_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- rentals

CREATE TABLE `listings` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `resident_id`    BIGINT        NOT NULL,
  `building_id`    BIGINT        NOT NULL,
  `unit_id`        BIGINT        NULL,
  `title`          VARCHAR(150)  NOT NULL,
  `description`    TEXT          NOT NULL,
  `rent`           DECIMAL(10,2) NOT NULL,
  `available_from` DATE          NOT NULL,
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_listings_building` (`building_id`),
  KEY `ix_listings_unit` (`unit_id`),
  KEY `ix_listings_resident` (`resident_id`),
  CONSTRAINT `fk_listings_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_listings_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_listings_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rental_requests` (
  `id`           BIGINT     NOT NULL AUTO_INCREMENT,
  `listing_id`   BIGINT     NOT NULL,
  `tenant_id`    BIGINT     NOT NULL,
  `status`       VARCHAR(8) NOT NULL DEFAULT 'pending',
  `requested_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_rental_requests_listing` (`listing_id`),
  KEY `ix_rental_requests_tenant` (`tenant_id`),
  CONSTRAINT `fk_rental_requests_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rental_requests_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `contracts` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `request_id`    BIGINT       NOT NULL,
  `contract_path` VARCHAR(255) NOT NULL,
  `signed_at`     DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_contracts_request` (`request_id`),
  CONSTRAINT `fk_contracts_request` FOREIGN KEY (`request_id`) REFERENCES `rental_requests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- utilities

CREATE TABLE `utility_meters` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `unit_id`      BIGINT       NOT NULL,
  `type`         VARCHAR(11)  NOT NULL,
  `meter_number` VARCHAR(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_utility_meters_number` (`meter_number`),
  UNIQUE KEY `uq_utility_meters_unit_type` (`unit_id`, `type`),
  CONSTRAINT `fk_utility_meters_unit` FOREIGN KEY (`unit_id`) REFERENCES `units` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `utility_bills` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `meter_id`      BIGINT        NOT NULL,
  `reading_date`  DATE          NOT NULL,
  `reading_value` DECIMAL(10,2) NOT NULL,
  `amount`        DECIMAL(10,2) NOT NULL,
  `status`        VARCHAR(7)    NOT NULL DEFAULT 'pending',
  PRIMARY KEY (`id`),
  KEY `ix_utility_bills_meter_date` (`meter_id`, `reading_date`),
  CONSTRAINT `fk_utility_bills_meter` FOREIGN KEY (`meter_id`) REFERENCES `utility_meters` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- assets

CREATE TABLE `assets` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`     BIGINT       NOT NULL,
  `name`            VARCHAR(150) NOT NULL,
  `type`            VARCHAR(100) NOT NULL,
  `purchase_date`   DATE         NULL,
  `warranty_expiry` DATE         NULL,
  `status`          VARCHAR(18)  NOT NULL DEFAULT 'operational',
  PRIMARY KEY (`id`),
  KEY `ix_assets_building` (`building_id`),
  CONSTRAINT `fk_assets_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `asset_maintenance` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `asset_id`       BIGINT        NOT NULL,
  `scheduled_date` DATE          NOT NULL,
  `completed_date` DATE          NULL,
  `description`    TEXT          NULL,
  `cost`           DECIMAL(10,2) NULL,
  `vendor_id`      BIGINT        NULL,
  PRIMARY KEY (`id`),
  KEY `ix_asset_maintenance_asset` (`asset_id`),
  KEY `ix_asset_maintenance_vendor` (`vendor_id`),
  CONSTRAINT `fk_asset_maintenance_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asset_maintenance_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- gate, lift, waste

CREATE TABLE `gate_events` (
  `id`          BIGINT     NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT     NOT NULL,
  `event_type`  VARCHAR(5) NOT NULL,
  `timestamp`   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actor_id`    BIGINT     NULL,
  PRIMARY KEY (`id`),
  KEY `ix_gate_events_building_time` (`building_id`, `timestamp`),
  KEY `ix_gate_events_actor` (`actor_id`),
  CONSTRAINT `fk_gate_events_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_gate_events_actor` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `lift_status_logs` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT      NOT NULL,
  `asset_id`    BIGINT      NULL,
  `status`      VARCHAR(12) NOT NULL,
  `timestamp`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_lift_status_building` (`building_id`),
  KEY `ix_lift_status_asset` (`asset_id`),
  CONSTRAINT `fk_lift_status_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_lift_status_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `waste_schedules` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `building_id`   BIGINT      NOT NULL,
  `schedule_time` DATETIME    NOT NULL,
  `recurring`     VARCHAR(50) NULL,
  PRIMARY KEY (`id`),
  KEY `ix_waste_schedules_building` (`building_id`),
  CONSTRAINT `fk_waste_schedules_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- notifications & events

CREATE TABLE `notifications` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT      NOT NULL,
  `resident_id` BIGINT      NULL,
  `type`        VARCHAR(50) NOT NULL,
  `message`     TEXT        NOT NULL,
  `sent_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_read`     TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `ix_notifications_building_sent` (`building_id`, `sent_at`),
  KEY `ix_notifications_resident_read` (`resident_id`, `is_read`),
  CONSTRAINT `fk_notifications_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_notifications_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `events` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id`   BIGINT       NOT NULL,
  `title`         VARCHAR(200) NOT NULL,
  `description`   TEXT         NULL,
  `event_date`    DATETIME     NOT NULL,
  `created_by_id` BIGINT       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_events_building` (`building_id`),
  KEY `ix_events_created_by` (`created_by_id`),
  CONSTRAINT `fk_events_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_events_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_attendees` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `event_id`    BIGINT      NOT NULL,
  `resident_id` BIGINT      NOT NULL,
  `status`      VARCHAR(11) NOT NULL DEFAULT 'interested',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_attendees` (`event_id`, `resident_id`),
  KEY `ix_event_attendees_resident` (`resident_id`),
  CONSTRAINT `fk_event_attendees_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_event_attendees_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- access & emergency contacts

CREATE TABLE `access_cards` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `resident_id` BIGINT       NOT NULL,
  `card_number` VARCHAR(100) NOT NULL,
  `issued_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status`      VARCHAR(8)   NOT NULL DEFAULT 'active',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_access_cards_number` (`card_number`),
  KEY `ix_access_cards_resident` (`resident_id`),
  CONSTRAINT `fk_access_cards_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `emergency_contacts` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT       NOT NULL,
  `name`        VARCHAR(100) NOT NULL,
  `phone`       VARCHAR(20)  NOT NULL,
  `type`        VARCHAR(12)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_emergency_contacts_building` (`building_id`),
  CONSTRAINT `fk_emergency_contacts_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- parking

CREATE TABLE `parking_slots` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT      NOT NULL,
  `slot_number` VARCHAR(50) NOT NULL,
  `status`      VARCHAR(9)  NOT NULL DEFAULT 'available',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_parking_slots_building_number` (`building_id`, `slot_number`),
  CONSTRAINT `fk_parking_slots_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vehicles` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `resident_id`     BIGINT      NOT NULL,
  `parking_slot_id` BIGINT      NULL,
  `vehicle_number`  VARCHAR(50) NOT NULL,
  `type`            VARCHAR(10) NOT NULL DEFAULT 'car',
  `registered_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_vehicles_number` (`vehicle_number`),
  KEY `ix_vehicles_resident` (`resident_id`),
  KEY `ix_vehicles_slot` (`parking_slot_id`),
  CONSTRAINT `fk_vehicles_resident` FOREIGN KEY (`resident_id`) REFERENCES `residents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_vehicles_slot` FOREIGN KEY (`parking_slot_id`) REFERENCES `parking_slots` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- ML registry

CREATE TABLE `ml_models` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100) NOT NULL,
  `version`       VARCHAR(50)  NOT NULL,
  `artifact_path` VARCHAR(255) NOT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ml_models_name_version` (`name`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ml_training_runs` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `model_id`     BIGINT   NOT NULL,
  `started_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` DATETIME NULL,
  `params_json`  JSON     NULL,
  `metrics_json` JSON     NULL,
  PRIMARY KEY (`id`),
  KEY `ix_ml_training_runs_model` (`model_id`),
  CONSTRAINT `fk_ml_training_runs_model` FOREIGN KEY (`model_id`) REFERENCES `ml_models` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ml_city_price_cache` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `city`        VARCHAR(120)  NOT NULL,
  `currency`    VARCHAR(10)   NOT NULL DEFAULT 'BDT',
  `estimate`    DECIMAL(12,2) NOT NULL,
  `model_id`    BIGINT        NOT NULL,
  `computed_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ml_city_price_cache` (`city`, `model_id`),
  CONSTRAINT `fk_ml_city_price_cache_model` FOREIGN KEY (`model_id`) REFERENCES `ml_models` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- platform

CREATE TABLE `activity_logs` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT      NOT NULL,
  `entity_type`  VARCHAR(50) NOT NULL,
  `entity_id`    INT         NOT NULL,
  `action`       VARCHAR(50) NOT NULL,
  `details_json` JSON        NULL,
  `timestamp`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_activity_logs_user` (`user_id`),
  KEY `ix_activity_logs_entity` (`entity_type`, `entity_id`),
  CONSTRAINT `fk_activity_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `building_settings` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `building_id` BIGINT       NOT NULL,
  `key_name`    VARCHAR(100) NOT NULL,
  `value_json`  JSON         NULL,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_building_settings` (`building_id`, `key_name`),
  CONSTRAINT `fk_building_settings_building` FOREIGN KEY (`building_id`) REFERENCES `buildings` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
