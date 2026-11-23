"""Record live catalog/volume coverage; never infer uncut text from catalog counts."""
import argparse
import concurrent.futures
import json
import time
from pathlib import Path

import requests

SAMPLES = [
    '回复术士的重启人生', '义妹生活', '无职转生', '欢迎来到实力至上主义的教室',
    'Re:从零开始的异世界生活', 'OVERLORD', '刀剑神域', '游戏人生',
    '果然我的青春恋爱喜剧搞错了', '为美好的世界献上祝福', '龙与虎',
]


def collect(provider, proxy):
    session = requests.Session()
    session.trust_env = False
    if proxy:
        session.proxies = {'https': proxy}
    rows = []
    for query in SAMPLES:
        row = {'provider': provider, 'query': query, 'candidates': []}
        try:
            if provider == 'Mangacopy':
                base = 'https://api.mangacopy.com/api/v3'
                response = session.get(base + '/search/books', params={
                    'q': query, 'limit': 20, 'offset': 0, '_update': 'true',
                }, timeout=(20, 30))
                response.raise_for_status()
                data = response.json()
                assert data['code'] == 200, data.get('message')
                candidates = data['results']['list']
                for book in candidates[:8]:
                    response = session.get(base + '/book/' + book['path_word'] + '/volumes',
                                           params={'_update': 'true'}, timeout=(20, 30))
                    response.raise_for_status()
                    data = response.json()
                    assert data['code'] == 200, data.get('message')
                    row['candidates'].append({
                        'title': book['name'], 'id': book['path_word'],
                        'authors': [author['name'] for author in book.get('author', [])],
                        'volumes': [{'id': volume['id'], 'title': volume['name']}
                                    for volume in data['results']['list']],
                    })
                    time.sleep(0.5)
            else:
                base = 'https://n.novelia.cc/api/wenku'
                response = session.get(base, params={'query': query, 'page': 0, 'pageSize': 20, 'level': 0},
                                       timeout=(20, 30))
                response.raise_for_status()
                for book in response.json()['items'][:8]:
                    response = session.get(base + '/' + book['id'], timeout=(20, 30))
                    response.raise_for_status()
                    detail = response.json()
                    row['candidates'].append({
                        'title': book.get('titleZh', ''), 'originalTitle': book['title'], 'id': book['id'],
                        'authors': detail.get('authors', []),
                        'volumes': detail.get('volumeZh', []),
                        'metadataVolumes': [volume.get('title', '') for volume in detail.get('volumes', [])],
                    })
                    time.sleep(0.5)
        except Exception as error:
            row['error'] = type(error).__name__ + ': ' + str(error)[:180]
        rows.append(row)
        print(provider, query, [(book['title'], len(book['volumes'])) for book in row['candidates']],
              row.get('error', ''), flush=True)
    return rows


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--proxy')
    parser.add_argument('--output', default='artifacts/light-novel-coverage.json')
    args = parser.parse_args()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda provider: collect(provider, args.proxy), ['Mangacopy', 'Novelia']))
    output = Path(args.output)
    output.parent.mkdir(exist_ok=True, parents=True)
    output.write_text(json.dumps([row for group in results for row in group], ensure_ascii=False, indent=2), 'utf-8')
