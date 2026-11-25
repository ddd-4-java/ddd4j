"""Generate and verify the Maven release CycloneDX report."""

from dataclasses import dataclass
import json
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import parse_qs, unquote, urlsplit
from xml.etree import ElementTree as ET


CYCLONEDX = "org.cyclonedx:cyclonedx-maven-plugin:2.9.3"
REPORT_NAME = "ddd4j-sbom"
MAVEN_PURL = re.compile(r"pkg:maven/([^/@?#]+)/([^/@?#]+)@([^/@?#]+)(?:\?[^#\s]+)?")
MAVEN_IDENTIFIER = re.compile(r"[A-Za-z0-9_.-]+")


def maven_coordinate(component):
    purl = component.get("purl") if isinstance(component, dict) else None
    match = MAVEN_PURL.fullmatch(purl) if isinstance(purl, str) else None
    if not match:
        raise ValueError(f"missing or invalid Maven package URL: {purl!r}")
    group, name, version = map(unquote, match.groups())
    if (not MAVEN_IDENTIFIER.fullmatch(group) or not MAVEN_IDENTIFIER.fullmatch(name)
            or not version or any(c.isspace() or c in ":/${}" for c in version)):
        raise ValueError(f"incomplete Maven coordinate: {purl}")
    return f"{group}:{name}:{version}"


def read_sbom(path):
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
        if (not isinstance(document, dict) or document.get("bomFormat") != "CycloneDX"
                or document.get("specVersion") not in ("1.5", "1.6")
                or type(document.get("version")) is not int or document["version"] < 1):
            raise ValueError("invalid CycloneDX document header")
        components = document.get("components", [])
        if not isinstance(components, list):
            raise ValueError("CycloneDX components must be a list")
        coordinates = set()
        for component in components:
            coordinate = maven_coordinate(component)
            group = coordinate.split(":", 1)[0]
            artifact_types = parse_qs(urlsplit(component["purl"]).query).get("type", ["jar"])
            if group != "io.ddd4j" and "jar" in artifact_types:
                hashes = component.get("hashes")
                if not isinstance(hashes, list) or not any(
                    isinstance(digest, dict) and digest.get("alg") == "SHA-256"
                    and isinstance(digest.get("content"), str)
                    and re.fullmatch(r"[0-9a-fA-F]{64}", digest["content"])
                    for digest in hashes
                ):
                    raise ValueError(f"missing SHA-256 for external Maven JAR: {coordinate}")
            coordinates.add(coordinate)
        return document, coordinates
    except (OSError, ValueError) as error:
        raise ValueError(f"{path}: {error}") from error


def load_sbom_coordinates(path):
    document, coordinates = read_sbom(Path(path))
    root = metadata_component(document)
    _, graph = report_graph(document, root)
    if root["bom-ref"] not in graph:
        raise ValueError(f"{path}: SBOM has no root dependency graph")
    if not coordinates:
        raise ValueError(f"{path}: SBOM contains no Maven dependency coordinates")
    return coordinates


@dataclass(frozen=True)
class Project:
    coordinate: str
    dependencies: frozenset[str]
    skip_deploy: bool

    @property
    def artifact_id(self):
        return self.coordinate.split(":")[1]


def reactor_projects(root):
    """Use the default reactor declarations and their local Maven parent chain."""
    default_modules = runpy.run_path(
        str(Path(__file__).with_name("verify-three-line-convergence.py")),
    )["default_reactor_modules"]
    models = {}
    coordinates = {}
    revision = ET.parse(root / "pom.xml").getroot().findtext("{*}properties/{*}revision", "").strip()
    for path in default_modules(root):
        model = ET.parse(path / "pom.xml").getroot()
        group = model.findtext("{*}groupId") or model.findtext("{*}parent/{*}groupId", "")
        artifact = model.findtext("{*}artifactId", "")
        version = model.findtext("{*}version") or model.findtext("{*}parent/{*}version", "")
        if version.strip() == "${revision}":
            version = revision
        coordinate = maven_coordinate({"purl": f"pkg:maven/{group.strip()}/{artifact.strip()}@{version.strip()}"})
        artifact_id = artifact.strip()
        if artifact_id in models:
            raise ValueError(f"duplicate reactor artifactId: {artifact_id}")
        models[artifact_id] = model
        coordinates[artifact_id] = coordinate

    def release_options(artifact, ancestors=()):
        model = models[artifact]
        parent = model.findtext("{*}parent/{*}artifactId", "").strip()
        skip, scoped_dependencies = "false", {}
        if parent in models:
            if parent in (*ancestors, artifact):
                raise ValueError(f"cycle in reactor parent chain: {parent}")
            skip, inherited = release_options(parent, (*ancestors, artifact))
            scoped_dependencies.update(inherited)
        skip = model.findtext("{*}properties/{*}maven.deploy.skip", skip).strip().lower()
        if skip not in {"true", "false"}:
            raise ValueError(f"unsupported maven.deploy.skip in {artifact}: {skip!r}")

        for dependency in model.findall("{*}dependencies/{*}dependency"):
            group = dependency.findtext("{*}groupId", "").strip()
            name = dependency.findtext("{*}artifactId", "").strip()
            if not MAVEN_IDENTIFIER.fullmatch(group) or not MAVEN_IDENTIFIER.fullmatch(name):
                raise ValueError(f"unresolved dependency in {artifact}: {group}:{name}")
            kind = dependency.findtext("{*}type", "jar").strip()
            classifier = dependency.findtext("{*}classifier", "").strip()
            scope = dependency.findtext("{*}scope", "compile").strip()
            scoped_dependencies[group, name, kind, classifier] = scope
        return skip, scoped_dependencies

    projects = {}
    for artifact, coordinate in coordinates.items():
        skip, scoped_dependencies = release_options(artifact)
        # The genuine resolver supplies effective versions, qualifiers and transitives.
        dependencies = frozenset(
            f"{group}:{name}" for (group, name, _, _), scope in scoped_dependencies.items()
            if scope != "test"
        )
        projects[artifact] = Project(coordinate, dependencies, skip == "true")
    return projects


def metadata_component(document):
    metadata = document.get("metadata")
    component = metadata.get("component") if isinstance(metadata, dict) else None
    maven_coordinate(component)
    return component


def report_graph(document, root_component):
    components = [root_component, *document.get("components", [])]
    refs = {}
    for component in components:
        ref = component.get("bom-ref")
        if not isinstance(ref, str) or not ref or ref in refs:
            raise ValueError(f"missing or duplicate BOM reference: {ref!r}")
        refs[ref] = maven_coordinate(component)

    graph = {}
    dependencies = document.get("dependencies")
    if not isinstance(dependencies, list):
        raise ValueError("CycloneDX dependencies must be a list")
    for dependency in dependencies:
        if not isinstance(dependency, dict):
            raise ValueError("invalid dependency graph entry")
        ref = dependency.get("ref")
        children = dependency.get("dependsOn", [])
        if (not isinstance(ref, str) or ref not in refs or ref in graph
                or not isinstance(children, list)
                or any(not isinstance(child, str) or child not in refs for child in children)):
            raise ValueError(f"invalid or duplicate dependency graph entry: {ref!r}")
        graph[ref] = set(children)
    return refs, graph


def check_report(prefix, root_project, projects):
    path = prefix.parent / f"{prefix.name}.json"
    try:
        document, _ = read_sbom(path)
        xml = ET.parse(prefix.parent / f"{prefix.name}.xml").getroot()
        if xml.tag != f"{{http://cyclonedx.org/schema/bom/{document['specVersion']}}}bom":
            raise ValueError("invalid CycloneDX XML document")
        root_component = metadata_component(document)
        if maven_coordinate(root_component) != root_project.coordinate:
            raise ValueError(f"SBOM root does not identify {root_project.coordinate}")
        coordinates, graph = report_graph(document, root_component)
        ref_by_coordinate = {coordinate: ref for ref, coordinate in coordinates.items()}
        for project in projects:
            ref = ref_by_coordinate.get(project.coordinate)
            if ref not in graph:
                raise ValueError(f"SBOM is missing the reactor project graph for {project.coordinate}")
            missing = project.dependencies - {coordinates[child].rsplit(":", 1)[0] for child in graph[ref]}
            if missing:
                raise ValueError(f"SBOM is missing dependencies of {project.coordinate}: {', '.join(sorted(missing))}")
        return document, root_component, graph
    except (OSError, ValueError, ET.ParseError) as error:
        raise ValueError(f"{prefix}: {error}") from error


def invoke_maven(*args):
    result = subprocess.run(
        ["./mvnw", "-B", "-ntp", "-DskipTests", "-Dquarkus.build.skip=true", *args],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding="utf-8", errors="replace",
    )
    if result.returncode:
        print("\n".join(result.stdout.splitlines()[-20:]), file=sys.stderr)
    return result.returncode


def cyclonedx(goal, prefix, *options):
    return invoke_maven(
        *options, f"{CYCLONEDX}:{goal}", "-Dcyclonedx.skipNotDeployed=false",
        "-DoutputFormat=all", f"-DoutputName={prefix.name}", f"-DoutputDirectory={prefix.parent}",
    )


def generate_module_reports(staging, root_project, projects):
    modules = staging / "modules"
    modules.mkdir()
    components = {}
    dependencies = {}
    for project in projects:
        prefix = modules / f"bom-{project.artifact_id}"
        if cyclonedx("makeBom", prefix, "-Denforcer.skip=true", "-N", "-pl", f":{project.artifact_id}"):
            raise ValueError(f"CycloneDX makeBom failed for {project.coordinate}")
        report, root_component, graph = check_report(prefix, project, [project])
        if project == root_project:
            merged = report
        else:
            components[root_component["bom-ref"]] = root_component
        for component in report.get("components", []):
            ref = component["bom-ref"]
            if ref not in components or component.get("scope") == "required":
                components[ref] = component
        for ref, children in graph.items():
            dependencies.setdefault(ref, set()).update(children)

    root_ref = merged["metadata"]["component"]["bom-ref"]
    components.pop(root_ref, None)
    merged["components"] = [components[ref] for ref in sorted(components)]
    merged["dependencies"] = [
        {"ref": ref, "dependsOn": sorted(children)} for ref, children in sorted(dependencies.items())
    ]
    # Publish reactor metadata without reusing the individual root BOM identifier.
    merged.pop("serialNumber", None)
    properties = merged["metadata"].get("properties", [])
    for prop in properties:
        if prop.get("name") == "maven.goal":
            prop["value"] = "makeBom (merged reactor reports)"
    return merged


def generate(report_dir):
    report_dir.mkdir(parents=True, exist_ok=True)
    # Clear the previous outputs so a failed regeneration leaves no canonical SBOM.
    for extension in ("json", "xml"):
        (report_dir / f"{REPORT_NAME}.{extension}").unlink(missing_ok=True)
    modules = report_dir / "modules"
    if modules.exists():
        shutil.rmtree(modules)

    root = Path.cwd()
    root_id = ET.parse(root / "pom.xml").getroot().findtext("{*}artifactId", "").strip()
    all_projects = reactor_projects(root)
    root_project = all_projects[root_id]
    with tempfile.TemporaryDirectory(prefix=".generation-", dir=report_dir) as directory:
        staging = Path(directory)
        excluded = sorted(p.artifact_id for p in all_projects.values() if p.skip_deploy and p != root_project)
        projects = [p for p in all_projects.values() if p.artifact_id not in excluded]
        if not any(p.dependencies for p in projects):
            raise ValueError("default release reactor contains no Maven dependency coordinates")

        print(f"[INFO] Generating CycloneDX SBOM for {len(projects)} reactor projects.", flush=True)
        prefix = staging / REPORT_NAME
        options = ["-DoutputReactorProjects=false"]
        if excluded:
            options.append(f"-DexcludeArtifactId={','.join(excluded)}")
        aggregate = cyclonedx("makeAggregateBom", prefix, *options) == 0
        if aggregate:
            check_report(prefix, root_project, projects)
        else:
            print("[INFO] CycloneDX makeAggregateBom failed. Generating individual reactor BOMs.", flush=True)
            merged = generate_module_reports(staging, root_project, projects)
            prefix.with_suffix(".json").write_text(json.dumps(merged, indent=2) + "\n", encoding="utf-8")
            (staging / "modules").replace(modules)
        output = prefix.with_suffix(".json")
        load_sbom_coordinates(output)
        if aggregate:
            prefix.with_suffix(".xml").replace(report_dir / f"{REPORT_NAME}.xml")
        output.replace(report_dir / output.name)
    print(f"[PASS] SBOM reports generated in {report_dir}")


if __name__ == "__main__":
    try:
        generate(Path(sys.argv[1]).resolve())
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        sys.exit(1)
