"""Select a reproducible general-fiction candidate pool; runtime validity is checked separately."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

parser = argparse.ArgumentParser()
parser.add_argument("input", type=Path)
parser.add_argument("--limit", type=int, default=300)
args = parser.parse_args()
raw = args.input.read_bytes()
records = json.loads(raw)
now = int(datetime.now(timezone.utc).timestamp() * 1000)
excluded = re.compile(r"🔞|18禁|r18|成人|小黄|色情|情色|h漫|h文|里番|漫画|有声|听书|英文|日文|韩文|xchina|lanlanbo", re.I)

def rule(source, key):
    value = source.get(key) or {}
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except ValueError:
            return {}
    return value if isinstance(value, dict) else {}

def updated(source):
    value = source.get("lastUpdateTime", 0) or 0
    return value if isinstance(value, (int, float)) and 0 < value < now + 86400000 else 0

selected, hosts = [], set()
for source in sorted(records, key=updated, reverse=True):
    if source.get("bookSourceType", 0) != 0 or not source.get("enabled", True):
        continue
    metadata = " ".join(str(source.get(key, "")) for key in ("bookSourceName", "bookSourceGroup", "bookSourceUrl"))
    if excluded.search(metadata):
        continue
    url = urlsplit(source.get("bookSourceUrl", ""))
    host = (url.hostname or "").lower()
    host = re.sub(r"^(www|m|wap)\.", "", host)
    if url.scheme not in ("http", "https") or not host or host in hosts:
        continue
    if not source.get("searchUrl") or not rule(source, "ruleSearch").get("bookList"):
        continue
    if not rule(source, "ruleToc").get("chapterList") or not rule(source, "ruleContent").get("content"):
        continue
    source["bookSourceGroup"] = "柘栯·社区候选," + (source.get("bookSourceGroup") or "")
    source["customOrder"] = 100 + len(selected)
    selected.append(source)
    hosts.add(host)
    if len(selected) >= args.limit:
        break

project = Path(__file__).resolve().parent.parent
output = project / "app/src/main/assets/defaultData/zheyouCommunitySources.json"
output.write_text(json.dumps(selected, ensure_ascii=False, indent=2), encoding="utf-8")
manifest = {
    "upstream": "https://raw.githubusercontent.com/shidahuilang/shuyuan-bak/main/good.json",
    "license": "SHIDAHUILANG-LICENSE",
    "retrievedAt": datetime.now(timezone.utc).isoformat(),
    "upstreamSha256": hashlib.sha256(raw).hexdigest(),
    "upstreamRecordCount": len(records),
    "selectedRecordCount": len(selected),
    "selectedSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
    "runtimeVerified": False,
    "note": "候选规则不等于已检测通过；保留原作者注释，按规范化主机去重，排除明显非中文文字小说及无效时间戳排序。",
}
(project / "sources/community-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"input": len(records), "selected": len(selected), "bytes": output.stat().st_size}))
