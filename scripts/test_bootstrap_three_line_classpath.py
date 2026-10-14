import importlib.util
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


class BootstrapClasspathTest(unittest.TestCase):
    def module(self):
        spec = importlib.util.spec_from_file_location('bootstrap', Path(__file__).with_name('bootstrap-three-line-classpath.py'))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def jar(self, major=52):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as archive:
            archive.writestr('com/fasterxml/jackson/annotation/JsonCreator.class', b'\xca\xfe\xba\xbe\x00\x00' + major.to_bytes(2, 'big'))
        return data.getvalue()

    def test_cold_repository_downloads_and_validates_jdk8_jar(self):
        module = self.module()
        data = self.jar()
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'download', side_effect=[data, hashlib.sha256(data).hexdigest().encode()]):
            result = module.bootstrap(Path(directory))
            self.assertEqual(result.read_bytes(), data)

    def test_wrong_checksum_fails_without_publishing_cache_file(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'download', side_effect=[self.jar(), b'0' * 64]):
            with self.assertRaises(ValueError):
                module.bootstrap(Path(directory))
            self.assertEqual(list(Path(directory).rglob('*.jar')), [])

    def test_incompatible_bytecode_is_rejected(self):
        module = self.module()
        data = self.jar(61)
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'download', side_effect=[data, hashlib.sha256(data).hexdigest().encode()]):
            with self.assertRaises(ValueError):
                module.bootstrap(Path(directory))

    def test_cached_verified_artifact_does_not_require_network(self):
        module = self.module()
        data = self.jar()
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(module, 'download', side_effect=[data, hashlib.sha256(data).hexdigest().encode()]):
                first = module.bootstrap(Path(directory))
            with patch.object(module, 'download', side_effect=AssertionError('unexpected network')):
                self.assertEqual(module.bootstrap(Path(directory)), first)
