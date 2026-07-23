# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Architecture Overview

### Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, and PPTX files
- **highlight**: Code syntax highlighting implementation
- **search**: Search functionality SDK (Exa, Tavily, Zhipu)
- **tts**: Text-to-speech implementation for different providers
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)

### Key Technologies

- **Jetpack Compose**: Modern UI toolkit
- **Koin**: Dependency injection
- **Room**: Database ORM
- **DataStore**: Preferences storage
- **OkHttp**: HTTP client with SSE support
- **Navigation Compose**: App navigation
- **Kotlinx Serialization**: JSON handling

### Core Packages (app module)

- `data/`: Data layer with repositories, database entities, and API clients
- `ui/pages/`: Screen implementations and ViewModels
- `ui/components/`: Reusable UI components
- `di/`: Dependency injection modules
- `utils/`: Utility functions and extensions

### Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time, and pin status. Conversations can be truncated at a specific index and maintain chat suggestions. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT, SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables users to regenerate responses and switch between different conversation branches, creating a tree-like conversation structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Development Guidelines

### UI Development

- Follow Material Design 3 principles
- Use existing UI components from `ui/components/`
- Reference `SettingProviderPage.kt` for page layout patterns
- Use `FormItem` for consistent form layouts
- Implement proper state management with ViewModels
- Use `Lucide.XXX` for icons, and import `import com.composables.icons.lucide.XXX` for each icon
- Use `LocalToaster.current` for toast messages

### Internationalization

- String resources located in `app/src/main/res/values-*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- If the user explicitly requests localization, all languages should be supported.
- English(en) is the default language. Chinese(zh), Japanese(ja), and Traditional Chinese(zh-rTW), Korean(ko-rKR) are supported.
- When localization is needed, use the `locale-tui-localization` skill for managing string resources.

### Database

- Room database with migration support
- Schema files in `app/schemas/`
- Use KSP for Room annotation processing
- Current database version tracked in `AppDatabase.kt`

### AI Provider Integration

- New providers go in `ai/src/main/java/me/rerere/ai/provider/providers/`
- Extend base `Provider` class
- Implement required API methods following existing patterns
- Support for streaming responses via SSE

## Lulu AI Modification Efficiency Rules

These rules are mandatory for AI agents working on the Lulu fork. They exist to prevent small or nearly completed changes from turning into long, repetitive CI sessions.

### 1. Read before editing

- Read this file before every modification task.
- Read the current code index or relevant architecture document before changing a large subsystem.
- Inspect the exact current implementation and call sites. Do not rely only on an issue description or an old failure log.
- Distinguish historical failures from failures on the current `master` commit.

### 2. Match validation scope to change scope

Use the smallest meaningful validation command. Do not run a larger suite merely because it exists.

- Documentation-only change: no compilation is required.
- Small Kotlin production-code change in `app`: run `./gradlew :app:compileReleaseKotlin --no-daemon`.
- Isolated unit-test change: run only the affected test class when possible.
- UI-only change with no release-specific code: a focused Kotlin compilation is enough before committing.
- Full `assembleRelease`, Android Lint, and the entire test suite are milestone/release checks, not the default gate for every small edit.

### 3. Do not let unrelated historical tests block a scoped fix

- If production Release Kotlin compilation succeeds but the full test suite contains unrelated pre-existing failures, commit the scoped fix and record the test-baseline debt separately.
- Never repeatedly rerun the same full failing suite without first proving the new failure is related to the current files.
- One unrelated full-suite failure is enough evidence to stop using that suite as the current task's gate.
- Do not repair unrelated tests during a focused feature/refactor task unless the user explicitly expands the scope.

### 4. One diagnosis cycle, then patch directly

- On failure, extract the first actionable compiler/test errors with file names and line numbers.
- Patch those exact errors directly, then rerun the same focused command once.
- Do not create layers of diagnostic workflows, comments, artifacts, and temporary scripts when the existing build log already contains the error.
- Do not spend multiple cycles improving log collection instead of fixing the source.

### 5. Workflow discipline

- Do not create a new one-off GitHub Actions workflow for each repair.
- Reuse the existing Signed APK/Release workflow and existing CI wherever possible.
- Temporary workflows are allowed only when direct repository tooling cannot perform the change safely, and they must be removed or closed immediately after use.
- A temporary workflow must not become a new permanent entry in the Actions page without explicit user approval.

### 6. Commit strategy

- Make the requested code change first; do not spend most of the task designing validation infrastructure.
- After the smallest relevant compilation passes, commit directly to the requested branch.
- Keep commits focused and reversible.
- Let the existing master-push Release workflow perform the final signed APK check after the code commit.
- Never claim the entire project is green until the current master Release actually succeeds.

### 7. Multi-part task execution

- Maintain a concrete checklist of the user's requested parts.
- Finish implementation parts sequentially instead of repeatedly re-auditing already completed work.
- Do not describe a task as “almost finished” while major requested parts remain.
- Report exactly what entered `master`, what was only tested, and what remains.

### 8. Incident lesson from 2026-07-23

A companion-core refactor had already passed production Release Kotlin compilation, but work continued for many extra cycles because the entire repository test suite was used as a blocking gate. That suite contained more than thirty unrelated historical failures. Additional diagnostic workflows and repeated runs consumed substantial user time without materially advancing the requested implementation.

The correct response should have been:

1. Run the focused Release Kotlin compilation.
2. Fix the three actual stale `sessions` call sites.
3. Rerun the same focused compilation.
4. Commit immediately after it passed.
5. Record unrelated test-baseline failures separately.

For future Lulu work, user time and implementation progress take priority over ceremonial validation. Validation must be proportional, focused, and directly tied to the changed code.