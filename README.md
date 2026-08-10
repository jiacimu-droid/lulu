# Lulu

Lulu is an open-source Android AI companion app built around user-created characters, persistent memory, and real actions across chat, calls, reading, study, and games.

[简体中文](README_ZH_CN.md) · [繁體中文](README_ZH_TW.md)

## What it includes

- Character-based private and group chat with world books and configurable model providers
- Long-term memory, raw timelines, relationship context, commitments, and source traceability
- Voice calls, proactive messages, diaries, moments, reading with documents, and user-created theaters
- A focused Study app with Pomodoro sessions and user-created daily tasks
- Gomoku and Yacht Dice with character participation and shared history
- Local-first data storage, backup tools, optional WebDAV/Supabase sync, and an embedded Web UI

Lulu starts with no default character, relationship, personality, study plan, or shared API key. Users create their own characters and configure the model or voice providers they choose.

## Download

Download the latest APK from [GitHub Releases](https://github.com/jiacimu-droid/lulu/releases).

## Privacy

Data is stored locally by default. Optional third-party model, voice, search, and sync services receive only the data required for features the user explicitly configures and uses. The public build contains no Firebase Analytics, Crashlytics, project remote configuration, or shared API keys.

Read the complete [privacy notice](docs/PRIVACY.md).

## Build

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17+
- Android SDK compatible with compileSdk 37

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Firebase configuration is not required. Release signing values may be provided through an untracked `local.properties` file or the repository's existing release environment.

## Project structure

- `app`: Android UI, application services, memory, companion runtime, and persistence
- `ai`: model provider abstractions
- `speech`: speech recognition and text-to-speech providers
- `document`: document parsing
- `web` and `web-ui`: embedded browser UI
- `plugins`: optional local plugin features

## Upstream and license

Lulu is a modified distribution based on [RikkaHub](https://github.com/rikkahub/rikkahub). It is an independent project and not an official RikkaHub release. Upstream attribution and modification notices are preserved in [NOTICE](NOTICE).

This project is free software licensed under the [GNU Affero General Public License v3.0](LICENSE). There is no separate user-count restriction or commercial-license requirement in this repository.
