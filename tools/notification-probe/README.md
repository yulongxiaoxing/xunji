# 寻迹 Android 演示原型

当前版本：`18 / 0.17-empty-result-fix`，`ExtractionPolicy.VERSION=5`。项目从早期通知/群名浮层验证发展为电脑 NapCat 接入、手机按群号关注、DeepSeek 自动提取的任务卡片演示。最新状态以 [交接文档](../../docs/HANDOFF.md) 为准。

## 构建与安装

前提：Windows PowerShell、JDK、Android SDK build-tools 36.0.0 与已安装的 Android platform。构建脚本优先 android-37.0，缺失时从本机平台目录选择；最低 Android API 28，target API 36。

在项目根目录执行：

```powershell
& .\tools\notification-probe\build.ps1
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

确认现场设备序号后，把下方 SERIAL 替换为该序号：

```powershell
adb -s SERIAL install --no-streaming -r -i com.android.shell tools/notification-probe/build/xunji-notification-test.apk
adb -s SERIAL reverse tcp:3000 tcp:13001
adb -s SERIAL shell am start -W -n cn.xunji.probe/.DemoActivity
```

使用 Java + WebView，无 Gradle。构建自动运行 Java 检查并校验测试签名；请保留本机签名文件以便覆盖安装。

## 演示流程

1. 按 [电脑端说明](../napcat-desktop/README.md) 准备隔离的 NapCat 运行环境并完成本人登录。保持电脑唤醒及 USB 连接。
2. 手机设置中配置本机接口及口令、个人 DeepSeek 密钥；检查连接状态。私密配置不在仓库中。
3. 在关注群页面搜索群名或群号，关注所需群。首次关注建立历史基线，只处理之后的新消息。
4. 用户自行发送测试文字，寻迹自动保留关注群原文并整理。成功结果进入收件箱，失败原文保留待处理。
5. 核对来源群、发布人、分类、标题、具体要求、截止时间与完整原文，再加入待办、完成或取消。取消返回收件箱。

同一作业的题干完整保留，独立事项才拆卡。允许没有截止时间或没有任何事项；空结果成功缓存。提示词在 `assets/extraction-rules.md`，更新提取语义时同步提升规则版本。

## 数据边界与检查

- 当前主流程有互联网权限，自动整理时会将相应文字发送给 DeepSeek。密钥由手机输入并使用 Android Keystore 加密保存；关闭云备份。
- 按群号过滤；不自动发消息，不修改手机 QQ，不 Root。图片与附件尚未实现。
- 早期通知监听和 QQ 普通群列表浮层诊断代码仍保留，不能以其旧版群名匹配能力描述当前群号主流程。
- 不用默认 `uiautomator dump` 检查浮层：它可能干扰无障碍服务。可检查应用日志或截图，避免记录真实正文。
- 2026-10-22 上传前：96 项 Java 检查、现有浏览器回归、APK 编译和 v3 签名验证通过。此次没有重新安装或复查真机画面。
- 云端结果仍可能不一致；同版本同正文复用本机缓存。编译及静态检查不能证明所有真实消息都处理正确。
