"""Publish the explicitly selected gallery manifest using the existing project token."""
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / '26.3/docs/screenshots/2.6'
BASE = 'https://api.modrinth.com/v2'
PROJECT = 'eJsPPGy9'
HEADERS = {'Authorization': os.environ['MODRINTH_TOKEN'],
           'User-Agent': 'topasmusic/Village-Quest-gallery', 'Content-Type': 'image/png'}


def request(path, data=None, method='GET'):
    try:
        with urllib.request.urlopen(urllib.request.Request(
                BASE + path, data=data, headers=HEADERS, method=method), timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Modrinth returned HTTP {error.code}; no automatic retry. Inspect gallery before resuming.') from None


def main():
    entries = json.loads((FOLDER / 'gallery.json').read_text(encoding='utf-8'))
    for item in entries:
        path = (FOLDER / item['file']).resolve()
        assert path.parent == FOLDER.resolve()
        raw = path.read_bytes()
        assert raw.startswith(b'\x89PNG\r\n\x1a\n') and len(raw) <= 5 * 1024 * 1024
        assert hashlib.sha256(raw).hexdigest() == item['sha256']
    user = request('/user')
    assert user['username'].lower() == 'topasmusic'
    project = request('/project/' + PROJECT)
    assert project['slug'] == 'village-quest'
    original_urls = {image['url'] for image in project['gallery']}
    for item in entries:
        project = request('/project/' + PROJECT)
        matches = [image for image in project['gallery'] if image.get('title') == item['title']]
        assert len(matches) <= 1, 'Ambiguous gallery title; inspect before changing anything.'
        query = {'title': item['title'], 'description': item['description'],
                 'ordering': item['ordering']}
        if matches:
            assert matches[0]['description'] == item['description'], 'Existing caption differs; inspect first.'
            print('Already present: ' + item['title'])
        else:
            query.update(ext='png', featured='false')
            request('/project/' + PROJECT + '/gallery?' + urllib.parse.urlencode(query),
                    (FOLDER / item['file']).read_bytes(), 'POST')
            print('Uploaded: ' + item['title'])
        verified = request('/project/' + PROJECT)
        matches = [image for image in verified['gallery'] if image.get('title') == item['title']]
        assert len(matches) == 1 and matches[0]['description'] == item['description']
    final = request('/project/' + PROJECT)
    assert original_urls.issubset({image['url'] for image in final['gallery']})
    print(f'PASS: {len(entries)} gallery images and captions verified; existing images preserved.')


if __name__ == '__main__':
    main()
