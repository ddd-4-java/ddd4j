"""冷缓存依赖目录准备的回归测试，不访问网络。"""
import hashlib
import io
import tempfile
import unittest
import zipfile
from pathlib import Path

from bootstrap_dependency_catalog import artifact_path, remote_url, verified_download


def jar_bytes():
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        archive.writestr('sample/Value.class', b'\xca\xfe\xba\xbe\x00\x00\x00\x34')
    return output.getvalue()


class BootstrapDependencyCatalogTest(unittest.TestCase):
    def test_verified_download_writes_exact_coordinate(self):
        data = jar_bytes()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / artifact_path('example', 'library', '1')
            responses = {'https://repo/library.jar': data,
                         'https://repo/library.jar.sha256': hashlib.sha256(data).hexdigest().encode()}
            verified_download('https://repo/library.jar', target, responses.__getitem__)
            self.assertEqual(data, target.read_bytes())

    def test_wrong_checksum_does_not_publish_cache_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'library.jar'
            responses = {'https://repo/library.jar': jar_bytes(),
                         'https://repo/library.jar.sha256': b'0' * 64}
            with self.assertRaisesRegex(ValueError, 'checksum'):
                verified_download('https://repo/library.jar', target, responses.__getitem__)
            self.assertFalse(target.exists())

    def test_matching_checksum_cannot_accept_corrupt_jar(self):
        data = b'not a jar'
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'library.jar'
            responses = {'https://repo/library.jar': data,
                         'https://repo/library.jar.sha256': hashlib.sha256(data).hexdigest().encode()}
            with self.assertRaises(zipfile.BadZipFile):
                verified_download('https://repo/library.jar', target, responses.__getitem__)
            self.assertFalse(target.exists())

    def test_missing_remote_checksum_is_a_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'library.jar'
            responses = {'https://repo/library.jar': jar_bytes()}
            with self.assertRaisesRegex(ValueError, 'checksum'):
                verified_download('https://repo/library.jar', target, responses.__getitem__)
            self.assertFalse(target.exists())

    def test_classifier_is_part_of_exact_cache_identity(self):
        self.assertEqual(Path('example/library/1/library-1-tests.jar'),
                         artifact_path('example', 'library', '1', 'tests'))

    def test_sha1_fallback_still_verifies_actual_bytes(self):
        data = jar_bytes()
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'library.jar'
            responses = {'https://repo/library.jar': data,
                         'https://repo/library.jar.sha1': hashlib.sha1(data).hexdigest().encode()}
            evidence = verified_download('https://repo/library.jar', target, responses.__getitem__)
            self.assertEqual('sha1', evidence['remoteChecksum'])
            self.assertEqual(data, target.read_bytes())

    def test_snapshot_metadata_resolves_exact_classifier(self):
        metadata = b'''<metadata><versioning><snapshotVersions>
        <snapshotVersion><extension>jar</extension><classifier>tests</classifier>
        <value>1-20261014.010000-2</value></snapshotVersion>
        <snapshotVersion><extension>jar</extension><value>1-20261014.010000-1</value></snapshotVersion>
        </snapshotVersions></versioning></metadata>'''
        self.assertEqual('https://repo/example/library/1-SNAPSHOT/library-1-20261014.010000-2-tests.jar',
                         remote_url({'url': 'https://repo'}, 'example', 'library', '1-SNAPSHOT',
                                    'tests', lambda _: metadata))

    def test_snapshot_metadata_cannot_switch_version_line(self):
        metadata = b'''<metadata><versioning><snapshotVersions>
        <snapshotVersion><extension>jar</extension><value>2-20261014.010000-1</value></snapshotVersion>
        </snapshotVersions></versioning></metadata>'''
        with self.assertRaisesRegex(ValueError, 'version'):
            remote_url({'url': 'https://repo'}, 'example', 'library', '1-SNAPSHOT',
                       '', lambda _: metadata)

    def test_path_traversal_coordinates_are_rejected(self):
        for coordinate in [('example', '../other', '1'), ('..', 'library', '1'),
                           ('example', 'library', '../1')]:
            with self.assertRaises(ValueError):
                artifact_path(*coordinate)


if __name__ == '__main__':
    unittest.main()
