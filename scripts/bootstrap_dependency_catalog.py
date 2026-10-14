"""下载严格依赖检查所缺的精确 JAR，不替代版本及字节码门禁。"""
import argparse
import concurrent.futures
import hashlib
import io
import json
import os
import re
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from urllib.parse import urlsplit

from verify_dependency_alignment import verify

MAX_DOWNLOAD_BYTES = 256 * 1024 * 1024


def safe_component(value):
    if not re.fullmatch(r'[A-Za-z0-9_+.-]+', value) or value in ('.', '..'):
        raise ValueError('invalid artifact coordinate')
    return value


def artifact_path(group, artifact, version, classifier=''):
    parts = [safe_component(part) for part in group.split('.')]
    if any(not part for part in parts):
        raise ValueError('invalid artifact group')
    artifact, version = safe_component(artifact), safe_component(version)
    suffix = '-' + safe_component(classifier) if classifier else ''
    return Path(*parts) / artifact / version / (artifact + '-' + version + suffix + '.jar')


def verified_download(url, destination, fetch):
    data = fetch(url)
    if len(data) > MAX_DOWNLOAD_BYTES:
        raise ValueError('artifact exceeds download limit')
    checksum = None
    for algorithm in ('sha256', 'sha1'):
        try:
            value = fetch(url + '.' + algorithm).decode('ascii').strip().split()[0]
        except (OSError, KeyError, IndexError, UnicodeError):
            continue
        expected_length = 64 if algorithm == 'sha256' else 40
        if not re.fullmatch('[0-9a-fA-F]{' + str(expected_length) + '}', value):
            raise ValueError('invalid remote checksum')
        if hashlib.new(algorithm, data).hexdigest().lower() != value.lower():
            raise ValueError('artifact checksum mismatch')
        checksum = algorithm
        break
    if checksum is None:
        raise ValueError('remote checksum missing')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if archive.testzip() is not None:
            raise ValueError('corrupt JAR entry')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as output:
            temporary = Path(output.name)
            output.write(data)
        os.replace(temporary, destination)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    return {'sha256': hashlib.sha256(data).hexdigest(), 'remoteChecksum': checksum,
            'bytes': len(data)}


def repositories(effective_paths):
    result = []
    for path in effective_paths:
        root = ET.parse(path).getroot()
        namespace = {'m': root.tag.split('}')[0][1:]}
        for repository in root.findall('m:repositories/m:repository', namespace):
            url = (repository.findtext('m:url', namespaces=namespace) or '').rstrip('/')
            parsed = urlsplit(url)
            if parsed.scheme != 'https' or parsed.username or parsed.password:
                continue
            item = {'id': repository.findtext('m:id', namespaces=namespace), 'url': url,
                    'snapshots': repository.findtext('m:snapshots/m:enabled', default='true', namespaces=namespace) != 'false',
                    'releases': repository.findtext('m:releases/m:enabled', default='true', namespaces=namespace) != 'false'}
            if item not in result:
                result.append(item)
    if not any(item['url'] == 'https://repo.maven.apache.org/maven2' for item in result):
        result.append({'id': 'central', 'url': 'https://repo.maven.apache.org/maven2',
                       'snapshots': False, 'releases': True})
    return result


def downloader(repository_list, settings):
    credentials = {}
    if settings.exists():
        root = ET.parse(settings).getroot()
        for element in root.iter():
            element.tag = element.tag.rsplit('}', 1)[-1]
        for server in root.findall('servers/server'):
            credentials[server.findtext('id')] = (server.findtext('username'), server.findtext('password'))
    manager = urllib.request.HTTPPasswordMgrWithDefaultRealm()
    for repository in repository_list:
        if repository['id'] in credentials:
            username, password = credentials[repository['id']]
            if username and password:
                manager.add_password(None, repository['url'], username, password)

    def fetch(url):
        # 每次请求持有独立 opener，避免并行认证处理器共享请求状态。
        opener = urllib.request.build_opener(urllib.request.HTTPBasicAuthHandler(manager))
        with opener.open(url, timeout=30) as response:
            data = response.read(MAX_DOWNLOAD_BYTES + 1)
        if len(data) > MAX_DOWNLOAD_BYTES:
            raise ValueError('artifact exceeds download limit')
        return data
    return fetch


def remote_url(repository, group, artifact, version, classifier, fetch):
    relative = artifact_path(group, artifact, version, classifier)
    prefix = repository['url'] + '/' + relative.parent.as_posix() + '/'
    remote_version = version
    if version.endswith('-SNAPSHOT'):
        metadata = ET.fromstring(fetch(prefix + 'maven-metadata.xml'))
        remote_version = next((entry.findtext('value') for entry in metadata.findall('./versioning/snapshotVersions/snapshotVersion')
                               if entry.findtext('extension') == 'jar' and (entry.findtext('classifier') or '') == classifier), None)
        if not remote_version:
            raise ValueError('snapshot JAR metadata missing')
        safe_component(remote_version)
    return prefix + artifact + '-' + remote_version + ('-' + classifier if classifier else '') + '.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--roots', nargs=3, required=True, type=Path)
    parser.add_argument('--effective', nargs=3, required=True, type=Path)
    parser.add_argument('--policy', required=True, type=Path)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    parser.add_argument('--settings', type=Path, default=Path.home() / '.m2/settings.xml')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--workers', type=int, choices=range(1, 5), default=4)
    args = parser.parse_args()
    policy = json.loads(args.policy.read_text())
    _, _, rows = verify(args.roots, args.effective, policy, args.repository, None, False)
    pending = set()
    for coordinate, _, version, status, _, _ in rows:
        if status != 'unverified-artifact':
            continue
        parts = coordinate.split(':')
        group, artifact = parts[:2]
        classifier = parts[3] if len(parts) == 4 else ''
        artifact_path(group, artifact, version, classifier)
        pending.add((group, artifact, version, classifier))
    repository_list = repositories(args.effective)
    fetch = downloader(repository_list, args.settings)

    def prepare(coordinate):
        group, artifact, version, classifier = coordinate
        destination = args.repository / artifact_path(*coordinate)
        errors = []
        for repository in repository_list:
            if not repository['snapshots' if version.endswith('-SNAPSHOT') else 'releases']:
                continue
            try:
                url = remote_url(repository, *coordinate, fetch)
                evidence = verified_download(url, destination, fetch)
                return {'coordinate': ':'.join(coordinate), 'status': 'DOWNLOADED',
                        'repositoryId': repository['id'], **evidence}
            except (OSError, ValueError, ET.ParseError, zipfile.BadZipFile) as error:
                errors.append({'repositoryId': repository['id'], 'errorType': type(error).__name__,
                               'httpStatus': getattr(error, 'code', None)})
        return {'coordinate': ':'.join(coordinate), 'status': 'FAILED', 'errors': errors}

    args.output.mkdir(parents=True, exist_ok=True)
    evidence = []
    (args.output / 'downloads.json').write_text('[]\n')
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        for row in executor.map(prepare, sorted(pending)):
            evidence.append(row)
            if row['status'] == 'FAILED':
                print('catalog download FAILED: ' + row['coordinate'], flush=True)
            elif len(evidence) % 50 == 0:
                print('catalog downloads: ' + str(len(evidence)) + '/' + str(len(pending)), flush=True)
            if len(evidence) % 25 == 0:
                (args.output / 'downloads.json').write_text(json.dumps(evidence, indent=2) + '\n')
    (args.output / 'downloads.json').write_text(json.dumps(evidence, indent=2) + '\n')
    failed = sum(row['status'] == 'FAILED' for row in evidence)
    print('catalog preparation: ' + str(len(evidence)) + ' downloads, ' + str(failed) + ' failures')
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
