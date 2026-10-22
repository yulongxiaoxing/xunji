# 寻迹 QQ 后台完整消息接入调研

调研日期：2026年10月20日。只读检索仓库、项目文档和安装脚本，未下载运行第三方安装器、未修改手机QQ、未进行Root操作。项目文档的支持声明不等于已经在小米15 Pro和Android16实测通过。

## 优先验证 Termux 和 NapCat

NapCat自己的Shell文档现已列出Termux部署：在proot-distro Debian环境安装NapCat及Linux QQ，随后启动QQ会话。Termux本身不要求手机Root；这是在手机里运行独立客户端接收事件，不是让寻迹突破Android沙箱直接读取原版QQ内部存储。手机充当接入服务的运行机器，因此无需额外电脑或公网服务器常开；手机环境必须联网并常驻。

- 仓库：https://github.com/NapNeko/NapCatQQ
- Termux部署文档：https://napneko.github.io/guide/boot/Shell#termux
- 安装器源码（已审阅，并在本轮授权试验中执行）：https://github.com/NapNeko/NapCat-Installer/blob/main/script/install.termux.sh
- 群列表与历史消息API：https://napneko.github.io/onebot/api
- 消息事件字段：https://napneko.github.io/develop/plugin/api/doc
- Termux后台限制说明：https://github.com/termux/termux-app

NapCat消息结构提供group_id、message_id、sender、message和raw_message，另有get_group_list和get_group_msg_history。可据此在寻迹中展示账号已加入群的列表，按群号白名单筛选，优于仅靠群名。建议只让白名单群的正文进入寻迹数据库和DeepSeek；底层QQ会话仍会收到其账号可见的其他事件，应用过滤不等于服务器只向该会话发送白名单群。

这一方向预期绕开本次通知摘要148字符的来源限制；仍需验证长消息、合并转发、离线补齐和丢消息情况，不能承诺所有消息无损。原版手机QQ前台运行不再是接收条件，但两个QQ会话能否稳定共存必须实测。Android后台进程管理、耗电、下载资源与账号登录限制是实施约束；使用非官方接入存在登录验证、限制或封禁风险，不承诺主账号安全。

## 可参考的安卓封装项目

- https://github.com/MuFengDR/AstrBot-Bubble-Android-App
- https://github.com/linger-su/astrbot-termux

它们把NapCat和AstrBot相关环境封装为安卓入口，文档描述本机运行、扫码登录和后台常驻。仓库规模较小，部分README有AI生成说明，不以README宣传作为兼容性或长期可靠性证据。寻迹只需消息接入与本地桥接，不必先引入完整聊天机器人功能，更不应默认自动向群里回复。优先使用NapCat自身文档验证链路，再决定是否借鉴封装代码；未审计或安装这些APK。

## Hook 备选

- https://github.com/ion-aluminium/OpenShamrock-QQ-9.2.95-adapt

该分支README声明在Redmi K50 Pro、Magisk／LSPosed、QQ9.2.95上验证真实群消息到OneBot反向WebSocket。与当前已知QQ9.3.70版本不同，未找到对小米15 Pro／Android16的明确保证。原whitechi73/OpenShamrock上游本次无法访问，因此不推荐为10月24日前的首选。免Root注入方案也可能需要修改QQ APK，不等于普通系统权限。

## 官方机器人作为另一条路线

https://github.com/tencent-connect/qqbot-nodejs 和 https://github.com/tencent-connect/bot-docs 提供官方接入。机器人须获得对应权限并加入目标群；用户已经加入某群不自动等于机器人有读取权。平台权限与群主能否配合尚未知，不把所有普通群消息开放性当作既成事实，也不沿用旧资料断言官方永远只能接收@消息。

## 最小验收再接入寻迹

先在手机端独立接入，关闭自动群回复，只做只读验证：指定测试群长通知包含末尾截止时间和提交说明；手机QQ在前台仍能接收；锁屏后仍能接收；未关注群和私聊不进入寻迹数据库及云端；检查是否被踢下线。通过后再把OneBot消息适配到现有原文入库、自动提取、卡片及待处理流程。当前结论是有公开实现依据、值得优先试验，不是已经实现。


## 2026-10-21：稳定性反馈与路线边界

用户反馈NapCat反复登录仍未收到消息，明确提出希望沿用手机已登录QQ。此前get_login_info/get_group_list可用和应用显示已连接不能视为消息链路通过；WebUI一度返回reconnecting、coreReady=false、SelfOffline，而OneBot在线状态也有变化，尚未定位失联的最终原因。没有完成真实长消息、QQ前台、锁屏或排除未关注群的验收。

当前实机QQ9.3.70（versionCode16410）；adb shell PATH未找到su，仅作为当前权限线索，不据此断言设备绝对没有Root。Android应用沙箱使普通寻迹进程不能直接读取QQ私有登录凭证和消息库。NapCat是独立Linux QQ会话，不能把一次QQ授权登录误称为共享手机QQ原有会话。

直接利用现有QQ进程接收消息需要评估Hook/注入方案。OpenShamrock的9.2.95适配分支明确只在Redmi K50 Pro、Magisk/LSPosed、QQ9.2.95验证，没有当前手机Android16/QQ9.3.70稳定支持证据。QAuxiliary本身是Xposed模块，不等于已有群消息OneBot桥；Shizuku提供ADB或Root级系统API桥，也不自动提供QQ消息接口。

保留官方QQ且不注入时，可以试验打开群聊时的可见文字采集，或沿用复制全文/分享导入。无障碍窗口内容能力不等于后台消息订阅，不能承诺不打开群聊也完整接收。新路线等待用户在保留官方QQ的前台/手动导入与进一步研究Hook之间选择；未Root、未修改/降级QQ、未安装新模块。

来源：https://source.android.com/docs/security/app-sandbox ；https://developer.android.com/guide/topics/ui/accessibility/views/service ；https://github.com/ion-aluminium/OpenShamrock-QQ-9.2.95-adapt ；https://github.com/cinit/QAuxiliary ；https://github.com/RikkaApps/Shizuku 。

应用状态修正：调用get_status并同时要求online/good为true后才继续查询身份和群；离线明确提示未恢复消息接收，在线文案改为本机QQ在线及关注数，避免把接口响应误称为已经收到消息。仍以实际新消息入库验证接收。


后续已按用户授权完成手机QQ9.3.70原始APK只读核查，详细结果见 [Hook可行性核查](qq-hook-feasibility.md)。消息监听53个方法与参考分支描述符一致，群监听新增3个方法；Bootloader锁定。未修改QQ或Root，后续真机加载待设备条件和具体操作确认。
