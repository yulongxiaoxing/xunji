# 寻迹：复用手机 QQ 登录的 Hook 可行性核查

日期：2026-10-21。用户授权继续研究 Hook，修改 QQ 或 Root 前再次确认。本次只读复制安装包并解析 DEX、查阅公开源码和文档；没有修改、卸载、降级或重新签名手机 QQ，没有刷机、解锁或安装 Hook 框架。

## 结论

技术上可沿着“在手机现有 QQ 进程监听消息 → 按群号筛选 → 寻迹保存原文 → DeepSeek → 卡片”继续研究。这条路线与 Termux 中独立登录 Linux QQ 不同。

已经找到当前 QQ 的文字消息入口，但还没有证明模块可以在这台手机加载、后台持续接收或完整保留所有消息。当前需要解决的是模块的加载权限与运行兼容性，不能用接口名字存在来宣称稳定接入。

如果要求同时保留原版 QQ 安装、当前登录及本地数据、不 Root、后台完整自动读取，目前没有找到已验证满足这些条件的方案。

## 当前设备的只读证据

| 项目 | 实际结果 |
| --- | --- |
| 手机 | 小米 15 Pro，Android 16 |
| QQ | 9.3.70，versionCode 16410 |
| Bootloader 属性 | `ro.boot.flash.locked=1`；`ro.boot.vbmeta.device_state=locked` |
| 当前 shell 的 su | PATH 中未找到；这本身不是对所有 Root 状态的完整检测 |
| 安装包 | 通过 `pm path` 定位并 `adb pull`，只读取应用安装文件，没有读取 QQ 私有账号或聊天数据库 |
| APK SHA-256 | `96c5a8c430c6a52417b0324df4642109cb7c20f9b0b507536cc279ccba3901fa` |

研究材料在本地忽略目录 `.codex/hook-research/`：原始 APK、`dex_interfaces.py`、`qq-9.3.70-interfaces.json`、参考接口源码和 `interface-comparison.json`。原始 APK 不应提交到项目仓库或随寻迹分发。

## 接口对比结果

参考来源是 [OpenShamrock QQ 9.2.95 适配分支](https://github.com/ion-aluminium/OpenShamrock-QQ-9.2.95-adapt/tree/qq-9.2.95-adapt)，不是对 QQ 9.3.70 的官方支持声明。静态脚本解析 DEX 中实际声明的方法，将 Java 参考接口转换为擦除泛型后的 JVM 方法描述符进行比较。

| 接口 | 参考接口方法数 | 当前 QQ 方法数 | 完全相同的描述符数 |
| --- | ---: | ---: | ---: |
| `IKernelMsgListener` | 53 | 53 | 53 |
| `IKernelGroupListener` | 38 | 41 | 38 |

当前群监听新增 `onFindPageRecommendGroup`、`onGroupAiAuthListChanged`、`onGroupImportantMsgNotifySettingChanged`。旧实现如果未处理新回调，仍有发生方法实现错误的可能，不能直接把旧模块当成适配完成的版本安装。

在当前 APK 中确认存在的消息结构包括：

- 消息到达回调：`IKernelMsgListener.onRecvMsg(ArrayList)`。
- 注册入口：`IKernelMsgService.addKernelMsgListener(IKernelMsgListener)`。
- 消息记录：群/会话类型、peer UIN/UID、消息 ID/序号/时间、发送者 UIN/UID、群成员名称/昵称、消息元素。
- 文字内容：`MsgRecord.getElements()` → `MsgElement.getTextElement()` → `TextElement.getContent()`。

这些是结构证据，尚未验证实际回调时机、群类型数值含义、群号字段映射、发送者名称优先级、长消息/合并转发的展开以及免打扰群事件。新模块必须用真实来信核对，不能从方法名推断所有语义。

## 两条加载路线

| 路线 | 对现有 QQ 的影响 | 现有登录 | 目前判断 |
| --- | --- | --- | --- |
| 已 Root 设备上的 Vector/LSPosed 类框架 + 专用模块 | 可在进程内加载，不需要重新打包 QQ APK | 可以利用该进程已有会话；不能承诺所有环境均可无损保持 | 最接近用户目标，但当前手机尚不具备已验证的加载环境 |
| JingMatrix/LSPatch + 专用模块 | 重新打包目标 APK，嵌入加载器和模块，再安装 | 不能保证保留原版 QQ 当前登录/本地数据；不同签名不能正常覆盖原包 | 免 Root 不等于不改 QQ，需要先单独批准具体安装方案 |

[Vector 文档](https://github.com/JingMatrix/Vector)声明支持范围包含 Android 16，但要求 Magisk/KernelSU 与 Zygisk。框架支持 Android 16 不等于消息模块已经支持 QQ 9.3.70。

[JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)支持现代和传统 Xposed 模块，提供免 Root 的 APK 修改路线；当前看到的发布页为 [v1.2](https://github.com/JingMatrix/LSPatch/releases/tag/v1.2)。旧 LSPosed/LSPatch 上游归档不代表这个分支也已停止维护。

[Android 签名规则](https://developer.android.com/studio/publish/app-signing#considerations)要求正常更新的签名匹配。模块运行时的“签名伪装”不能当成系统安装器允许覆盖原签名 QQ 的保证。卸载重装或另一个包/容器也不等于直接沿用原 QQ 的私有数据和登录。

当前手机报告 Bootloader 锁定。[Android 官方解锁流程](https://source.android.com/docs/core/architecture/bootloader/locking_unlocking)涉及恢复出厂数据重置。因此不能把“Root 后模块可利用会话”误说成“现在给这台手机 Root 就能保住当前登录”。小米具体解锁资格和操作本次未开展核实，不据此给出可直接执行的刷机方案。

## 为什么其他项目没有直接解决这个需求

- OpenShamrock 的已查分支明确只测试 QQ 9.2.95、Redmi K50 Pro、Magisk/LSPosed；静态接口部分匹配是后续适配依据，不是当前真机验证。
- [QAuxiliary](https://github.com/cinit/QAuxiliary)是 QQ 增强 Xposed 模块；它可以提供参考，但不是拿来即可使用的寻迹消息接口。
- [Shizuku](https://github.com/RikkaApps/Shizuku)提供 ADB/Root 权限下的系统 API 桥接，不自动提供 QQ 消息监听，也不使普通进程获得任意读取 QQ 私有数据的能力。
- [SnowLuma 手机部署](https://snowluma.github.io/en/docs/guide/deploy/mobile)仍是 Termux/proot 中的 Linux QQ，官方标注实验性，并说明 ptrace 与 proot 叠加的限制；它不能替代“手机原版 QQ 现有登录”这一要求。
- 无障碍只能按实际可访问的窗口内容处理，不能作为锁屏或其他群页面未打开时的完整消息订阅接口。

## 建议的最小验证模块

这部分是后续方案建议，尚未实现或装机：

1. 首次仅针对 QQ 9.3.70/16410；加载时核对实际类和方法，未知版本停止采集并提示需要验证。
2. 只监听文字消息，不引入完整机器人的发消息、撤回、文件下载或系统保活 Hook。先验证一个用户指定测试群，再验证群列表接口。
3. 使用 QQ 自己的会话。消息回调先核验群类型和群号白名单，再提取发送者、时间和文字。非关注群/私聊不写入寻迹、不上传 DeepSeek，不把原文写进调试日志。
4. 在后台工作线程把已筛选事件交给寻迹，避免在 QQ 回调线程请求网络或调用 AI。包含账号标识、群号、消息 ID、发布人、时间和原文，以现有去重与待处理队列衔接。
5. 进程间桥接须明确鉴权，不能裸露无口令 HTTP 接口。可研究显式 Binder 服务，在每次 Binder 事务检查实际调用 UID 与用户选定 QQ 实例；不能误用“模块 APK 与寻迹同签名”来假设运行在 QQ UID 下的代码自动获得签名权限。桥接实现另行审查。
6. 暂不承诺补齐离线历史、合并转发、图片、附件或被撤回内容。接收状态区分模块加载、QQ 会话、桥接心跳和实际最近一条入库消息。

## 加载前的确认与验收

下一项需用户提供的条件是是否有备用安卓机或已 Root 测试设备。研究与本地准备可以继续；修改/卸载/替换 QQ、安装加载框架、解锁 Bootloader 或 Root 均不在本次授权内。

加载后先验证：关注群长消息含末尾截止与提交方式、同一消息重复回调去重、QQ 前台接收、免打扰群、未关注群与私聊排除、取消关注期间在途消息、锁屏至少 30 分钟、QQ/寻迹重启后的恢复。只有真实来信入库与原文比对通过，才报告相应能力。一次消息成功不等于长期稳定。

寻迹自身状态误报已独立修正并安装为 0.12-connection-status/versionCode13：身份查询前要求 OneBot `online` 与 `good`，在线文案仅报告会话状态和关注数。65 项既有 Java 检查、构建、签名和升级安装通过；这些检查没有证明 Hook 运行成功。


## 用户设备条件补充（2026-10-21）

用户确认只有这台小米15 Pro，没有备用/已Root设备。现阶段不建议把主力机解锁/Root作为黑客松前置条件，不将“只有一台”视为同意替换QQ、丢弃本地数据或重新登录。

已只读检查电脑SDK：有Android Emulator和Medium_Phone_API_37.0虚拟设备，现有镜像为API37.0的Google Play x86_64；尚未发现匹配手机Android16的现成镜像。当前QQ原始APK的原生库仅arm64-v8a。因此“电脑有模拟器”不等于已经具备匹配真机的Hook测试环境，需要另验ARM兼容、系统版本与加载框架。没有启动或修改现有AVD，也没有安装/修改QQ。

进一步研究免Root加载的前提是接受未来可能需要不同于原版签名的QQ安装及重新登录，不能承诺沿用原包数据。已询问是否先在电脑隔离验证免Root路线，或必须保留手机现有QQ与登录而暂不继续Hook。实际修改QQ/Root仍需具体方案和用户确认。


## 电脑隔离实验实际结果（2026-10-21）

用户已同意先在电脑验证免Root Hook，暂不动手机QQ。建立了项目专用AVD，未复用或覆盖原有AVD；没有向手机安装模块或QQ副本。

| 验证项 | 结果 | 证据边界 |
| --- | --- | --- |
| 官方Android16镜像 | 摘要校验通过并冷启动 | API36，x86_64主机，具备arm64翻译库 |
| 基础无Root方法Hook | Android16和Android17预检均通过 | 自建应用实际结果从ORIGINAL变为HOOK_OK，普通应用UID |
| QQ9.3.70原版 | 安装后可启动 | 未登录，停在用户协议/隐私摘要 |
| QQ实验副本模块加载 | 通过 | QQ进程日志MODULE_LOADED、53个消息监听方法可反射 |
| QQ实际方法Hook | 受控实验通过 | TextElement.getContent真实after-hook触发；仅使用模块新建标记对象，随后移除拦截 |
| 真实群消息接收 | 未测试 | 需要用户测试号及测试群；探针尚未订阅消息或接入寻迹 |
| 手机兼容与长期后台稳定 | 未测试 | 桌面模拟器结果不能替代小米真机验证 |

ADB为shell uid2000，QQ探针日志uid10219，实验没有使用adb root或Root框架。源码在tools/rootless-hook-lab；详细过程在docs/superpowers/plans/2026-10-21-rootless-hook-lab.md。本地报告与日志在.codex/hook-lab。用户手机QQ、账号和聊天数据保持现状。
