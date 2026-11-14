CREATE DATABASE IF NOT EXISTS campusexchange
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campusexchange;

CREATE TABLE users (
                       user_id INT PRIMARY KEY AUTO_INCREMENT,
                       username VARCHAR(50) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(10) NOT NULL DEFAULT 'USER',
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CHECK (role IN ('USER', 'ADMIN'))
) ENGINE = InnoDB;

CREATE TABLE listings (
                          listing_id INT PRIMARY KEY AUTO_INCREMENT,
                          seller_id INT NOT NULL,
                          title VARCHAR(100) NOT NULL,
                          description TEXT NOT NULL,
                          image_path VARCHAR(255) NULL,
                          price DECIMAL(8, 2) NOT NULL,
                          category VARCHAR(30) NOT NULL,
                          status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          FOREIGN KEY (seller_id) REFERENCES users(user_id),
                          CHECK (price BETWEEN 0.01 AND 999999.00),

                          INDEX (status, created_at)
) ENGINE = InnoDB;

CREATE TABLE handover_ledger (
                                 ledger_id INT PRIMARY KEY AUTO_INCREMENT,
                                 listing_id INT NOT NULL,
                                 buyer_id INT NOT NULL,
                                 seller_id INT NOT NULL,
                                 transaction_state VARCHAR(25) NOT NULL DEFAULT 'RESERVED',
                                 version BIGINT NOT NULL DEFAULT 0,
                                 meetup_location VARCHAR(200),
                                 meetup_time DATETIME,
                                 proposed_by INT,
                                 buyer_token VARCHAR(64),
                                 seller_token VARCHAR(64),
                                 buyer_verified BOOLEAN NOT NULL DEFAULT FALSE,
                                 seller_verified BOOLEAN NOT NULL DEFAULT FALSE,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,

                                 FOREIGN KEY (listing_id) REFERENCES listings(listing_id),
                                 FOREIGN KEY (buyer_id) REFERENCES users(user_id),
                                 FOREIGN KEY (seller_id) REFERENCES users(user_id),
                                 FOREIGN KEY (proposed_by) REFERENCES users(user_id),
                                 CHECK (buyer_id <> seller_id),

                                 INDEX (buyer_id, updated_at),
                                 INDEX (seller_id, updated_at)
) ENGINE = InnoDB;

CREATE TABLE disputes (
                          dispute_id INT PRIMARY KEY AUTO_INCREMENT,
                          ledger_id INT NOT NULL UNIQUE,
                          reporter_id INT NOT NULL,
                          reason TEXT NOT NULL,
                          status VARCHAR(15) NOT NULL DEFAULT 'OPEN',
                          admin_notes TEXT,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          resolved_at TIMESTAMP NULL,

                          FOREIGN KEY (ledger_id) REFERENCES handover_ledger(ledger_id),
                          FOREIGN KEY (reporter_id) REFERENCES users(user_id)
) ENGINE = InnoDB;

SHOW TABLES;