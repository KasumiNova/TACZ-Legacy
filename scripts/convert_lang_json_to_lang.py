#!/usr/bin/env python3
"""
临时工具：把 1.20+ 的 assets/tacz/lang/*.json 转成 1.12.2 可读的 *.lang。

默认行为（安全模式）：
- 保留已有 .lang 中的键值（避免覆盖手工翻译）
- 仅补全缺失键

可选行为：
- --replace: 以 json 完整覆盖生成 .lang（不保留旧值）
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, List, Tuple


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert TACZ lang json files into Minecraft 1.12 .lang format")
    parser.add_argument(
        "--source",
        type=Path,
        default=Path("src/main/resources/assets/tacz/lang"),
        help="Source folder containing *.json language files",
    )
    parser.add_argument(
        "--target",
        type=Path,
        default=Path("src/main/resources/assets/tacz/lang"),
        help="Target folder for generated *.lang files",
    )
    parser.add_argument(
        "--replace",
        action="store_true",
        help="Replace existing .lang values with json values instead of merge-filling",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="Do not generate *.lang.bak backup when overwriting existing lang files",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview actions without writing files",
    )
    return parser.parse_args()


def strip_json_comments(content: str) -> str:
    out: List[str] = []
    i = 0
    in_string = False
    escaped = False
    in_line_comment = False
    in_block_comment = False

    while i < len(content):
        ch = content[i]
        nxt = content[i + 1] if i + 1 < len(content) else "\0"

        if in_line_comment:
            if ch in "\r\n":
                in_line_comment = False
                out.append(ch)
            i += 1
            continue

        if in_block_comment:
            if ch == "*" and nxt == "/":
                in_block_comment = False
                i += 2
            else:
                i += 1
            continue

        if in_string:
            out.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            i += 1
            continue

        if ch == '"':
            in_string = True
            out.append(ch)
            i += 1
            continue

        if ch == "/" and nxt == "/":
            in_line_comment = True
            i += 2
            continue

        if ch == "/" and nxt == "*":
            in_block_comment = True
            i += 2
            continue

        out.append(ch)
        i += 1

    return "".join(out)


def normalize_locale(stem: str) -> str:
    return stem.lower().replace("-", "_")


def parse_lang_file(path: Path) -> Dict[str, str]:
    result: Dict[str, str] = {}
    if not path.is_file():
        return result

    text = path.read_text(encoding="utf-8")
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not key:
            continue
        result[key] = value
    return result


def value_to_lang_literal(value: object) -> str:
    if isinstance(value, str):
        text = value
    else:
        text = json.dumps(value, ensure_ascii=False, separators=(",", ":"))

    # 保留真实 UTF-8 字符（如中文、日文、§），仅转义控制字符（如换行、反斜杠、引号）。
    encoded = json.dumps(text, ensure_ascii=False)
    return encoded[1:-1]


def parse_json_lang(path: Path) -> Dict[str, str]:
    content = path.read_text(encoding="utf-8")
    data = json.loads(strip_json_comments(content))
    if not isinstance(data, dict):
        raise ValueError(f"Root of {path} is not a JSON object")

    out: Dict[str, str] = {}
    for key, value in data.items():
        if not isinstance(key, str):
            continue
        out[key] = value_to_lang_literal(value)
    return out


def collect_json_groups(source: Path) -> Dict[str, List[Path]]:
    groups: Dict[str, List[Path]] = {}
    for file in sorted(source.glob("*.json")):
        canonical = normalize_locale(file.stem)
        groups.setdefault(canonical, []).append(file)
    return groups


def sort_sources_for_merge(canonical_locale: str, files: List[Path]) -> List[Path]:
    # 先应用别名（如 tr-TR.json），再应用规范名（如 tr_tr.json）
    # 这样规范名可覆盖别名冲突键。
    return sorted(
        files,
        key=lambda p: (0 if normalize_locale(p.stem) != p.stem.lower() else 1, p.name.lower()),
    )


def write_lang(path: Path, mapping: Dict[str, str], dry_run: bool, backup: bool) -> Tuple[bool, int]:
    keys = sorted(mapping.keys())
    rendered = "\n".join(f"{k}={mapping[k]}" for k in keys) + "\n"

    changed = True
    if path.is_file():
        current = path.read_text(encoding="utf-8")
        changed = current != rendered

    if dry_run:
        return changed, len(keys)

    path.parent.mkdir(parents=True, exist_ok=True)

    if changed and backup and path.exists():
        backup_path = path.with_suffix(path.suffix + ".bak")
        backup_path.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

    if changed:
        path.write_text(rendered, encoding="utf-8")

    return changed, len(keys)


def main() -> int:
    args = parse_args()

    source = args.source.resolve()
    target = args.target.resolve()

    if not source.is_dir():
        print(f"[ERROR] source folder not found: {source}")
        return 2

    groups = collect_json_groups(source)
    if not groups:
        print(f"[WARN] no json lang files found in: {source}")
        return 0

    total_locales = 0
    changed_locales = 0
    total_keys = 0

    for locale, files in sorted(groups.items()):
        total_locales += 1
        ordered_sources = sort_sources_for_merge(locale, files)

        merged_json: Dict[str, str] = {}
        source_names = [p.name for p in ordered_sources]
        for src in ordered_sources:
            parsed = parse_json_lang(src)
            merged_json.update(parsed)

        target_lang = target / f"{locale}.lang"

        if args.replace:
            final_mapping = merged_json
            mode = "replace"
        else:
            existing = parse_lang_file(target_lang)
            final_mapping = dict(existing)
            for k, v in merged_json.items():
                final_mapping.setdefault(k, v)
            mode = "merge"

        changed, key_count = write_lang(
            path=target_lang,
            mapping=final_mapping,
            dry_run=args.dry_run,
            backup=not args.no_backup,
        )

        total_keys += key_count
        if changed:
            changed_locales += 1

        collision_note = ""
        if len(files) > 1:
            collision_note = f" [collision: {', '.join(source_names)}]"

        print(
            f"[{mode}] {locale}: keys={key_count} changed={changed} -> {target_lang.name}{collision_note}"
        )

    print(
        f"[DONE] locales={total_locales} changed={changed_locales} total_keys={total_keys} dry_run={args.dry_run}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
