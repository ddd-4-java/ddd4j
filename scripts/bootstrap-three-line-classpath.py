#!/usr/bin/env python3
"""为三线值对象探针准备经 SHA-256 和 JDK 8 字节码校验的固定测试依赖。"""

import argparse
import hashlib
import io
from pathlib import Path
import re
import time
import urllib.error
import urllib.request
import zipfile


VERSION = '2.22'
ARTIFACT = 'jackson-annotations-' + VERSION + '.jar'
RELATIVE = Path('com/fasterxml/jackson/core/jackson-annotations') / VERSION / ARTIFACT
URL = 'https://repo.maven.apache.org/maven2/' + RELATIVE.as_posix()


def download(url):
    """下载公开 Maven Central 文件；网络错误重试，最终仍失败关闭。"""
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read(2_000_001)
            if len(data) > 2_000_000:
                raise ValueError('Bootstrap artifact exceeds size limit')
            return data
        except urllib.error.URLError:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def validate(data, checksum):
    """验证来源校验和与探针实际使用的注解字节码。"""
    if not re.fullmatch(r'[0-9a-fA-F]{64}', checksum) or hashlib.sha256(data).hexdigest() != checksum.lower():
        raise ValueError('Jackson annotations SHA-256 mismatch')
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        header = archive.read('com/fasterxml/jackson/annotation/JsonCreator.class')[:8]
    if len(header) != 8 or header[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(header[6:8], 'big') > 52:
        raise ValueError('Jackson annotations bytecode is incompatible with JDK 8')


def bootstrap(repository):
    """仅在完整验证后写入缓存，供原来的失败关闭探针检查器使用。"""
    target = repository / RELATIVE
    checksum_path = target.with_suffix('.jar.sha256')
    if target.is_file() and checksum_path.is_file():
        validate(target.read_bytes(), checksum_path.read_text().strip())
        return target
    data = download(URL)
    checksum = download(URL + '.sha256').decode('ascii').strip()
    validate(data, checksum)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.jar.tmp')
    temporary.write_bytes(data)
    temporary.replace(target)
    checksum_path.write_text(checksum + '\n')
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    args = parser.parse_args()
    print('Prepared JDK 8 compatible value-probe annotations:', bootstrap(args.repository))
