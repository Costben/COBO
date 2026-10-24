# Cobo

Android 第三方微博客户端。Kotlin + Jetpack Compose，UI 基于本地 [COUI](../../coui)（ColorOS 17 设计语言）。

数据层走 `m.weibo.cn` Web API：网页端登录一次，之后复用 Cookie，覆盖信息流、详情、评论、发布、主页、搜索、消息、设置等页面。

| 项 | 值 |
| :--- | :--- |
| applicationId | `com.cobo.app` |
| 应用名 | Cobo |
| minSdk / targetSdk | 26 / 37 |
| versionName | 1.0.27-pre |
| 工程 | `frontend/`（独立 Gradle 工程，Gradle 9.6.1） |

## 功能

- **信息流**：推荐（`containerid=102803`）、关注（`feed/friends`）、好友圈（`feed/circle`），支持 `since_id` / `max_id` 分页与下拉刷新
- **微博卡片**：正文富文本（@ / 话题 / 链接 / 表情）、九宫格图片、视频、转发块、来源与时间
- **详情页**：正文、评论（热度 / 时间双排序）、点赞、评论、转发
- **发布**：文字 + 多图上传、@ / 话题 / 表情工具条、草稿箱
- **主页与关系**：个人主页、用户微博、关注 / 粉丝
- **搜索**：热搜、关键词搜索
- **消息**：私信列表、通知
- **本地数据**：浏览历史、草稿、多账号、设置（深色模式 / 字号）
- **登录**：全屏官方网页登录，自动抓取并保存 Cookie

## 目录

```
Cobo COUI/
├── frontend/                  # Android 应用（com.cobo.app）
│   ├── app/                   # 应用模块
│   ├── scripts/publish-coui.sh # 发布本地 COUI 到 mavenLocal
│   ├── scripts/e2e.sh         # 真机端到端测试
│   └── gradlew                # Gradle 9.6.1
├── docs/                      # 文档
└── icon-lab/                  # 图标候选生成脚本（SVG）
```

## 构建

```bash
cd frontend
./scripts/publish-coui.sh          # 首次构建前：发布本地 COUI 到 mavenLocal
./gradlew :app:assembleDebug
```

`settings.gradle.kts` 依赖同级目录 `../../coui/build/repository/local`，构建前先确保 `coui` 仓库已 `publishToMavenLocal`。

## 安装

```bash
adb install -r --no-incremental app/build/outputs/apk/debug/app-debug.apk
```

## 测试

```bash
cd frontend
./scripts/e2e.sh                   # 真机端到端测试（需已登录）
```

## 文档

| 文档 | 内容 |
| :--- | :--- |
| [`01-微博API.md`](docs/01-微博API.md) | Host、公共参数、接口目录、上传、返回结构与错误码 |
| [`02-技术方案.md`](docs/02-技术方案.md) | 技术栈、模块拆分、数据模型、里程碑、关键实现 |
| [`03-功能清单.md`](docs/03-功能清单.md) | 已实现功能与对应接口、已知边界 |
| [`04-前端架构.md`](docs/04-前端架构.md) | COUI 主题、导航、页面清单与实测记录 |
| [`05-端到端测试.md`](docs/05-端到端测试.md) | E2E 套件、覆盖范围与运行方式 |

## 说明

第三方客户端，与微博官方无关，仅个人使用。
