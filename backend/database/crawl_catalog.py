"""Download manufacturer product galleries for explicit visual color review.

No palette guessing, recoloring, or image-index-to-color assignment is performed.
The reviewed color assignments are maintained separately in catalog/review.json.
"""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import urljoin, urlparse
import hashlib
import json
import time
import requests
from bs4 import BeautifulSoup
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "backend/target/catalog-crawl"
ASSETS = ROOT / "backend/src/main/resources/static/images/catalog"
SOURCE = ROOT / "backend/data-backup/dataCawl/data/products_db_ready_clean.json"
if not SOURCE.exists(): SOURCE = ROOT / "backend/database/catalog/products.json"
EXCLUDED = {"kinetic-sir-50cc", "kinetic-creer", "kinetic-gofast", "kinetic-pansy-xs1-ban-nang-cap", "kinetic-pansy-xs", "kinetic-pansy-xs1", "kinetic-rs-50cc", "kinetic-tesla-x"}
BASE = "https://dibao.com.vn/"

def fetch(url):
    for attempt in range(3):
        try:
            response = requests.get(url, timeout=35, headers={"User-Agent": "EBikeStore-CatalogReview/1.0"})
            response.raise_for_status()
            return response
        except requests.RequestException:
            if attempt == 2: raise
            time.sleep(1 + attempt)

def source_url(slug):
    model = slug.removeprefix("kinetic-")
    prefix = "xe-may-dien-dibao-"
    if model in {"amy", "ella", "ryan", "s-one", "keva", "mxs", "rosa"}: prefix = "xe-dien-co-ban-dap-dibao-"
    if model in {"butterfly", "gogo-s4", "gogo-s5", "jeek-one", "r1"}: prefix = "xe-dien-dibao-"
    if model == "pansy-s4": model = "pansy-s4-sv"
    return urljoin(BASE, prefix + model + ".html")

def crawl(item):
    slug = item["product"]["slug"]
    url = source_url(slug)
    folder = ASSETS / slug
    folder.mkdir(parents=True, exist_ok=True)
    html = fetch(url).text
    (WORK / (slug + ".html")).write_text(html, encoding="utf-8")
    soup = BeautifulSoup(html, "html.parser")
    title = soup.h1.get_text(" ", strip=True) if soup.h1 else soup.title.get_text(" ", strip=True)
    if "50cc" in title.lower() or "50 cc" in title.lower(): raise ValueError("Gasoline source rejected: " + title)
    urls = list(dict.fromkeys(urljoin(url, node["href"]) for node in soup.select('a[data-fancybox-group="gallery"][href]')))
    if not urls: raise ValueError("No gallery: " + url)
    images = []
    for index, image_url in enumerate(urls[:6]):
        response = fetch(image_url)
        content = response.content
        if len(content) > 12_000_000: raise ValueError("Image too large: " + image_url)
        suffix = Path(urlparse(image_url).path).suffix.lower()
        if suffix not in {".jpg", ".jpeg", ".png", ".webp"}: suffix = ".jpg"
        file = folder / (f"source-{index + 1:02}" + suffix)
        file.write_bytes(content)
        with Image.open(file) as im:
            width, height = im.size
            im.verify()
        if width < 300 or height < 200: raise ValueError("Thumbnail is too small: " + image_url)
        images.append({"index":index + 1,"source_url":image_url,"path":"/images/catalog/" + slug + "/" + file.name,"sha256":hashlib.sha256(content).hexdigest(),"width":width,"height":height,"bytes":len(content)})
    specs = [[cell.get_text(" ",strip=True) for cell in row.select('td,th')] for row in soup.select('table tr')]
    return {"slug":slug,"source_url":url,"source_title":title,"source_swatches":[node.get('data-color') for node in soup.select('[data-color]')],"images":images,"specification_rows":specs}

def main():
    WORK.mkdir(parents=True, exist_ok=True)
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    products = [i for i in data["products"] if i["product"]["slug"] not in EXCLUDED]
    results, errors = [], []
    with ThreadPoolExecutor(max_workers=4) as pool:
        jobs={pool.submit(crawl,i):i["product"]["slug"] for i in products}
        for job in as_completed(jobs):
            try:
                result=job.result();results.append(result)
                print(result["slug"], len(result["images"]), "images", flush=True)
            except Exception as error:
                errors.append({"slug":jobs[job],"error":str(error)});print("ERROR",jobs[job],str(error),flush=True)
    results.sort(key=lambda item:next(index for index,p in enumerate(products) if p["product"]["slug"]==item["slug"]))
    manifest={"source":"https://dibao.com.vn/xe-may-dien","excluded_gasoline":sorted(EXCLUDED),"products":results,"errors":errors}
    (WORK / "manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    print("Crawled",len(results),"products; errors:",len(errors),flush=True)
    if errors: raise SystemExit(1)

if __name__ == "__main__": main()
