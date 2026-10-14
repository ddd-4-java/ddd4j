#!/usr/bin/env python3
"""使用三个版本的原始源码与对应 JDK 验证公开 API、值相等、哈希和文本契约。"""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import sys

SOURCES = [
    "ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfo.java",
    "ddd4j-data/ddd4j-data-mybatis/src/main/java/io/ddd4j/data/mybatis/adapter/SqlObservation.java",
    "ddd4j-mq/ddd4j-mq-redis-stream/src/main/java/io/ddd4j/mq/redisstream/RedisStreamRecord.java",
    "ddd4j-extensions/ddd4j-extension-qlexpress/src/main/java/io/ddd4j/extension/qlexpress/model/QLExpressValidationResult.java",
]
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--roots", nargs=3, required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--jdks", nargs=3, type=pathlib.Path)
    parser.add_argument("--classpaths", nargs=3,
                        help="各线隔离编译所需的本地 JAR 类路径，按 JDK8/17/21 顺序")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    results = []
    for index, (root, version) in enumerate(zip(args.roots, ("1.8", "17", "21"))):
        root = root.resolve()
        jdk = args.jdks[index] if args.jdks else pathlib.Path(subprocess.check_output(
            ["/usr/libexec/java_home", "-v", version], text=True).strip())
        destination = args.output / ("classes-" + str(index + 1))
        destination.mkdir(exist_ok=True)
        files = [root / p for p in SOURCES]
        probe = root / "verification/three-line-parity/ValueContractProbe.java"
        source_missing = [p for p in SOURCES if not (root / p).is_file()]
        source_hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest()
                         for p in SOURCES if (root / p).is_file()}
        probe_missing = not probe.is_file()
        probe_hash = None if probe_missing else hashlib.sha256(probe.read_bytes()).hexdigest()
        command = [str(jdk / "bin/javac"), "-encoding", "UTF-8", "-proc:none", "-parameters",
                   "-d", str(destination)]
        if args.classpaths:
            command.extend(("-classpath", args.classpaths[index]))
        command.extend((*map(str, files), str(probe)))
        compile_result = (None if probe_missing or source_missing else
                          subprocess.run(command, capture_output=True, text=True))
        compile_exit = 1 if compile_result is None else compile_result.returncode
        compile_stderr = ("missing inputs: " + ", ".join(source_missing +
                          ([str(probe.relative_to(root))] if probe_missing else []))
                          if compile_result is None else compile_result.stderr)
        head_result = subprocess.run(["git", "-C", str(root), "rev-parse", "--verify", "HEAD"],
                                     capture_output=True, text=True)
        result = {"root": str(root), "jdk": str(jdk), "compile_command": command,
                  "head": head_result.stdout.strip() if head_result.returncode == 0 else None,
                  "source_hashes": source_hashes,
                  "probe_sha256": probe_hash, "probe_missing": probe_missing,
                  "source_missing": source_missing,
                  "compile_exit": compile_exit, "compile_stderr": compile_stderr}
        if compile_exit == 0:
            runtime_classpath = str(destination)
            if args.classpaths:
                runtime_classpath += os.pathsep + args.classpaths[index]
            execution = subprocess.run([str(jdk / "bin/java"), "-cp", runtime_classpath, "ValueContractProbe"],
                                       capture_output=True, text=True)
            result.update(run_exit=execution.returncode, stdout=execution.stdout, stderr=execution.stderr)
        result["source_stable"] = not source_missing and not probe_missing and all(
            (root / p).is_file() and hashlib.sha256((root / p).read_bytes()).hexdigest() == digest
            for p, digest in source_hashes.items())
        result["source_stable"] = result["source_stable"] and probe.is_file() and (
            hashlib.sha256(probe.read_bytes()).hexdigest() == probe_hash)
        results.append(result)
        print("JDK " + version + ": compile=" + str(result["compile_exit"]) + " run=" + str(result.get("run_exit")))
        if result.get("stderr"):
            print(result["stderr"], file=sys.stderr)
    passed = all(r["compile_exit"] == 0 and r.get("run_exit") == 0 and r["source_stable"] for r in results)
    identical = passed and len({r.get("stdout", "") for r in results}) == 1
    (args.output / "results.json").write_text(json.dumps(
        {"passed": passed and identical, "public_api_and_values_identical": identical, "runs": results},
        ensure_ascii=False, indent=2), encoding="utf-8")
    if not identical:
        print("FAIL: compiled API/hash/text differs across versions", file=sys.stderr)
    return 0 if passed and identical else 1

if __name__ == "__main__":
    sys.exit(main())
