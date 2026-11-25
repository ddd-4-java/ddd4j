"""SBOM generator and license entry-point regression tests."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parent
COMPONENT = "pkg:maven/org.example/dependency@1"
ROOT = "pkg:maven/io.ddd4j/ddd4j@1?type=pom"
CORE = "pkg:maven/io.ddd4j/ddd4j-core@1"
GUICE = "pkg:maven/io.ddd4j/ddd4j-runtime-guice@1"
PUBLISHED_SAMPLE = "pkg:maven/io.ddd4j/ddd4j-sample-order-domain@1"


def component(purl):
    group, artifact = purl.removeprefix("pkg:maven/").split("/")
    name, version = artifact.split("@")
    item = {
        "type": "library", "group": group, "name": name,
        "version": version.partition("?")[0], "purl": purl, "bom-ref": purl,
    }
    if group == "org.example":
        item["hashes"] = [{"alg": "SHA-256", "content": "a" * 64}]
    return item


def bom(root, components, edges=None):
    return {
        "bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
        "metadata": {"component": component(root)},
        "components": [component(purl) for purl in components],
        "dependencies": [
            {"ref": ref, "dependsOn": children}
            for ref, children in (edges or {root: list(components)}).items()
        ],
    }


def pom(artifact_id, body=""):
    return (
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
        "<modelVersion>4.0.0</modelVersion><groupId>io.ddd4j</groupId>"
        f"<artifactId>{artifact_id}</artifactId><version>1</version>{body}</project>"
    )


class SbomTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.report_dir = self.root / "external reports"
        self.sbom = self.report_dir / "sbom" / "ddd4j-sbom.json"
        self.scripts = self.root / "scripts"
        self.scripts.mkdir()
        for filename in ("generate-sbom.sh", "sbom.py", "license_policy.py",
                         "verify-three-line-convergence.py"):
            if (SCRIPTS / filename).exists():
                shutil.copy2(SCRIPTS / filename, self.scripts / filename)

        self.maven_calls = self.root / "maven-calls.jsonl"
        home = self.root / "isolated-home"
        repository = home / ".m2" / "repository"
        repository.mkdir(parents=True)
        self.marker = repository / "_remote.repositories"
        self.marker.write_text("keep cache metadata", encoding="utf-8")
        self.environment = {
            **os.environ, "HOME": str(home), "DDD4J_REPORT_DIR": str(self.report_dir),
            "FIXTURE": str(self.root),
        }

        root_model = pom(
            "ddd4j",
            "<packaging>pom</packaging><modules><module>ddd4j-core</module>"
            "<module>runtime/guice</module><module>samples/order-domain</module>"
            "<module>samples/private</module></modules>",
        )
        dependency = (
            "<dependencies><dependency><groupId>org.example</groupId>"
            "<artifactId>dependency</artifactId><version>1</version></dependency></dependencies>"
        )
        projects = (
            root_model, pom("ddd4j-core"), pom("ddd4j-runtime-guice", dependency),
            pom("ddd4j-sample-order-domain"),
            pom("private-sample", "<properties><maven.deploy.skip>true</maven.deploy.skip></properties>"),
        )
        (self.root / "pom.xml").write_text(root_model, encoding="utf-8")
        for directory, model in zip(
            ("ddd4j-core", "runtime/guice", "samples/order-domain", "samples/private"), projects[1:],
        ):
            module = self.root / directory
            module.mkdir(parents=True)
            (module / "pom.xml").write_text(model, encoding="utf-8")

        maven = self.root / "mvnw"
        maven.write_text("""#!/usr/bin/env python3
import json
import os
from pathlib import Path
import shutil
import sys

root = Path(os.environ["FIXTURE"])
args = sys.argv[1:]
with (root / "maven-calls.jsonl").open("a") as f:
    f.write(json.dumps(args) + "\\n")
options = dict(arg[2:].split("=", 1) for arg in args if arg.startswith("-D") and "=" in arg)
if any(arg.endswith("makeAggregateBom") for arg in args):
    case = os.environ["AGGREGATE"]
    name = "aggregate"
else:
    module = args[args.index("-pl") + 1].lstrip(":")
    if module == ".":
        module = "ddd4j"
    name = module
    case = os.environ["MODULE_" + name] if "MODULE_" + name in os.environ else "valid"
    if not (root / (name + ".json")).is_file():
        name = "ddd4j-core"
if case == "exit":
    sys.exit(23)
path = Path(options["outputDirectory"], options["outputName"])
path.parent.mkdir(parents=True, exist_ok=True)
if case == "missing":
    sys.exit(0)
if case == "malformed":
    path.with_suffix(".json").write_text("{")
elif case == "partial":
    shutil.copyfile(root / "partial.json", path.with_suffix(".json"))
else:
    shutil.copyfile(root / (name + ".json"), path.with_suffix(".json"))
path.with_suffix(".xml").write_text('<bom xmlns="http://cyclonedx.org/schema/bom/1.6" version="1"/>')
""", encoding="utf-8")
        maven.chmod(0o755)

        self.write_bom("aggregate", bom(ROOT, [CORE, GUICE, PUBLISHED_SAMPLE, COMPONENT], {
            ROOT: [CORE, GUICE, PUBLISHED_SAMPLE], CORE: [], GUICE: [COMPONENT],
            PUBLISHED_SAMPLE: [], COMPONENT: [],
        }))
        self.write_bom("partial", bom(ROOT, [CORE, COMPONENT]))
        self.write_bom("ddd4j", bom(ROOT, []))
        empty_module = bom(CORE, [])
        del empty_module["components"]
        self.write_bom("ddd4j-core", empty_module)
        self.write_bom("ddd4j-runtime-guice", bom(GUICE, [COMPONENT]))
        self.write_bom("ddd4j-sample-order-domain", bom(PUBLISHED_SAMPLE, []))

    def write_bom(self, name, document):
        (self.root / f"{name}.json").write_text(json.dumps(document), encoding="utf-8")

    def generate(self, aggregate, **overrides):
        return subprocess.run(
            ["bash", str(self.scripts / "generate-sbom.sh")], cwd=self.root,
            env={**self.environment, "AGGREGATE": aggregate, **overrides},
            capture_output=True, text=True, timeout=30,
        )

    def test_aggregate_preserves_output_and_cache(self):
        run = self.generate("valid")
        self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
        self.assertTrue(self.sbom.with_suffix(".xml").is_file())
        self.assertEqual(self.marker.read_text(encoding="utf-8"), "keep cache metadata")

    def test_aggregate_missing_malformed_or_missing_module_fails(self):
        for case in ("missing", "malformed", "partial"):
            with self.subTest(case=case):
                run = self.generate(case)
                self.assertNotEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertFalse(self.sbom.exists())

    def test_successful_maven_run_cannot_archive_external_jar_without_sha256(self):
        complete = json.loads((self.root / "aggregate.json").read_text())
        for hashes in ([], [{"alg": "SHA-256", "content": "too-short"}]):
            with self.subTest(hashes=hashes):
                dependency = next(
                    item for item in complete["components"] if item["purl"] == COMPONENT
                )
                dependency["hashes"] = hashes
                self.write_bom("aggregate", complete)
                run = self.generate("valid")
                self.assertNotEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertIn("missing SHA-256 for external Maven JAR", run.stderr)
                self.assertFalse(self.sbom.exists())

    def test_fallback_covers_newly_added_modules_and_allows_dependency_free_modules(self):
        run = self.generate("exit")
        self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
        coordinates = {item["purl"] for item in json.loads(self.sbom.read_text())["components"]}
        self.assertEqual(coordinates, {CORE, GUICE, PUBLISHED_SAMPLE, COMPONENT})
        self.assertNotIn(":private-sample", self.maven_calls.read_text())

    def test_fallback_module_failure_or_missing_or_malformed_report_fails_closed(self):
        for case in ("exit", "missing", "malformed", "partial"):
            with self.subTest(case=case):
                run = self.generate("exit", **{"MODULE_ddd4j-runtime-guice": case})
                self.assertNotEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertFalse(self.sbom.exists())

    def test_module_report_with_omitted_declared_dependency_fails(self):
        self.write_bom("ddd4j-runtime-guice", bom(GUICE, []))
        run = self.generate("exit")
        self.assertNotEqual(run.returncode, 0, run.stdout + run.stderr)
        self.assertFalse(self.sbom.exists())

    def test_license_entrypoint_rejects_unusable_sbom_before_classifying_exclusions(self):
        inventory = self.root / "THIRD-PARTY.txt"
        inventory.write_text(
            "(GPL-3.0-only) Example (org.example:dependency:1 - https://example.org)\n",
            encoding="utf-8",
        )
        selections = self.root / "selections.tsv"
        selections.write_text(
            "coordinate\tdeclared_expression\tselected_spdx\tevidence_url\tevidence_type\tjustification\n",
            encoding="utf-8",
        )
        exclusions = self.root / "exclusions.tsv"
        exclusions.write_text("org.example:dependency:1\tbuild-tool fixture\n", encoding="utf-8")
        path = self.root / "report.json"
        command = [str(self.scripts / "license_policy.py"), "--inventory", str(inventory),
                   "--selections", str(selections), "--build-tool-exclusions", str(exclusions),
                   "--sbom", str(path)]
        for document in (
            {}, bom(ROOT, []), bom(ROOT, ["pkg:maven/org.example/dependency@"]),
            {**bom(ROOT, [COMPONENT]), "dependencies": "malformed"},
        ):
            with self.subTest(document=document):
                path.write_text(json.dumps(document), encoding="utf-8")
                run = subprocess.run(command, capture_output=True, text=True, timeout=10)
                self.assertNotEqual(run.returncode, 0, run.stdout + run.stderr)
                self.assertIn("[FAIL]", run.stderr)
        path.write_text(json.dumps(bom(ROOT, [COMPONENT])), encoding="utf-8")
        run = subprocess.run(command, capture_output=True, text=True, timeout=10)
        self.assertNotEqual(run.returncode, 0)
        self.assertIn("build-tool exclusion overlaps published SBOM", run.stderr)


if __name__ == "__main__":
    unittest.main()
