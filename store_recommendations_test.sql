-- ─────────────────────────────────────────────────────────────────────────────
-- PAWLY — Recommendation engine test seed
-- Run against: pawly_db
-- Assumes: user id=1 exists (your test login), DataSeeder already ran (categories exist)
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Check your user id and category ids first ─────────────────────────────
-- SELECT id, email FROM users LIMIT 5;
-- SELECT id, name FROM product_categories;
-- Adjust @user_id below if needed.

SET @user_id = 1;

-- ── 2. Add pets to the user (drives species filtering) ───────────────────────
INSERT INTO pets (owner_id, name, species, breed, date_of_birth, gender, is_vaccinated, created_at, updated_at)
VALUES
  (@user_id, 'Max',  'dog', 'Labrador', '2021-03-15', 'MALE',   1, NOW(), NOW()),
  (@user_id, 'Luna', 'cat', 'Persian',  '2022-07-01', 'FEMALE', 1, NOW(), NOW());

-- ── 3. Products (15 items across all categories) ─────────────────────────────
SET @cat_food   = (SELECT id FROM product_categories WHERE name = 'Food'        LIMIT 1);
SET @cat_acc    = (SELECT id FROM product_categories WHERE name = 'Accessories' LIMIT 1);
SET @cat_toys   = (SELECT id FROM product_categories WHERE name = 'Toys'        LIMIT 1);
SET @cat_health = (SELECT id FROM product_categories WHERE name = 'Health'      LIMIT 1);
SET @cat_groom  = (SELECT id FROM product_categories WHERE name = 'Grooming'    LIMIT 1);

INSERT INTO products (name, description, price, stock_quantity, is_available, average_rating, review_count, target_species, version, category_id, created_by_id, created_at, updated_at)
VALUES
  ('Royal Canin Adult Dog 4kg',     'Complete dry food for adult dogs',           29.99, 50, 1, 4.50, 12, 'dog', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Pedigree Puppy Chicken 2kg',    'Nutritious puppy food with chicken',         14.99, 40, 1, 4.20,  8, 'dog', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Hill''s Science Diet Senior',   'Senior dog formula, joint support',          34.99, 30, 1, 4.70,  5, 'dog', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Whiskas Adult Cat Pouches',     'Variety pack wet food for adult cats',       12.99, 60, 1, 4.10, 20, 'cat', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Royal Canin Indoor Cat 2kg',    'Dry food formulated for indoor cats',        22.99, 35, 1, 4.30, 15, 'cat', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Organic Pet Treats Mix',        'All-natural treats for all pets',             9.99, 80, 1, 4.60, 30, 'all', 0, @cat_food,   @user_id, NOW(), NOW()),
  ('Heavy Duty Dog Leash 2m',       'Strong nylon leash for large breeds',        15.99, 25, 1, 4.40, 10, 'dog', 0, @cat_acc,    @user_id, NOW(), NOW()),
  ('Dog Harness — Medium',          'No-pull adjustable harness',                 24.99, 20, 1, 4.80,  7, 'dog', 0, @cat_acc,    @user_id, NOW(), NOW()),
  ('Cat Collar with Bell',          'Soft breakaway collar for cats',              7.99, 45, 1, 4.00, 18, 'cat', 0, @cat_acc,    @user_id, NOW(), NOW()),
  ('Rubber Chew Toy — Kong XL',     'Durable chew toy, fill with treats',        12.99, 35, 1, 4.90, 22, 'dog', 0, @cat_toys,   @user_id, NOW(), NOW()),
  ('Rope Tug Toy',                  'Interactive rope toy for dogs',               8.99, 50, 1, 4.30, 11, 'dog', 0, @cat_toys,   @user_id, NOW(), NOW()),
  ('Feather Wand Cat Toy',          'Telescopic wand with feather attachment',     6.99, 55, 1, 4.50, 25, 'cat', 0, @cat_toys,   @user_id, NOW(), NOW()),
  ('Omega-3 Fish Oil Supplement',   'Supports coat and joint health',             18.99, 40, 1, 4.70, 14, 'all', 0, @cat_health, @user_id, NOW(), NOW()),
  ('FURminator Dog Deshedding Tool','Reduces shedding by up to 90%',             34.99, 15, 1, 4.80,  9, 'dog', 0, @cat_groom,  @user_id, NOW(), NOW()),
  ('Self-Cleaning Slicker Brush',   'Gentle brush for cats, easy clean button',  16.99, 20, 1, 4.40, 16, 'cat', 0, @cat_groom,  @user_id, NOW(), NOW());

-- ── 4. User history signals ───────────────────────────────────────────────────

-- Views (weight 0.3) — browsed 10 products
INSERT INTO product_views (user_id, product_id, viewed_at)
SELECT @user_id, id, DATE_SUB(NOW(), INTERVAL FLOOR(1 + RAND() * 9) DAY)
FROM products WHERE is_available = 1 ORDER BY id DESC LIMIT 10;

-- Cart item (weight 0.7) — added dog food to cart
INSERT INTO cart_items (user_id, product_id, quantity, added_at)
SELECT @user_id, id, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM products WHERE name = 'Royal Canin Adult Dog 4kg' LIMIT 1;

-- Order + purchase history (weight 1.0)
INSERT INTO orders (buyer_id, status, payment_method, shipping_address, shipping_fee, total_amount, created_at, updated_at)
VALUES (@user_id, 'DELIVERED', 'CASH_ON_DELIVERY', '1 Rue des Lilas, Tunis', 5.00, 42.98, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW());

SET @order_id = LAST_INSERT_ID();

INSERT INTO payments (order_id, amount, currency, status, created_at, updated_at)
VALUES (@order_id, 42.98, 'eur', 'SUCCEEDED', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW());

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price)
SELECT @order_id, id, name, 1, price FROM products WHERE name = 'Royal Canin Adult Dog 4kg' LIMIT 1;

INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price)
SELECT @order_id, id, name, 1, price FROM products WHERE name = 'Rubber Chew Toy — Kong XL' LIMIT 1;

INSERT INTO product_purchase_history (user_id, product_id, order_id, product_name, quantity, unit_price, purchased_at)
SELECT @user_id, id, @order_id, name, 1, price, DATE_SUB(NOW(), INTERVAL 7 DAY)
FROM products WHERE name = 'Royal Canin Adult Dog 4kg' LIMIT 1;

INSERT INTO product_purchase_history (user_id, product_id, order_id, product_name, quantity, unit_price, purchased_at)
SELECT @user_id, id, @order_id, name, 1, price, DATE_SUB(NOW(), INTERVAL 7 DAY)
FROM products WHERE name = 'Rubber Chew Toy — Kong XL' LIMIT 1;

-- Review (weight 0.9 — rating >= 4)
INSERT INTO product_reviews (user_id, product_id, rating, comment, created_at, updated_at)
SELECT @user_id, id, 5, 'My dog loves this food!', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()
FROM products WHERE name = 'Royal Canin Adult Dog 4kg' LIMIT 1;

-- ── Done ─────────────────────────────────────────────────────────────────────
-- Go to /store/recommendations and click "Find my recommendations"
-- Dog + cat products will rank higher (Max=dog, Luna=cat)
-- Royal Canin dog food + Kong toy are excluded (already purchased)
-- Budget tier = "mid" (median purchase ~29.99)
