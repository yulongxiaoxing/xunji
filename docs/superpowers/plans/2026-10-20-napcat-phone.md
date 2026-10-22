# 手机 NapCat 接入试验计划

Goal: 用手机本机NapCat替代通知摘要，按群号筛选后复用寻迹原文、AI和卡片流程。
Architecture: Termux/proot运行Linux QQ与NapCat；仅本机OneBot HTTP/WS接口；寻迹群列表按group_id持久化，未选群和私聊不入库不上云。验证期间不自动回复群消息。
Tech Stack: Android Java本地WebView、JobScheduler、Termux、NapCat OneBot11。
Spec: docs/qq-message-source-research.md 和用户本轮明确授权。

- [x] 安装官方Termux ARM64包，验证bootstrap和手机网络。
- [x] 安装NapCat，启动本机QQ，交由用户扫码登录。
- [x] 验证本机接口身份与群列表；配置随机令牌，不向局域网公开接口。
- [x] 寻迹增加群号白名单、接入状态和群列表；关闭通知正文导入，保留旧数据。
- [x] 接收群消息时先过滤再提取正文；以账号、群、消息ID去重，复用自动AI流程。
- [ ] 构建安装并验证长消息末尾、QQ前台、锁屏接收、未选群和私聊排除。

验收不能以安装或接口可达代替真实QQ来信。扫码、账号验证和测试群来信需要用户完成。试验阶段不做QQ降级、Root、自动发消息或删除旧数据。


## 当前验证状态（2026-10-20）

手机端Termux、Debian、Linux QQ及NapCat已安装并启动。用户已扫码登录；WebUI返回isLogin=true、coreReady=true、loginPhase=ready。本机OneBot的get_login_info、get_status、get_group_list均返回ok/retcode=0，群列表同步后有385个群（刚登录的第一次查询为空，随后同步完成）。

寻迹已保存加密本机连接口令并连接；Android系统确认NapCatService处于前台运行，手机关注群界面显示“已连接 · 正在接收0个关注群的新消息”及实际群名、群号、关注按钮。未擅自关注任何群。接口只绑定127.0.0.1；电脑ADB端口转发只用于本次调试。

等待用户在寻迹关注测试群并重发全文后，再验证历史消息接口结构、新消息入库、自动提取、长消息末尾、QQ前台和锁屏接收，以及未关注群/私聊排除。当前不能声称真实消息全链路已通过。

## 早期排障记录（以下安装失败状态已解决）

官方Termux v0.118.3 ARM64 APK已下载，SHA256与官方发布清单一致：72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e。手机安装两次返回INSTALL_FAILED_USER_RESTRICTED / Install canceled by user；用户已确认继续，第二次仍取消，已请求其说明手机界面。当前com.termux尚不存在。

安装包已放到手机Download/Termux-0.118.3-arm64.apk，可由用户文件管理器手动安装并打开一次初始化。寻迹新APK亦已放到Download/Xunji-NapCat-Trial.apk，versionCode11/0.10-napcat-trial，尚未覆盖安装。不能声称NapCat已运行或真实消息链路已通。

寻迹接入代码已完成首轮：本机127.0.0.1:3000、加密连接口令、前台服务每5秒查询已关注群最近50条、按账号/群号/消息ID去重、首次快照排除旧消息、原有通知输入停止导入、旧数据库保留。65项Java边界检查与原有浏览器交互检查通过。独立代码审查指出并发选群、取消和暂停期间在途请求、首次快照边界三项问题，已用统一锁、群修订号、连接会话号和入库边界复核处理，并重新构建验证。真实QQ/Android服务保活仍需实测。

后续：先完成Termux安装初始化，验证run-as com.termux和私有bash是否可执行，再部署已下载审阅的官方install.termux.sh。随后配置本机鉴权、扫码登录（用户操作），验证OneBot接口，再安装寻迹新版并进行真实来信矩阵。当前不改Root、QQ版本或系统安全设置。


更新：用户说明手机未出现任何安装弹窗。实际日志显示MIUI安装流程返回user restricted。改用adb install --no-streaming -i com.android.shell后，Termux安装成功（未修改系统安全配置）；已启动初始化，run-as com.termux的私有bash可执行。寻迹versionCode11也已用相同方式覆盖安装，并授予声明的通知权限。手机端官方Termux安装脚本已在Termux独立应用目录后台运行，Debian容器已建立，正在部署依赖。普通run-as不会自动带上Termux PATH/HOME，辅助脚本仅为目标子进程设置正确的应用环境，没有修改电脑或手机全局HOME。脚本执行日志在Termux files/home/xunji-setup.log；启动ADB命令等待子进程而超时不等于手机安装任务中断，已通过独立日志查询确认继续执行。


## 群搜索补充（2026-10-21）

寻迹0.11-group-search（versionCode12）已覆盖安装到USB连接手机。关注群页面增加群名/群号包含搜索、结果数量、一键清空和无结果提示；搜索不会修改关注或准备中的状态，操作继续使用原群号。沿用原生WebView和现有简洁样式。浏览器验证通过群名/部分群号、首尾空白、英文大小写、无结果/清空、输入焦点、重新渲染保留查询、同名群对应正确群号、关注/准备状态和320/390/667宽度。原有浏览器检查与构建所含65项Java检查通过，APK签名验证通过，ADB安装返回Success。手机实际输入部分群号865，列表从385个群筛选为5个群，搜索框与关注按钮显示正常。此时本机QQ接口暂不可连接（主机查询RemoteDisconnected，手机界面显示未连接）；群搜索可使用已缓存列表，真实关注和接收仍需恢复接口后验证。


口令疑问排查（2026-10-21）：手机设置明确显示本机连接口令已保存，不需要重填。WebUI鉴权成功，QQ状态实际为isLogin=false、coreReady=false、loginPhase=waiting_qrcode，OneBot尚未启动。已刷新登录二维码供用户重扫；未更换连接口令或DeepSeek密钥。
