"""Check anonymous search, volume links and EPUB structure before APK packaging."""
import argparse
import io
import json
import zipfile
from pathlib import Path
from urllib.parse import quote
import requests

parser = argparse.ArgumentParser()
parser.add_argument('--proxy')
args = parser.parse_args()
session = requests.Session()
session.trust_env = False
if args.proxy:
    session.proxies = {'https': args.proxy}
source = json.loads(Path('app/src/main/assets/defaultData/zheyouLightNovelSources.json').read_text('utf-8'))[0]
base = source['bookSourceUrl']
report = []
for title in ['回复术士的重启人生', '无职转生', '义妹生活']:
    response = session.get(base + '/api/wenku', params={
        'page': 0, 'pageSize': 20, 'query': title, 'level': 0,
    }, timeout=(8, 25))
    response.raise_for_status()
    items = response.json()['items']
    matches = [item for item in items if title in item.get('titleZh', '')]
    assert matches, f'{title}: no title match'
    item = matches[0]
    response = session.get(base + '/api/wenku/' + item['id'], timeout=(8, 25))
    response.raise_for_status()
    detail = response.json()
    volumes = detail.get('volumeZh', [])
    assert volumes, f'{title}: no Chinese EPUB volumes'
    response = session.get(base + '/files-wenku/' + item['id'] + '/' + quote(sorted(volumes)[0], safe=''), timeout=(8, 45))
    response.raise_for_status()
    with zipfile.ZipFile(io.BytesIO(response.content)) as epub:
        assert epub.read('mimetype').strip() == b'application/epub+zip'
        assert 'META-INF/container.xml' in epub.namelist()
        chapters = [name for name in epub.namelist() if name.endswith(('.xhtml', '.html', '.htm'))]
        assert chapters, f'{title}: no chapter documents'
        assert any(len(epub.read(name)) > 1000 for name in chapters)
    row = {'query': title, 'title': item['titleZh'], 'authors': detail['authors'],
           'volumes': len(volumes), 'epubBytes': len(response.content), 'documents': len(chapters)}
    report.append(row)
    print(json.dumps(row, ensure_ascii=False), flush=True)
Path('artifacts').mkdir(exist_ok=True)
Path('artifacts/light-novel-api-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), 'utf-8')
