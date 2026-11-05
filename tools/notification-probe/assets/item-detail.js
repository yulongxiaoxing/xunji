function renderItemDetail(c,actions,source,siblings){
 const interval=c.category==='课程'?c.course:c.event;
 const label=interval?(c.category==='课程'?'上课时间':'安排时间'):c.dueDate?'截止时间':c.deadline?'原文时间':'时间待确认';
 const range=interval?`${interval.startTime}—${interval.endTime}`:(c.dueTime||'未注明时刻');
 const time=c.dueDate?`<strong>${esc(c.dueDate)}</strong><span>${esc(range)}</span>`:interval?`<strong>${esc(range)}</strong><span>日期待确认</span>`:`<strong>${esc(c.deadline||'尚未设置')}</strong>${c.deadline?'<span>具体日期待确认</span>':''}`;
 const facts=interval?`${interval.location?`<div><dt>地点</dt><dd>${esc(interval.location)}</dd></div>`:''}${c.category==='课程'&&interval.teacher?`<div><dt>教师</dt><dd>${esc(interval.teacher)}</dd></div>`:''}`:'';
 return `<article class="card item-detail"><div class="item-detail-heading"><span>事项详情</span><span class="tag ${esc(c.category)}">${esc(c.category)}</span></div><h2 class="item-detail-title">${esc(c.title)}</h2>
 <section class="item-detail-time" aria-label="${esc(label)}"><span class="item-detail-caption">${esc(label)}</span><div>${time}</div>${c.scheduleOverride?'<small>已手动调整</small>':''}</section>
 ${facts?`<dl class="item-detail-facts">${facts}</dl>`:''}
 ${c.requirements?.length?`<section class="item-detail-requirements"><h3>具体要求</h3><ul class="requirements">${c.requirements.map(v=>`<li>${esc(v)}</li>`).join('')}</ul></section>`:''}
 ${c.status==='done'&&c.autoCompleted?`<p class="small muted">${c.category==='课程'?'已下课':'已结束'}，保留在日程中。</p>`:''}<div class="actions item-detail-actions">${actions}</div>
 <footer class="item-detail-source"><p>${esc(c.group||'来源未注明')}${c.publisher?' · '+esc(c.publisher):''}</p>${siblings>1?`<p class="small muted">同一条原文拆分为 ${siblings} 个事项</p>`:''}<div class="card-tools"><button class="textbtn" ${source?'':'disabled'} onclick="original('${esc(c.sourceId)}')">查看原文 ↗</button>${c.status!=='deleted'?`<button class="textbtn" aria-label="更多操作" onclick="cardMenu('${esc(c.id)}')">更多</button>`:''}</div></footer></article>`;
}
