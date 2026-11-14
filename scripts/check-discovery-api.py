"""Opt-in HTTP contract checks; no Android device, emulator, or app state is used.

Requires requests and beautifulsoup4. Reads endpoints/selectors from bundled rules.
This checks upstream data, not the complete Android rule engine or rendered UI.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import secrets
import time
from urllib.parse import quote, urljoin

import requests
from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/main/assets/defaultData"


def source_rules():
    sources = {}
    for name in ("goubinSources.json", "goubinCommunitySources.json"):
        for source in json.loads((DATA / name).read_text(encoding="utf-8")):
            sources[source["bookSourceUrl"]] = source
    return sources


def text(node):
    return node.get_text(" ", strip=True) if node else ""


def run_case(case, proxy):
    started = time.monotonic()
    source, title, template, mode, key, page = case
    result = {"source": source["bookSourceName"], "case": title, "page": page}
    session = requests.Session()
    session.trust_env = False
    session.headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"
    if proxy:
        session.proxies.update(http=proxy, https=proxy)
    try:
        if mode == "qidian-rank":
            session.get("https://m.qidian.com/", timeout=12).raise_for_status()
        csrf = next((cookie.value for cookie in session.cookies if cookie.name == "_csrfToken"), "")
        if mode == "qidian-rank" and not csrf:
            csrf = secrets.token_hex(16)
            session.cookies.set("_csrfToken", csrf, domain=".qidian.com", path="/")
        template = template.replace("{{page}}", str(page)).replace("{{key}}", quote(key))
        template = re.sub(r'\{\{cookie\.getKey\([^{}]*\)\}\}', csrf, template)
        base = source["bookSourceUrl"].split("##", 1)[0]
        if mode in ("sudugu-search", "shuhaige-search"):
            # Resolve the site's canonical host before POST; 301/302 may discard its body.
            landing = session.get(base, timeout=12)
            landing.raise_for_status()
            endpoint = urljoin(landing.url, template.split(",", 1)[0])
            response = session.post(endpoint, data={"searchkey": key, "action": "login", "searchtype": "all", "submit": ""}, timeout=12)
        else:
            response = session.get(urljoin(base, template), timeout=12)
        response.raise_for_status()
        response.encoding = response.apparent_encoding if response.encoding == "ISO-8859-1" else response.encoding
        result["url"] = re.sub(r"(_csrfToken=)[^&]*", r"\1[guest]", response.url)
        if mode == "qidian-rank":
            body = response.json()
            result["response_code"] = body.get("code")
            data = body.get("data") or {}
            records = data.get("records") or data.get("list") or []
            books = [{"name": b.get("bName", ""), "author": b.get("bAuth", ""), "id": str(b.get("bid", ""))} for b in records]
        else:
            soup = BeautifulSoup(response.text, "html.parser")
            if mode == "qidian-search":
                books = [{"name": text(b.select_one("h2")), "author": text(b.select_one('p[class^="_searchBookAuthor"]'))} for b in soup.select("div.y-list__content>div.y-list__item")]
            elif mode == "sudugu-search":
                books = [{"name": text(b.select_one("a")), "author": text(b.select_one(".author"))} for b in soup.select(".bookbox")]
            elif mode == "shuhaige-search":
                books = [{"name": text(b.select_one("h3 a")), "author": text(b.select_one("span"))} for b in soup.select("#sitembox dl")]
            else:
                books = [{"name": (b.select_one("img") or {}).get("alt", ""), "author": text(b.select_one(".itemtxt p:nth-of-type(2) a"))} for b in soup.select(".item")]
        books = [b for b in books if b["name"].strip()]
        result["count"] = len(books)
        result["books"] = books
        if key:
            compact = lambda value: re.sub(r"\s+", "", value)
            result["exact_title_hits"] = sum(compact(key) == compact(b["name"]) for b in books)
            expected = {"执魔": "我是墨水", "万古神帝": "飞天鱼", "九龙至尊": "盘古"}
            result["matching_work_hits"] = sum(compact(key) == compact(b["name"]) and expected.get(key, "") in b["author"] for b in books)
        result["ok"] = bool(books)
        if not books:
            result["error"] = "HTTP succeeded but the bundled rule's data shape returned no books"
            if mode == "qidian-rank":
                result["error"] += ": " + str(body.get("msg", body.get("message", "")))
    except (requests.RequestException, ValueError, TypeError, AttributeError) as error:
        result.update(ok=False, error=str(error))
    finally:
        session.close()
    result["elapsed_ms"] = round((time.monotonic() - started) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proxy", default="")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/discovery-api-report.json")
    args = parser.parse_args()
    sources = source_rules()
    qidian = sources["https://m.qidian.com##部分限免源"]
    sudugu = sources["https://www.sudugu.co"]
    shuhaige = sources["https://www.shuhaige.net/"]
    qidian_kinds = json.loads(qidian["exploreUrl"])
    cases = []
    for title in ("月票榜", "畅销榜", "推荐榜", "新书榜", "畅销完本"):
        url = next(k["url"] for k in qidian_kinds if k["title"] == title)
        cases.append((qidian, title, url, "qidian-rank", "", 1))
        if title == "月票榜":
            cases.append((qidian, title, url, "qidian-rank", "", 2))
    for line in sudugu["exploreUrl"].splitlines():
        title, url = line.split("::", 1)
        if title in ("最近更新", "玄幻小说", "都市小说", "仙侠小说", "完本排行"):
            cases.append((sudugu, title, url, "sudugu-rank", "", 1))
    for key in ("执魔", "万古神帝", "九龙至尊"):
        cases.append((qidian, "搜索 " + key, qidian["searchUrl"], "qidian-search", key, 1))
        cases.append((sudugu, "搜索 " + key, sudugu["searchUrl"], "sudugu-search", key, 1))
        cases.append((shuhaige, "搜索 " + key, shuhaige["searchUrl"], "shuhaige-search", key, 1))
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda case: run_case(case, args.proxy), cases))
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "checks": results}
    pages = [r for r in results if r["case"] == "月票榜" and r["ok"]]
    if len(pages) == 2:
        report["monthly_page_overlap"] = len({b["id"] for b in pages[0]["books"]} & {b["id"] for b in pages[1]["books"]})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    for result in results:
        print(f'{"OK" if result["ok"] else "FAIL"} {result["source"]} {result["case"]} p{result["page"]}: {result.get("count", 0)} books, {result["elapsed_ms"]} ms')
    print(f'{sum(r["ok"] for r in results)}/{len(results)} HTTP contracts returned books; report: {args.output}')
    return 0 if all(r["ok"] for r in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
