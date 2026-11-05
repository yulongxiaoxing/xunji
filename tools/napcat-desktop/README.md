# 寻迹电脑端 NapCat 接入

此工具管理已安装在 `.codex/napcat-desktop/runtime` 的官方 v4.18.41 Windows Node 独立包。不会修改手机 QQ、Root 手机或发送群消息；不会结束普通 QQ 进程。运行包、QQ 登录数据和口令均留在忽略目录，不提交或分享。

准备Python 3、Android Platform-Tools和官方固定版本运行包。下载与首次部署步骤见[使用指南](../../docs/RUNNING.md#接入-qq-通知)。运行目录须包含node.exe、index.js及napcat/；不依赖开发者的登录或旧口令文件。

演示前从托盘退出普通电脑版 QQ，保持手机 QQ 登录。电脑保持联网和唤醒，通过 USB 连接已允许调试的手机。双击 `start-demo.bat`，即可启动已有接入端或复用进程，并恢复 USB 转接。启动器优先使用系统Python，现有开发环境可作为后备。

首次启动自动生成`.codex/napcat-desktop/local-secrets.json`中的独立onebot和webui口令；将onebot填入手机寻迹的本机连接口令。已有口令和历史配置迁移会保留，不输出口令到日志。扫码由用户本人确认，登录是否保持取决于QQ服务状态。

电脑睡眠、USB 断开、QQ 离线时无法保证接收；恢复 USB 后重新运行启动脚本。正常电脑版 QQ 与 NapCat 的同账号登录会冲突，不要同时启动。

```powershell
py -3 tools/napcat-desktop/desktop.py start
py -3 tools/napcat-desktop/desktop.py status
py -3 tools/napcat-desktop/desktop.py link
py -3 tools/napcat-desktop/desktop.py stop
# 多设备或自定义SDK：
py -3 tools/napcat-desktop/desktop.py start --serial YOUR_SERIAL --adb "D:\Android\Sdk\platform-tools\adb.exe"
```

`status` 只显示在线状态和群数量，不输出口令/消息。确认在线后把登录账号编号保存在本机私有 account.json，供以后启动请求快速登录；这不保证自动登录成功。`stop` 只终止可执行文件路径精确匹配独立运行目录的 Node 进程。

未指定设备时自动选择唯一处于device状态的手机；无设备、未授权或多设备时明确报错，须处理调试授权或使用--serial。也可用环境变量XUNJI_ADB_SERIAL指定默认设备，不再绑定开发者手机。

接口仅绑定电脑127.0.0.1（OneBot13001、WebUI16098），ADB反向转接手机本机3000。手机白名单、原文、AI与卡片仍由寻迹管理，首次关注从关注时刻起记录。手机端的“信息与待办 → 设置 → QQ 接入”有完整连接教程、原文同步和 AI 队列进度。重连后按各群检查点自动向前分页补取，先落库再推进进度，失败和暂停保留续点；重复历史不改已有原文或用户决定。电脑关机时不采集，只有电脑 QQ 重新在线并恢复 USB 后才补取接口能返回的历史，不承诺全部离线历史无损。

为避免访问日常电脑版 QQ 数据，启动脚本对本地 NapCat 副本的两处数据目录读取加入环境覆盖，保留原文件并严格检查匹配次数。这个适配仅验证于本次固定版本，更新运行包后需重新验证。

群列表或在线状态不能替代真实长消息、手机QQ前台、锁屏与过滤验收。
