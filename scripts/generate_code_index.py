#!/usr/bin/env python3
"""Generate a deterministic human map and a machine-readable repository index."""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


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
CODE_EXTENSIONS = {".kt", ".kts", ".java", ".js", ".jsx", ".ts", ".tsx"}
TODO_PATTERN = re.compile(r"\b(TODO|FIXME|HACK|XXX)\b", re.IGNORECASE)

SYMBOL_PATTERNS = {
    "class": re.compile(
        r"^\s*(?:(?:public|private|protected|internal|open|abstract|sealed|data|enum|annotation|value)\s+)*"
        r"class\s+([A-Za-z_]\w*)"
    ),
    "interface": re.compile(
        r"^\s*(?:(?:public|private|protected|internal|sealed|fun)\s+)*interface\s+([A-Za-z_]\w*)"
    ),
    "object": re.compile(
        r"^\s*(?:(?:public|private|protected|internal|data|companion)\s+)*object\s+([A-Za-z_]\w*)"
    ),
    "function": re.compile(
        r"^\s*(?:(?:public|private|protected|internal|open|override|abstract|suspend|inline|tailrec|operator|infix|external)\s+)*"
        r"fun\s+(?:<[^>]+>\s*)?(?:[\w?.<>]+\.)?([A-Za-z_]\w*)\s*\("
    ),
    "typealias": re.compile(r"^\s*(?:(?:public|private|protected|internal)\s+)*typealias\s+([A-Za-z_]\w*)"),
}
PACKAGE_PATTERN = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
IMPORT_PATTERN = re.compile(r"^\s*import\s+(me\.rerere\.[\w.*]+)", re.MULTILINE)

FEATURE_FLOWS: dict[str, dict[str, Any]] = {
    "陪伴聊天主链路": {
        "purpose": "用户消息、上下文装配、模型流式回复、工具执行与回合落库。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/service/ChatService.kt",
            "app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt",
            "app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt",
            "app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt",
        ],
    },
    "连续记忆": {
        "purpose": "记忆批次、提取、召回、checkpoint、失败重试与私人印象。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt",
            "app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt",
            "app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt",
            "app/src/main/java/me/rerere/rikkahub/service/ChatService.kt",
        ],
    },
    "主动陪伴与生活监督": {
        "purpose": "主动消息、承诺、关注、提醒、通知、起床和睡眠监督。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt",
            "app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageWorker.kt",
            "app/src/main/java/me/rerere/rikkahub/service/ProactiveReminderPlanner.kt",
            "app/src/main/java/me/rerere/rikkahub/service/ProactiveToolPlanner.kt",
            "app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt",
            "app/src/main/java/me/rerere/rikkahub/data/ai/tools/AlarmTool.kt",
        ],
    },
    "电话与语音": {
        "purpose": "主动来电、通话页面、流式分段、TTS 与通话回合连续性。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallPage.kt",
            "app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreaming.kt",
            "app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt",
            "app/src/main/java/me/rerere/rikkahub/service/ChatService.kt",
            "speech/build.gradle.kts",
        ],
    },
    "娱乐与数字生活": {
        "purpose": "日记、收藏、小游戏、回放、共读、番茄钟和可追溯数字活动。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/data/companion/CompanionDigitalActivities.kt",
            "plugins/moments/main.js",
            "plugins/共读/reader.js",
            "plugins/番茄钟/main.js",
            "app/src/main/java/me/rerere/rikkahub/plugin/webview/MusicPlayerService.kt",
        ],
    },
    "学习监督": {
        "purpose": "考研计划、每日任务、番茄钟、完成反馈与角色监督。",
        "paths": [
            "app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt",
            "app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt",
            "app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt",
            "app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt",
            "app/src/main/java/me/rerere/rikkahub/data/ai/tools/TodayStudyPlanTool.kt",
        ],
    },
}


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
    generated_paths = {
        MARKDOWN_PATH.relative_to(ROOT).as_posix(),
        JSON_PATH.relative_to(ROOT).as_posix(),
    }
    for path in candidates:
        try:
            relative = path.relative_to(ROOT)
        except ValueError:
            continue
        if any(part in EXCLUDED_DIRS for part in relative.parts):
            continue
        if path.suffix.lower() not in SOURCE_EXTENSIONS:
            continue
        if relative.as_posix() in generated_paths:
            continue
        result.append(path)
    return sorted(result, key=lambda item: item.relative_to(ROOT).as_posix())


def module_for(relative: Path) -> str:
    return relative.parts[0] if len(relative.parts) > 1 else "root"


def is_test_path(relative: Path) -> bool:
    lowered_parts = {part.lower() for part in relative.parts}
    return bool(lowered_parts & {"test", "androidtest", "commontest", "jvmtest"}) or relative.stem.endswith("Test")


def is_production_code(relative: Path) -> bool:
    return relative.suffix.lower() in CODE_EXTENSIONS and not is_test_path(relative)


def symbols_for(text: str, suffix: str) -> list[dict[str, object]]:
    if suffix not in CODE_EXTENSIONS:
        return []
    symbols: list[dict[str, object]] = []
    pending_composable = False
    for line_number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("@Composable"):
            pending_composable = True
            continue
        matched = False
        for kind in ("class", "interface", "object", "function", "typealias"):
            match = SYMBOL_PATTERNS[kind].match(line)
            if match:
                symbol_kind = "composable" if kind == "function" and pending_composable else kind
                symbols.append({"name": match.group(1), "kind": symbol_kind, "line": line_number})
                matched = True
                break
        if stripped and not stripped.startswith("@"):
            pending_composable = False
        if matched:
            pending_composable = False
    return symbols


def category_for(path: str) -> list[str]:
    lowered = path.lower()
    rules = {
        "AI与上下文": ("/ai/", "generation", "prompt", "transformer", "contextenvelope"),
        "记忆": ("memory", "hnsw", "summary"),
        "主动代理": ("proactive", "notification", "scheduler", "worker", "commitment", "concern"),
        "关系与承诺": ("relationship", "commitment", "promise", "concern"),
        "电话与语音": ("voicecall", "/voice/", "/tts/", "/speech/"),
        "学习监督": ("/study/", "studyplan", "pomodoro"),
        "娱乐与数字生活": ("digitalactivit", "minigame", "/plugins/", "favorite", "replay"),
        "数据库": ("/db/", "database", "dao", "entity", "migration", "/schemas/"),
        "界面": ("/ui/", "page.kt", "screen.kt", "viewmodel"),
        "插件": ("/plugin/", "/plugins/"),
        "工具": ("/tool/", "toolprovider", "tools.kt"),
        "构建与发布": (".github/workflows/", "build.gradle", "settings.gradle"),
    }
    return [name for name, needles in rules.items() if any(needle in lowered for needle in needles)]


def load_previous_index() -> dict[str, Any]:
    try:
        return json.loads(JSON_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError, TypeError):
        return {}


def collect() -> dict[str, object]:
    files: list[dict[str, object]] = []
    extension_counts: Counter[str] = Counter()
    module_counts: Counter[str] = Counter()
    category_paths: dict[str, list[str]] = defaultdict(list)
    package_edges: Counter[tuple[str, str]] = Counter()
    fingerprint = hashlib.sha256()

    for path in tracked_files():
        relative = path.relative_to(ROOT)
        relative_text = relative.as_posix()
        try:
            raw = path.read_bytes()
            text = raw.decode("utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        suffix = path.suffix.lower()
        digest = hashlib.sha256(raw).hexdigest()
        fingerprint.update(relative_text.encode("utf-8"))
        fingerprint.update(b"\0")
        fingerprint.update(digest.encode("ascii"))
        fingerprint.update(b"\n")

        line_count = len(text.splitlines())
        package_match = PACKAGE_PATTERN.search(text)
        package_name = package_match.group(1) if package_match else None
        symbols = symbols_for(text, suffix)
        categories = category_for("/" + relative_text)
        local_imports = sorted(set(IMPORT_PATTERN.findall(text)))
        todo_count = len(TODO_PATTERN.findall(text))

        extension_counts[suffix or "[none]"] += 1
        module_counts[module_for(relative)] += 1
        for category in categories:
            category_paths[category].append(relative_text)

        if package_name:
            source_area = ".".join(package_name.split(".")[:6])
            for imported in local_imports:
                target_area = ".".join(imported.replace(".*", "").split(".")[:6])
                if source_area != target_area:
                    package_edges[(source_area, target_area)] += 1

        files.append({
            "path": relative_text,
            "module": module_for(relative),
            "extension": suffix,
            "lines": line_count,
            "sha256": digest,
            "package": package_name,
            "categories": categories,
            "symbols": symbols,
            "localImportCount": len(local_imports),
            "todoMarkers": todo_count,
            "isTest": is_test_path(relative),
            "isProductionCode": is_production_code(relative),
        })

    source_fingerprint = fingerprint.hexdigest()
    previous = load_previous_index()
    same_source = previous.get("sourceFingerprint") == source_fingerprint
    generated_at = previous.get("generatedAt") if same_source else None
    indexed_commit = previous.get("commit") if same_source else None

    largest_files = sorted(
        (item for item in files if item["isProductionCode"]),
        key=lambda item: (int(item["lines"]), str(item["path"])),
        reverse=True,
    )[:30]
    most_symbols = sorted(
        (item for item in files if item["isProductionCode"]),
        key=lambda item: (len(item["symbols"]), str(item["path"])),
        reverse=True,
    )[:30]
    highest_fan_out = sorted(
        (item for item in files if item["isProductionCode"]),
        key=lambda item: (int(item["localImportCount"]), str(item["path"])),
        reverse=True,
    )[:30]
    todo_hotspots = sorted(
        (item for item in files if int(item["todoMarkers"]) > 0),
        key=lambda item: (int(item["todoMarkers"]), int(item["lines"])),
        reverse=True,
    )[:30]

    existing_paths = {str(item["path"]) for item in files}
    feature_flows = {
        name: {
            "purpose": definition["purpose"],
            "paths": [path for path in definition["paths"] if path in existing_paths],
        }
        for name, definition in FEATURE_FLOWS.items()
    }

    production_files = [item for item in files if item["isProductionCode"]]
    test_files = [item for item in files if item["isTest"]]
    large_files = [item for item in production_files if int(item["lines"]) >= 800]
    very_large_files = [item for item in production_files if int(item["lines"]) >= 1500]

    return {
        "schemaVersion": 2,
        "generatedAt": generated_at or datetime.now(timezone.utc).isoformat(),
        "commit": indexed_commit or os.environ.get("INDEX_COMMIT") or git("rev-parse", "HEAD"),
        "branch": os.environ.get("INDEX_BRANCH") or previous.get("branch") or git("branch", "--show-current"),
        "sourceFingerprint": source_fingerprint,
        "summary": {
            "files": len(files),
            "lines": sum(int(item["lines"]) for item in files),
            "symbols": sum(len(item["symbols"]) for item in files),
            "productionCodeFiles": len(production_files),
            "testFiles": len(test_files),
            "largeProductionFiles": len(large_files),
            "veryLargeProductionFiles": len(very_large_files),
            "todoMarkers": sum(int(item["todoMarkers"]) for item in files),
            "byModule": dict(module_counts.most_common()),
            "byExtension": dict(extension_counts.most_common()),
            "byCategory": {name: len(paths) for name, paths in sorted(category_paths.items())},
        },
        "featureFlows": feature_flows,
        "hotspots": {
            "largestFiles": compact_hotspots(largest_files),
            "mostSymbols": compact_hotspots(most_symbols),
            "highestLocalImportCount": compact_hotspots(highest_fan_out),
            "todoMarkers": compact_hotspots(todo_hotspots),
        },
        "categories": {name: paths for name, paths in sorted(category_paths.items())},
        "packageEdges": [
            {"from": source, "to": target, "imports": count}
            for (source, target), count in package_edges.most_common(200)
        ],
        "files": files,
    }


def compact_hotspots(items: list[dict[str, object]]) -> list[dict[str, object]]:
    return [
        {
            "path": item["path"],
            "lines": item["lines"],
            "symbols": len(item["symbols"]),
            "localImportCount": item["localImportCount"],
            "todoMarkers": item["todoMarkers"],
        }
        for item in items
    ]


def markdown(index: dict[str, object]) -> str:
    summary = index["summary"]
    categories = index["categories"]
    modules = summary["byModule"]
    extensions = summary["byExtension"]
    files = index["files"]
    hotspots = index["hotspots"]
    feature_flows = index["featureFlows"]

    lines = [
        "# Lulu 代码索引",
        "",
        "> 本文件由 `scripts/generate_code_index.py` 自动生成。统计与热点请勿手工维护；架构决策写入同目录人工文档。",
        "",
        "## 使用顺序",
        "",
        "1. 先读根目录 `AGENTS.md` 与 `docs/architecture/COMPANION_REBUILD_PLAN.md`。",
        "2. 从“核心产品链路”进入，不要先全仓扫描。",
        "3. 用“瘦身与耦合热点”决定拆分顺序，再回读真实调用方、持久化与失败路径。",
        "4. 需要逐文件符号、指纹和分类时读取 `docs/architecture/code-index.json`。",
        "5. 修改后运行 `python3 scripts/generate_code_index.py`；`master` 推送时 Actions 也会自动刷新。",
        "",
        "## 索引状态",
        "",
        f"- 基准提交：`{index['commit']}`",
        f"- 分支：`{index['branch']}`",
        f"- 源码指纹：`{str(index['sourceFingerprint'])[:16]}…`",
        f"- 已索引文件：{summary['files']}",
        f"- 已索引代码/文本行：{summary['lines']}",
        f"- 已发现类、接口、对象、函数及 Composable：{summary['symbols']}",
        "",
        "## 仓库健康信号",
        "",
        "| 指标 | 数量 |",
        "|---|---:|",
        f"| 生产代码文件 | {summary['productionCodeFiles']} |",
        f"| 测试文件 | {summary['testFiles']} |",
        f"| ≥800 行生产文件 | {summary['largeProductionFiles']} |",
        f"| ≥1500 行生产文件 | {summary['veryLargeProductionFiles']} |",
        f"| TODO/FIXME/HACK/XXX | {summary['todoMarkers']} |",
        "",
        "## 核心产品链路",
        "",
    ]
    for name, flow in feature_flows.items():
        lines.append(f"### {name}")
        lines.append("")
        lines.append(str(flow["purpose"]))
        lines.append("")
        for path in flow["paths"]:
            lines.append(f"- `{path}`")
        if not flow["paths"]:
            lines.append("- 暂未匹配到现存入口；需要更新索引规则。")
        lines.append("")

    lines.extend([
        "## 瘦身与耦合热点",
        "",
        "### 最大生产文件",
        "",
        "| 文件 | 行数 | 符号 | 本地导入 |",
        "|---|---:|---:|---:|",
    ])
    for item in hotspots["largestFiles"][:15]:
        lines.append(
            f"| `{item['path']}` | {item['lines']} | {item['symbols']} | {item['localImportCount']} |"
        )

    lines.extend([
        "",
        "### 本地导入最多的生产文件",
        "",
        "| 文件 | 本地导入 | 行数 |",
        "|---|---:|---:|",
    ])
    for item in hotspots["highestLocalImportCount"][:15]:
        lines.append(f"| `{item['path']}` | {item['localImportCount']} | {item['lines']} |")

    lines.extend(["", "## 模块概览", "", "| 模块 | 文件数 |", "|---|---:|"])
    lines.extend(f"| `{name}` | {count} |" for name, count in list(modules.items())[:30])
    lines.extend(["", "## 文件类型", "", "| 扩展名 | 文件数 |", "|---|---:|"])
    lines.extend(f"| `{name}` | {count} |" for name, count in list(extensions.items())[:30])

    lines.extend(["", "## 功能入口", ""])
    for category, paths in categories.items():
        lines.append(f"### {category}")
        lines.append("")
        for path in paths[:30]:
            lines.append(f"- `{path}`")
        if len(paths) > 30:
            lines.append(f"- ……另有 {len(paths) - 30} 个文件，见 `code-index.json`")
        lines.append("")

    important: list[tuple[str, list[str]]] = []
    important_names = {
        "MainActivity", "RikkaHubApplication", "ChatService", "GenerationHandler",
        "CompanionContextEnvelope", "CompanionRuntime", "MemoryBankService",
        "ProactiveMessageService", "VoiceCallStreaming", "AppDatabase", "Assistant",
        "Conversation", "StudyVM", "StudyRules",
    }
    for item in files:
        names = {symbol["name"] for symbol in item["symbols"]}
        if names & important_names:
            important.append((item["path"], sorted(names & important_names)))
    lines.extend(["## 关键符号快速入口", "", "| 文件 | 符号 |", "|---|---|"])
    lines.extend(f"| `{path}` | {', '.join(f'`{name}`' for name in names)} |" for path, names in important)
    lines.extend([
        "",
        "## 索引边界",
        "",
        "- 这是静态导航索引，不是编译器级调用图；本地导入数只表示耦合信号，不等同于运行时调用次数。",
        "- 路径分类和符号识别使用保守规则，重构前仍须回读实现、调用方、数据库、测试与失败路径。",
        "- 自动生成提交使用 `[skip ci]` 和 `[skip index]`，避免工作流循环。",
        "- 二进制、构建产物、依赖缓存和生成目录不会进入索引。",
        "- 源码指纹未变化时保留原生成时间和基准提交，避免无意义索引提交。",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    DOC_DIR.mkdir(parents=True, exist_ok=True)
    index = collect()
    JSON_PATH.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MARKDOWN_PATH.write_text(markdown(index), encoding="utf-8")
    print(
        "Indexed "
        f"{index['summary']['files']} files, "
        f"{index['summary']['symbols']} symbols, "
        f"fingerprint {str(index['sourceFingerprint'])[:16]}"
    )


if __name__ == "__main__":
    main()
