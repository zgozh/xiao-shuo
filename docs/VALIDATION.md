# 验证记录

## 1.0.6 柘栯小说统一品牌（2026-11-23）

- 当前源码、文档、资源及文件名的旧品牌扫描无匹配；包名与原签名证书保持兼容。旧证书身份不重新签发。
- 正式构建与定向测试命令：`:app:testAppDebugUnitTest --tests '*JapaneseSourceRuleTest' --tests '*Discovery*Test' :app:assembleAppRelease`，6m50s成功，含R8及release lint；20项测试全部通过。未操作MuMu，未继续轻小说网络问题。
- `aapt2 dump badging`：默认及全部语言应用名均为“柘栯小说”，包名 `com.zgozh.xiaoshuo`，1.0.6/code7。
- `apksigner verify --print-certs`通过，证书SHA256仍为 `e6dbd8aca496ad75c3b81a1ff7a957951d7da0ff6c2a8b45adf9324d0c9e5e03`，支持覆盖安装。
- `downloads/zheyou_1.0.6.apk`：27,012,589字节，SHA256 `84378ace97387a600f9ee27b9f37696e51e42b2df364c91d7c5397575c07cd9b`。当前下载清单只包含该版本；此前历史仅存于本机恢复备份，仓库以当前版本作为初始基线。
- 资源XML、内置JSON、目录镜像、相关Python脚本语法及工作流YAML检查通过。新版初始化标记对旧code5/6直接延续，保留用户已删除的来源；已有书库、书源规则和阅读设置不重置。
- 主会话直接执行：本会话禁止未明确要求的子代理委派。受影响diff已检查，构建Java进程在打包后关闭。

## 初始基线整理

仅调整仓库历史、发布配置与文档；应用源码和APK未改动，沿用上述有效构建和测试证据，不重复本地构建。工作流YAML与diff检查通过；远端发布门禁仍按既定流程执行。
