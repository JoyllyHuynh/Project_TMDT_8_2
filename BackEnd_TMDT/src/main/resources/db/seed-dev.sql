-- ComicWorm development catalog/product seed.
-- Run manually against the existing comicworm database.
-- This script does not create, alter, truncate, or drop any table.
-- It does not touch Phase 1 test accounts or authentication session/token data.

USE comicworm;
SET NAMES utf8mb4;
START TRANSACTION;

-- Categories: 3 roots and 8 children. Parent rows are inserted first.
INSERT INTO categories (name, slug, parent_id)
SELECT 'Manga', 'manga', NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE slug = 'manga'
);

INSERT INTO categories (name, slug, parent_id)
SELECT 'Manhwa', 'manhwa', NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE slug = 'manhwa'
);

INSERT INTO categories (name, slug, parent_id)
SELECT 'Light Novel', 'light-novel', NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM categories
    WHERE slug = 'light-novel'
);

INSERT INTO categories (name, slug, parent_id)
SELECT 'Shonen', 'manga-shonen', parent.id
FROM categories AS parent
WHERE parent.slug = 'manga'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manga-shonen'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Shojo', 'manga-shojo', parent.id
FROM categories AS parent
WHERE parent.slug = 'manga'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manga-shojo'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Seinen', 'manga-seinen', parent.id
FROM categories AS parent
WHERE parent.slug = 'manga'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manga-seinen'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Action', 'manhwa-action', parent.id
FROM categories AS parent
WHERE parent.slug = 'manhwa'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manhwa-action'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Romance', 'manhwa-romance', parent.id
FROM categories AS parent
WHERE parent.slug = 'manhwa'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manhwa-romance'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Fantasy', 'manhwa-fantasy', parent.id
FROM categories AS parent
WHERE parent.slug = 'manhwa'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'manhwa-fantasy'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Fantasy', 'light-novel-fantasy', parent.id
FROM categories AS parent
WHERE parent.slug = 'light-novel'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'light-novel-fantasy'
  );

INSERT INTO categories (name, slug, parent_id)
SELECT 'Adventure', 'light-novel-adventure', parent.id
FROM categories AS parent
WHERE parent.slug = 'light-novel'
  AND NOT EXISTS (
      SELECT 1
      FROM categories
      WHERE slug = 'light-novel-adventure'
  );

-- The schema has no unique key on authors.name. NOT EXISTS avoids duplicates
-- on normal sequential reruns without changing the existing schema.
INSERT INTO authors (name)
SELECT seed.name
FROM (
    SELECT 'Eiichiro Oda' AS name
    UNION ALL SELECT 'Masashi Kishimoto'
    UNION ALL SELECT 'Hajime Isayama'
    UNION ALL SELECT 'Gege Akutami'
    UNION ALL SELECT 'Naoki Urasawa'
    UNION ALL SELECT 'Tatsuya Endo'
    UNION ALL SELECT 'Kentaro Miura'
    UNION ALL SELECT 'Chugong'
) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM authors AS existing
    WHERE existing.name = seed.name
);

INSERT INTO publishers (name, slug)
SELECT seed.name, seed.slug
FROM (
    SELECT 'Shueisha' AS name, 'shueisha' AS slug
    UNION ALL SELECT 'Kodansha', 'kodansha'
    UNION ALL SELECT 'Viz Media', 'viz-media'
    UNION ALL SELECT 'NXB Kim Dong', 'nxb-kim-dong'
    UNION ALL SELECT 'NXB Tre', 'nxb-tre'
    UNION ALL SELECT 'Yen Press', 'yen-press'
) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM publishers AS existing
    WHERE existing.slug = seed.slug
);

-- Dedicated development seller. Existing password hashes are never replaced.
-- Password for a newly inserted seller: ComicWormDev2026!
-- Hash generated using Spring Security BCryptPasswordEncoder (strength 10).
INSERT INTO users (
    email,
    password_hash,
    full_name,
    role,
    is_seller,
    account_status
)
SELECT
    'seller@comicworm.com',
    '$2a$10$7eS86uxi5DSsTzhj.PI7A.iLZJwSCs7Tw4TqAmsRKE5TRaaKO6r3a',
    'ComicWorm Seller',
    'USER',
    TRUE,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'seller@comicworm.com'
);

-- Only normalizes the explicitly dedicated development seller account.
-- In particular, test@comicworm.com and all auth tables are untouched.
UPDATE users
SET role = 'USER',
    is_seller = TRUE,
    account_status = 'ACTIVE',
    deleted_at = NULL
WHERE email = 'seller@comicworm.com';

-- 14 approved and active products cover search, category, price, condition,
-- stock, listing type, and pagination. Fields match comicworm_schema.sql.
INSERT INTO products (
    seller_id,
    title,
    slug,
    description,
    category_id,
    author_id,
    publisher_id,
    volume_numbers,
    edition_type,
    publication_year,
    condition_percent,
    price,
    stock_quantity,
    listing_type,
    trade_wish_note,
    is_active,
    moderation_status
)
SELECT
    seller.id,
    seed.title,
    seed.slug,
    seed.description,
    category.id,
    author.id,
    publisher.id,
    seed.volume_numbers,
    seed.edition_type,
    seed.publication_year,
    seed.condition_percent,
    seed.price,
    seed.stock_quantity,
    seed.listing_type,
    seed.trade_wish_note,
    TRUE,
    'APPROVED'
FROM (
    SELECT 'One Piece - Volume 1' AS title, 'dev-one-piece-volume-1' AS slug,
           'The beginning of the Straw Hat Pirates adventure. Development catalog item.' AS description,
           'manga-shonen' AS category_slug, 'Eiichiro Oda' AS author_name, 'shueisha' AS publisher_slug,
           '1' AS volume_numbers, 'Paperback' AS edition_type, 1997 AS publication_year,
           100 AS condition_percent, 75000.00 AS price, 10 AS stock_quantity,
           'SELL' AS listing_type, NULL AS trade_wish_note
    UNION ALL SELECT 'One Piece - Volume 2', 'dev-one-piece-volume-2',
           'The Straw Hat Pirates continue their journey. Development catalog item.',
           'manga-shonen', 'Eiichiro Oda', 'shueisha', '2', 'Paperback', 1998,
           98, 75000.00, 5, 'SELL_AND_TRADE', 'Trade for another One Piece volume.'
    UNION ALL SELECT 'Naruto - Volume 1', 'dev-naruto-volume-1',
           'The first volume introducing Naruto Uzumaki. Development catalog item.',
           'manga-shonen', 'Masashi Kishimoto', 'viz-media', '1', 'Paperback', 1999,
           95, 50000.00, 20, 'SELL', NULL
    UNION ALL SELECT 'Naruto - Volume 2', 'dev-naruto-volume-2',
           'A second Naruto volume for keyword and pagination testing.',
           'manga-shonen', 'Masashi Kishimoto', 'viz-media', '2', 'Paperback', 2000,
           90, 50000.00, 3, 'TRADE', 'Looking for another classic shonen manga.'
    UNION ALL SELECT 'Attack on Titan - Volume 1', 'dev-attack-on-titan-volume-1',
           'The first volume of the human survival story. Development catalog item.',
           'manga-shonen', 'Hajime Isayama', 'kodansha', '1', 'Paperback', 2009,
           80, 100000.00, 0, 'SELL', NULL
    UNION ALL SELECT 'Attack on Titan - Volume 2', 'dev-attack-on-titan-volume-2',
           'The next chapter in Attack on Titan. Development catalog item.',
           'manga-shonen', 'Hajime Isayama', 'kodansha', '2', 'Paperback', 2010,
           98, 100000.00, 5, 'SELL', NULL
    UNION ALL SELECT 'Jujutsu Kaisen - Volume 1', 'dev-jujutsu-kaisen-volume-1',
           'Yuji Itadori enters the world of curses. Development catalog item.',
           'manga-shonen', 'Gege Akutami', 'shueisha', '1', 'Paperback', 2018,
           95, 50000.00, 3, 'SELL', NULL
    UNION ALL SELECT 'Jujutsu Kaisen - Volume 2', 'dev-jujutsu-kaisen-volume-2',
           'The next volume of Yuji Itadoris story. Development catalog item.',
           'manga-shonen', 'Gege Akutami', 'shueisha', '2', 'Paperback', 2019,
           90, 50000.00, 10, 'SELL', NULL
    UNION ALL SELECT 'Spy x Family - Volume 1', 'dev-spy-family-volume-1',
           'A spy, an assassin, and a telepath form an unusual family.',
           'manga-shonen', 'Tatsuya Endo', 'shueisha', '1', 'Paperback', 2019,
           90, 75000.00, 20, 'SELL', NULL
    UNION ALL SELECT 'Spy x Family - Volume 2', 'dev-spy-family-volume-2',
           'More missions and family moments from Spy x Family.',
           'manga-shonen', 'Tatsuya Endo', 'shueisha', '2', 'Paperback', 2020,
           98, 75000.00, 5, 'TRADE', 'Looking for volume 3 or 4.'
    UNION ALL SELECT 'Berserk - Volume 1', 'dev-berserk-volume-1',
           'Guts begins his dark fantasy journey. Development catalog item.',
           'manga-seinen', 'Kentaro Miura', 'viz-media', '1', 'Deluxe Edition', 1989,
           80, 250000.00, 3, 'SELL', NULL
    UNION ALL SELECT 'Monster - Volume 1', 'dev-monster-volume-1',
           'Dr. Tenma faces the consequences of a life-changing decision.',
           'manga-seinen', 'Naoki Urasawa', 'viz-media', '1', 'Paperback', 1994,
           90, 150000.00, 0, 'SELL', NULL
    UNION ALL SELECT 'Solo Leveling - Volume 1', 'dev-solo-leveling-volume-1',
           'A low-ranked hunter begins an unexpected path to power.',
           'manhwa-action', 'Chugong', 'yen-press', '1', 'Paperback', 2019,
           95, 150000.00, 5, 'SELL_AND_TRADE', 'Trade offers for fantasy manhwa welcome.'
    UNION ALL SELECT 'Naruto - Volume 3', 'dev-naruto-volume-3',
           'A third Naruto volume for keyword and pagination testing.',
           'manga-shonen', 'Masashi Kishimoto', 'viz-media', '3', 'Paperback', 2000,
           98, 35000.00, 10, 'SELL', NULL
) AS seed
JOIN users AS seller
  ON seller.email = 'seller@comicworm.com'
 AND seller.role = 'USER'
 AND seller.is_seller = TRUE
 AND seller.account_status = 'ACTIVE'
 AND seller.deleted_at IS NULL
JOIN categories AS category
  ON category.slug = seed.category_slug
JOIN authors AS author
  ON author.id = (
      SELECT existing_author.id
      FROM authors AS existing_author
      WHERE existing_author.name = seed.author_name
      ORDER BY existing_author.id
      LIMIT 1
  )
JOIN publishers AS publisher
  ON publisher.slug = seed.publisher_slug
WHERE NOT EXISTS (
    SELECT 1
    FROM products AS existing_product
    WHERE existing_product.slug = seed.slug
);

-- Two public placeholder images per seeded product.
-- The unique (product_id, display_order) key and NOT EXISTS make inserts rerunnable.
INSERT INTO product_images (product_id, image_url, display_order)
SELECT product.id, seed.image_url, seed.display_order
FROM (
    SELECT 'dev-one-piece-volume-1' AS product_slug, 'https://placehold.co/600x900/png?text=One+Piece+Vol+1' AS image_url, 0 AS display_order
    UNION ALL SELECT 'dev-one-piece-volume-1', 'https://placehold.co/600x900/png?text=One+Piece+Vol+1+Back', 1
    UNION ALL SELECT 'dev-one-piece-volume-2', 'https://placehold.co/600x900/png?text=One+Piece+Vol+2', 0
    UNION ALL SELECT 'dev-one-piece-volume-2', 'https://placehold.co/600x900/png?text=One+Piece+Vol+2+Back', 1
    UNION ALL SELECT 'dev-naruto-volume-1', 'https://placehold.co/600x900/png?text=Naruto+Vol+1', 0
    UNION ALL SELECT 'dev-naruto-volume-1', 'https://placehold.co/600x900/png?text=Naruto+Vol+1+Back', 1
    UNION ALL SELECT 'dev-naruto-volume-2', 'https://placehold.co/600x900/png?text=Naruto+Vol+2', 0
    UNION ALL SELECT 'dev-naruto-volume-2', 'https://placehold.co/600x900/png?text=Naruto+Vol+2+Back', 1
    UNION ALL SELECT 'dev-attack-on-titan-volume-1', 'https://placehold.co/600x900/png?text=Attack+on+Titan+Vol+1', 0
    UNION ALL SELECT 'dev-attack-on-titan-volume-1', 'https://placehold.co/600x900/png?text=Attack+on+Titan+Vol+1+Back', 1
    UNION ALL SELECT 'dev-attack-on-titan-volume-2', 'https://placehold.co/600x900/png?text=Attack+on+Titan+Vol+2', 0
    UNION ALL SELECT 'dev-attack-on-titan-volume-2', 'https://placehold.co/600x900/png?text=Attack+on+Titan+Vol+2+Back', 1
    UNION ALL SELECT 'dev-jujutsu-kaisen-volume-1', 'https://placehold.co/600x900/png?text=Jujutsu+Kaisen+Vol+1', 0
    UNION ALL SELECT 'dev-jujutsu-kaisen-volume-1', 'https://placehold.co/600x900/png?text=Jujutsu+Kaisen+Vol+1+Back', 1
    UNION ALL SELECT 'dev-jujutsu-kaisen-volume-2', 'https://placehold.co/600x900/png?text=Jujutsu+Kaisen+Vol+2', 0
    UNION ALL SELECT 'dev-jujutsu-kaisen-volume-2', 'https://placehold.co/600x900/png?text=Jujutsu+Kaisen+Vol+2+Back', 1
    UNION ALL SELECT 'dev-spy-family-volume-1', 'https://placehold.co/600x900/png?text=Spy+Family+Vol+1', 0
    UNION ALL SELECT 'dev-spy-family-volume-1', 'https://placehold.co/600x900/png?text=Spy+Family+Vol+1+Back', 1
    UNION ALL SELECT 'dev-spy-family-volume-2', 'https://placehold.co/600x900/png?text=Spy+Family+Vol+2', 0
    UNION ALL SELECT 'dev-spy-family-volume-2', 'https://placehold.co/600x900/png?text=Spy+Family+Vol+2+Back', 1
    UNION ALL SELECT 'dev-berserk-volume-1', 'https://placehold.co/600x900/png?text=Berserk+Vol+1', 0
    UNION ALL SELECT 'dev-berserk-volume-1', 'https://placehold.co/600x900/png?text=Berserk+Vol+1+Back', 1
    UNION ALL SELECT 'dev-monster-volume-1', 'https://placehold.co/600x900/png?text=Monster+Vol+1', 0
    UNION ALL SELECT 'dev-monster-volume-1', 'https://placehold.co/600x900/png?text=Monster+Vol+1+Back', 1
    UNION ALL SELECT 'dev-solo-leveling-volume-1', 'https://placehold.co/600x900/png?text=Solo+Leveling+Vol+1', 0
    UNION ALL SELECT 'dev-solo-leveling-volume-1', 'https://placehold.co/600x900/png?text=Solo+Leveling+Vol+1+Back', 1
    UNION ALL SELECT 'dev-naruto-volume-3', 'https://placehold.co/600x900/png?text=Naruto+Vol+3', 0
    UNION ALL SELECT 'dev-naruto-volume-3', 'https://placehold.co/600x900/png?text=Naruto+Vol+3+Back', 1
) AS seed
JOIN products AS product
  ON product.slug = seed.product_slug
WHERE NOT EXISTS (
    SELECT 1
    FROM product_images AS existing_image
    WHERE existing_image.product_id = product.id
      AND existing_image.display_order = seed.display_order
);

COMMIT;

-- Overall table counts; compare with the database before and after the seed.
SELECT 'categories' AS table_name, COUNT(*) AS row_count FROM categories
UNION ALL SELECT 'authors', COUNT(*) FROM authors
UNION ALL SELECT 'publishers', COUNT(*) FROM publishers
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'product_images', COUNT(*) FROM product_images;

-- Seed-scoped counts, unaffected by unrelated application records.
SELECT COUNT(*) AS development_categories
FROM categories
WHERE slug IN (
    'manga', 'manhwa', 'light-novel', 'manga-shonen', 'manga-shojo',
    'manga-seinen', 'manhwa-action', 'manhwa-romance', 'manhwa-fantasy',
    'light-novel-fantasy', 'light-novel-adventure'
);

SELECT COUNT(*) AS development_authors
FROM authors
WHERE name IN (
    'Eiichiro Oda', 'Masashi Kishimoto', 'Hajime Isayama', 'Gege Akutami',
    'Naoki Urasawa', 'Tatsuya Endo', 'Kentaro Miura', 'Chugong'
);

SELECT COUNT(*) AS development_publishers
FROM publishers
WHERE slug IN (
    'shueisha', 'kodansha', 'viz-media', 'nxb-kim-dong', 'nxb-tre', 'yen-press'
);

SELECT COUNT(*) AS development_sellers
FROM users
WHERE email = 'seller@comicworm.com'
  AND role = 'USER'
  AND is_seller = TRUE
  AND account_status = 'ACTIVE'
  AND deleted_at IS NULL;

SELECT COUNT(*) AS development_products
FROM products
WHERE slug LIKE 'dev-%'
  AND is_active = TRUE
  AND moderation_status = 'APPROVED'
  AND deleted_at IS NULL;

SELECT COUNT(*) AS development_product_images
FROM product_images AS image
JOIN products AS product
  ON product.id = image.product_id
WHERE product.slug LIKE 'dev-%';