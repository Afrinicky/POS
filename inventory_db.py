"""
inventory_db.py - SQLite Product Inventory Database for Ghanaian Retail Businesses
Tailored for Provisions Stores, Pharmacies / OTC Medicine Sellers, Groceries, and Cosmetics in Ghana.
Currency: Ghanaian Cedi (GH₵)
Supports Mobile Money (MTN MoMo, Telecel Cash), Cash, Barcode Generation & Printing.
"""

import sqlite3
import json
import random
from typing import Dict, List, Optional, Any
from datetime import datetime
import os

DEFAULT_DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "inventory.db")
CURRENCY = "GH₵"


def get_connection(db_path: str = DEFAULT_DB_PATH) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.row_factory = sqlite3.Row
    return conn


def init_db(db_path: str = DEFAULT_DB_PATH):
    conn = get_connection(db_path)
    cursor = conn.cursor()

    # 1. Products table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS products (
        sku TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        category TEXT NOT NULL DEFAULT 'Provisions',
        price REAL NOT NULL,
        cost_price REAL NOT NULL DEFAULT 0.0,
        stock_level INTEGER NOT NULL DEFAULT 0,
        reorder_threshold INTEGER NOT NULL DEFAULT 5,
        barcode TEXT UNIQUE,
        unit TEXT DEFAULT 'pcs',
        expiry_date TEXT,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # 2. Inventory transaction history (audit trail)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS inventory_transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sku TEXT NOT NULL,
        delta INTEGER NOT NULL,
        previous_stock INTEGER NOT NULL,
        new_stock INTEGER NOT NULL,
        reason TEXT NOT NULL,
        notes TEXT,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (sku) REFERENCES products(sku) ON DELETE CASCADE
    )
    """)

    # 3. Sales orders
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS sales (
        sale_id TEXT PRIMARY KEY,
        items_json TEXT NOT NULL,
        subtotal REAL NOT NULL,
        tax REAL NOT NULL,
        discount REAL NOT NULL,
        total_amount REAL NOT NULL,
        payment_method TEXT NOT NULL,
        momo_reference TEXT,
        created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )
    """)

    cursor.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_trans_sku ON inventory_transactions(sku)")

    # Migration check for existing databases
    cursor.execute("PRAGMA table_info(products)")
    columns = [row["name"] for row in cursor.fetchall()]
    if "expiry_date" not in columns:
        cursor.execute("ALTER TABLE products ADD COLUMN expiry_date TEXT")

    cursor.execute("PRAGMA table_info(sales)")
    sales_columns = [row["name"] for row in cursor.fetchall()]
    if "momo_reference" not in sales_columns:
        cursor.execute("ALTER TABLE sales ADD COLUMN momo_reference TEXT")
    if "momo_network" not in sales_columns:
        cursor.execute("ALTER TABLE sales ADD COLUMN momo_network TEXT")
    if "customer_phone" not in sales_columns:
        cursor.execute("ALTER TABLE sales ADD COLUMN customer_phone TEXT")

    conn.commit()
    conn.close()


def generate_local_barcode(category_code: str = "GH") -> str:
    """Generate a unique 12-digit EAN/UPC-compatible barcode for products without an existing barcode."""
    prefix = "200"  # Retail in-store prefix
    digits = "".join([str(random.randint(0, 9)) for _ in range(8)])
    base = prefix + digits
    # Calculate Luhn check digit
    total = sum((int(d) * (3 if i % 2 == 1 else 1)) for i, d in enumerate(base))
    check_digit = (10 - (total % 10)) % 10
    return f"{base}{check_digit}"


def add_product(
    sku: str,
    name: str,
    price: float,
    stock_level: int,
    category: str = "Provisions",
    cost_price: float = 0.0,
    barcode: Optional[str] = None,
    unit: str = "pcs",
    reorder_threshold: int = 5,
    expiry_date: Optional[str] = None,
    db_path: str = DEFAULT_DB_PATH
) -> bool:
    if not barcode:
        barcode = generate_local_barcode(category[:2].upper())

    conn = get_connection(db_path)
    cursor = conn.cursor()
    try:
        cursor.execute("""
        INSERT INTO products (sku, name, category, price, cost_price, stock_level, reorder_threshold, barcode, unit, expiry_date, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """, (sku.strip().upper(), name.strip(), category.strip(), price, cost_price, stock_level, reorder_threshold, barcode, unit, expiry_date))

        cursor.execute("""
        INSERT INTO inventory_transactions (sku, delta, previous_stock, new_stock, reason, notes)
        VALUES (?, ?, 0, ?, 'INITIAL', 'Initial Ghanaian inventory setup')
        """, (sku.strip().upper(), stock_level, stock_level))

        conn.commit()
        return True
    except sqlite3.IntegrityError as e:
        print(f"Error adding product: {e}")
        return False
    finally:
        conn.close()


def update_stock(
    sku: str,
    delta: int,
    reason: str = "RESTOCK",
    notes: str = "",
    db_path: str = DEFAULT_DB_PATH
) -> Optional[int]:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    sku = sku.strip().upper()

    cursor.execute("SELECT stock_level FROM products WHERE sku = ?", (sku,))
    row = cursor.fetchone()
    if not row:
        conn.close()
        return None

    current_stock = row["stock_level"]
    new_stock = current_stock + delta

    if new_stock < 0:
        conn.close()
        return None

    cursor.execute("UPDATE products SET stock_level = ?, updated_at = CURRENT_TIMESTAMP WHERE sku = ?", (new_stock, sku))
    cursor.execute("""
    INSERT INTO inventory_transactions (sku, delta, previous_stock, new_stock, reason, notes)
    VALUES (?, ?, ?, ?, ?, ?)
    """, (sku, delta, current_stock, new_stock, reason, notes))

    conn.commit()
    conn.close()
    return new_stock


def get_product(sku: str, db_path: str = DEFAULT_DB_PATH) -> Optional[Dict[str, Any]]:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM products WHERE sku = ?", (sku.strip().upper(),))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None


def get_product_by_barcode(barcode: str, db_path: str = DEFAULT_DB_PATH) -> Optional[Dict[str, Any]]:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM products WHERE barcode = ? OR sku = ?", (barcode.strip(), barcode.strip().upper()))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else None


def get_all_products(category: Optional[str] = None, db_path: str = DEFAULT_DB_PATH) -> List[Dict[str, Any]]:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    if category and category.lower() != "all":
        cursor.execute("SELECT * FROM products WHERE category = ? ORDER BY name ASC", (category,))
    else:
        cursor.execute("SELECT * FROM products ORDER BY category ASC, name ASC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]


def get_low_stock_products(threshold: Optional[int] = None, db_path: str = DEFAULT_DB_PATH) -> List[Dict[str, Any]]:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    if threshold is not None:
        cursor.execute("SELECT * FROM products WHERE stock_level <= ? ORDER BY stock_level ASC", (threshold,))
    else:
        cursor.execute("SELECT * FROM products WHERE stock_level <= reorder_threshold ORDER BY stock_level ASC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(r) for r in rows]


def record_sale(
    sale_id: str,
    items: List[Dict[str, Any]],
    subtotal: float,
    tax: float,
    discount: float,
    total_amount: float,
    payment_method: str = "MTN MoMo",
    momo_network: Optional[str] = None,
    customer_phone: Optional[str] = None,
    momo_reference: Optional[str] = None,
    db_path: str = DEFAULT_DB_PATH
) -> bool:
    conn = get_connection(db_path)
    cursor = conn.cursor()
    try:
        for item in items:
            sku = item["sku"].strip().upper()
            qty = item["quantity"]
            cursor.execute("SELECT stock_level FROM products WHERE sku = ?", (sku,))
            row = cursor.fetchone()
            if not row or row["stock_level"] < qty:
                raise ValueError(f"Insufficient stock for SKU {sku}")

        for item in items:
            sku = item["sku"].strip().upper()
            qty = item["quantity"]
            cursor.execute("SELECT stock_level FROM products WHERE sku = ?", (sku,))
            cur_stock = cursor.fetchone()["stock_level"]
            new_stock = cur_stock - qty

            cursor.execute("UPDATE products SET stock_level = ?, updated_at = CURRENT_TIMESTAMP WHERE sku = ?", (new_stock, sku))
            cursor.execute("""
            INSERT INTO inventory_transactions (sku, delta, previous_stock, new_stock, reason, notes)
            VALUES (?, ?, ?, ?, 'SALE', ?)
            """, (sku, -qty, cur_stock, new_stock, f"Sale #{sale_id} via {payment_method}"))

        cursor.execute("""
        INSERT INTO sales (sale_id, items_json, subtotal, tax, discount, total_amount, payment_method, momo_network, customer_phone, momo_reference)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (sale_id, json.dumps(items), subtotal, tax, discount, total_amount, payment_method, momo_network, customer_phone, momo_reference))

        conn.commit()
        return True
    except Exception as e:
        conn.rollback()
        print(f"Sale recording error: {e}")
        return False
    finally:
        conn.close()


def seed_ghana_inventory(db_path: str = DEFAULT_DB_PATH):
    """Seed authentic inventory for Ghanaian Provisions, OTC Pharmaceuticals, Groceries, and Cosmetics."""
    sample_products = [
        # Provisions (Popular staple brands in Ghanaian stores)
        ("PRO-001", "Nestle Milo Refill Pack 400g", "Provisions", 45.00, 36.00, 25, 8, "200000100011", "tin", None),
        ("PRO-002", "Ideal Evaporated Milk 160g", "Provisions", 12.50, 9.80, 48, 12, "200000100028", "tin", None),
        ("PRO-003", "Geisha Mackerel in Tomato Sauce 425g", "Provisions", 22.00, 17.50, 30, 8, "200000100035", "can", None),
        ("PRO-004", "Key Soap Bar (Original Brown)", "Provisions", 18.00, 14.00, 20, 6, "200000100042", "bar", None),
        ("PRO-005", "Pepsodent Cavity Fighter Toothpaste 140g", "Provisions", 16.00, 12.00, 18, 5, "200000100059", "tube", None),
        ("PRO-006", "St. Louis Granulated Sugar Cubes 500g", "Provisions", 25.00, 19.50, 4, 8, "200000100066", "box", None), # Low stock
        ("PRO-007", "Cerelac Maize with Milk 400g", "Provisions", 55.00, 44.00, 12, 4, "200000100073", "tin", None),

        # Pharmaceuticals (Licensed OTC / Pharmacy essentials in Ghana)
        ("PHA-101", "Paracetamol 500mg Tablets (Blister of 10)", "Pharmaceuticals", 5.00, 2.50, 50, 15, "200000200018", "blister", "2027-12-31"),
        ("PHA-102", "Coartem (Artemether-Lumefantrine 20/120mg)", "Pharmaceuticals", 38.00, 28.00, 8, 5, "200000200025", "box", "2027-08-30"),
        ("PHA-103", "Oral Rehydration Salts (ORS Sachets)", "Pharmaceuticals", 3.50, 1.80, 40, 10, "200000200032", "sachet", "2028-01-31"),
        ("PHA-104", "Methylated Spirit 70% 200ml", "Pharmaceuticals", 15.00, 9.50, 2, 6, "200000200049", "btl", "2028-06-30"), # Low stock
        ("PHA-105", "Dettol Antiseptic Liquid 250ml", "Pharmaceuticals", 32.00, 24.00, 14, 5, "200000200056", "btl", "2028-04-30"),
        ("PHA-106", "Lonart DS Malaria Suspension 60ml", "Pharmaceuticals", 42.00, 32.00, 3, 5, "200000200063", "btl", "2027-05-31"), # Low stock
        ("PHA-107", "Vitamin C 1000mg Effervescent (10s)", "Pharmaceuticals", 28.00, 19.00, 16, 5, "200000200070", "tube", "2027-10-31"),

        # Groceries
        ("GRO-201", "Ghana Jasmine Perfume Rice 5kg", "Groceries", 115.00, 92.00, 15, 5, "200000300015", "bag", None),
        ("GRO-202", "Frytol Pure Vegetable Cooking Oil 1L", "Groceries", 42.00, 33.00, 24, 6, "200000300022", "btl", None),
        ("GRO-203", "Gari Premium White (Olorka Bowl)", "Groceries", 20.00, 14.00, 18, 5, "200000300039", "bowl", None),

        # Cosmetics (High demand skincare & beauty in Ghana)
        ("COS-301", "Raw Organic Ghanaian Shea Butter 500g", "Cosmetics", 35.00, 18.00, 25, 6, "200000400012", "jar", None),
        ("COS-302", "Cocoa Butter Moisturizing Body Lotion 400ml", "Cosmetics", 48.00, 34.00, 16, 5, "200000400029", "btl", None),
        ("COS-303", "Dudu-Osun Black Soap (150g)", "Cosmetics", 14.00, 9.00, 30, 8, "200000400036", "bar", None),
        ("COS-304", "Sunscreen SPF 50+ Invisible Gel 50ml", "Cosmetics", 95.00, 65.00, 3, 5, "200000400043", "tube", "2027-11-30") # Low stock
    ]

    for p in sample_products:
        add_product(
            sku=p[0],
            name=p[1],
            category=p[2],
            price=p[3],
            cost_price=p[4],
            stock_level=p[5],
            reorder_threshold=p[6],
            barcode=p[7],
            unit=p[8],
            expiry_date=p[9],
            db_path=db_path
        )


def export_inventory_json(file_path: str, db_path: str = DEFAULT_DB_PATH) -> bool:
    products = get_all_products(db_path=db_path)
    with open(file_path, "w", encoding="utf-8") as f:
        json.dump({
            "store_country": "Ghana",
            "currency": "GH₵",
            "exported_at": datetime.utcnow().isoformat(),
            "products": products
        }, f, indent=2)
    return True


if __name__ == "__main__":
    init_db()
    # Reset and seed with authentic Ghanaian inventory
    conn = get_connection()
    conn.execute("DELETE FROM inventory_transactions")
    conn.execute("DELETE FROM products")
    conn.commit()
    conn.close()
    seed_ghana_inventory()
    print("Database initialized and seeded with Ghanaian Provisions, Pharmaceuticals, Groceries, and Cosmetics!")
