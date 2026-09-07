#!/usr/bin/env python3
"""使用三个版本的原始源码与对应 JDK 验证公开 API、值相等、哈希和文本契约。"""
import argparse
import hashlib
import json
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
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    probe = pathlib.Path(__file__).resolve().parents[1] / "verification/three-line-parity/ValueContractProbe.java"
    results = []
    for index, (root, version) in enumerate(zip(args.roots, ("1.8", "17", "21"))):
        root = root.resolve()
        jdk = args.jdks[index] if args.jdks else pathlib.Path(subprocess.check_output(
            ["/usr/libexec/java_home", "-v", version], text=True).strip())
        destination = args.output / ("classes-" + str(index + 1))
        destination.mkdir(exist_ok=True)
        files = [root / p for p in SOURCES]
        source_hashes = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in SOURCES}
        command = [str(jdk / "bin/javac"), "-encoding", "UTF-8", "-proc:none", "-parameters",
                   "-d", str(destination), *map(str, files), str(probe)]
        compile_result = subprocess.run(command, capture_output=True, text=True)
        result = {"root": str(root), "jdk": str(jdk), "compile_command": command,
                  "head": subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
                  "source_hashes": source_hashes,
                  "compile_exit": compile_result.returncode, "compile_stderr": compile_result.stderr}
        if compile_result.returncode == 0:
            execution = subprocess.run([str(jdk / "bin/java"), "-cp", str(destination), "ValueContractProbe"],
                                       capture_output=True, text=True)
            result.update(run_exit=execution.returncode, stdout=execution.stdout, stderr=execution.stderr)
        result["source_stable"] = all(
            (root / p).is_file() and hashlib.sha256((root / p).read_bytes()).hexdigest() == digest
            for p, digest in source_hashes.items())
        results.append(result)
        print("JDK " + version + ": compile=" + str(result["compile_exit"]) + " run=" + str(result.get("run_exit")))
        if result.get("stderr"):
            print(result["stderr"], file=sys.stderr)
    passed = all(r["compile_exit"] == 0 and r.get("run_exit") == 0 and r["source_stable"] for r in results)
    identical = len({r.get("stdout", "") for r in results}) == 1
    (args.output / "results.json").write_text(json.dumps(
        {"passed": passed and identical, "public_api_and_values_identical": identical, "runs": results},
        ensure_ascii=False, indent=2), encoding="utf-8")
    if not identical:
        print("FAIL: compiled API/hash/text differs across versions", file=sys.stderr)
    return 0 if passed and identical else 1

if __name__ == "__main__":
    sys.exit(main())
