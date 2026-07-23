from __future__ import annotations

import base64
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHANGES: list[str] = []


def path(relative: str) -> Path:
    return ROOT / relative


def read(relative: str) -> str:
    return path(relative).read_text(encoding="utf-8")


def write(relative: str, content: str) -> None:
    target = path(relative)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_literal(relative: str, old: str, new: str = "") -> None:
    current = read(relative)
    updated = current.replace(old, new)
    if updated != current:
        write(relative, updated)
        CHANGES.append(relative)


def replace_regex(relative: str, pattern: str, replacement: str = "", flags: int = 0) -> None:
    current = read(relative)
    updated = re.sub(pattern, replacement, current, flags=flags)
    if updated != current:
        write(relative, updated)
        CHANGES.append(relative)


def remove_file(relative: str) -> None:
    target = path(relative)
    if target.exists():
        target.unlink()
        CHANGES.append(relative)


def main() -> None:
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/service/CompanionChatPort.kt",
        "    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation)\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/service/CompanionChatPort.kt",
        "    override suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) =\n"
        "        chatService.generateSuggestion(conversationId, conversation)\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt",
        "    fun generateSuggestion(conversation: Conversation) {\n"
        "        viewModelScope.launch {\n"
        "            chatService.generateSuggestion(_conversationId, conversation)\n"
        "        }\n"
        "    }\n\n",
    )

    preferences = "app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt"
    for obsolete in (
        "import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT\n",
        "        val SUGGESTION_MODEL = stringPreferencesKey(\"suggestion_model\")\n",
        "        val SUGGESTION_PROMPT = stringPreferencesKey(\"suggestion_prompt\")\n",
        "                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }\n"
        "                    ?: DEFAULT_AUTO_MODEL_ID,\n",
        "                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,\n",
        "            preferences[SUGGESTION_MODEL] = settings.suggestionModelId.toString()\n",
        "            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt\n",
        "    val suggestionModelId: Uuid = Uuid.random(),\n",
        "    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,\n",
    ):
        replace_literal(preferences, obsolete)
    remove_file("app/src/main/java/me/rerere/rikkahub/data/ai/prompts/Suggestion.kt")

    replace_regex(
        "app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt",
        r"\n\s*description = \{\s*Text\(stringResource\(R\.string\.setting_model_page_suggestion_prompt_vars\)\)\s*\}",
        flags=re.S,
    )
    for resource in path("app/src/main/res").glob("values*/strings.xml"):
        current = resource.read_text(encoding="utf-8")
        updated = "\n".join(
            line for line in current.splitlines()
            if "setting_model_page_suggestion_" not in line
        ) + "\n"
        if updated != current:
            resource.write_text(updated, encoding="utf-8")
            CHANGES.append(str(resource.relative_to(ROOT)))

    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt",
        "    val chatSuggestions: List<String> = emptyList(),\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt",
        "            chatSuggestions = JsonInstant.encodeToString(conversation.chatSuggestions),\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt",
        "            chatSuggestions = JsonInstant.decodeFromString(conversationEntity.chatSuggestions),\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/data/db/entity/ConversationEntity.kt",
        "    @ColumnInfo(\"suggestions\", defaultValue = \"[]\")\n"
        "    val chatSuggestions: String,\n",
        "    // Legacy database column retained only for non-destructive upgrades; no feature reads it.\n"
        "    @ColumnInfo(\"suggestions\", defaultValue = \"[]\")\n"
        "    val legacyQuickReplyPayload: String = \"[]\",\n",
    )

    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/web/routes/ConversationDiff.kt",
        "        chatSuggestions != current.chatSuggestions ||\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt",
        "    val chatSuggestions: List<String>,\n",
    )
    replace_literal(
        "app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt",
        "    chatSuggestions = chatSuggestions,\n",
    )

    route = "web-ui/app/routes/conversations.tsx"
    replace_literal(route, "const EMPTY_SUGGESTIONS: string[] = [];\n")
    replace_literal(route, "  const chatSuggestions = detail?.chatSuggestions ?? EMPTY_SUGGESTIONS;\n")
    replace_regex(
        route,
        r"\n\s*const showSuggestions =\s*.*?const displaySuggestions = .*?;\n",
        flags=re.S,
    )
    replace_regex(
        route,
        r"\n\s*const handleClickSuggestion = React\.useCallback\(.*?\n\s*\);\n",
        flags=re.S,
    )
    replace_regex(route, r"^\s*suggestions=\{displaySuggestions\}\n", flags=re.M)
    replace_regex(route, r"^\s*onSuggestionClick=\{handleClickSuggestion\}\n", flags=re.M)

    chat_input = "web-ui/app/components/input/chat-input.tsx"
    for obsolete in (
        "  suggestions?: string[];\n",
        "  onSuggestionClick?: (suggestion: string) => void;\n",
        "  suggestions = [],\n",
        "  onSuggestionClick,\n",
        "  const handleSuggestionSelect = React.useCallback(\n"
        "    (suggestion: string) => {\n"
        "      if (!canUseQuickMessage || !suggestion) {\n"
        "        return;\n"
        "      }\n\n"
        "      onSuggestionClick?.(suggestion);\n"
        "      if (error) {\n"
        "        setError(null);\n"
        "      }\n"
        "      textareaRef.current?.focus();\n"
        "    },\n"
        "    [canUseQuickMessage, error, onSuggestionClick],\n"
        "  );\n\n",
    ):
        replace_literal(chat_input, obsolete)
    replace_regex(
        chat_input,
        r"\n\s*\{suggestions\.length > 0 \? \(.*?\n\s*\) : null\}\n",
        flags=re.S,
    )
    replace_literal("web-ui/app/types/dto.ts", "  chatSuggestions: string[];\n")
    replace_literal("web-ui/app/types/conversation.ts", "  chatSuggestions: string[];\n")

    test = "app/src/test/java/me/rerere/rikkahub/data/service/MemoryCandidateDeduplicationTest.kt"
    current_test = read(test)
    updated_test = (
        current_test
        .replace("我记得她不喜欢自动生成建议回复。", "我记得她不喜欢太亮的番茄钟界面。")
        .replace("我知道她不需要系统自动生成对话建议。", "我知道她不需要系统使用明亮的番茄钟主题。")
        .replace("自动生成对话建议", "明亮的番茄钟主题")
    )
    if updated_test != current_test:
        write(test, updated_test)
        CHANGES.append(test)

    original_index = base64.b64decode(os.environ["ORIGINAL_INDEX_WORKFLOW_B64"]).decode("utf-8")
    write(".github/workflows/code-index.yml", original_index)
    CHANGES.append(".github/workflows/code-index.yml")
    remove_file("docs/architecture/suggestion-remnants.txt")

    pattern = re.compile(
        r"generateSuggestion|chatSuggestions|suggestionModelId|suggestionPrompt|"
        r"DEFAULT_SUGGESTION_PROMPT|SUGGESTION_MODEL|SUGGESTION_PROMPT|"
        r"onSuggestionClick|handleSuggestionSelect|handleClickSuggestion|"
        r"EMPTY_SUGGESTIONS|displaySuggestions|showSuggestions|"
        r"suggestions\.map|suggestions\.length|setting_model_page_suggestion_|"
        r"建议回复|自动生成对话建议",
        re.IGNORECASE,
    )
    remaining: list[str] = []
    for source_root in (path("app/src"), path("web-ui/app"), path("plugins")):
        if not source_root.exists():
            continue
        for source in source_root.rglob("*"):
            if not source.is_file():
                continue
            try:
                lines = source.read_text(encoding="utf-8").splitlines()
            except (UnicodeDecodeError, OSError):
                continue
            for line_number, line in enumerate(lines, 1):
                if pattern.search(line):
                    remaining.append(f"{source.relative_to(ROOT)}:{line_number}:{line.strip()}")

    report_lines = [
        "# Suggestion cleanup report",
        "",
        f"Changed paths: {len(set(CHANGES))}",
        *[f"- {item}" for item in sorted(set(CHANGES))],
        "",
        f"Remaining active lines: {len(remaining)}",
        *remaining,
        "",
    ]
    write("docs/architecture/suggestion-cleanup-report.txt", "\n".join(report_lines))

    if not remaining:
        remove_file(".github/workflows/scan-suggestion-remnants.yml")
        remove_file(".github/scripts/cleanup_suggestion_chain.py")


if __name__ == "__main__":
    main()
