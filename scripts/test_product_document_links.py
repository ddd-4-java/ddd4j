"""README 中的产品架构入口必须指向真实文件。"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOCUMENTS = ("5、ddd4j-技术方案与路线.md", "8、ddd4j-Architecture.zh_CN.md")
CANONICAL_DOCUMENTS = (
    *DOCUMENTS,
    "6、ddd4j-产品与版本规划.md",
    "7、ddd4j-领域模型设计.md",
    "8、ddd4j-1.0.x-Architecture.zh_CN.md",
)


class ProductDocumentLinksTest(unittest.TestCase):
    def test_readme_architecture_links_resolve(self):
        for readme in ("README.md", "README.en.md"):
            content = (ROOT / readme).read_text()
            for document in DOCUMENTS:
                destinations = re.findall(r'(?:href="|\]\()(.*?' + re.escape(document) + r')(?:"|\))', content)
                self.assertTrue(destinations, (readme, document))
                for destination in destinations:
                    self.assertTrue((ROOT / destination).resolve().is_file(), (readme, destination))

    def test_line_specific_architecture_cross_links_resolve(self):
        document = ROOT / "docs" / "8、ddd4j-1.0.x-Architecture.zh_CN.md"
        self.assertTrue(document.is_file())
        content = document.read_text()
        for target in DOCUMENTS:
            destinations = re.findall(r'\]\(([^)]*' + re.escape(target) + r')\)', content)
            self.assertTrue(destinations, target)
            for destination in destinations:
                self.assertTrue((document.parent / destination).resolve().is_file(), (document, destination))

    def test_canonical_product_documents_exist(self):
        for name in CANONICAL_DOCUMENTS:
            self.assertTrue((ROOT / "docs" / name).is_file(), name)

    def test_legacy_product_document_redirects_resolve(self):
        legacy = [ROOT / name for name in CANONICAL_DOCUMENTS if (ROOT / name).is_file()]
        special = ROOT / "1.0.x" / "8、ddd4j-1.0.x-Architecture.zh_CN.md"
        if special.is_file():
            legacy.append(special)
        for document in legacy:
            destinations = re.findall(r"\]\(([^)]+\.md)\)", document.read_text())
            self.assertEqual(1, len(destinations), document)
            self.assertEqual(
                ROOT / "docs" / document.name,
                (document.parent / destinations[0]).resolve(),
                document,
            )


if __name__ == "__main__":
    unittest.main()
