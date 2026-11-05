# 寻迹使用与构建指南

## 安装与版本

从 [v0.58 发布页](https://github.com/yulongxiaoxing/xunji/releases/tag/v0.58) 下载对应 APK。Android 最低版本为 9。把 APK 传到设备并打开，根据系统提示允许本次安装。

- **实际应用版**，应用名称「寻迹」，应用 ID 为 `cn.xunji.probe`。全新安装为空，可立即手动建事项或导入课表。覆盖旧版需要相同签名。遇到签名不一致时，保留含真实数据的旧安装。
- **黑客松演示版**，应用名称「寻迹演示版」，应用 ID 为 `cn.xunji.probe.demo`。首启导入三条虚构通知和两条日程，按首启当天的北京时间排到明天与后天。重启、升级和编辑后保留原来的选择。录制需要恢复初始状态时，可在专用模拟器清除演示版数据。

## 实际版快速开始

1. 打开「今日」，点「＋」手动新建活动，填写起止时间和地点。
2. 在今日列表中查看事项，到「日程」切换周表或月表。周表可点重叠时间块，查看各项详情。
3. 在课程管理中选择 ICS 或 WakeUp 课表文件，核对预览后导入。课表和待办会一起显示。
4. 在「成长」选类别，点「加入经历」手动记录。可添加本地证明图片。[成长档案使用说明](GROWTH.md)

## 配置 DeepSeek

在「信息与待办 → 设置 → DeepSeek」保存自己的 API 密钥。密钥在手机加密保存。联网整理时发送对应文字给 DeepSeek；成长证明图片存放在本地。

- 「今日 → ＋ → 粘贴通知原文」可以把通知送入待确认收件箱。核对原文后，点「加入待办」或「不加入待办」。
- 「口头记忆」支持输入和粘贴，也可使用系统输入法的语音转文字。AI 返回预览后，由用户确认保存。
- 有明确时间的待办按时间更新完成状态，后台调度可能延后。已完成事项的成长候选需要联网和密钥，并经本人核对后才能保存。
- 可复制的虚构通知和导入文件见 [使用示例](../examples/delivery/README.md)。

## 接入 QQ 通知

准备 Windows、Python 3、Android Platform-Tools、可用的 USB 调试连接和 [NapCat v4.18.41 Windows Node 运行包](https://github.com/NapNeko/NapCatQQ/releases/tag/v4.18.41)。电脑负责 QQ 接入，手机保存原文、整理事项和管理日程。

1. 将 `NapCat.Shell.Windows.Node.zip` 解压到仓库根目录的 `.codex/napcat-desktop/runtime/`，确认目录下有 `node.exe`、`index.js` 和 `napcat/`。
2. 从系统托盘退出普通电脑版 QQ，保持手机 QQ 登录。用 USB 连接手机，允许调试并确认 `adb devices` 状态为 `device`。
3. 在源码根目录运行 `py -3 tools/napcat-desktop/desktop.py start`。电脑工具建立手机 3000 端口到电脑 13001 端口的本机转接。
4. 打开 `.codex/napcat-desktop/local-secrets.json`，只把其中的 `onebot` 字段填入实际版「信息与待办 → 设置 → QQ 接入」的本机连接口令。需要登录时，由本人扫描运行目录中的 `napcat/cache/qrcode.png`。
5. 手机端显示在线后，选择并关注测试群。关注起点之前的历史不自动导入。验证时由本人在测试群发送通知，先确认原文已保存，再查看 AI 整理状态和收件箱卡片。

连接恢复后，应用从各群的已保存位置尝试补取可访问的历史。USB 重插后可执行 `py -3 tools/napcat-desktop/desktop.py link`；同时连接多个设备时用 `--serial SERIAL` 指定设备。详见 [电脑接入端说明](../tools/napcat-desktop/README.md)。

## 从源码构建

在 Windows 安装 JDK、Android SDK Build Tools 36.0.0 和 Android SDK Platform。项目直接编译 Java 与 Android 资源，无 Gradle。

```powershell
& .\tools\notification-probe\build.ps1 -Edition Actual
& .\tools\notification-probe\build.ps1 -Edition Demo
```

| 版本 | 本地 APK |
| --- | --- |
| 实际应用版 | `tools/notification-probe/build/xunji-notification-test.apk` |
| 黑客松演示版 | `tools/notification-probe/build/demo/xunji-hackathon-demo.apk` |

SDK 不在默认位置时加 `-SdkRoot` 指定路径。构建会运行 Java 策略检查，并用本机开发签名签名和校验 APK。首次自建生成的签名与公开安装包可能不同；请在独立模拟器测试自建包。源代码与两个 APK 同属一个发布版本，虚构首启数据位于演示版专用源码目录。
