# 寻迹黑客松演示与 Claude Code 录制手册

建议制作约 100 秒的竖屏产品视频。使用「寻迹演示版」的真实 Android 界面取景，演示数据全为虚构。展示通知整理后的卡片核对、时间安排和成长档案。真实 QQ 收信与实时 DeepSeek 调用需要另行配置，本片的离线路线从预置提取结果开始。

## 录制准备

1. 下载 [演示版 APK](https://github.com/yulongxiaoxing/xunji/releases/download/v0.58/Xunji-v0.58-Hackathon.apk)，安装到专用 Android 模拟器或手机。最低 Android 9。录制前将模拟器时区设为中国标准时间（Asia/Shanghai）。
2. 首启后，从「信息与待办 → 待确认」核对三张样例卡。今日应出现明天的「视觉设计基础」与「设计社作品复盘」。
3. 录制前确认设备日期，以「视觉设计基础」显示的日期为准。首次启动会按北京时间排期；跨天后样例日期保持原样。
4. 同一镜头需要重录时，可在专用模拟器上执行下方重置命令，恢复演示版初始状态，再按相同顺序操作。

```powershell
adb -s SERIAL install -r Xunji-v0.58-Hackathon.apk
adb -s SERIAL shell am start -n cn.xunji.probe.demo/cn.xunji.probe.DemoActivity

# 仅在需要重拍时清空专用模拟器中的演示版数据。
adb -s SERIAL shell pm clear cn.xunji.probe.demo
adb -s SERIAL shell am start -n cn.xunji.probe.demo/cn.xunji.probe.DemoActivity
```

## 录制分镜

| 时长 | 屏幕操作 | 旁白参考 |
| --- | --- | --- |
| 0–12 秒 | 从今日切入信息与待办，露出待确认卡片 | 「课程、作业和活动通知散在各个群里。寻迹把它们集中到可以核对的收件箱。」 |
| 12–35 秒 | 找到「校园摄影工作坊」，打开原文，停留在时间与签到要求，返回点「加入待办」 | 「这是演示数据里已经整理好的通知。原文和事项同时保留，核对地点、起止时间和要求后，再决定是否排进日程。」 |
| 35–60 秒 | 打开今日和周日程，选工作坊当天，滚动到 10:30，点重合区分别查看课程和工作坊，然后切到月表 | 「视觉设计基础 10:10 到 11:50，工作坊 10:30 到 12:00。重合时段可以分别打开，冲突一眼能看出来。」 |
| 60–88 秒 | 打开「成长 → 校园活动 → 加入经历」，以「设计社作品复盘」为例填写虚构参与经历，保存并展开查看 | 「活动之后，可以把实际参加过的经历记进成长档案，留下角色、收获和证明。这里用虚构内容演示手动记录。」 |
| 88–100 秒 | 切回今日或展示 GitHub 发布页 | 「寻迹把群里的通知整理成可以安排的下一步，也让值得留下的经历有地方可查。」 |

## 可直接交给 Claude Code 的任务

> 读取仓库的 README 与 docs/VIDEO.md。用 Android 模拟器安装寻迹黑客松演示版，按录制分镜输出约 100 秒的中文竖屏产品视频。每段先录真实应用操作，再剪辑、配音和加字幕。画面里的日期取自模拟器中的样例日程。在收件箱镜头上加字幕「预置演示数据」，不要把它配成 QQ 实时来信或 DeepSeek 实时生成。成长段落使用手动保存的虚构经历。发布页地址为 https://github.com/yulongxiaoxing/xunji/releases/tag/v0.58。保留录屏原文件、字幕与成片，并放在本地输出目录。

可以使用模拟器自带录屏，也可以按镜头执行 `adb -s SERIAL shell screenrecord /sdcard/xunji-scene.mp4`，停止后用 `adb -s SERIAL pull /sdcard/xunji-scene.mp4` 导出。每次使用不同的镜头文件名。
