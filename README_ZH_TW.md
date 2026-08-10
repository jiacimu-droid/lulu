# Lulu（露露）

Lulu 是一款開源 Android AI 陪伴應用，核心是使用者自行建立的角色、連續記憶，以及聊天之外可以真實執行和留檔的數位生活。

[English](README.md) · [简体中文](README_ZH_CN.md)

## 主要功能

- 基於角色卡、世界書和使用者自選模型的私聊與群聊
- 長期記憶、原始時間線、關係上下文、承諾和來源回查
- 電話、主動訊息、日記、動態、文件共讀和使用者自建小劇場
- 精簡學習 App：番茄鐘和使用者自行新增的今日待辦
- 五子棋與快艇骰子，角色可參與並記錄共同經歷
- 本機優先儲存、備份、可選 WebDAV／Supabase 同步和內嵌網頁端

首次安裝不會預置角色、關係、性格、考試計畫或共享 API Key。使用者需要建立自己的角色，並設定自己選擇的模型或語音服務。

## 下載

請從 [GitHub Releases](https://github.com/jiacimu-droid/lulu/releases) 下載最新 APK。

## 隱私

資料預設保存在本機。只有使用者主動設定並使用模型、語音、搜尋或同步服務時，完成對應功能所需的資料才會傳送給該第三方。公開構建不包含 Firebase Analytics、Crashlytics、專案方遠端設定或共享 API Key。

完整內容見 [隱私說明](docs/PRIVACY.md)。

## 構建

需要 JDK 17+、Android Studio 或 Android SDK，以及與 compileSdk 37 相容的 SDK：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

構建不再需要 `google-services.json`。Release 簽名可透過未納入版本控制的 `local.properties` 或現有發佈環境注入。

## 上游與授權

Lulu 基於 [RikkaHub](https://github.com/rikkahub/rikkahub) 修改，是獨立社群專案，並非 RikkaHub 官方版本。上游歸屬與修改聲明見 [NOTICE](NOTICE)。

本專案依 [GNU Affero General Public License v3.0](LICENSE) 發佈，不附加使用者人數或商業授權限制。
