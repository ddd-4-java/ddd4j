#!/usr/bin/env python3
"""检查三线共同产品文档正文与本地 Markdown 链接。"""

import argparse
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


DOCUMENTS = (
    "5、ddd4j-技术方案与路线.md",
    "6、ddd4j-产品与版本规划.md",
    "7、ddd4j-领域模型设计.md",
    "8、ddd4j-Architecture.zh_CN.md",
    "8、ddd4j-1.0.x-Architecture.zh_CN.md",
    "auth-satoken.md",
    "webmvc-flow.md",
    "webflux-flow.md",
    "cqrs-es-base-capabilities.md",
)
LINK = re.compile(r'(?:\]\(([^)]+)\)|href=["\']([^"\']+)["\'])')


def local_markdown_links(document: Path):
    for match in LINK.finditer(document.read_text(encoding="utf-8")):
        raw = (match.group(1) or match.group(2)).strip().split(" ", 1)[0]
        parsed = urlsplit(raw)
        if parsed.scheme or parsed.netloc or not parsed.path:
            continue
        path = unquote(parsed.path)
        if path.endswith(".md"):
            yield raw, (document.parent / path).resolve()


def check(roots: list[Path], output: Path) -> int:
    differences = {}
    broken_links = []
    hashes = {}
    for name in DOCUMENTS:
        relative = "docs/" + name
        values = []
        for root in roots:
            document = root / relative
            values.append(hashlib.sha256(document.read_bytes()).hexdigest() if document.is_file() else "MISSING")
        hashes[relative] = values
        if len(set(values)) != 1 or "MISSING" in values:
            differences[relative] = values
    for root in roots:
        for relative in ("README.md", "README.en.md", *("docs/" + name for name in DOCUMENTS)):
            document = root / relative
            if not document.is_file():
                broken_links.append({"root": str(root), "source": relative, "target": "MISSING"})
                continue
            for raw, target in local_markdown_links(document):
                if not target.is_file():
                    broken_links.append({"root": str(root), "source": relative, "target": raw})
    status = "FAIL" if differences or broken_links else "PASS"
    report = {"schemaVersion": 1, "status": status, "roots": list(map(str, roots)),
              "documents": hashes, "differences": differences, "brokenLinks": broken_links}
    output.mkdir(parents=True, exist_ok=True)
    (output / "product-docs.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                              encoding="utf-8")
    print(f"product-docs={status} differences={len(differences)} broken-links={len(broken_links)}")
    return 0 if status == "PASS" else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--roots", nargs=3, type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    return check([root.resolve() for root in args.roots], args.output)


if __name__ == "__main__":
    raise SystemExit(main())
