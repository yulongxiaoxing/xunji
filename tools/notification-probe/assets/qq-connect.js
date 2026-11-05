/* QQ setup and synchronization UI. Transport and credentials stay in native code. */
let qqGuideOpen=false;
function qqTime(seconds){const value=Number(seconds);if(!Number.isFinite(value)||value<=0||value>253402300799)return '尚未开始';return new Date(value*1000).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function qqCopyGuide(kind){const commands={start:'py -3 tools/napcat-desktop/desktop.py start',status:'py -3 tools/napcat-desktop/desktop.py status',link:'py -3 tools/napcat-desktop/desktop.py link',devices:'adb devices -l'};if(commands[kind])act('qqCopyGuide',commands[kind]);}
function qqProgressHtml(){
 const n=real.napcat||{},rows=n.sync||[];
 if(!rows.length)return '<p class="small muted">还没有关注群。连接成功后，选择需要同步的群；从关注时刻开始记录。</p>';
 return rows.map(s=>`<article class="qq-group-progress"><div class="qq-group-heading"><strong>${esc(s.groupName||s.groupId)}</strong><span class="tag">${!n.enabled?'已暂停':({synced:'已检查',checking:'检查中',continuing:'分批补取',error:'待重试',waiting:'待连接'}[s.phase]||'待检查')}</span></div><p class="small">上次原文检查到：<strong>${esc(qqTime(s.checkedThrough))}</strong></p>${s.phase!=='synced'||s.resuming?`<p class="small muted">待补区间：${esc(qqTime(s.checkedThrough||s.since))} → ${esc(qqTime(s.batchEnd||Math.floor(Date.now()/1000)))}</p>`:''}<p class="small ${s.phase==='error'?'error':'muted'}">${esc(!n.enabled?'进度已保留，重新连接后继续。'+(s.detail||''):s.detail||'等待连接后检查历史')}</p></article>`).join('');
}
function qqAiHtml(){const q=real.qqExtraction||{};return `<p class="small">AI 已整理 ${Number(q.ready)||0} · 待处理 ${(Number(q.pending)||0)+(Number(q.processing)||0)} · 失败 ${Number(q.error)||0}</p><p class="small muted">原文先保存在手机，整理失败不影响已保存原文。${real.hasKey?'联网后继续整理。':'填写下方 DeepSeek 密钥后才能自动整理。'}</p><div class="toolbar"><button class="textbtn" onclick="go('pending')">查看待处理消息</button>${Number(q.error)>0?'<button class="textbtn" onclick="act(\'retryPending\')">重试失败消息</button>':''}</div>`;}
function renderQqConnection(){const n=real.napcat||{};return `
<section class="card qq-connect" id="qqConnection" aria-labelledby="qqConnectionTitle">
 <div class="qq-group-heading"><h2 id="qqConnectionTitle">QQ 接入</h2><span class="tag" id="qqEnabledLabel">${n.enabled?'接收已开启':'接收已暂停'}</span></div>
 <p class="small muted">电脑读取关注群，手机保存原文并整理。电脑开机、启动接入并连好 USB 后，自动接着补取。</p>
 <ol class="qq-route" aria-label="消息传输路径"><li>电脑 NapCat</li><li>USB 连接</li><li>手机保存原文</li><li>AI 整理 → 收件箱</li></ol>
 <p id="qqConnectionStatus" class="notice small" role="status">${esc(n.status||'请先按下方教程启动电脑接入端')}</p>
 <label class="formlabel" for="napcatKey" id="qqTokenLabel">电脑连接口令 · ${n.hasToken?'已保存（留空可复用）':'未配置'}</label>
 <input class="field" id="napcatKey" type="password" autocomplete="off" aria-describedby="qqTokenHelp" placeholder="填写电脑端 onebot 口令">
 <p id="qqTokenHelp" class="small muted">连接口令用于手机连接电脑；与 DeepSeek 密钥不同，不要填 webui 口令。</p>
 <div class="toolbar qq-actions"><button class="primary" onclick="connectNapCat()">保存并连接</button><button class="secondary" onclick="act('napcatSync')">立即同步</button><button class="textbtn" onclick="act('napcatStop')">暂停接收</button></div>
 <div class="toolbar"><button class="secondary" onclick="go('groups')">选择关注群</button></div>
 <section class="qq-progress-section" aria-labelledby="qqProgressTitle"><h3 id="qqProgressTitle">原文同步进度</h3><div id="qqSyncProgress">${qqProgressHtml()}</div><p class="small muted">每个群独立记住位置。断线或历史未接上时保留进度，不把“在线”当成“已补齐”；QQ 无法返回的历史不保证找回。仅整理文字，图片和附件暂不处理。</p></section>
 <section class="qq-progress-section" aria-labelledby="qqAiTitle"><h3 id="qqAiTitle">AI 整理进度</h3><div id="qqAiProgress">${qqAiHtml()}</div></section>
 <details class="qq-guide" id="qqConnectionGuide" ${qqGuideOpen?'open':''} ontoggle="qqGuideOpen=this.open"><summary>连接教程：从电脑到手机</summary>
 <p class="small muted">首次配置按顺序完成。以后重新开机，启动电脑接入、连接 USB 即可；已关注群会按保存位置补取，不用重选日期。</p>
 <ol class="qq-guide-steps">
  <li><h3>准备电脑环境</h3><p>使用 Windows 电脑，准备 Python 3、Android SDK Platform-Tools（含 adb）和本项目源码目录。交付压缩包解压后进入 source 文件夹操作。</p><p>下载已验证的 NapCat v4.18.41 的 NapCat.Shell.Windows.Node.zip，解压到：</p><code>.codex/napcat-desktop/runtime/</code><p>里面应直接包含 node.exe、index.js 和 napcat 文件夹。</p><button class="textbtn" onclick="act('qqNapCatDownload')">打开 NapCat 官方下载页</button></li>
  <li><h3>连接手机</h3><p>在手机开发者选项开启 USB 调试，使用可传数据的 USB 线连接电脑，并在手机上确认允许这台电脑调试。</p><code>adb devices -l</code><p>设备状态应为 device。unauthorized 表示需要在手机确认授权；没有设备时先检查线材、USB 模式和驱动。</p><button class="textbtn" onclick="qqCopyGuide('devices')">复制设备检查命令</button></li>
  <li><h3>启动电脑接入</h3><p>从电脑系统托盘完全退出普通电脑版 QQ，手机 QQ 保持登录。在项目目录双击：</p><code>tools/napcat-desktop/start-demo.bat</code><p>也可以在项目目录的终端运行：</p><code>py -3 tools/napcat-desktop/desktop.py start</code><button class="textbtn" onclick="qqCopyGuide('start')">复制启动命令</button><p>这是独立接入环境，不会修改手机 QQ，也不会代你发消息。</p></li>
  <li><h3>本人扫码登录 QQ</h3><p>如登录失效，在电脑打开下方图片，用手机 QQ 扫码并确认：</p><code>.codex/napcat-desktop/runtime/napcat/cache/qrcode.png</code><p>扫码后检查电脑状态；QQ online: True 只说明账号在线，实际是否同步成功还要看软件中的原文进度。</p><code>py -3 tools/napcat-desktop/desktop.py status</code><button class="textbtn" onclick="qqCopyGuide('status')">复制状态检查命令</button></li>
  <li><h3>填写手机连接口令</h3><p>在自己的电脑上打开下方文件，只取 onebot 字段的值：</p><code>.codex/napcat-desktop/local-secrets.json</code><p>不要复制整个 JSON，不要取 webui 字段，也不要把文件或口令发给别人。将 onebot 值填进本页“电脑连接口令”，点“保存并连接”。已保存时可以留空重连。</p></li>
  <li><h3>选群，检查整理结果</h3><p>点“选择关注群”，搜索群名或群号并关注。首次从关注时刻开始，之前的消息不自动导入；已关注群的原起点保留。</p><p>回到本页看各群原文同步进度。填写 DeepSeek 密钥后，文字自动进入整理队列；到收件箱核对后再决定是否加入待办。</p></li>
  <li><h3>下次开机自动补取</h3><p>电脑关机或 USB 断开时无法继续读取。下次电脑开机、运行启动脚本并连好手机后，会自动补取上次保存位置到本次检查之间的消息，重复读取不会重复建卡。</p><p>仅重连 USB 时，也可运行下方命令恢复转接，再点“立即同步”：</p><code>py -3 tools/napcat-desktop/desktop.py link</code><button class="textbtn" onclick="qqCopyGuide('link')">复制重连命令</button><p>历史未返回或翻页停滞时显示待重试，不把检查进度直接跳到当前时间。此版使用 USB，手机移动数据只能用于 AI 联网，不能代替电脑连接。</p></li>
 </ol>
 <h3>连不上时检查</h3><ul class="qq-troubleshooting"><li>未连接电脑：检查 USB 授权并重新运行启动脚本。</li><li>口令不匹配：核对 onebot 字段，重新填写并连接。</li><li>QQ 离线：在电脑重新扫码，确认普通电脑版 QQ 已退出。</li><li>历史待重试：保留现有关注与进度，连接恢复后重试；不要反复取消关注。</li><li>原文已有、卡片未出现：检查 DeepSeek 密钥、手机网络和待处理消息。</li></ul>
 <button class="textbtn" onclick="act('diagnostics')">打开诊断工具</button>
 </details>
</section>`;}
function updateQqConnection(){const n=real.napcat||{};const status=document.getElementById('qqConnectionStatus');if(!status)return;status.textContent=n.status||'等待连接电脑 QQ 接入端';document.getElementById('qqEnabledLabel').textContent=n.enabled?'接收已开启':'接收已暂停';document.getElementById('qqTokenLabel').textContent='电脑连接口令 · '+(n.hasToken?'已保存（留空可复用）':'未配置');document.getElementById('qqSyncProgress').innerHTML=qqProgressHtml();document.getElementById('qqAiProgress').innerHTML=qqAiHtml();const label=document.querySelector('label[for="key"]');if(label)label.textContent='API 密钥 · '+(real.hasKey?'已配置':'未配置');}
