# ddd4r Rust 迁移入口

`ddd4j` 的 Rust 语义移植项目固定为
[`ddd-4-rust/ddd4r`](https://github.com/ddd-4-rust/ddd4r)。

移植以标签 `ddd4r-port-baseline-2026-07-21` 为不可移动基线。82 个 Maven Reactor 项的
逐项状态、Rust package、测试和已知语义差异由 ddd4r 仓库中的 `port-manifest.toml` 管理。

Rust 的架构、RBatis 扩展设计和实现细节保留在 ddd4r 仓库；本仓库只维护 Java 基线、
行为 Fixtures 与迁移入口，避免两个项目的规范发生双向漂移。
