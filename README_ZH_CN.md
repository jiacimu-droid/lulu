# Lulu（露露）

Lulu 是一款开源 Android AI 陪伴应用，核心是用户自行创建的角色、连续记忆，以及聊天之外可以真实执行和留档的数字生活。

[English](README.md) · [繁體中文](README_ZH_TW.md)

## 主要功能

- 基于角色卡、世界书和用户自选模型的私聊与群聊
- 长期记忆、原始时间线、关系上下文、承诺和来源回查
- 电话、主动消息、日记、朋友圈、文档共读和用户自建小剧场
- 精简学习 App：番茄钟和用户自行添加的今日待办
- 五子棋与快艇骰子，角色可参与并记录共同经历
- 本地优先存储、备份、可选 WebDAV／Supabase 同步和内嵌网页端

首次安装不会预置角色、关系、性格、考研计划或共享 API Key。用户需要创建自己的角色，并配置自己选择的模型或语音服务。

## 下载

请从 [GitHub Releases](https://github.com/jiacimu-droid/lulu/releases) 下载最新 APK。

## 隐私

数据默认保存在本机。只有用户主动配置并使用模型、语音、搜索或同步服务时，完成对应功能所需的数据才会发送给该第三方。公开构建不包含 Firebase Analytics、Crashlytics、项目方远程配置或共享 API Key。

完整内容见 [隐私说明](docs/PRIVACY.md)。

## 构建

需要 JDK 17+、Android Studio 或 Android SDK，并准备与 compileSdk 37 兼容的 SDK：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

构建不再需要 `google-services.json`。Release 签名可通过未纳入版本控制的 `local.properties` 或仓库现有发布环境注入。

## 目录

- `app`：Android 界面、应用服务、记忆、陪伴运行时和持久化
- `ai`：模型供应商抽象
- `speech`：语音识别与合成
- `document`：文档解析
- `web`、`web-ui`：内嵌网页端
- `plugins`：可选本地插件功能

## 上游与许可证

Lulu 基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 修改，是独立社区项目，并非 RikkaHub 官方版本。上游归属和修改声明见 [NOTICE](NOTICE)。

本项目按 [GNU Affero General Public License v3.0](LICENSE) 发布。仓库不再附加“用户超过 10 人需商业授权”等额外限制。
