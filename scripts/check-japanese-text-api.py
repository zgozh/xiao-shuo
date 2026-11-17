"""Bounded anonymous checks of the Yolo/Mangacopy source contract."""
import argparse
import json
from pathlib import Path
import requests

parser = argparse.ArgumentParser()
parser.add_argument('--proxy')
args = parser.parse_args()
session = requests.Session()
session.trust_env = False
if args.proxy:
    session.proxies = {'https': args.proxy}
base = 'https://api.mangacopy.com/api/v3'


def api(path, **params):
    response = session.get(base + path, params={'_update': 'true', **params}, timeout=(20, 30))
    response.raise_for_status()
    data = response.json()
    assert data['code'] == 200, data.get('message')
    return data['results']


report = []
for query, slug in [('回复术士的重启人生', 'huifushushidechongqirensheng'), ('义妹生活', 'yimeishenghuo')]:
    found = api('/search/books', q=query, limit=20, offset=0)['list']
    book = next(book for book in found if book['path_word'] == slug)
    volumes = api('/book/' + slug + '/volumes')['list']
    assert volumes
    chapter_counts = []
    # First and last listed volumes check both ends, not every intermediate volume.
    for listed in [volumes[0], volumes[-1]]:
        detail = api('/book/' + slug + '/volume/' + listed['id'])
        assert not detail.get('is_lock')
        volume = detail['volume']
        assert volume['contents'] and volume['txt_addr']
        response = session.get(volume['txt_addr'], timeout=(20, 45))
        response.raise_for_status()
        text = response.content.decode(volume['txt_encoding'], errors='strict')
        assert '\ufffd' not in text
        lines = text.splitlines()
        chapters = [item for item in volume['contents'] if item['content_type'] == 1]
        assert chapters
        for chapter in chapters:
            assert 0 <= chapter['start_lines'] < chapter['end_lines'] <= len(lines)
            assert '\n'.join(lines[chapter['start_lines']:chapter['end_lines']]).strip()
        chapter_counts.append({'volume': listed['name'], 'textChapters': len(chapters), 'bytes': len(response.content)})
    row = {'query': query, 'title': book['name'], 'volumes': len(volumes), 'checked': chapter_counts}
    report.append(row)
    print(json.dumps(row, ensure_ascii=False), flush=True)
discovery = api('/books', ordering='-datetime_updated', limit=21, offset=0, free_type=1)
assert discovery['list']
print('discovery books:', len(discovery['list']), flush=True)
Path('artifacts').mkdir(exist_ok=True)
Path('artifacts/japanese-text-api-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), 'utf-8')
