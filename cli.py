"""
cli.py - Interactive CLI for Ghanaian Retail POS & SQLite Inventory System
Supports Provisions, Pharmaceuticals, Groceries, and Cosmetics.
Features:
- SQLite Inventory Management (SKU, Name, Category, Price in GH₵, Cost, Stock, Barcode, Expiry)
- Unpaid AI Capabilities (Gemini 3.5 Flash Free Tier + Offline Heuristic AI Engine)
- Print Barcode Sticker Option (for products that do not have their own barcode)
- Mobile Money Payment Processing (MTN MoMo, Telecel Cash, AT Money)
- Low Stock Alerts & Health Audits
"""

import sys
import os
import json
import random
from typing import Optional
import inventory_db as db
import ai_inventory as ai


def print_header(title: str):
    print("\n" + "=" * 65)
    print(f"  {title.upper()}")
    print("=" * 65)


def display_products_table(products):
    if not products:
        print("No products found in inventory.")
        return

    print(f"{'SKU':<10} | {'Name':<35} | {'Category':<15} | {'Price':<10} | {'Stock':<6} | {'Barcode':<13}")
    print("-" * 100)
    for p in products:
        stock_display = str(p['stock_level'])
        if p['stock_level'] <= p.get('reorder_threshold', 5):
            stock_display += " (!)"
        print(f"{p['sku']:<10} | {p['name'][:35]:<35} | {p['category'][:15]:<15} | GH₵{p['price']:<7.2f} | {stock_display:<6} | {p['barcode'] or 'Generated':<13}")
    print("-" * 100)
    print(f"Total: {len(products)} products  |  (!) = At or below reorder threshold (Low Stock Alert)")


def render_ascii_barcode_sticker(sku: str, name: str, price: float, barcode: str, expiry: Optional[str] = None):
    """Render a printable retail barcode shelf/package sticker."""
    width = 44
    border = "+" + "-" * (width - 2) + "+"
    print("\n" + border)
    print(f"| {'GHANA RETAIL & PHARMACY':^{width - 4}} |")
    print(f"| {name[:width - 4]:^{width - 4}} |")
    
    sku_exp = f"SKU: {sku}"
    if expiry:
        sku_exp += f"  EXP: {expiry}"
    print(f"| {sku_exp:<{width - 4}} |")
    
    # Generate realistic pseudo-barcode bars based on digits
    bars_pattern = ""
    for char in barcode:
        if char in "012":
            bars_pattern += "|| |"
        elif char in "345":
            bars_pattern += "| ||"
        elif char in "67":
            bars_pattern += "||| "
        else:
            bars_pattern += "| | "
    bars_pattern = bars_pattern[:width - 6]
    
    print(f"| {bars_pattern:^{width - 4}} |")
    print(f"| {bars_pattern:^{width - 4}} |")
    print(f"| {barcode:^{width - 4}} |")
    print(f"| {'GH₵ ' + f'{price:.2f}':^{width - 4}} |")
    print(border + "\n")


def run_cli():
    db.init_db()
    # Check if empty, seed if so
    if not db.get_all_products():
        print("Database was empty. Seeding with Ghanaian Provisions, Pharmaceuticals, Groceries, and Cosmetics...")
        db.seed_ghana_inventory()

    while True:
        print_header("Ghana Retail POS & Inventory Terminal")
        print("1. View Full Inventory (Provisions, Pharmaceuticals, Groceries, Cosmetics)")
        print("2. Filter by Category")
        print("3. View Low Stock Alerts & Restock List")
        print("4. Add New Product (with AI Auto-Categorization & Barcode Generation)")
        print("5. Quick Stock Adjustment (Restock / Damage / Return)")
        print("6. Barcode Lookup / Scan Simulation")
        print("7. Print Barcode Sticker (Generate & Print for unbarcoded products)")
        print("8. Checkout Sale with Mobile Money (MTN MoMo, Telecel Cash, AT Money)")
        print("9. Run Unpaid AI Restock & Margin Report")
        print("10. Ask Natural Language AI Advisor")
        print("11. Export Inventory to JSON")
        print("0. Exit")

        choice = input("\nSelect an option [0-11]: ").strip()

        if choice == "1":
            print_header("All Products")
            display_products_table(db.get_all_products())

        elif choice == "2":
            print("\nCategories: Provisions, Pharmaceuticals, Groceries, Cosmetics")
            cat = input("Enter category name: ").strip()
            print_header(f"Category: {cat}")
            display_products_table(db.get_all_products(category=cat))

        elif choice == "3":
            print_header("Low Stock Alerts")
            low = db.get_low_stock_products()
            if not low:
                print("All products are adequately stocked!")
            else:
                display_products_table(low)
                total_restock_est = sum((p.get('reorder_threshold', 5) * 2 - p['stock_level']) * p.get('cost_price', 0.0) for p in low)
                print(f"\nEstimated Capital Required to Replenish: GH₵ {total_restock_est:.2f}")

        elif choice == "4":
            print_header("Add New Product")
            name = input("Product Name: ").strip()
            if not name:
                print("Name is required.")
                continue

            cost_str = input("Cost Price (GH₵) [default 0.0]: ").strip()
            cost = float(cost_str) if cost_str else 0.0

            # Use Unpaid AI to classify and suggest price
            print("\nAnalyzing product with Unpaid AI Engine...")
            ai_data = ai.ai_categorize_product(name, cost)
            print(f"-> AI Recommended Category: {ai_data.get('category')}")
            print(f"-> AI Suggested Retail Price: GH₵ {ai_data.get('suggested_price', 10.00):.2f}")
            print(f"-> AI Recommended Reorder Threshold: {ai_data.get('reorder_threshold', 5)}")

            sku = input(f"Enter SKU (leave blank to auto-generate): ").strip().upper()
            if not sku:
                prefix = ai_data.get('category', 'PRO')[:3].upper()
                sku = f"{prefix}-{random.randint(100, 999)}"
                print(f"Generated SKU: {sku}")

            price_str = input(f"Selling Price [default GH₵ {ai_data.get('suggested_price', 10.00):.2f}]: ").strip()
            price = float(price_str) if price_str else ai_data.get('suggested_price', 10.00)

            stock_str = input("Initial Stock Level [default 10]: ").strip()
            stock = int(stock_str) if stock_str else 10

            barcode = input("Barcode / UPC (leave blank to auto-generate printable barcode): ").strip() or None
            category = input(f"Category [default {ai_data.get('category', 'Provisions')}]: ").strip() or ai_data.get('category', 'Provisions')
            expiry = input("Expiry Date (YYYY-MM-DD, e.g. for pharmaceuticals) [optional]: ").strip() or None

            success = db.add_product(
                sku=sku,
                name=name,
                category=category,
                price=price,
                cost_price=cost,
                stock_level=stock,
                reorder_threshold=ai_data.get('reorder_threshold', 5),
                barcode=barcode,
                expiry_date=expiry
            )
            if success:
                saved = db.get_product(sku)
                print(f"\nProduct '{name}' successfully saved!")
                print(f"Assigned Barcode: {saved['barcode']}")
                print_label = input("Do you want to print a barcode label for this product now? (y/n): ").strip().lower()
                if print_label == 'y':
                    render_ascii_barcode_sticker(sku, name, price, saved['barcode'], expiry)

        elif choice == "5":
            print_header("Quick Stock Adjustment")
            sku = input("Enter SKU or scan barcode: ").strip()
            prod = db.get_product_by_barcode(sku) or db.get_product(sku)
            if not prod:
                print("Product not found!")
                continue

            print(f"Found: {prod['name']} (Current Stock: {prod['stock_level']} {prod.get('unit', 'pcs')})")
            delta_str = input("Enter stock change (e.g. +20 for restock, -3 for damage/expired): ").strip()
            try:
                delta = int(delta_str)
                reason = "RESTOCK" if delta > 0 else "ADJUSTMENT"
                notes = input("Reason / Note [optional]: ").strip()
                new_stock = db.update_stock(prod['sku'], delta, reason=reason, notes=notes)
                if new_stock is not None:
                    print(f"Stock updated! New Stock Level for {prod['sku']}: {new_stock}")
                else:
                    print("Error updating stock (cannot drop below 0).")
            except ValueError:
                print("Invalid number.")

        elif choice == "6":
            print_header("Barcode Lookup")
            barcode = input("Scan or Enter Barcode / SKU: ").strip()
            item = db.get_product_by_barcode(barcode)
            if item:
                print("\nMatch Found:")
                print(f"  SKU:         {item['sku']}")
                print(f"  Name:        {item['name']}")
                print(f"  Category:    {item['category']}")
                print(f"  Price:       GH₵ {item['price']:.2f}")
                print(f"  Stock Level: {item['stock_level']} {item.get('unit', 'pcs')}")
                print(f"  Barcode:     {item['barcode']}")
                if item.get('expiry_date'):
                    print(f"  Expiry Date: {item['expiry_date']}")
            else:
                print("No product matches this barcode.")

        elif choice == "7":
            print_header("Print Barcode Label")
            print("Select an unbarcoded or existing product to generate and print its barcode label.")
            sku = input("Enter SKU or product name: ").strip()
            prod = db.get_product(sku) or db.get_product_by_barcode(sku)
            if not prod:
                # search by name
                matches = [p for p in db.get_all_products() if sku.lower() in p['name'].lower()]
                if matches:
                    prod = matches[0]

            if not prod:
                print("Product not found.")
                continue

            barcode = prod.get('barcode')
            if not barcode:
                barcode = db.generate_local_barcode(prod['category'][:2].upper())
                conn = db.get_connection()
                conn.execute("UPDATE products SET barcode = ? WHERE sku = ?", (barcode, prod['sku']))
                conn.commit()
                conn.close()
                print(f"Generated new unique barcode: {barcode}")

            print(f"\nPrinting Barcode Sticker for: {prod['name']} (SKU: {prod['sku']})")
            render_ascii_barcode_sticker(prod['sku'], prod['name'], prod['price'], barcode, prod.get('expiry_date'))
            print("Barcode Sticker rendered! You can send this output to your label printer.")

        elif choice == "8":
            print_header("Checkout Sale with Mobile Money (MoMo)")
            cart = []
            while True:
                item_code = input("Enter SKU or scan barcode to add to cart (or press ENTER when done): ").strip()
                if not item_code:
                    break
                p = db.get_product_by_barcode(item_code) or db.get_product(item_code)
                if not p:
                    print("Product not found.")
                    continue
                if p['stock_level'] <= 0:
                    print(f"Warning: {p['name']} is SOLD OUT.")
                    continue
                qty_str = input(f"Quantity for {p['name']} (available: {p['stock_level']}) [default 1]: ").strip()
                qty = int(qty_str) if qty_str else 1
                if qty > p['stock_level']:
                    print(f"Cannot add {qty}. Only {p['stock_level']} available.")
                    continue
                cart.append({
                    "sku": p['sku'],
                    "name": p['name'],
                    "price": p['price'],
                    "quantity": qty,
                    "total": p['price'] * qty
                })
                print(f"Added {qty}x {p['name']} to cart.")

            if not cart:
                print("Cart is empty.")
                continue

            subtotal = sum(item['total'] for item in cart)
            tax = subtotal * 0.03  # 3% GRA flat rate
            total = subtotal + tax

            print(f"\nOrder Subtotal: GH₵ {subtotal:.2f}")
            print(f"GRA Tax (3%):   GH₵ {tax:.2f}")
            print(f"Total Due:      GH₵ {total:.2f}")

            print("\nSelect Payment Method:")
            print("1. MTN Mobile Money (Prompt to customer phone)")
            print("2. Telecel Cash (Vodafone)")
            print("3. AirtelTigo (AT Money)")
            print("4. Cash in Cedis")
            pay_opt = input("Choice [1-4]: ").strip()

            payment_method = "Mobile Money"
            momo_network = "MTN"
            customer_phone = None
            momo_ref = None

            if pay_opt in ["1", "2", "3"]:
                network_map = {"1": "MTN MoMo", "2": "Telecel Cash", "3": "AT Money"}
                momo_network = network_map.get(pay_opt, "MTN MoMo")
                customer_phone = input(f"Enter customer's {momo_network} phone number (e.g. 0244123456): ").strip()
                momo_ref = f"MOM-{random.randint(10000000, 99999999)}"
                print(f"\n>>> SENDING USSD PROMPT OF GH₵ {total:.2f} TO {customer_phone} ({momo_network}) <<<")
                print(f">>> Customer receives prompt: 'Approve GH₵ {total:.2f} to GHANA RETAIL STORE? Enter PIN' <<<")
                input("Press ENTER once customer confirms approval on their phone...")
                print(f"[SUCCESS] MoMo Transaction Approved! Reference ID: {momo_ref}")
            else:
                payment_method = "Cash"

            sale_id = f"GH-{random.randint(100000, 999999)}"
            success = db.record_sale(
                sale_id=sale_id,
                items=cart,
                subtotal=subtotal,
                tax=tax,
                discount=0.0,
                total_amount=total,
                payment_method=payment_method,
                momo_network=momo_network if payment_method == "Mobile Money" else None,
                customer_phone=customer_phone,
                momo_reference=momo_ref
            )
            if success:
                print(f"\nSale #{sale_id} completed successfully!")
                print(f"Payment: {payment_method} ({momo_network if momo_network else 'Cash'})")
                print("Inventory updated and deducted automatically in SQLite!")

        elif choice == "9":
            print_header("Unpaid AI Restock & Margin Report")
            products = db.get_all_products()
            print("Analyzing inventory via Unpaid AI Engine...")
            report = ai.ai_generate_restock_report(products)
            print(f"\n[AI Engine Source: {report['source']}]")
            print(f"Items at or below reorder threshold: {report['low_stock_count']}\n")
            print(report['analysis'])

            stats = report['offline_stats']
            print("\n" + "-" * 50)
            print("INVENTORY HEALTH SCORE: ", f"{stats['health_score']}/100")
            print(f"Total Valuation: GH₵ {stats['retail_value']:.2f} (Cost: GH₵ {stats['cost_value']:.2f})")
            print(f"Estimated Gross Margin: {stats['average_margin_pct']}% (+GH₵ {stats['estimated_profit']:.2f})")
            print("-" * 50)

        elif choice == "10":
            print_header("Natural Language AI Assistant")
            print("Examples:")
            print(" - 'Which pharmaceuticals have low stock?'")
            print(" - 'What are our top-margin provisions?'")
            print(" - 'How is our inventory health score?'")
            q = input("\nEnter your inventory question: ").strip()
            if q:
                products = db.get_all_products()
                ans = ai.ai_ask_inventory(q, products)
                print(f"\n[AI Assistant Answer]:\n{ans}")

        elif choice == "11":
            export_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "exported_inventory.json")
            db.export_inventory_json(export_path)
            print(f"Inventory exported successfully to {export_path}")

        elif choice == "0":
            print("Exiting Ghana Retail POS Terminal. Medaase (Thank you)!")
            break


if __name__ == "__main__":
    run_cli()
