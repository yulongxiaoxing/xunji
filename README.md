# 寻迹

把关注 QQ 群中的文字通知整理成可确认的任务卡片。当前为黑客松 Android 演示原型，版本 `20 / 0.19-today-schedule`，提取规则版本 `5`。

## 当前流程

电脑 NapCat 接收 QQ → 按群号过滤关注群 → 手机保存原文 → DeepSeek 自动提取 → 收件箱 → 加入待办、完成或取消。取消待办回到收件箱；重新整理保留已有待办和完成状态。关注群支持待关注/已关注分组及搜索。

同一作业的多道题保留在一张卡片；独立任务可拆卡。没有明确事项时允许返回空列表，不猜截止时间。提取失败保留原文到待处理；相同正文按规则版本复用本机缓存。

## 目录

- `tools/notification-probe/`：Java + WebView Android 应用、提取提示词及 Java 检查。
- `tools/napcat-desktop/`：Windows 电脑端接入管理；依赖单独安装的 NapCat 运行包及本机配置。
- `tools/rootless-hook-lab/`：历史模拟器实验，独立于当前演示流程。
- `docs/HANDOFF.md`：最新交接与已知限制。其他带日期的研究/设计文档记录历史状态。

## 构建

需要 Windows PowerShell、JDK（javac/java/jar/keytool）、Android SDK build-tools 36.0.0 及 Android platform。默认 SDK 路径为 `$env:LOCALAPPDATA\Android\Sdk`，也可通过 `-SdkRoot` 指定。

```powershell
& .\tools\notification-probe\build.ps1
```

此命令运行 Java 检查、构建并验证本地测试签名。APK 在 `tools/notification-probe/build/xunji-notification-test.apk`。不使用 Gradle。测试签名仅用于本机演示，签名文件不上传。

安装及 USB 转接见 [Android 说明](tools/notification-probe/README.md)，电脑端前提见 [NapCat 说明](tools/napcat-desktop/README.md)。克隆仓库不包含 NapCat 运行包、已登录会话、手机数据库或个人密钥，需要在本机单独配置；电脑管理脚本当前仍绑定原开发设备序号，换手机前需调整。

## 数据与验证

主流程需要互联网权限，并将待整理的关注群文字发送给 DeepSeek。密钥由用户在手机设置中输入，使用 Android Keystore 加密保存；本地备份关闭。程序不自动向群发送消息。不处理图片与附件。

2026-10-22 本地复核重新运行：127 项 Java 检查通过；现有浏览器回归通过（真实模式、关注分组/搜索、待办取消、原文和 320/390/667 宽度）；APK v19 编译及 v3 签名验证通过。浏览器回归工具仍在本机忽略目录 `.codex/demo-qa/real-only.cjs`，不随仓库分发。

这些检查不能替代真机端到端验收。历史真机记录及 v19 卡片画面尚未复查等限制见交接文档；v20 已覆盖安装并核对版本；新界面尚未在真实数据中逐项验收。

## 上传范围

仅提交源码、文档和测试。`.codex/` 中的运行包、口令、账号配置、日志和截图，构建目录、APK、签名文件、数据库及本地初稿 DOCX 均留在本机。没有包含第三方运行包，也未为第三方组件重新授予许可。

## 本地新增事项管理（2026-10-22）

收件箱支持不加入待办，进入已忽略后可恢复；更多菜单支持编辑和删除。删除进入回收站，恢复到删除前状态。支持无需 AI 的手动新建待办及搜索标题、群名、发布人、要求和截止时间。编辑不会更改来源原文；用户决定不会被重新整理覆盖。

当重新提取无法可靠对应受保护事项时，保留该来源现有卡片，优先保护用户修改；不会自动把已忽略或已删除事项重新加入收件箱。

新增检查：

```powershell
node tools/notification-probe/tests/ui/card-management.cjs
& .\tools\notification-probe\test-store.ps1 -Serial SERIAL
```

界面检查需要 Playwright 与 Edge，可用 `PLAYWRIGHT_MODULE` 和 `EDGE_PATH` 指定位置。SQLite 检查安装独立测试包 `cn.xunji.cardtests`，仅访问自己的测试数据库，不访问寻迹真实数据库。


## 今日与日程（本地 v20）

首页为“今日”，包括当天事项、已过日期、未来七天及日期待确认列表。原收件箱、待办、关注群和设置统一收在“信息与待办”大窗；底部为今日、信息与待办、日程。

编辑事项中的“日程日期”和可选“日程时间”由用户明确确认，独立于原文截止时间；没有日期的旧记录保留在待确认日期中，不猜测“周五前”等表达。日程支持日期选择、附近日期、查看、完成及可选显示已完成事项；忽略、删除和未确认收件箱不进入正式日程。当前没有课程导入和系统提醒。

新增界面检查：`node tools/notification-probe/tests/ui/today-schedule.cjs`。127项Java检查、34项隔离Android SQLite断言、三组界面回归、APK编译和签名验证通过。v20已覆盖安装，真实数据中的完整使用流程仍需用户验收。仅本地保存，不上传GitHub。
