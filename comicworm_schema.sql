-- ============================================================
-- COMICWORM - DATABASE SCHEMA
-- Website mua bán / trao đổi truyện
-- MySQL 8.x
-- Generated from Nhom8_ChucNang.docx DBML
-- ============================================================

DROP DATABASE IF EXISTS comicworm;

CREATE DATABASE comicworm
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE comicworm;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. AUTHENTICATION
-- ============================================================

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255),
    google_sub VARCHAR(255),
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    avatar_url TEXT,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_seller BOOLEAN NOT NULL DEFAULT FALSE,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_google_sub (google_sub),

    CONSTRAINT ck_user_role
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_account_status
        CHECK (account_status IN ('ACTIVE', 'LOCKED')),
    CONSTRAINT ck_user_login_identity
        CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE auth_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_sessions_token_hash (token_hash),
    KEY idx_session_user_expiry (user_id, expires_at),

    CONSTRAINT fk_auth_sessions_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_password_reset_tokens_token_hash (token_hash),

    CONSTRAINT fk_password_reset_tokens_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. CATALOG
-- ============================================================

CREATE TABLE categories (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    parent_id INT,

    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_slug (slug),

    CONSTRAINT fk_categories_parent_id
        FOREIGN KEY (parent_id)
        REFERENCES categories(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE authors (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE publishers (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(160) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_publishers_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. PRODUCTS
-- ============================================================

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category_id INT NOT NULL,
    author_id INT,
    publisher_id INT,
    volume_numbers VARCHAR(100),
    edition_type VARCHAR(50),
    publication_year INT,
    condition_percent INT NOT NULL,
    price DECIMAL(15,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 1,
    listing_type VARCHAR(20) NOT NULL DEFAULT 'SELL',
    trade_wish_note TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    moderated_by BIGINT,
    moderation_reason TEXT,
    moderated_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME,

    PRIMARY KEY (id),

    UNIQUE KEY uq_products_slug (slug),

    -- Required for composite FK (product_id, seller_id)
    UNIQUE KEY uq_product_owner (id, seller_id),

    KEY idx_product_latest (
        moderation_status,
        is_active,
        created_at
    ),

    KEY idx_product_filter (
        category_id,
        price
    ),

    KEY idx_seller_products (
        seller_id,
        created_at
    ),

    CONSTRAINT ck_product_condition
        CHECK (condition_percent BETWEEN 0 AND 100),

    CONSTRAINT ck_product_price
        CHECK (price >= 0),

    CONSTRAINT ck_product_stock
        CHECK (stock_quantity >= 0),

    CONSTRAINT ck_product_listing_type
        CHECK (listing_type IN ('SELL', 'TRADE', 'SELL_AND_TRADE')),

    CONSTRAINT ck_product_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),

    CONSTRAINT fk_products_seller_id
        FOREIGN KEY (seller_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_products_category_id
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_products_author_id
        FOREIGN KEY (author_id)
        REFERENCES authors(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_products_publisher_id
        FOREIGN KEY (publisher_id)
        REFERENCES publishers(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_products_moderated_by
        FOREIGN KEY (moderated_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_url TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uq_product_image_order (product_id, display_order),

    CONSTRAINT ck_product_image_display_order
        CHECK (display_order >= 0),

    CONSTRAINT fk_product_images_product_id
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. CART
-- ============================================================

CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_carts_user_id (user_id),

    CONSTRAINT fk_carts_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    is_selected BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    UNIQUE KEY uq_cart_product (cart_id, product_id),

    CONSTRAINT ck_cart_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT fk_cart_items_cart_id
        FOREIGN KEY (cart_id)
        REFERENCES carts(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_cart_items_product_id
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 5. CHECKOUT
-- ============================================================

CREATE TABLE checkout_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    checkout_code VARCHAR(60) NOT NULL,
    buyer_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_checkout_code (checkout_code),
    UNIQUE KEY uq_checkout_idempotency_key (idempotency_key),

    CONSTRAINT ck_checkout_total_amount
        CHECK (total_amount >= 0),

    CONSTRAINT fk_checkout_batches_buyer_id
        FOREIGN KEY (buyer_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 6. ORDERS
-- ============================================================

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_code VARCHAR(60) NOT NULL,
    checkout_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,

    subtotal_amount DECIMAL(15,2) NOT NULL,
    shipping_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(15,2) NOT NULL,

    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    buyer_note TEXT,

    status VARCHAR(25) NOT NULL DEFAULT 'WAITING_PAYMENT',
    payment_status VARCHAR(25) NOT NULL DEFAULT 'PENDING',

    cancelled_by BIGINT,
    cancel_reason TEXT,
    cancelled_at DATETIME,
    stock_released_at DATETIME,
    completed_at DATETIME,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_order_code (order_code),

    KEY idx_buyer_history (buyer_id, created_at),
    KEY idx_seller_orders (seller_id, status, created_at),
    KEY idx_order_reports (status, completed_at),

    CONSTRAINT ck_order_subtotal
        CHECK (subtotal_amount >= 0),

    CONSTRAINT ck_order_shipping_fee
        CHECK (shipping_fee >= 0),

    CONSTRAINT ck_order_total_amount
        CHECK (total_amount >= 0),

    CONSTRAINT ck_order_total
        CHECK (total_amount = subtotal_amount + shipping_fee),

    CONSTRAINT ck_order_status
        CHECK (
            status IN (
                'WAITING_PAYMENT',
                'WAITING_CONFIRM',
                'PACKING',
                'SHIPPING',
                'DELIVERED',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_order_payment_status
        CHECK (
            payment_status IN (
                'PENDING',
                'PAID',
                'PARTIALLY_REFUNDED',
                'REFUNDED'
            )
        ),

    CONSTRAINT fk_orders_checkout_id
        FOREIGN KEY (checkout_id)
        REFERENCES checkout_batches(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_orders_buyer_id
        FOREIGN KEY (buyer_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_orders_seller_id
        FOREIGN KEY (seller_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_orders_cancelled_by
        FOREIGN KEY (cancelled_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    product_title VARCHAR(255) NOT NULL,
    image_url TEXT,
    condition_percent INT NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    quantity INT NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_order_product (order_id, product_id),

    CONSTRAINT ck_order_item_condition
        CHECK (condition_percent BETWEEN 0 AND 100),

    CONSTRAINT ck_order_item_unit_price
        CHECK (unit_price >= 0),

    CONSTRAINT ck_order_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT fk_order_items_order_id
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_order_items_product_id
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    previous_status VARCHAR(25),
    new_status VARCHAR(25) NOT NULL,
    changed_by BIGINT,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_order_status_history_order_id
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_order_status_history_changed_by
        FOREIGN KEY (changed_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 7. PAYMENTS
-- ============================================================

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_code VARCHAR(60) NOT NULL,
    checkout_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_transaction_id VARCHAR(150),
    idempotency_key VARCHAR(100) NOT NULL,
    paid_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_payment_code (payment_code),
    UNIQUE KEY uq_payment_idempotency_key (idempotency_key),
    UNIQUE KEY uq_payment_provider (method, provider_transaction_id),

    CONSTRAINT ck_payment_method
        CHECK (
            method IN (
                'VNPAY',
                'MOMO',
                'VIETQR',
                'CREDIT_CARD'
            )
        ),

    CONSTRAINT ck_payment_amount
        CHECK (amount > 0),

    CONSTRAINT ck_payment_status
        CHECK (
            status IN (
                'PENDING',
                'SUCCEEDED',
                'FAILED',
                'EXPIRED',
                'CANCELLED'
            )
        ),

    CONSTRAINT fk_payments_checkout_id
        FOREIGN KEY (checkout_id)
        REFERENCES checkout_batches(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE payment_allocations (
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,

    PRIMARY KEY (payment_id, order_id),

    CONSTRAINT ck_payment_allocation_amount
        CHECK (amount > 0),

    CONSTRAINT fk_payment_allocations_payment_id
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_payment_allocations_order_id
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE payment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    provider_event_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at DATETIME,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_payment_event (
        payment_id,
        provider_event_id
    ),

    CONSTRAINT fk_payment_events_payment_id
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_code VARCHAR(60) NOT NULL,
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) NOT NULL,
    provider_refund_id VARCHAR(150),
    failure_reason TEXT,
    completed_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_refund_code (refund_code),
    UNIQUE KEY uq_refund_idempotency_key (idempotency_key),
    UNIQUE KEY uq_provider_refund_id (provider_refund_id),

    CONSTRAINT ck_refund_amount
        CHECK (amount > 0),

    CONSTRAINT ck_refund_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'SUCCEEDED',
                'FAILED'
            )
        ),

    CONSTRAINT fk_refund_paid_order
        FOREIGN KEY (payment_id, order_id)
        REFERENCES payment_allocations(payment_id, order_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 8. REVIEWS
-- ============================================================

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_reviews_order_item_id (order_item_id),

    CONSTRAINT ck_review_rating
        CHECK (rating BETWEEN 1 AND 5),

    CONSTRAINT fk_reviews_order_item_id
        FOREIGN KEY (order_item_id)
        REFERENCES order_items(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_reviews_buyer_id
        FOREIGN KEY (buyer_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE review_replies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_review_replies_review_id (review_id),

    CONSTRAINT fk_review_replies_review_id
        FOREIGN KEY (review_id)
        REFERENCES reviews(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_review_replies_seller_id
        FOREIGN KEY (seller_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 9. LOYALTY / POINTS
-- ============================================================

CREATE TABLE loyalty_accounts (
    user_id BIGINT NOT NULL,
    points_balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),

    CONSTRAINT ck_loyalty_points_balance
        CHECK (points_balance >= 0),

    CONSTRAINT fk_loyalty_accounts_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE loyalty_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    points_delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_loyalty_transaction_idempotency (
        idempotency_key
    ),

    CONSTRAINT ck_loyalty_transaction_type
        CHECK (
            transaction_type IN ('EARN', 'REVERSAL')
        ),

    CONSTRAINT ck_loyalty_points_delta
        CHECK (points_delta <> 0),

    CONSTRAINT ck_loyalty_balance_after
        CHECK (balance_after >= 0),

    CONSTRAINT ck_loyalty_sign
        CHECK (
            (transaction_type = 'EARN' AND points_delta > 0)
            OR
            (transaction_type = 'REVERSAL' AND points_delta < 0)
        ),

    CONSTRAINT fk_loyalty_transactions_user_id
        FOREIGN KEY (user_id)
        REFERENCES loyalty_accounts(user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_loyalty_transactions_order_id
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 10. EXCHANGE / TRADE
-- ============================================================

CREATE TABLE exchange_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_code VARCHAR(60) NOT NULL,
    host_id BIGINT NOT NULL,
    target_product_id BIGINT NOT NULL,
    selected_offer_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME,

    PRIMARY KEY (id),

    UNIQUE KEY uq_exchange_room_code (room_code),

    -- Required so selected_offer_id + room_id can reference
    -- exchange_offers(id, room_id).
    UNIQUE KEY uq_room_selected_offer (selected_offer_id, id),

    CONSTRAINT ck_exchange_room_status
        CHECK (
            status IN (
                'OPEN',
                'AGREED',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT fk_exchange_rooms_host_id
        FOREIGN KEY (host_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    -- Target product must belong to the room host.
    CONSTRAINT fk_room_product_owner
        FOREIGN KEY (target_product_id, host_id)
        REFERENCES products(id, seller_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE exchange_room_members (
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (room_id, user_id),

    CONSTRAINT fk_exchange_room_members_room_id
        FOREIGN KEY (room_id)
        REFERENCES exchange_rooms(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_exchange_room_members_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE exchange_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,

    cash_compensation DECIMAL(15,2) NOT NULL DEFAULT 0,
    compensation_payer_id BIGINT,

    note TEXT,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    buyer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    host_confirmed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at DATETIME,

    PRIMARY KEY (id),

    -- Required for composite FK from exchange_rooms.
    UNIQUE KEY uq_offer_room (id, room_id),

    CONSTRAINT ck_offer_cash_compensation
        CHECK (cash_compensation >= 0),

    CONSTRAINT ck_exchange_offer_status
        CHECK (
            status IN (
                'PENDING',
                'ACCEPTED',
                'DECLINED',
                'WITHDRAWN'
            )
        ),

    CONSTRAINT ck_offer_compensation
        CHECK (
            (cash_compensation = 0 AND compensation_payer_id IS NULL)
            OR
            (cash_compensation > 0 AND compensation_payer_id IS NOT NULL)
        ),

    -- Offer creator must be a member of the room.
    CONSTRAINT fk_offer_room_member
        FOREIGN KEY (room_id, buyer_id)
        REFERENCES exchange_room_members(room_id, user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_exchange_offers_compensation_payer_id
        FOREIGN KEY (compensation_payer_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Circular dependency:
-- exchange_rooms.selected_offer_id -> exchange_offers.id
-- Therefore this FK is created after exchange_offers exists.

ALTER TABLE exchange_rooms
    ADD CONSTRAINT fk_room_selected_offer
        FOREIGN KEY (selected_offer_id, id)
        REFERENCES exchange_offers(id, room_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;


CREATE TABLE exchange_offer_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    offer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    title_snapshot VARCHAR(255) NOT NULL,
    condition_snapshot INT NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uq_offer_product (offer_id, product_id),

    CONSTRAINT ck_exchange_offer_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT ck_exchange_offer_item_condition
        CHECK (condition_snapshot BETWEEN 0 AND 100),

    CONSTRAINT fk_exchange_offer_items_offer_id
        FOREIGN KEY (offer_id)
        REFERENCES exchange_offers(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    -- Product must actually belong to owner_id.
    CONSTRAINT fk_offer_product_owner
        FOREIGN KEY (product_id, owner_id)
        REFERENCES products(id, seller_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE exchange_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT,
    image_url TEXT,
    client_message_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_exchange_message_client_key (
        client_message_key
    ),

    KEY idx_exchange_chat (room_id, id),

    CONSTRAINT ck_exchange_message_content
        CHECK (
            content IS NOT NULL
            OR image_url IS NOT NULL
        ),

    -- Only room members can send messages.
    CONSTRAINT fk_message_room_member
        FOREIGN KEY (room_id, sender_id)
        REFERENCES exchange_room_members(room_id, user_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 11. AI - IMAGE SEARCH
-- ============================================================

CREATE TABLE product_image_features (
    image_id BIGINT NOT NULL,
    embedding JSON NOT NULL,
    model_name VARCHAR(100) NOT NULL,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (image_id),

    CONSTRAINT ck_image_embedding
        CHECK (
            JSON_TYPE(embedding) = 'ARRAY'
            AND JSON_LENGTH(embedding) > 0
        ),

    CONSTRAINT fk_product_image_features_image_id
        FOREIGN KEY (image_id)
        REFERENCES product_images(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 12. AI - PRODUCT CLASSIFICATION
-- ============================================================

CREATE TABLE product_classifications (
    product_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_comic BOOLEAN,
    confidence DECIMAL(5,4),
    model_name VARCHAR(100),
    classified_at DATETIME,
    error_message TEXT,

    PRIMARY KEY (product_id),

    CONSTRAINT ck_product_classification_status
        CHECK (
            status IN (
                'PENDING',
                'SUCCEEDED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_product_classification_confidence
        CHECK (
            confidence BETWEEN 0 AND 1
        ),

    CONSTRAINT ck_classification_result
        CHECK (
            status <> 'SUCCEEDED'
            OR (
                is_comic IS NOT NULL
                AND confidence IS NOT NULL
                AND model_name IS NOT NULL
                AND classified_at IS NOT NULL
            )
        ),

    CONSTRAINT fk_product_classifications_product_id
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 13. AI - CONSULTATION
-- ============================================================

CREATE TABLE consultation_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_consultation_sessions_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE consultation_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    suggested_product_ids JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_consultation_chat (session_id, id),

    CONSTRAINT ck_consultation_sender_type
        CHECK (
            sender_type IN ('USER', 'ASSISTANT')
        ),

    CONSTRAINT fk_consultation_messages_session_id
        FOREIGN KEY (session_id)
        REFERENCES consultation_sessions(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 14. NOTIFICATIONS
-- ============================================================

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,

    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,

    deduplication_key VARCHAR(150) NOT NULL,

    read_at DATETIME,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_notification_deduplication_key (
        deduplication_key
    ),

    CONSTRAINT fk_notifications_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_notifications_order_id
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- END
-- ============================================================

SET FOREIGN_KEY_CHECKS = 1;
