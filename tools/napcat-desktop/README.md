# 寻迹电脑端 NapCat 演示

此工具管理已安装在 `.codex/napcat-desktop/runtime` 的官方 v4.18.41 Windows Node 独立包。不会修改手机 QQ、Root 手机或发送群消息；不会结束普通 QQ 进程。运行包、QQ 登录数据和口令均留在忽略目录，不提交或分享。

演示前退出普通电脑版 QQ，保持手机 QQ 登录。电脑保持联网和唤醒，通过 USB 连接已允许调试的小米手机。双击 `start-demo.bat`，即可启动已有接入端或复用进程，并恢复 USB 转接。手机寻迹继续使用已保存的接口口令。扫码由用户本人确认，登录是否保持取决于 QQ 服务状态。

电脑睡眠、USB 断开、QQ 离线时无法保证接收；恢复 USB 后重新运行启动脚本。正常电脑版 QQ 与 NapCat 的同账号登录会冲突，不要同时启动。

```powershell
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" tools/napcat-desktop/desktop.py start
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" tools/napcat-desktop/desktop.py status
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" tools/napcat-desktop/desktop.py link
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" tools/napcat-desktop/desktop.py stop
```

`status` 只显示在线状态和群数量，不输出口令/消息。确认在线后把登录账号编号保存在本机私有 account.json，供以后启动请求快速登录；这不保证自动登录成功。`stop` 只终止可执行文件路径精确匹配独立运行目录的 Node 进程。

接口仅绑定电脑127.0.0.1（OneBot13001、WebUI16098），ADB反向转接手机本机3000。手机白名单、原文、AI与卡片仍由寻迹管理，关注时先建立历史基线，只处理之后的新消息。

为避免访问日常电脑版 QQ 数据，启动脚本对本地 NapCat 副本的两处数据目录读取加入环境覆盖，保留原文件并严格检查匹配次数。这个适配仅验证于本次固定版本，更新运行包后需重新验证。

验证记录见 `docs/superpowers/plans/2026-10-21-napcat-desktop.md`。群列表或在线状态不能替代真实长消息、手机QQ前台、锁屏与过滤验收。
