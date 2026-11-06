import os
import time
import json
import re
import requests
from urllib.parse import urljoin

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager


BASE_URL = "https://dibao.com.vn"

CATEGORIES = {
    "xe-may-dien": "/xe-may-dien",
    "xe-dap-dien": "/xe-dap-dien"
}


# =============================
# DRIVER
# =============================
def init_driver():
    options = webdriver.ChromeOptions()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-blink-features=AutomationControlled")

    return webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options
    )


# =============================
# UTILS
# =============================
def slugify(text):
    text = text.lower()
    text = re.sub(r"[^\w\s-]", "", text)
    text = re.sub(r"[-\s]+", "-", text)
    return text.strip("-")


def parse_price(price):
    if not price:
        return 0
    price = price.replace(".", "").replace(",", "")
    nums = re.findall(r"\d+", price)
    return float("".join(nums)) if nums else 0


def create_folder(path):
    if not os.path.exists(path):
        os.makedirs(path)


# =============================
# DOWNLOAD IMAGE (NAME = PRODUCT)
# =============================
def download_image(url, folder, product_name, index):
    try:
        res = requests.get(url, timeout=10)

        if res.status_code == 200:
            safe_name = slugify(product_name)
            filename = f"{safe_name}-{index}.jpg"

            path = os.path.join(folder, filename)

            # tránh trùng
            if os.path.exists(path):
                filename = f"{safe_name}-{index}-{int(time.time())}.jpg"
                path = os.path.join(folder, filename)

            with open(path, "wb") as f:
                f.write(res.content)

            return filename

    except:
        pass

    return None


# =============================
# SPEC
# =============================
def extract_specs(text):
    text = text.lower()

    speed = re.search(r"(\d+)\s*km/h", text)
    range_ = re.search(r"(\d+)\s*km[/ ]?lần", text)
    power = re.search(r"(\d+)\s*w", text)
    battery = re.search(r"(pin|ắc quy)[^.,;]+", text)

    result = []

    if speed:
        result.append(f"Tốc độ: {speed.group(1)} km/h")

    if range_:
        result.append(f"Quãng đường: {range_.group(1)} km")

    if power:
        result.append(f"Công suất: {power.group(1)} W")

    if battery:
        result.append(battery.group(0))

    return " | ".join(result)


# =============================
# GET LINKS
# =============================
def get_links(driver, category_url):
    driver.get(category_url)
    time.sleep(3)

    links = []
    elements = driver.find_elements(By.CSS_SELECTOR, "a[href]")

    for el in elements:
        href = el.get_attribute("href")

        if not href:
            continue

        if not href.endswith(".html"):
            continue

        if "xe-" not in href:
            continue

        if "c24" in href:
            continue

        if href not in links:
            links.append(href)

    return links[:50]


# =============================
# CRAWL PRODUCT (VARIANTS)
# =============================
def crawl_product(driver, url, image_folder, category_slug):
    wait = WebDriverWait(driver, 10)

    try:
        driver.get(url)

        # NAME
        try:
            name = wait.until(
                EC.presence_of_element_located((By.TAG_NAME, "h1"))
            ).text
        except:
            name = wait.until(
                EC.presence_of_element_located((By.CSS_SELECTOR, ".product-name"))
            ).text

        slug = slugify(name)

        # PRICE
        try:
            price = wait.until(
                EC.presence_of_element_located((By.CLASS_NAME, "price"))
            ).text
        except:
            price = ""

        price = parse_price(price)

        # SPEC
        page_text = driver.find_element(By.TAG_NAME, "body").text
        specs = extract_specs(page_text)

        # =============================
        # VARIANTS + IMAGES
        # =============================
        variants = []
        images = []

        img_elements = driver.find_elements(By.CSS_SELECTOR, "img")

        variant_index = 0

        for img in img_elements:
            src = img.get_attribute("src")

            if not src:
                continue

            if any(x in src.lower() for x in ["logo", "icon", "banner"]):
                continue

            color = f"color_{variant_index}"
            sku = f"{slug}-{color}"

            filename = download_image(src, image_folder, name, variant_index)

            if filename:
                # VARIANT
                variants.append({
                    "sku": sku,
                    "color": color,
                    "size": "",
                    "stock_quantity": 20
                })

                # IMAGE gắn vào variant
                images.append({
                    "variant_sku": sku,
                    "image_url": filename,
                    "alt_text": f"{name} - {color}",
                    "sort_order": 0,
                    "is_primary": True
                })

                variant_index += 1

            if variant_index >= 5:
                break

        return {
            "category_slug": category_slug,
            "product": {
                "name": name,
                "slug": slug,
                "description": specs,
                "price": price,
                "discount_price": None,
                "stock_quantity": 40,
                "rating": 0,
                "review_count": 0,
                "is_featured": False,
                "is_active": True
            },
            "variants": variants,
            "images": images
        }

    except Exception as e:
        print("❌ ERROR:", url, e)
        return None


# =============================
# MAIN
# =============================
def main():
    driver = init_driver()

    create_folder("data/images")

    all_data = []

    for category, path in CATEGORIES.items():
        print(f"\n📂 {category}")

        links = get_links(driver, BASE_URL + path)

        for link in links:
            print("→", link)

            data = crawl_product(driver, link, "data/images", category)

            if data:
                all_data.append(data)

    with open("data/products_variants.json", "w", encoding="utf-8") as f:
        json.dump(all_data, f, ensure_ascii=False, indent=2)

    driver.quit()

    print("\n✅ DONE")


if __name__ == "__main__":
    main()