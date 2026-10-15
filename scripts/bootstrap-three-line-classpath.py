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
MIRROR_URL = 'https://maven.aliyun.com/repository/public/' + RELATIVE.as_posix()
CHECKSUM = '21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0'


def download():
    """从 Maven Central 或镜像下载固定构件；两处都失败时保留网络错误。"""
    last_error = None
    for url in (URL, MIRROR_URL):
        for attempt in range(2):
            try:
                with urllib.request.urlopen(url, timeout=30) as response:
                    data = response.read(2_000_001)
                if len(data) > 2_000_000:
                    raise ValueError('Bootstrap artifact exceeds size limit')
                return data
            except urllib.error.URLError as error:
                last_error = error
                if attempt == 0:
                    time.sleep(1)
    raise last_error


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
        if checksum_path.read_text().strip().lower() != CHECKSUM:
            raise ValueError('Cached Jackson annotations checksum differs from pinned SHA-256')
        validate(target.read_bytes(), CHECKSUM)
        return target
    data = download()
    validate(data, CHECKSUM)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.jar.tmp')
    temporary.write_bytes(data)
    temporary.replace(target)
    checksum_path.write_text(CHECKSUM + '\n')
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path.home() / '.m2/repository')
    args = parser.parse_args()
    print('Prepared JDK 8 compatible value-probe annotations:', bootstrap(args.repository))
