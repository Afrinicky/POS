"""
ai_inventory.py - Unpaid AI Capabilities for Ghanaian Retail Businesses
Optimized for Provisions, Pharmaceuticals, Groceries, and Cosmetics in Ghana.
Currency: Ghanaian Cedi (GH₵)
"""

import os
import json
import urllib.request
import urllib.error
from typing import Dict, List, Any, Optional

GEMINI_MODEL = "gemini-3.5-flash"
CURRENCY = "GH₵"


def get_gemini_api_key() -> Optional[str]:
    api_key = os.getenv("GEMINI_API_KEY")
    if api_key and api_key != "MY_GEMINI_API_KEY":
        return api_key

    env_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if os.path.exists(env_file):
        with open(env_file, "r") as f:
            for line in f:
                line = line.strip()
                if line.startswith("GEMINI_API_KEY="):
                    val = line.split("=", 1)[1].strip().strip('"').strip("'")
                    if val and val != "MY_GEMINI_API_KEY":
                        return val
    return None


def call_gemini_rest(prompt: str, api_key: str, system_instruction: Optional[str] = None) -> Optional[str]:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={api_key}"

    payload: Dict[str, Any] = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.3,
            "maxOutputTokens": 1024
        }
    }

    if system_instruction:
        payload["systemInstruction"] = {"parts": [{"text": system_instruction}]}

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})

    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            result = json.loads(response.read().decode("utf-8"))
            candidates = result.get("candidates", [])
            if candidates:
                parts = candidates[0].get("content", {}).get("parts", [])
                if parts:
                    return parts[0].get("text", "")
    except Exception as e:
        # Fallback to local heuristic engine
        pass
    return None


def ai_generate_restock_report(products: List[Dict[str, Any]]) -> Dict[str, Any]:
    low_stock = [p for p in products if p.get("stock_level", 0) <= p.get("reorder_threshold", 5)]
    api_key = get_gemini_api_key()

    if api_key and low_stock:
        prompt = (
            f"Here are retail products at or below reorder threshold in a Ghanaian retail store:\n"
            f"{json.dumps(low_stock, indent=2)}\n\n"
            f"Provide reorder quantities, note shelf-life/expiry concerns (critical for pharmaceuticals and food provisions), "
            f"calculate estimated restock cost in GH₵, and prioritize items. Keep it concise."
        )
        sys_inst = "You are a Ghanaian retail store & pharmacy consultant. Provide practical advice in Ghanaian Cedis (GH₵)."
        ai_resp = call_gemini_rest(prompt, api_key, sys_inst)
        if ai_resp:
            return {
                "source": "Gemini 3.5 Flash (Free Tier)",
                "low_stock_count": len(low_stock),
                "analysis": ai_resp,
                "offline_stats": offline_inventory_health_check(products)
            }

    return {
        "source": "Built-in Offline Heuristic AI Engine",
        "low_stock_count": len(low_stock),
        "analysis": offline_generate_restock_analysis(low_stock),
        "offline_stats": offline_inventory_health_check(products)
    }


def ai_categorize_product(product_name: str, cost_price: float = 0.0) -> Dict[str, Any]:
    api_key = get_gemini_api_key()
    if api_key:
        prompt = (
            f"Classify product for Ghanaian market: '{product_name}' costing GH₵{cost_price:.2f}.\n"
            f"Return JSON: {{\"category\": \"Provisions|Pharmaceuticals|Groceries|Cosmetics|General\", "
            f"\"suggested_price\": float, \"target_margin_pct\": float, \"reorder_threshold\": int, "
            f"\"unit\": \"pcs|tin|box|blister|sachet|btl|bar|bag\", \"notes\": string}}"
        )
        ai_resp = call_gemini_rest(prompt, api_key, "You are a Ghanaian retail specialist. Return only valid JSON.")
        if ai_resp:
            try:
                clean = ai_resp.strip().removeprefix("```json").removeprefix("```").removeSuffix("```").strip()
                return json.loads(clean)
            except Exception:
                pass

    return offline_classify_product(product_name, cost_price)


def offline_inventory_health_check(products: List[Dict[str, Any]]) -> Dict[str, Any]:
    total_items = len(products)
    if total_items == 0:
        return {"health_score": 100, "total_units": 0, "total_value": 0.0}

    total_units = sum(p.get("stock_level", 0) for p in products)
    retail_value = sum(p.get("stock_level", 0) * p.get("price", 0.0) for p in products)
    cost_value = sum(p.get("stock_level", 0) * p.get("cost_price", 0.0) for p in products)
    low_stock_count = sum(1 for p in products if p.get("stock_level", 0) <= p.get("reorder_threshold", 5))
    out_of_stock_count = sum(1 for p in products if p.get("stock_level", 0) == 0)

    penalty = (out_of_stock_count * 15) + (low_stock_count * 5)
    health_score = max(15, min(100, 100 - penalty))

    avg_margin = 0.0
    if retail_value > 0:
        avg_margin = ((retail_value - cost_value) / retail_value) * 100.0

    return {
        "health_score": health_score,
        "total_products": total_items,
        "total_units": total_units,
        "retail_value": round(retail_value, 2),
        "cost_value": round(cost_value, 2),
        "estimated_profit": round(retail_value - cost_value, 2),
        "average_margin_pct": round(avg_margin, 1),
        "low_stock_count": low_stock_count,
        "out_of_stock_count": out_of_stock_count,
        "currency": CURRENCY
    }


def offline_generate_restock_analysis(low_stock: List[Dict[str, Any]]) -> str:
    if not low_stock:
        return "All inventory levels are healthy! No items currently require reordering."

    lines = ["RECOMMENDED REORDER PLAN (Offline Heuristic AI - Ghana Retail):", ""]
    total_est_cost = 0.0

    for p in low_stock:
        cur = p.get("stock_level", 0)
        thresh = p.get("reorder_threshold", 5)
        cat = p.get("category", "Provisions")
        cost = p.get("cost_price", 0.0)

        if cat.lower() == "pharmaceuticals":
            batch_size = max(20, thresh * 3)
            turnover_note = "Essential health/medicine item. Check FDA batch expiry date."
        elif cat.lower() == "provisions":
            batch_size = max(24, thresh * 3)
            turnover_note = "High turnover staple provision. High frequency customer purchase."
        elif cat.lower() == "cosmetics":
            batch_size = max(10, thresh * 2)
            turnover_note = "High margin skincare/beauty item."
        else:
            batch_size = max(12, thresh * 2)
            turnover_note = "Standard retail grocery stock."

        order_qty = batch_size - cur
        line_cost = order_qty * cost
        total_est_cost += line_cost
        priority = "CRITICAL" if cur == 0 else ("HIGH" if cur <= thresh // 2 else "MEDIUM")

        lines.append(f"• [{priority}] {p.get('name')} (SKU: {p.get('sku')})")
        lines.append(f"  Current: {cur} {p.get('unit','pcs')} | Target: {batch_size} | Order Qty: +{order_qty}")
        if cost > 0:
            lines.append(f"  Est. Cost: GH₵{line_cost:.2f} (@ GH₵{cost:.2f}/{p.get('unit','pcs')})")
        lines.append(f"  Note: {turnover_note}\n")

    lines.append(f"Total Estimated Restock Capital Required: GH₵{total_est_cost:.2f}")
    return "\n".join(lines)


def offline_classify_product(product_name: str, cost_price: float = 0.0) -> Dict[str, Any]:
    name_lower = product_name.lower()

    pharma_keywords = [
        "paracetamol", "coartem", "artemether", "lumefantrine", "ors", "rehydration",
        "spirit", "dettol", "syrup", "capsule", "tablet", "blister", "sachet", "vitamin",
        "lonart", "malaria", "antiseptic", "ibuprofen", "antibiotic", "plaster", "cotton",
        "bandage", "cough", "eye drop", "inhaler", "pharmacy", "medicine"
    ]
    provision_keywords = [
        "milo", "ideal milk", "milk", "geisha", "mackerel", "sardine", "key soap",
        "soap", "pepsodent", "sugar", "cerelac", "tea", "lipton", "biscuits", "this way",
        "cornflakes", "oats", "nido", "carnation", "pasta", "spaghetti", "provision"
    ]
    cosmetic_keywords = [
        "shea butter", "cocoa butter", "lotion", "dudu-osun", "black soap", "cream",
        "lipstick", "sunscreen", "spf", "perfume", "serum", "deodorant", "hair food",
        "vaseline", "shampoo", "relaxer"
    ]
    grocery_keywords = [
        "rice", "oil", "frytol", "gari", "flour", "yam", "plantain", "beans",
        "salt", "pepper", "tomato", "shito", "onion", "egg", "maggi", "curry"
    ]

    if any(k in name_lower for k in pharma_keywords):
        category = "Pharmaceuticals"
        margin = 0.40
        reorder = 8
        unit = "blister" if "blister" in name_lower or "tablet" in name_lower else "box"
        notes = "Licensed medicine / OTC. Ensure batch number and expiry are tracked."
    elif any(k in name_lower for k in provision_keywords):
        category = "Provisions"
        margin = 0.25
        reorder = 10
        unit = "tin" if "milo" in name_lower or "milk" in name_lower else "pcs"
        notes = "Fast-moving neighborhood provision. High repeat customer demand."
    elif any(k in name_lower for k in cosmetic_keywords):
        category = "Cosmetics"
        margin = 0.50
        reorder = 5
        unit = "jar" if "shea" in name_lower or "cream" in name_lower else "btl"
        notes = "Skincare & beauty. Popular Ghanaian personal care item."
    elif any(k in name_lower for k in grocery_keywords):
        category = "Groceries"
        margin = 0.30
        reorder = 6
        unit = "bag" if "rice" in name_lower else "btl"
        notes = "Food & kitchen grocery staple."
    else:
        category = "General"
        margin = 0.35
        reorder = 5
        unit = "pcs"
        notes = "General retail merchandise."

    suggested_price = round(cost_price / (1.0 - margin), 2) if cost_price > 0 else 10.00

    return {
        "category": category,
        "suggested_price": suggested_price,
        "target_margin_pct": round(margin * 100, 1),
        "reorder_threshold": reorder,
        "unit": unit,
        "notes": notes
    }


def offline_answer_query(question: str, products: List[Dict[str, Any]]) -> str:
    q_lower = question.lower()

    if "pharma" in q_lower or "medicine" in q_lower or "drug" in q_lower:
        pharma = [p for p in products if p.get("category") == "Pharmaceuticals"]
        return f"You have {len(pharma)} pharmaceutical & OTC products in inventory ({sum(p.get('stock_level', 0) for p in pharma)} units total). Coartem, Paracetamol, and ORS are top priority essentials."

    if "provision" in q_lower:
        prov = [p for p in products if p.get("category") == "Provisions"]
        return f"Provisions store stock: {len(prov)} products with {sum(p.get('stock_level', 0) for p in prov)} units in stock. Fast turnover items include Milo, Ideal Milk, and Key Soap."

    if "low" in q_lower or "reorder" in q_lower:
        low = [p for p in products if p.get("stock_level", 0) <= p.get("reorder_threshold", 5)]
        if not low:
            return "No items currently require immediate reordering in your store."
        items_str = ", ".join([f"{p['name']} ({p['stock_level']} {p.get('unit','pcs')} left)" for p in low])
        return f"Alert: {len(low)} items need restock: {items_str}."

    health = offline_inventory_health_check(products)
    return (
        f"Ghana Retail Status: Health Score {health['health_score']}/100. "
        f"Total Retail Value: GH₵{health['retail_value']:.2f}. "
        f"Active inventory: {health['total_units']} items across {health['total_products']} products."
    )
