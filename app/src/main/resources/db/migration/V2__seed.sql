INSERT INTO category (id, name, description) VALUES
    (1, 'Electronics', 'Electronic devices and components'),
    (2, 'Office Supplies', 'General office consumables'),
    (3, 'Hardware', 'Tools and hardware items');

INSERT INTO supplier (id, name, email, phone) VALUES
    (1, 'Acme Distribution', 'sales@acme.example', '+1-555-0100'),
    (2, 'Globex Supply Co', 'orders@globex.example', '+1-555-0200');

INSERT INTO warehouse (id, code, name, region) VALUES
    (1, 'WH-EAST', 'East Coast DC', 'us-east-1'),
    (2, 'WH-WEST', 'West Coast DC', 'us-west-2');

INSERT INTO product (id, sku, name, category_id, supplier_id, unit_cost, reorder_point, reorder_qty) VALUES
    (1, 'SKU-1001', 'USB-C Cable 1m',        1, 1, 3.50,  50, 200),
    (2, 'SKU-1002', 'Wireless Mouse',         1, 1, 12.75, 30, 100),
    (3, 'SKU-2001', 'A4 Paper Ream',          2, 2, 4.20,  40, 150),
    (4, 'SKU-3001', 'Cordless Drill',         3, 2, 58.00, 10, 25);

INSERT INTO inventory_level (id, product_id, warehouse_id, quantity_on_hand, quantity_reserved, version) VALUES
    (1, 1, 1, 120, 0, 0),
    (2, 1, 2, 40,  0, 0),
    (3, 2, 1, 25,  0, 0),
    (4, 3, 1, 200, 0, 0),
    (5, 4, 1, 8,   0, 0),
    (6, 4, 2, 5,   0, 0);

ALTER TABLE category ALTER COLUMN id RESTART WITH 100;
ALTER TABLE supplier ALTER COLUMN id RESTART WITH 100;
ALTER TABLE warehouse ALTER COLUMN id RESTART WITH 100;
ALTER TABLE product ALTER COLUMN id RESTART WITH 100;
ALTER TABLE inventory_level ALTER COLUMN id RESTART WITH 100;
