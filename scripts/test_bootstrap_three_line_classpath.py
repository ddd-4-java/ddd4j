import importlib.util
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
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
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'CHECKSUM', hashlib.sha256(data).hexdigest()), patch.object(module, 'download', return_value=data) as download:
            result = module.bootstrap(Path(directory))
            self.assertEqual(result.read_bytes(), data)
            download.assert_called_once()

    def test_wrong_checksum_fails_without_publishing_cache_file(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'CHECKSUM', '0' * 64), patch.object(module, 'download', return_value=self.jar()):
            with self.assertRaises(ValueError):
                module.bootstrap(Path(directory))
            self.assertEqual(list(Path(directory).rglob('*.jar')), [])

    def test_incompatible_bytecode_is_rejected(self):
        module = self.module()
        data = self.jar(61)
        with tempfile.TemporaryDirectory() as directory, patch.object(module, 'CHECKSUM', hashlib.sha256(data).hexdigest()), patch.object(module, 'download', return_value=data):
            with self.assertRaises(ValueError):
                module.bootstrap(Path(directory))

    def test_cached_verified_artifact_does_not_require_network(self):
        module = self.module()
        data = self.jar()
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(module, 'CHECKSUM', hashlib.sha256(data).hexdigest()):
                with patch.object(module, 'download', return_value=data):
                    first = module.bootstrap(Path(directory))
                with patch.object(module, 'download', side_effect=AssertionError('unexpected network')):
                    self.assertEqual(module.bootstrap(Path(directory)), first)

    def test_rate_limited_central_falls_back_to_verified_mirror(self):
        module = self.module()
        data = self.jar()
        limited = urllib.error.HTTPError(module.URL, 429, 'Too Many Requests', {}, None)
        with patch.object(module, 'CHECKSUM', hashlib.sha256(data).hexdigest()), \
                patch.object(module.urllib.request, 'urlopen', side_effect=[limited, limited, io.BytesIO(data)]) as open_url, \
                patch.object(module.time, 'sleep'):
            with tempfile.TemporaryDirectory() as directory:
                result = module.bootstrap(Path(directory))
                self.assertEqual(result.read_bytes(), data)
        self.assertEqual([call.args[0] for call in open_url.call_args_list],
                         [module.URL, module.URL, module.MIRROR_URL])

    def test_cached_checksum_cannot_replace_pinned_digest(self):
        module = self.module()
        data = self.jar()
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(module, 'CHECKSUM', hashlib.sha256(data).hexdigest()), \
                patch.object(module, 'download', return_value=data):
            target = module.bootstrap(Path(directory))
            target.with_suffix('.jar.sha256').write_text('0' * 64)
            with self.assertRaises(ValueError):
                module.bootstrap(Path(directory))
