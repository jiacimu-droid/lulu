#!/usr/bin/env python3
"""Generate a compact human map and a detailed machine-readable repository index."""

from __future__ import annotations

import json
import os
import re
import subprocess
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC_DIR = ROOT / "docs" / "architecture"
MARKDOWN_PATH = DOC_DIR / "CODE_INDEX.md"
JSON_PATH = DOC_DIR / "code-index.json"

EXCLUDED_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", ".vscode", "build", "dist",
    "node_modules", "generated", "out", "vendor",
}
SOURCE_EXTENSIONS = {
    ".kt", ".kts", ".java", ".xml", ".json", ".toml", ".yaml", ".yml",
    ".md", ".js", ".jsx", ".ts", ".tsx", ".html", ".css", ".sql",
    ".properties", ".pro", ".gradle",
}

SYMBOL_PATTERNS = {
    "class": re.compile(r"^\s*(?:(?:public|private|protected|internal|open|abstract|sealed|data|enum|annotation|value)\s+)*class\s+([A-Za-z_]\w*)"),
    "interface": re.compile(r"^\s*(?:(?:public|private|protected|internal|sealed|fun)\s+)*interface\s+([A-Za-z_]\w*)"),
    "object": re.compile(r"^\s*(?:(?:public|private|protected|internal|data|companion)\s+)*object\s+([A-Za-z_]\w*)"),
    "function": re.compile(r"^\s*(?:(?:public|private|protected|internal|open|override|abstract|suspend|inline|tailrec|operator|infix|external)\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w?.<>]+\.)?([A-Za-z_]\w*)\s*\("),
    "composable": re.compile(r"^\s*fun\s+([A-Za-z_]\w*)\s*\("),
}
PACKAGE_PATTERN = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
IMPORT_PATTERN = re.compile(r"^\s*import\s+(me\.rerere\.[\w.]+)", re.MULTILINE)


def git(*args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", *args], cwd=ROOT, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def tracked_files() -> list[Path]:
    try:
        raw = subprocess.check_output(
            ["git", "ls-files", "-z"], cwd=ROOT, stderr=subprocess.DEVNULL
        )
        candidates = [ROOT / item.decode("utf-8") for item in raw.split(b"\0") if item]
    except (subprocess.CalledProcessError, FileNotFoundError):
        candidates = [path for path in ROOT.rglob("*") if path.is_file()]

    result: list[Path] = []
    for path in candidates:
        try:
            relative = path.relative_to(ROOT)
        except ValueError:
            continue
        if any(part in EXCLUDED_DIRS for part in relative.parts):
            continue
        if path.suffix.lower() not in SOURCE_EXTENSIONS:
            continue
        if relative.as_posix() in {MARKDOWN_PATH.relative_to(ROOT).as_posix(), JSON_PATH.relative_to(ROOT).as_posix()}:
            continue
        result.append(path)
    return sorted(result, key=lambda item: item.relative_to(ROOT).as_posix())


def module_for(relative: Path) -> str:
    return relative.parts[0] if len(relative.parts) > 1 else "root"


def symbols_for(text: str, suffix: str) -> list[dict[str, object]]:
    if suffix not in {".kt", ".kts", ".java", ".js", ".jsx", ".ts", ".tsx"}:
        return []
    symbols: list[dict[str, object]] = []
    pending_composable = False
    for line_number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("@Composable"):
            pending_composable = True
            continue
        matched = False
        for kind in ("class", "interface", "object", "function"):
            match = SYMBOL_PATTERNS[kind].match(line)
            if match:
                symbol_kind = "composable" if kind == "function" and pending_composable else kind
                symbols.append({"name": match.group(1), "kind": symbol_kind, "line": line_number})
                matched = True
                break
        if stripped and not stripped.startswith("@") and not matched:
            pending_composable = False
        elif matched:
            pending_composable = False
    return symbols


def category_for(path: str) -> list[str]:
    lowered = path.lower()
    rules = {
        "AI与上下文": ("/ai/", "generation", "prompt", "transformer"),
        "记忆": ("memory", "hnsw", "summary"),
        "主动代理": ("proactive", "notification", "scheduler", "worker"),
        "关系与承诺": ("relationship", "commitment", "promise", "concern"),
        "电话与语音": ("voicecall", "/voice/", "/tts/", "/speech/"),
        "数据库": ("/db/", "database", "dao", "entity", "migration"),
        "界面": ("/ui/", "page.kt", "screen.kt", "viewmodel"),
        "插件": ("/plugin/", "/plugins/"),
        "工具": ("/tool/", "toolprovider", "tools.kt"),
        "构建与发布": (".github/workflows/", "build.gradle", "settings.gradle"),
    }
    return [name for name, needles in rules.items() if any(needle in lowered for needle in needles)]


def collect() -> dict[str, object]:
    files: list[dict[str, object]] = []
    extension_counts: Counter[str] = Counter()
    module_counts: Counter[str] = Counter()
    category_paths: dict[str, list[str]] = defaultdict(list)
    package_edges: Counter[tuple[str, str]] = Counter()

    for path in tracked_files():
        relative = path.relative_to(ROOT)
        relative_text = relative.as_posix()
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        suffix = path.suffix.lower()
        line_count = len(text.splitlines())
        package_match = PACKAGE_PATTERN.search(text)
        package_name = package_match.group(1) if package_match else None
        symbols = symbols_for(text, suffix)
        categories = category_for("/" + relative_text)

        extension_counts[suffix or "[none]"] += 1
        module_counts[module_for(relative)] += 1
        for category in categories:
            category_paths[category].append(relative_text)

        if package_name:
            source_area = ".".join(package_name.split(".")[:6])
            for imported in IMPORT_PATTERN.findall(text):
                target_area = ".".join(imported.split(".")[:6])
                if source_area != target_area:
                    package_edges[(source_area, target_area)] += 1

        files.append({
            "path": relative_text,
            "module": module_for(relative),
            "extension": suffix,
            "lines": line_count,
            "package": package_name,
            "categories": categories,
            "symbols": symbols,
        })

    return {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "commit": os.environ.get("INDEX_COMMIT") or git("rev-parse", "HEAD"),
        "branch": os.environ.get("INDEX_BRANCH") or git("branch", "--show-current"),
        "summary": {
            "files": len(files),
            "lines": sum(int(item["lines"]) for item in files),
            "symbols": sum(len(item["symbols"]) for item in files),
            "byModule": dict(module_counts.most_common()),
            "byExtension": dict(extension_counts.most_common()),
        },
        "categories": {name: paths for name, paths in sorted(category_paths.items())},
        "packageEdges": [
            {"from": source, "to": target, "imports": count}
            for (source, target), count in package_edges.most_common(200)
        ],
        "files": files,
    }


def markdown(index: dict[str, object]) -> str:
    summary = index["summary"]
    categories = index["categories"]
    modules = summary["byModule"]
    extensions = summary["byExtension"]
    files = index["files"]

    lines = [
        "# Lulu 代码索引",
        "",
        "> 本文件由 `scripts/generate_code_index.py` 自动生成。请勿手工维护统计区；架构决策写入同目录的人工文档。",
        "",
        "## 使用顺序",
        "",
        "1. 先读根目录 `AGENTS.md` 与 `docs/architecture/COMPANION_REBUILD_PLAN.md`。",
        "2. 用本页定位功能模块和关键文件。",
        "3. 需要逐文件符号时读取 `docs/architecture/code-index.json`。",
        "4. 修改后运行 `python3 scripts/generate_code_index.py`；`master` 上也会由 Actions 自动刷新。",
        "",
        "## 索引状态",
        "",
        f"- 基准提交：`{index['commit']}`",
        f"- 分支：`{index['branch']}`",
        f"- 已索引文件：{summary['files']}",
        f"- 已索引代码/文本行：{summary['lines']}",
        f"- 已发现类、接口、对象、函数及 Composable：{summary['symbols']}",
        "",
        "## 模块概览",
        "",
        "| 模块 | 文件数 |",
        "|---|---:|",
    ]
    lines.extend(f"| `{name}` | {count} |" for name, count in list(modules.items())[:30])
    lines.extend(["", "## 文件类型", "", "| 扩展名 | 文件数 |", "|---|---:|"])
    lines.extend(f"| `{name}` | {count} |" for name, count in list(extensions.items())[:30])

    lines.extend(["", "## 功能入口", ""])
    for category, paths in categories.items():
        lines.append(f"### {category}")
        lines.append("")
        for path in paths[:40]:
            lines.append(f"- `{path}`")
        if len(paths) > 40:
            lines.append(f"- ……另有 {len(paths) - 40} 个文件，见 `code-index.json`")
        lines.append("")

    important = []
    important_names = {
        "MainActivity", "RikkaHubApplication", "ChatService", "GenerationHandler",
        "CompanionContextEnvelope", "MemoryBankService", "ProactiveMessageService",
        "VoiceCallStreaming", "AppDatabase", "Assistant", "Conversation",
    }
    for item in files:
        names = {symbol["name"] for symbol in item["symbols"]}
        if names & important_names:
            important.append((item["path"], sorted(names & important_names)))
    lines.extend(["## 关键符号快速入口", "", "| 文件 | 符号 |", "|---|---|"])
    lines.extend(f"| `{path}` | {', '.join(f'`{name}`' for name in names)} |" for path, names in important)
    lines.extend([
        "",
        "## 维护约束",
        "",
        "- 自动生成提交使用 `[skip ci]`，避免索引工作流循环触发。",
        "- 二进制、构建产物、依赖缓存和生成目录不会进入索引。",
        "- 本页是导航，不替代测试、数据库迁移说明或任务账本。",
        "- 若索引基准提交落后于当前 `master`，先重新生成再据此修改代码。",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    DOC_DIR.mkdir(parents=True, exist_ok=True)
    index = collect()
    JSON_PATH.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MARKDOWN_PATH.write_text(markdown(index), encoding="utf-8")
    print(f"Indexed {index['summary']['files']} files and {index['summary']['symbols']} symbols")


if __name__ == "__main__":
    main()
