# NapCat 电脑端演示接入验证

用户授权：演示时电脑保持开启，重新尝试 NapCat。手机 QQ 不替换、不 Root；扫码由用户完成，群消息由用户手动发送。

## 接入方式

电脑 Windows Node 独立包 → 本机 HTTP 127.0.0.1:13001 → ADB reverse 将手机 127.0.0.1:3000 转接到电脑 → 寻迹原有群号白名单、原文保存、自动 DeepSeek 与卡片。

WebUI 只绑定电脑 127.0.0.1:16098；两个接口均使用独立鉴权。API 口令沿用已在手机加密保存的口令，密钥不写入本文。USB 断开后须重新建立 reverse；电脑睡眠或进程退出时不能继续接收。

官方包 NapCat v4.18.41：

- https://github.com/NapNeko/NapCatQQ/releases/tag/v4.18.41
- 文件 NapCat.Shell.Windows.Node.zip，124089498 字节。
- SHA256 51fbb10e7bcd56b0c2a6de86c37811e7518330e48a137ac689ab1e4d8627d3f0，与官方 expanded_assets 公布值一致，下载后完整校验。
- 包内 QQ 9.9.31-49738、Node 22.11.0；wrapper.node 在电脑实际加载成功。
- 忽略目录 `.codex/napcat-desktop/` 保存运行文件、私有配置、进程信息和报告。

独立 QQ 数据目录 `.codex/napcat-desktop/qq-profile`。官方 Node 入口另起 worker，且 native wrapper 用 dlopen 加载，不使用 require 缓存；仅父进程替换 wrapper 无法隔离。因此启动脚本对本地副本 napcat.mjs 的两处数据目录读取加入 XUNJI_QQ_DATA 子进程环境覆盖，逐处断言只匹配一次，保留原始 mjs 备份。没有改电脑原版 QQ 安装目录。实际独立目录生成 nt_qq，重启后快速账号列表不再显示原 QQ 缓存账号。

## 排障和当前证据

1. 手机已通过 USB 连接。原 Termux screen 退出后 Linux QQ 子进程仍在；核对进程路径后向所属主进程发送 TERM，确认旧 Linux QQ 已退出，手机原版 QQ 保留。
2. 首次扫码明确报“当前账号已登录，无法重复登录”，此时 OneBot 尚未监听，不能称连接成功。
3. 用户退出普通电脑版 QQ，进程检查确认 QQ.exe 不再运行；刷新二维码后用户再次确认登录。
4. WebUI isLogin=true、coreReady=true、phase=ready；OneBot get_status online=true、good=true；get_group_list 返回386群。
5. adb reverse tcp:3000 tcp:13001 建立成功。手机寻迹实际显示“本机QQ在线 · 已关注0个群”，NapCatService 为前台服务；旧接口口令无需重填。

## 验收清单

- [x] 官方包完整性、独立进程启动、登录状态与群列表。
- [x] 手机通过 USB 使用电脑接口。
- [x] 用户关注测试群后发送长文本，比较末尾标记和消息长度。
- [x] 原文进入寻迹，DeepSeek 自动生成真实卡片。
- [ ] 手机 QQ 前台时接收。
- [ ] 锁屏时收到新消息并在返回后显示。
- [ ] 未关注群、私聊不进入寻迹。
- [ ] 连续观察在线状态，记录观察时长和限制。

接口在线不能代替真实消息验收。不得把历史群列表、演示卡片或控制对象当作实时消息成功。

## 本轮观察结果

电脑端连续60次、每5秒采样，覆盖0至295秒，均为 isLogin/coreReady/online/good=true，没有在这段约5分钟观察中出现掉线。这仅代表短时间在线，不能推断全天稳定。

手机实际已经通过USB显示QQ在线，但后续ADB设备消失；已告知用户保持USB连接并请求重连。早期诊断脚本把OneBot完整响应直接当作data读取，错误输出历史0条，此结论已撤回；修正响应层级后查到19条历史消息。用户实际标记使用“循迹”而非预期的“寻迹”，诊断现接受两者。当时尚未验收真实消息，后续验收见下文。

可复用启动工具：`tools/napcat-desktop/start-demo.bat`；CLI在同目录desktop.py，提供start/status/link/stop。已实际运行start复用现有进程并恢复reverse，随后status再次确认在线与386群。首次以该工具新启动并快速登录的路径尚未做重启实测，避免打断用户正在进行的消息测试。

待用户重新连接USB后执行link，再继续关注群与真实消息验收。实际运行和采样报告保留在 `.codex/napcat-desktop/stability-report.json`，不包含消息正文或口令。

## 2026-10-22 真实长消息与AI验收

用户确认重新连接USB并完成发送。复查电脑QQ在线，386群；恢复reverse成功。寻迹真实模式显示已关注1群，待处理4条（发布人已识别），原有收件箱1张卡片。

测试群276560744，两条思考题正文各476字符，末尾均有“循迹完整性测试1021”。SHA256均为15537ccbffad9a10e08453014add1c4da334596ea61220a84ad7c08a51154dbb。两条均包含乡村病、10月25日、PDF、5页、提交邮箱和有效提交说明。实际测试消息ID为492634494、1348733427。完整原文和来源仍由寻迹管理；诊断脚本只向控制台输出长度/哈希/检查结果。

### 自动整理等待问题及修复

最初手机默认Wi-Fi未通过网络验证，Job41等待CONNECTIVITY；一次强制执行后请求失败，原文留在待处理。用户切到移动网络后，默认网络154已VALIDATED，任务仍在入队42秒后显示TIMING_DELAY/CONNECTIVITY未满足，界面持续等待。这构成真机修复前复现。Termux后台curl仍超时，不能据此断言寻迹自身无法联网，最终以寻迹实际请求结果为准。

修复为：Activity可见且网络已验证时直接启动共享提取队列，后台保留JobScheduler；前后台通过同一个AtomicBoolean协调，避免同时处理同一批。失败仍保留原文、不无限自动重试。新增诊断只记录消息字符数、成功状态及异常类别，不记录密钥、正文或AI结果。

寻迹versionCode14/0.13-visible-extraction构建、65项Java检查、APK v3签名验证通过，ADB原位更新Success，原有数据和密钥保留。实际打开新版后，日志依次记录4、476、5、476字符原文处理，每条均Source extraction saved，约5秒完成。真实收件箱从1增至5张，待处理4条清空；两个476字符记录直接来自数据库原文。无需用户逐条点整理。原有浏览器交互回归检查全部通过。

已证明：关注群原文接收与保存、此次476字符文本完整性、手机联网后DeepSeek自动提取及卡片入库。尚未完成独立的锁屏新来信、QQ前台实时来信和未关注群/私聊真机矩阵，不把历史补收和这次成功扩大为长期后台稳定承诺。
