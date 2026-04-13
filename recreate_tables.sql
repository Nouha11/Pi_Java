-- ============================================================
-- Run this in phpMyAdmin with nova_db selected
-- This recreates the corrupted library tables
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Drop corrupted tables
DROP TABLE IF EXISTS book_library;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS loans;
DROP TABLE IF EXISTS books;
DROP TABLE IF EXISTS libraries;

-- ── libraries ──────────────────────────────────────────────
CREATE TABLE libraries (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(255) NULL,
    latitude   DECIMAL(10,7) NULL,
    longitude  DECIMAL(10,7) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── books ──────────────────────────────────────────────────
CREATE TABLE books (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    description  TEXT NULL,
    is_digital   TINYINT(1) NOT NULL DEFAULT 1,
    price        DECIMAL(10,2) NULL,
    cover_image  VARCHAR(255) NULL,
    pdf_url      VARCHAR(500) NULL,
    author       VARCHAR(255) NULL,
    isbn         VARCHAR(20) NULL,
    published_at DATETIME NULL,
    uploader_id  INT NULL,
    user_id      INT NULL,
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NULL,
    type         VARCHAR(20) NULL DEFAULT 'physical',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── book_library (many-to-many) ────────────────────────────
CREATE TABLE book_library (
    book_id    INT NOT NULL,
    library_id INT NOT NULL,
    PRIMARY KEY (book_id, library_id),
    FOREIGN KEY (book_id)    REFERENCES books(id)     ON DELETE CASCADE,
    FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── loans ──────────────────────────────────────────────────
CREATE TABLE loans (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    book_id          INT NOT NULL,
    user_id          INT NOT NULL,
    library_id       INT NULL,
    start_at         DATETIME NOT NULL,
    end_at           DATETIME NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at     DATETIME NOT NULL,
    approved_at      DATETIME NULL,
    rejection_reason TEXT NULL,
    FOREIGN KEY (book_id)    REFERENCES books(id)     ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)     ON DELETE CASCADE,
    FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── payments ───────────────────────────────────────────────
CREATE TABLE payments (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT NOT NULL,
    book_id          INT NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    payment_method   VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_id   VARCHAR(100) NOT NULL UNIQUE,
    card_last_four   VARCHAR(20) NULL,
    card_holder_name VARCHAR(50) NULL,
    failure_reason   TEXT NULL,
    created_at       DATETIME NOT NULL,
    completed_at     DATETIME NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ── Sample library data ────────────────────────────────────
INSERT INTO libraries (name, address, latitude, longitude) VALUES
('Bibliothèque Nationale de Tunisie', '20 Souk El Attarine, Tunis', 36.7992, 10.1706),
('Bibliothèque Municipale de Tunis', 'Avenue de France, Tunis', 36.8008, 10.1797),
('Bibliothèque de Sfax', 'Avenue Habib Bourguiba, Sfax', 34.7406, 10.7603),
('Bibliothèque de Sousse', 'Avenue Léopold Senghor, Sousse', 35.8245, 10.6346),
('Bibliothèque de Monastir', 'Avenue de la République, Monastir', 35.7643, 10.8113);
