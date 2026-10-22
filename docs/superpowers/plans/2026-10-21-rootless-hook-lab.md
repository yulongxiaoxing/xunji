# 电脑隔离验证免 Root Hook

用户已同意先在电脑验证免Root Hook，暂不动手机QQ。沿用2026-10-21核查结论：QQ9.3.70安装包为arm64，消息接口静态匹配并不证明可运行。此次为有明确退出条件的实验，不部署到手机。

1. 在项目忽略目录建立独立AVD，使用官方Android16镜像，核验官方下载摘要；不覆盖用户已有AVD。限制模拟器内存/CPU，ADB写操作始终指定该模拟器序列号。
2. 从JingMatrix/LSPatch正式发布获取命令行工具，记录来源/摘要；先在自建最小测试应用验证无Root模块加载与方法回调。此阶段不登录QQ，不调用DeepSeek。
3. 若框架通过，将本地QQ原始APK副本用于电脑实验，核验ARM兼容、启动与模块加载，不安装到61d20d8手机。明确这是在电脑环境修改副本的实验，不是批准替换手机QQ。
4. 真实群消息测试必须在QQ可启动后由用户决定并完成测试账号登录，再验证指定群文字、末尾、发送者与去重。没有登录/真实来信不宣称消息链路通过。

退出与记录：发生框架/ARM兼容阻塞先按证据诊断，不能以改手机Root替代实验条件；更新ledger和可行性文档，保留最短复现。没有Git仓库，不创建虚假提交。实验目录为`.codex/hook-lab`，与主应用分离。

## 进展

- 预检：WHPX硬件加速可用；电脑约16GB RAM、当前空闲约3.3GB；已有AVD为API37.0/x86_64。主力手机序列号61d20d8仅保留连接，本实验不向其部署。
- 已确认官方镜像仓库与LSPatch1.2正式发布资产。尚未启动AVD或执行修补。


- 用户确认继续电脑免Root验证，手机QQ保持现状。
- Ruling：下载API36镜像期间，先用现有API37镜像建立全新隔离AVD做加载预检，避免等待期间没有运行证据；此结果不替代Android16/QQ验证。
- 官方LSPatch1.2 build487 debug JAR SHA256与发布资产摘要一致；Xposed API82从官方Maven根地址取得（错误的/repo/前缀404已纠正），仅用于编译。
- 自建target.apk/module.apk编译签名通过；修补包内嵌模块逐字节一致，包含x86_64加载器。
- API37隔离AVD emulator-5590已冷启动：Android17/API37，ABI x86_64/arm64-v8a，libndk_translation.so；ADB为shell uid2000。模拟器强制RAM至4096MB，主机可用内存明显下降，预检后主动关闭，不并行运行第二台。
- 真运行前后：未修补返回ORIGINAL（uid10235）；修补后返回HOOK_OK（uid10236），日志确认MODULE_LOADED。应用私有result.txt与截图均保存于.codex/hook-lab/api37-*。未Root、未安装QQ、未登录账号、未调用DeepSeek。这只证明电脑API37基础加载与Java方法Hook有效。
- API36官方镜像1.9GB正在断点分段下载，最终必须校验仓库SHA1后解压。下载会话66302，结束后另生成patched-target测试包；API37启动会话10804、对照验证会话45973均已完成。


- 下载遇到分段超时，已保留完成内容并以较小分段重试。API36镜像最终SHA1 c6bf44bdcd885bb902b4ba752d111a073ad7a817与官方仓库相符，SHA256 b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160；完成校验后才解压运行。
- QQ副本仅在电脑目录修补，sigbypasslv2，嵌入接口加载探针。APK签名验证通过，模块逐字节核对通过；没有以此宣称QQ能启动或接收。Ruling：在镜像下载期间准备QQ实验副本，真运行仍按基础对照→QQ原版→QQ实验版顺序。
- API36隔离AVD emulator-5588已冷启动：Android16/API36，x86_64/arm64-v8a，libndk_translation.so，ADB shell uid2000。镜像强制内存至2560MB，只有此台模拟器运行。
- API36真运行对照通过：baseline ORIGINAL、patched HOOK_OK，实际MODULE_LOADED日志与报告保存在api36-fixture-report.json及api36-hook-result.png。
- 正在进行QQ原版/实验副本启动对照（会话28357），未登录账号。已询问是否有备用测试QQ号，避免用主号影响手机当前会话。不得以只有主号/未回复当作允许登录。


## 本轮结果

- QQ原版安装超过180秒，但后台安装会话继续处理原生库和DEX，最终完成；没有更改安装验证设置。通过模拟器内sha256sum确认原始APK与电脑副本一致后恢复对照，避免重复卸载安装。后续大APK安装等待上限调整为480秒。
- Android16 QQ原版和实验V1均成功启动，停在用户协议/隐私摘要界面，没有代替用户同意或登录。V1实际日志MODULE_LOADED及QQ_INTERFACE_OK（53个方法），进程存活。
- 为避免把类存在当作Hook成功，V2增加受控方法实验：给QQ的TextElement.getContent安装只计数的after-hook，用模块自己新建的TextElement及固定标记调用，然后unhook；不读已有聊天对象，不改变返回值。
- V1日志没有QQ_METHOD_HOOK_OK；V2在QQ两个进程中实际出现QQ_METHOD_HOOK_OK controlledObject=true uid=10219，应用仍存活。报告qq-controlled-hook-report.json、日志qq-controlled-hook.log、截图qq-controlled-hook.png均已保存。此次确认的是QQ进程中的真实Java方法拦截，尚不是消息回调/后台收取验证。
- 当前测试探针并未订阅onRecvMsg或实现寻迹消息桥接；真实群、长文、私聊/未关注群排除、去重和锁屏保活均未验收。测试账号问题待用户答复，未使用主号。
