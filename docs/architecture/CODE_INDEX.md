# Lulu 代码索引

> 本文件由 `scripts/generate_code_index.py` 自动生成。统计与热点请勿手工维护；架构决策写入同目录人工文档。

## 使用顺序

1. 先读根目录 `AGENTS.md` 与 `docs/architecture/COMPANION_REBUILD_PLAN.md`。
2. 从“核心产品链路”进入，不要先全仓扫描。
3. 用“瘦身与耦合热点”决定拆分顺序，再回读真实调用方、持久化与失败路径。
4. 需要逐文件符号、指纹和分类时读取 `docs/architecture/code-index.json`。
5. 修改后运行 `python3 scripts/generate_code_index.py`；`master` 推送时 Actions 也会自动刷新。

## 索引状态

- 基准提交：`c0b5c34f791e4b05d52f0fbee7aff1cde1007ac9`
- 分支：`master`
- 源码指纹：`b935cadf463db813…`
- 已索引文件：1201
- 已索引代码/文本行：263442
- 已发现类、接口、对象、函数及 Composable：5738

## 仓库健康信号

| 指标 | 数量 |
|---|---:|
| 生产代码文件 | 863 |
| 测试文件 | 148 |
| ≥800 行生产文件 | 28 |
| ≥1500 行生产文件 | 3 |
| TODO/FIXME/HACK/XXX | 38 |

## 核心产品链路

### 陪伴聊天主链路

用户消息、上下文装配、模型流式回复、工具执行与回合落库。

- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt`

### 连续记忆

记忆批次、提取、召回、checkpoint、失败重试与私人印象。

- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`

### 主动陪伴与生活监督

主动消息、承诺、关注、提醒、通知、起床和睡眠监督。

- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageWorker.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveReminderPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveToolPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/AlarmTool.kt`

### 电话与语音

主动来电、通话页面、流式分段、TTS 与通话回合连续性。

- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallPage.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreaming.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- `speech/build.gradle.kts`

### 娱乐与数字生活

日记、收藏、小游戏、回放、共读、番茄钟和可追溯数字活动。

- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionDigitalActivities.kt`
- `plugins/moments/main.js`
- `plugins/番茄钟/main.js`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/MusicPlayerService.kt`

### 学习监督

考研计划、每日任务、番茄钟、完成反馈与角色监督。

- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt`

## 瘦身与耦合热点

### 最大生产文件

| 文件 | 行数 | 符号 | 本地导入 |
|---|---:|---:|---:|
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 3143 | 90 | 126 |
| `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt` | 2295 | 57 | 90 |
| `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt` | 1874 | 94 | 17 |
| `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt` | 1187 | 60 | 0 |
| `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt` | 1185 | 30 | 0 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt` | 1166 | 14 | 44 |
| `web-ui/app/routes/conversations.tsx` | 1132 | 3 | 0 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Markdown.kt` | 1124 | 23 | 6 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` | 1117 | 12 | 37 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | 1064 | 11 | 33 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 1032 | 77 | 89 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/TTSProviderConfigure.kt` | 1031 | 11 | 4 |
| `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginUIDeclarativePage.kt` | 985 | 19 | 10 |
| `web-ui/app/components/conversation-sidebar.tsx` | 983 | 1 | 0 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CihaiPage.kt` | 974 | 21 | 23 |

### 本地导入最多的生产文件

| 文件 | 本地导入 | 行数 |
|---|---:|---:|
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | 126 | 3143 |
| `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt` | 90 | 2295 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 89 | 1032 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt` | 44 | 1166 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` | 39 | 416 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/Export.kt` | 39 | 784 |
| `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt` | 38 | 280 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt` | 37 | 1117 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/starwish/StarWishVM.kt` | 34 | 455 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt` | 33 | 732 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | 33 | 1064 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatPage.kt` | 32 | 427 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | 32 | 953 |
| `ai/src/main/java/me/rerere/ai/provider/providers/GoogleProvider.kt` | 32 | 897 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` | 30 | 902 |

## 模块概览

| 模块 | 文件数 |
|---|---:|
| `app` | 746 |
| `web-ui` | 131 |
| `document` | 76 |
| `ai` | 47 |
| `speech` | 38 |
| `search` | 30 |
| `docs` | 25 |
| `common` | 21 |
| `plugins` | 17 |
| `.agents` | 10 |
| `root` | 10 |
| `website` | 10 |
| `highlight` | 9 |
| `web` | 7 |
| `.claude` | 5 |
| `.github` | 5 |
| `study-plans` | 5 |
| `locale-tui` | 4 |
| `money-lab` | 3 |
| `gradle` | 2 |

## 文件类型

| 扩展名 | 文件数 |
|---|---:|
| `.kt` | 814 |
| `.tsx` | 74 |
| `.java` | 64 |
| `.md` | 60 |
| `.json` | 55 |
| `.ts` | 42 |
| `.xml` | 37 |
| `.pro` | 15 |
| `.kts` | 11 |
| `.js` | 6 |
| `.html` | 5 |
| `.yml` | 4 |
| `.yaml` | 3 |
| `.properties` | 3 |
| `.sql` | 3 |
| `.css` | 3 |
| `.toml` | 2 |

## 功能入口

### AI与上下文

- `ai/README.md`
- `ai/build.gradle.kts`
- `ai/consumer-rules.pro`
- `ai/proguard-rules.pro`
- `ai/src/androidTest/java/me/rerere/ai/ExampleInstrumentedTest.kt`
- `ai/src/main/AndroidManifest.xml`
- `ai/src/main/java/me/rerere/ai/core/MessageRole.kt`
- `ai/src/main/java/me/rerere/ai/core/Reasoning.kt`
- `ai/src/main/java/me/rerere/ai/core/Tool.kt`
- `ai/src/main/java/me/rerere/ai/core/Usage.kt`
- `ai/src/main/java/me/rerere/ai/provider/Model.kt`
- `ai/src/main/java/me/rerere/ai/provider/Provider.kt`
- `ai/src/main/java/me/rerere/ai/provider/ProviderManager.kt`
- `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/ClaudeProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/GoogleProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/OpenAIProvider.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/ProviderMessageUtils.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/OpenAIImpl.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/OpenAIPromptCaching.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/openai/ResponseAPI.kt`
- `ai/src/main/java/me/rerere/ai/provider/providers/vertex/ServiceAccountTokenProvider.kt`
- `ai/src/main/java/me/rerere/ai/registry/ModelDsl.kt`
- `ai/src/main/java/me/rerere/ai/registry/ModelRegistry.kt`
- `ai/src/main/java/me/rerere/ai/ui/Image.kt`
- `ai/src/main/java/me/rerere/ai/ui/Message.kt`
- `ai/src/main/java/me/rerere/ai/util/ErrorParser.kt`
- `ai/src/main/java/me/rerere/ai/util/FileEncoder.kt`
- `ai/src/main/java/me/rerere/ai/util/Json.kt`
- ……另有 117 个文件，见 `code-index.json`

### 主动代理

- `app/src/main/java/me/rerere/rikkahub/data/ai/transformers/ProactiveContinuityTransformer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentRuntime.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/ProactiveMessageSetting.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ImageGenerationWorker.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/NotificationListenerService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageScheduler.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageWorker.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveTurnDispatcher.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/UnifiedProactiveAlarmReceiver.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveReminderPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/service/ProactiveToolPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCards.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProactiveMessagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/utils/NotificationUtil.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionConcernReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/datastore/ProactiveCallSettingTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageAssemblyTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageContextTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveMessageWorkerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/ProactiveProjectionTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/ProactiveReminderPlannerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/ProactiveToolPlannerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCardsTest.kt`
- `docs/superpowers/plans/2026-07-10-unified-companion-chat-proactive.md`

### 关系与承诺

- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionCommitmentRuntime.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionConcernReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipBehavior.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipInitialization.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducer.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveRelationshipEvents.kt`
- `app/src/main/java/me/rerere/rikkahub/service/CompanionRelationshipPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCards.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/cihai/CompanionRelationshipTimeline.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionCommitmentReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionConcernReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipBehaviorTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipInitializationTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRelationshipReducerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveRelationshipEventsTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/CompanionConcernCardsTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/pages/cihai/CompanionRelationshipTimelineTest.kt`

### 娱乐与数字生活

- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionDigitalActivities.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/FavoriteDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/FavoriteEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/favorite/FavoriteAdapter.kt`
- `app/src/main/java/me/rerere/rikkahub/data/favorite/NodeFavoriteAdapter.kt`
- `app/src/main/java/me/rerere/rikkahub/data/model/Favorite.kt`
- `app/src/main/java/me/rerere/rikkahub/data/repository/FavoriteRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/favorite/FavoritePage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/favorite/FavoriteVM.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionDigitalActivitiesTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionDigitalActivityWiringTest.kt`
- `plugins/example/weather/main.js`
- `plugins/example/weather/manifest.json`
- `plugins/moments/main.js`
- `plugins/moments/manifest.json`
- `plugins/moments/supabase_schema.sql`
- `plugins/moments/ui/index.html`
- `plugins/supabase_memory/README.md`
- `plugins/supabase_memory/main.js`
- `plugins/supabase_memory/manifest.json`
- `plugins/supabase_memory/supabase_schema.sql`
- `plugins/橘市商业街/supabase_schema.sql`
- `plugins/番茄钟/main.js`
- `plugins/番茄钟/manifest.json`
- `plugins/番茄钟/ui/index.html`
- `plugins/音乐播放器/main.js`
- `plugins/音乐播放器/manifest.json`
- `plugins/音乐播放器/ui/index.html`

### 学习监督

- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/StudyPlanTool.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/ExamStudyPlan.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/July26StudyDayCorrection.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/RoleJudgedSleepRewardPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/SelfDirectedStudyPlan.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyAchievementRewards.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyExecutionPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyGachaRewardPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyModels.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyPlanCatalog.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyPlanTaskSync.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyStore.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/StudyVocabularyPolicy.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/SummerCourseDeadlinePlan.kt`
- `app/src/main/java/me/rerere/rikkahub/data/study/ThreeRoundRecitationPlan.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PomodoroTimerService.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/DrawRevealFlow.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/MoonlightGachaCard.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/PomodoroInAppOverlayController.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyCompactPanels.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyConversationFactoryCompat.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyDrawDialogs.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyMainPanels.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPaddingValues.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyPomodoroPages.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyRewardPanels.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt`
- `app/src/test/java/me/rerere/rikkahub/data/study/ExamStudyPlanTest.kt`
- ……另有 10 个文件，见 `code-index.json`

### 工具

- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/LocalTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SearchTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsTools.kt`
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SystemTools.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/provider/PluginToolProvider.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageTools.kt`

### 插件

- `app/src/main/java/me/rerere/rikkahub/plugin/data/PluginDataStore.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/di/PluginModule.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/LoadedPlugin.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/PluginLoader.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/loader/PluginSandbox.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/manager/PluginManager.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginInfo.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginManifest.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/model/PluginUI.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/provider/PluginToolProvider.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/repository/PluginRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/scanner/PluginScanner.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginDetailPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginManagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginUIDeclarativePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginViewModel.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/MusicPlayerService.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginBridgeWebViewClient.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginOverlayController.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewFileLaunchers.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewScripts.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PomodoroTimerService.kt`
- `plugins/example/weather/main.js`
- `plugins/example/weather/manifest.json`
- `plugins/moments/main.js`
- `plugins/moments/manifest.json`
- `plugins/moments/supabase_schema.sql`
- `plugins/moments/ui/index.html`
- `plugins/supabase_memory/README.md`
- ……另有 10 个文件，见 `code-index.json`

### 数据库

- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/1.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/10.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/11.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/12.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/13.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/14.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/15.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/16.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/17.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/18.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/19.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/2.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/20.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/21.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/22.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/23.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/24.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/25.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/26.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/27.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/3.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/4.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/5.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/6.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/7.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/8.json`
- `app/schemas/me.rerere.rikkahub.data.db.AppDatabase/9.json`
- `app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12_Test.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV1Migration.kt`
- `app/src/main/java/me/rerere/rikkahub/data/datastore/migration/PreferenceStoreV2Migration.kt`
- ……另有 45 个文件，见 `code-index.json`

### 构建与发布

- `.github/workflows/code-index.yml`
- `.github/workflows/release.yml`
- `ai/build.gradle.kts`
- `app/baselineprofile/build.gradle.kts`
- `app/build.gradle.kts`
- `build.gradle.kts`
- `common/build.gradle.kts`
- `document/build.gradle.kts`
- `highlight/build.gradle.kts`
- `search/build.gradle.kts`
- `settings.gradle.kts`
- `speech/build.gradle.kts`
- `web/build.gradle.kts`

### 电话与语音

- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/RequestedVoiceCallTool.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/ProactiveCallManager.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallModels.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallRepository.kt`
- `app/src/main/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreaming.kt`
- `app/src/main/java/me/rerere/rikkahub/service/VoiceCallForegroundService.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallComponents.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallHistoryPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/voicecall/VoiceCallSupport.kt`
- `app/src/test/java/me/rerere/rikkahub/data/voicecall/VoiceCallRepositorySummaryTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/voicecall/VoiceCallStreamingTest.kt`
- `app/src/test/java/me/rerere/rikkahub/service/VoiceCallChatSyncTest.kt`
- `app/src/test/java/me/rerere/rikkahub/ui/VoiceCallStateTest.kt`
- `speech/build.gradle.kts`
- `speech/consumer-rules.pro`
- `speech/proguard-rules.pro`
- `speech/src/androidTest/java/me/rerere/tts/ExampleInstrumentedTest.kt`
- `speech/src/main/AndroidManifest.xml`
- `speech/src/main/java/me/rerere/asr/ASRController.kt`
- `speech/src/main/java/me/rerere/asr/ASRProviderSetting.kt`
- `speech/src/main/java/me/rerere/asr/ASRState.kt`
- `speech/src/main/java/me/rerere/asr/AudioAmplitude.kt`
- `speech/src/main/java/me/rerere/asr/EmojiStrip.kt`
- `speech/src/main/java/me/rerere/asr/providers/OpenAIRealtimeASRController.kt`
- `speech/src/main/java/me/rerere/asr/providers/SiliconFlowASRController.kt`
- `speech/src/main/java/me/rerere/asr/providers/VolcengineASRController.kt`
- `speech/src/main/java/me/rerere/tts/controller/AudioPlayer.kt`
- `speech/src/main/java/me/rerere/tts/controller/TextChunker.kt`
- `speech/src/main/java/me/rerere/tts/controller/TtsAudioCache.kt`
- ……另有 22 个文件，见 `code-index.json`

### 界面

- `ai/src/main/java/me/rerere/ai/ui/Image.kt`
- `ai/src/main/java/me/rerere/ai/ui/Message.kt`
- `ai/src/test/java/me/rerere/ai/ui/MessageTest.kt`
- `ai/src/test/java/me/rerere/ai/ui/ToolApprovalStateTest.kt`
- `app/src/main/java/me/rerere/rikkahub/di/ViewModelModule.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginDetailPage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginManagePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginUIDeclarativePage.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/ui/PluginViewModel.kt`
- `app/src/main/java/me/rerere/rikkahub/plugin/webview/PluginWebViewPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/activity/ShortcutHandlerActivity.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AsrButton.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AssistantPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AttachmentChips.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CropLauncher.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ExtensionContent.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/FilesPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/McpPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ProviderBalanceText.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/ai/SearchPicker.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/easteregg/EmojiBurst.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageAvatar.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageBranch.kt`
- ……另有 312 个文件，见 `code-index.json`

### 记忆

- `app/src/main/java/me/rerere/rikkahub/data/ai/prompts/TitleSummary.kt`
- `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionAlwaysOnMemory.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryBankDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/dao/MemoryDAO.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryBankEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryExtractionBatchEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryExtractionCheckpointEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/db/entity/MemoryGraphEdgeEntity.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionPlanner.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionSupport.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractor.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/DailySummaryService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryCandidateQualityGate.kt`
- `app/src/main/java/me/rerere/rikkahub/data/service/MemoryVector.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankControls.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankDialogs.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankItem.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankPage.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankVM.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryDiagnostics.kt`
- `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelMemoryFeatures.kt`
- `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionAlwaysOnMemoryTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/db/entity/MemoryGraphEdgeEntityTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionPlannerTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractionSupportTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryExtractorTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/AffectiveMemoryPromptPhilosophyTest.kt`
- `app/src/test/java/me/rerere/rikkahub/data/service/MemoryArchiveTest.kt`
- ……另有 19 个文件，见 `code-index.json`

## 关键符号快速入口

| 文件 | 符号 |
|---|---|
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | `Assistant` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/CompanionContextEnvelope.kt` | `CompanionContextEnvelope` |
| `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` | `GenerationHandler` |
| `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt` | `CompanionRuntime` |
| `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt` | `AppDatabase` |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | `Assistant` |
| `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt` | `Conversation` |
| `app/src/main/java/me/rerere/rikkahub/data/service/MemoryBankService.kt` | `MemoryBankService` |
| `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt` | `ProactiveMessageService` |
| `app/src/main/java/me/rerere/rikkahub/data/study/StudyRules.kt` | `StudyRules` |
| `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` | `ChatService` |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/study/StudyVM.kt` | `StudyVM` |
| `app/src/main/java/me/rerere/rikkahub/web/routes/ConversationRoutes.kt` | `Conversation` |

<!-- reverse-index:start -->
## 反向依赖热点

> 这里统计能由明确本地 import 唯一解析出的静态依赖；反射、同包引用、通配导入和运行时注册不会被计入。

- 已解析本地导入边：2070
- 可检索完整符号：5121

| 文件 | 被本地文件导入 | 行数 |
|---|---:|---:|
| `ai/src/main/java/me/rerere/ai/ui/Message.kt` | 130 | 812 |
| `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` | 114 | 953 |
| `ai/src/main/java/me/rerere/ai/core/MessageRole.kt` | 62 | 19 |
| `ai/src/main/java/me/rerere/ai/provider/Model.kt` | 61 | 62 |
| `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` | 57 | 296 |
| `app/src/main/java/me/rerere/rikkahub/ui/theme/Color.kt` | 54 | 191 |
| `ai/src/main/java/me/rerere/ai/core/Tool.kt` | 50 | 29 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/nav/BackButton.kt` | 43 | 31 |
| `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt` | 39 | 529 |
| `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt` | 35 | 148 |
| `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` | 34 | 1032 |
| `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt` | 34 | 248 |
| `app/src/main/java/me/rerere/rikkahub/utils/ComposeExt.kt` | 34 | 50 |
| `ai/src/main/java/me/rerere/ai/provider/Provider.kt` | 31 | 141 |
| `app/src/main/java/me/rerere/rikkahub/data/files/FilesManager.kt` | 27 | 676 |
<!-- reverse-index:end -->

## 索引边界

- 这是静态导航索引，不是编译器级调用图；本地导入数只表示耦合信号，不等同于运行时调用次数。
- 路径分类和符号识别使用保守规则，重构前仍须回读实现、调用方、数据库、测试与失败路径。
- 自动生成提交使用 `[skip ci]` 和 `[skip index]`，避免工作流循环。
- 二进制、构建产物、依赖缓存和生成目录不会进入索引。
- 源码指纹未变化时保留原生成时间和基准提交，避免无意义索引提交。
