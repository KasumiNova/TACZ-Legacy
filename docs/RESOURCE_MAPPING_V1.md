# TACZ 资源映射清单（V1）

> 目标：完成 Phase 1 的“资源映射清单首版”，建立可执行、可测试、可扩展的 1.20 → 1.12 资源路径映射基线。

## 1. 入口与实现位置

- 映射规则：`src/main/kotlin/com/tacz/legacy/common/application/resource/TaczResourcePathMapper.kt`
- 迁移器（manifest 规划/写出）：`src/main/kotlin/com/tacz/legacy/common/application/resource/TaczResourceMigrator.kt`
- 规则单测：`src/test/kotlin/com/tacz/legacy/common/application/resource/TaczResourcePathMapperTest.kt`
- 迁移器单测：`src/test/kotlin/com/tacz/legacy/common/application/resource/TaczResourceMigratorTest.kt`
- 覆盖率回归：`src/test/kotlin/com/tacz/legacy/common/application/resource/TaczResourceMappingCoverageTest.kt`

## 2. TACZ 资源结构基线（当前）

来自 `TACZ/src/main/resources/assets/tacz` 的快速统计（2026-03-01）：

- 顶层目录：`animations/`、`blockstates/`、`custom/`、`lang/`、`models/`、`particles/`、`sounds/`、`textures/`
- 文件分布（按顶层）：
  - `custom/`: 2939
  - `textures/`: 73
  - `models/`: 29
  - `lang/`: 21
  - `sounds/`: 8
  - `blockstates/`: 6
  - `animations/`: 2
  - `sounds.json`: 1
  - `particles/`: 1

> 该分布说明 V1 规则优先覆盖 `custom/*` 枪包资源与标准资源目录。

## 3. V1 映射规则

| 来源路径（相对 `assets/tacz/`） | 目标路径 | 动作 |
|---|---|---|
| `textures/**` `models/**` `sounds/**` `particles/**` `blockstates/**` `animations/**` `sounds.json` | `src/main/resources/assets/tacz/<原相对路径>` | 直接复制 |
| `lang/*.json` | `src/main/resources/assets/tacz/lang/<locale>.lang` | 结构转换（json → .lang） |
| `custom/<packId>/**`（除 README） | `run/tacz/<packId>/<pack内相对路径>` | 作为 TACZ 外置枪包复制 |
| `custom/<packId>/README.txt` | 无 | 忽略（文档） |
| 其余未知路径 | 无 | 人工审查 |

## 4. 覆盖率门槛

V1 通过自动化回归约束：

- 规则覆盖率阈值：`>= 95%`
- 覆盖率定义：
  - 非 `MANUAL_REVIEW` 视为“被规则覆盖”
  - `IGNORE`（例如 README）计入可处理范围

## 5. 已知后续扩展点（V2+）

1. 增加 `lang/*.json -> .lang` 的实际内容转换器（目前仅路径映射）。
2. 在 `custom/<packId>/assets/**` 内按资源类型拆分 finer-grained 策略（可选）。
3. 在 manifest 规划基础上补全“执行层”（实际拷贝/转换写入），并提供 dry-run 与 apply 两种模式。

## 6. Migrator 雏形产出

当前已可通过 `TaczResourceMigrator` 生成机器可读迁移清单（JSON manifest）：

- `buildManifestFromSourceRoot(sourceRoot)`：扫描目录并生成清单
- `buildManifestFromRelativePaths(paths)`：基于相对路径集合生成清单
- `TaczResourceMigrationManifestWriter.writeToFile(...)`：写出 JSON

manifest 关键字段：

- `schemaVersion`
- `generatedAtEpochMillis`
- `sourceRoot`
- `totalFiles / mappedCount / coverageRatio`
- `actionCounts`
- `manualReviewSamples`
- `entries[]`（每个资源的 source/target/action/note）
