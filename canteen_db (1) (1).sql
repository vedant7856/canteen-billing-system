-- =====================================================
--  Canteen Token & Billing System  -  MySQL Workbench
--  Run this whole file once (lightning icon), then run the Java app.
-- =====================================================

CREATE DATABASE IF NOT EXISTS canteen_db;
USE canteen_db;

-- 1) Food menu
CREATE TABLE IF NOT EXISTS menu_items (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(60)  NOT NULL UNIQUE,
    category  VARCHAR(30)  NOT NULL,
    price     DECIMAL(8,2) NOT NULL
);

-- 2) One row per bill. Token restarts from 1 every day.
CREATE TABLE IF NOT EXISTS orders (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    token_no   INT           NOT NULL,
    order_date DATE          NOT NULL,
    total      DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_token_per_day (order_date, token_no)   -- no duplicate token in a day
);

-- 3) Items inside each bill
CREATE TABLE IF NOT EXISTS order_items (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    order_id   INT           NOT NULL,
    item_name  VARCHAR(60)   NOT NULL,
    price      DECIMAL(8,2)  NOT NULL,
    qty        INT           NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE
);

-- 4) Sample menu
INSERT IGNORE INTO menu_items (name, category, price) VALUES
('Veg Thali',        'Meals',     80.00),
('Dal Rice',         'Meals',     60.00),
('Chole Bhature',    'Meals',     70.00),
('Paneer Sandwich',  'Snacks',    45.00),
('Samosa',           'Snacks',    15.00),
('Veg Burger',       'Snacks',    50.00),
('French Fries',     'Snacks',    40.00),
('Tea',              'Beverages', 10.00),
('Coffee',           'Beverages', 20.00),
('Cold Drink',       'Beverages', 25.00),
('Lassi',            'Beverages', 30.00),
('Gulab Jamun',      'Desserts',  25.00),
('Ice Cream',        'Desserts',  35.00);

-- 5) Quick checks (optional)
-- SELECT * FROM menu_items;
-- SELECT * FROM orders;
-- SELECT COUNT(*) AS orders_today, SUM(total) AS revenue_today FROM orders WHERE order_date = CURDATE();
