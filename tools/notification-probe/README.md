# 寻迹 Android 源码

Java 宿主提供 SQLite、文件选择、后台任务及 WebView 桥接。界面在 `assets/`，版本入口分别在 `editions/actual/` 和 `editions/demo/`。实际版保持应用 ID `cn.xunji.probe`，演示版使用 `cn.xunji.probe.demo`。

构建、安装和配置见 [使用与构建指南](../../docs/RUNNING.md)。离线演示路径见 [录制手册](../../docs/VIDEO.md)。

`build.ps1` 自动运行 Java 策略检查，`test-store.ps1 -Serial SERIAL` 在独立测试包中验证 SQLite 行为。测试设备可以使用模拟器。
