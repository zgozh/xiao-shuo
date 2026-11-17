# 书源调研与接入记录

调研日期：2026-11-14。只把来源页或原始 JSON 中观察到的数量记为“规则条数”，不据此宣称存在同等数量的独立可读网站。

| 渠道 | 原始入口 / 导入地址 | 核对结果与接入方式 |
| --- | --- | --- |
| [XIU2/Yuedu](https://github.com/XIU2/Yuedu) | [JSON](https://raw.githubusercontent.com/XIU2/Yuedu/master/shuyuan) | 实际下载 22 条、12 条启用；GPL-3.0；作为初始快照保留。数量不足以代表整个阅读社区。 |
| [shidahuilang/shuyuan-bak](https://github.com/shidahuilang/shuyuan-bak) | [good.json](https://raw.githubusercontent.com/shidahuilang/shuyuan-bak/main/good.json) | 实际下载 2,973 条；其中 1,994 条符合启用、文字类型以及搜索/目录/正文规则字段筛选。仓库有 GPL-3.0 LICENSE，规则中原作者信息继续保留。 |
| [AOAOSTAR 汇总](https://github.com/aoaostar/legado/blob/release/README.md) | [全量 JSON](https://legado.aoaostar.com/sources/b778fe6b.json) | 来源页标注 3,907 条。镜像同步日期与原始规则更新时间不同；作为按需导入入口，不把全部规则默认标成可用。 |
| AOAOSTAR / 破冰 | [JSON](https://legado.aoaostar.com/sources/4dc410d1.json) | 实际解析 128 条；抽查规则时间多为 2023 年。新同步不能证明旧域名仍可用。 |
| AOAOSTAR / 关耳女频 | [JSON](https://legado.aoaostar.com/sources/e3e5d620.json) | 汇总页列 86 条，可补充女频覆盖；不作为已验证可读数量。 |
| [源仓库合集](https://www.yckceo.com/yuedu/shuyuans/index.html) | 合集帖各有独立 JSON 地址 | 当前列表可见 440、164、830、463 等规模的分享。条数属于发布帖统计，需按每个集合的发布者、原作者说明与实际测试选择。 |
| [源论坛书源发布区](https://www.ycoo.net/forum.php?fid=2&filter=hot&mod=forumdisplay) | 各作者发布帖 | 活跃的规则发布与修复渠道。论坛要求有效 JSON/直链及转载出处；论坛公开展示不等于统一的再分发许可。 |
| [cjj200011222/legado-booksource](https://github.com/cjj200011222/legado-booksource) | [all.json](https://raw.githubusercontent.com/cjj200011222/legado-booksource/master/all.json) | 起点相关变体合集，可供比较规则设计；未核到明确仓库 LICENSE，因此本轮不打包其规则。 |
| [MoonBegonia / Source](https://moonbegonia.github.io/Source/) | 作者分类页 | 页面统计 1,836 条，标有效 1,123、失效 713，但原始数据较旧。用于了解社区来源与分类，不作为当前可用性凭据。 |

## 当前扩充

从 `good.json` 选择 300 条候选，保存在 `app/src/main/assets/defaultData/goubinCommunitySources.json`。原始来源、下载时间与 SHA-256 记录在 `sources/community-manifest.json`，许可文本在 `sources/SHIDAHUILANG-LICENSE`。

筛选排除明显的非中文文字小说类型，要求存在搜索、目录和正文规则，并按去掉 www/m/wap 前缀后的主机去重。异常未来时间戳不作为新规则排序依据。保留原作者注释与启停信息，不覆盖用户已编辑的同地址规则。

这属于候选池筛选，**不是 300 个已验证可读来源**；同主机去重也不能识别所有镜像或同一运营方。大型社区集合中第三方作者声明可能各不相同，后续更新应继续保留并检查原作者说明。

应用书源中心同时提供较大集合的按需导入和网址导入，避免把可获取范围限制在内置快照。

## 验收样本

用户指定《执魔》/我是墨水、《万古神帝》/飞天鱼、《九龙至尊》/盘古。每本分别记录：启用规则数、完成请求数、书名与作者匹配来源数、目录与正文验证结果。实际执行结果写入验证记录，不能以集合统计替代。

最初 22 条规则的模拟器搜索已观察到“天蚕土豆”多部作品及部分作品 2 个来源；这只证明该小样本的聚合路径可运行，尚不满足用户要求的跨源覆盖。

## 工作记录

2026-11-17 日轻扩展：新增自行编写的 Novelia 文库规则，参考 [Auto Novel 官方开源项目](https://github.com/auto-novel/auto-novel) 的 `web/src/api/WenkuNovelApi.ts`、`WenkuNovel.vue` 和 `server/src/main/kotlin/api/RouteWenkuNovel.kt`。匿名 `/api/wenku` 可检索文库，详情列出的 `volumeZh` 对应站点公开 `/files-wenku/{id}/{卷名}` EPUB。普通 `/api/novel` 匿名访问返回 401，未将该接口接入。接口预检脚本 `scripts/check-light-novel-api.py`；本次须使用代理，直连失败。

ESJ 实测可搜索「聖女」并读取《聖女，為祈求世界和平嘆息。》目录和一章正文；指定《回复术士的重启人生》未命中，旧详情已失效。哔哩搜索进入验证码页，抽查正文有截断提示；文库8各公开域名返回 Cloudflare 403，未标记为已验证来源。

[ZWolken/Light-Novel-Yuedu-Source](https://github.com/ZWolken/Light-Novel-Yuedu-Source) 声明 MIT，并要求转载保留名称、注释。应用提供原作者 Release 合集的按需预览导入，保留源名称与注释，仅补充分组；不宣称半维护合集全量可用。

本次使用 research skill 指定的独立研究代理核对主要渠道。代理在限定范围结束时返回了带来源的发现，未写入仓库；本文件由主会话根据返回结果及本机下载检查补齐。
