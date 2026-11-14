# 勾彬小说

一款无广告、无需注册的 Android 小说阅读器。基于 [LegadoTeam/legado](https://github.com/LegadoTeam/legado) 二次开发，复用成熟书源引擎、原生阅读器和本地书库，而非从零重写。

支持 Android 8.0 及以上。源码遵循 GPL-3.0，保留上游许可与贡献者信息。

[直接下载 1.0.0 APK](https://github.com/zgozh/xiao-shuo/raw/refs/heads/main/downloads/goubin_1.0.0.apk) · [版本发布](https://github.com/zgozh/xiao-shuo/releases) · [验证记录](docs/VALIDATION.md)

## 找书与阅读

- **全源搜索**：书名、作者、关键词，跨已启用书源并发搜索；同名同作者结果合并，展示可换来源。
- **书源诊断**：搜索菜单中查看各来源命中数、响应耗时、失败原因；保留分组搜索、搜索历史、结果筛选和暂停控制。
- **书源中心**：首次安装导入 XIU2 社区规则快照，保留原始启停状态；支持在线目录更新、网络/本地/二维码导入、书源订阅、脚本编辑与检测。
- **扩充候选池**：另接入从大型社区集合按类型和主机筛选的 300 条中文小说规则；启用状态与本机检测状态分开显示，更多集合可按需导入。来源与筛选记录见 [书源调研](docs/BOOK-SOURCE-RESEARCH.md)。
- **发现广场**：从已启用的书源加载真实作品卡片、分类和排行榜；切换来源、选择榜单、浏览完整分页列表。各平台的排名分别展示。
- **本地书库**：书架分组、更新提醒、离线缓存、换源、书签、全文搜索，以及 TXT、EPUB 等本地文件阅读。
- **内置阅读器**：多种翻页方式、自动阅读、TTS 听书、亮度、进度、阅读菜单与丰富排版选项。
- **阅读面板**：累计时长、阅读排行、最近阅读，以及根据本机阅读作者和分类排列的书架待读推荐。
- **正文质量检查**：书源检测提示异常短章、替换字符、私用区字符和疑似验证页。提示属于线索，不证明版本是否完整，也不会自动删除或改写正文。
- **附加能力**：RSS 订阅、WebDAV 与文件备份、规则编辑、漫画及音视频等上游已有功能继续保留。

书源是独立网站的解析规则。网站是否收录作品、是否需要账号、网络可达性、付费范围和版本完整度由网站决定；应用不解锁付费内容，也不保证每本书在每个来源都有结果。

## 高度自定义，简洁主页

从「我的 → 主题设置」进入集中管理页：

- 界面主题：日间、夜间、跟随系统、墨水屏；主题色、强调色、背景色、背景图及模糊、底栏皮肤和缩放。
- 阅读字体：导入 TTF/OTF，字号、字重、字间距、行距、段距、首行缩进。
- 一键模板：夸克、起点、番茄、微信读书等参考风格，以及护眼绿、羊皮纸、夜读和大字模板；预览后点选套用，再继续微调。模板由本项目独立设计。
- 阅读背景：多套方案、自定义图片、文字与强调色、下划线及段评图标配色，配置导入导出。
- 阅读中还可调整页边距、状态栏、点击区域、翻页、自动阅读等设置。
- 封面、书架字体、欢迎页与备份同步分别提供设置入口。

## 本地构建

需要 JDK 21、Android SDK 36、Build Tools 36，以及项目 Wrapper 指定的 Gradle 8.14.4。

1. 创建未跟踪的 `local.properties`，填写 `sdk.dir=你的SDK路径`，路径建议用 `/`。
2. Windows 上将 `GRADLE_USER_HOME` 指向与项目同盘的缓存目录，避免 KSP 的跨盘相对路径错误；本机可复用 `D:/Cache/gradle`。
3. 调试包：`./gradlew :app:assembleAppDebug`。
4. 首次创建自己的发布签名：`powershell -File scripts/new-signing-key.ps1`。
5. 发布包：`./gradlew :app:assembleAppRelease`。

产物位于 `app/build/outputs/apk/app/`。正式包使用独立包名 `com.zgozh.xiaoshuo`，调试包后缀为 `.debug`。

**务必安全备份本机 `signing/` 目录。** 密钥和口令均被 Git 忽略，不能提交到仓库；后续覆盖升级必须复用同一密钥。

网络受限时，可通过本机 Gradle 配置设置代理；代理地址和个人 SDK 路径不应提交。Wrapper 指定的 Gradle 版本已缓存时可以复用。

## 验证与发布

针对新增的正文质量提示、远端目录格式及现有搜索并发/分页、阅读排版执行定向测试：

```sh
./gradlew :app:testAppDebugUnitTest --tests '*ContentQualityTest' --tests '*SourceRepositoryTest' --tests '*SearchProgressReporterTest' --tests '*SearchPaginationContractTest' --tests '*SearchCommandGateTest' --tests '*ReadStyleLineSpacingTest'
```

GitHub Actions 在 main 与 PR 构建调试 APK。发布工作流使用 `v*` 标签，并需要仓库 Secrets：`RELEASE_KEY_STORE`（本机 p12 文件的 Base64）、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_PASSWORD`。签名别名为 `goubin`。APK 资产名称使用 `goubin_版本号.apk`，以便应用内更新识别。

首个正式 APK 已在本机签名，随 `downloads/` 提供；main 的 CI 构建通过后，工作流可使用仓库令牌把这个已有签名的包发布到 1.0.0 Release，无需上传签名私钥。后续版本从源码在 CI 签名时才需要上述 Secrets。

应用内更新只查询本仓库的 Releases，不会安装上游项目的更新。

## 来源与维护

- Android 底座：LegadoTeam/legado，基准提交 `330e606c4d57e08adc056ff8ab7a4132c784da9a`；上游 README 保存在 [docs/UPSTREAM-README.md](docs/UPSTREAM-README.md)。
- 书源规则：[XIU2/Yuedu](https://github.com/XIU2/Yuedu)，GPL-3.0；快照与许可证保存在 `sources/xiu2.json` 和 `sources/XIU2-LICENSE`，应用内初始化文件与快照一致。
- 远端目录：`sources/catalog.json`；内置离线目录为 `app/src/main/assets/source-catalog.json`。修改目录后请同步两者，已安装应用可在书源中心更新。
- 原上游 CI 文件以 `.disabled` 扩展名保留，避免在新仓库中执行原项目发布或第三方上传流程；本项目使用 `goubin.yml`。

勾彬小说的品牌、导航、书源中心、阅读面板与质量提示为此分支改动。原作者、社区规则作者和第三方组件的版权及许可继续保留，详见 [LICENSE](LICENSE) 与应用内「关于 → 开源许可」。
