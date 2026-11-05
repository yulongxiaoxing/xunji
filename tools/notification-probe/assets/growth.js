/* Local, user-confirmed growth archive. Uses the existing native mutation bridge. */
const growthCategories=['志愿','竞赛','证书','项目','学生工作','校园活动','实践','其他成果'];
let growthTab='active',growthCategory='全部',growthQuery='',growthEditingId='',growthSourceId='',growthInitial='';
const growthRecords=()=>data().growth||[];
const growthRegistration=c=>/报名|报考|注册参赛/.test(c.title||'');
const growthEligible=c=>c&&c.status==='done'&&c.category!=='课程'&&!growthRegistration(c);
function growthForSource(id){return growthRecords().find(g=>g.sourceCardId===id)}
function growthCategoryFor(c){const title=c.title||'';if(/志愿/.test(title))return '志愿';if(/项目/.test(title))return '项目';if(/证书|考证/.test(title))return '证书';if(/实践|研学/.test(title))return '实践';if(c.category==='竞赛')return '竞赛';if(c.category==='活动')return '校园活动';return '其他成果'}
function growthCardAction(c){
 if(c.category==='课程'||c.status!=='done')return '';
 const record=growthForSource(c.id);
 if(record)return `<button class="secondary" onclick="showGrowth('${esc(record.id)}')">${record.state==='deleted'?'查看已移除经历':'查看成长记录'}</button>`;
 if(growthRegistration(c))return '<p class="small muted">报名完成不代表实际参与；参与后可在成长中手动补充。</p>';
 return `<button class="secondary" onclick="editGrowth('', '${esc(c.id)}')">建立成长记录</button>`;
}
function suggestGrowth(id){/* AI candidates arrive through the native completion queue. */}
const growthCategoryPaths={
 '志愿':'M12 20S3 14 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6-9 12-9 12Z',
 '竞赛':'M8 3h8v7a4 4 0 0 1-8 0V3ZM8 5H4v3a4 4 0 0 0 4 4M16 5h4v3a4 4 0 0 1-4 4M12 14v6M8 21h8',
 '证书':'M5 3h14v14H5V3ZM8 7h8M8 10h5M9 17v5l3-2 3 2v-5',
 '项目':'M3 7h18v14H3V7ZM8 7V3h8v4M3 12h18M10 12v3h4v-3',
 '学生工作':'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-4M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 '校园活动':'M4 5h16v16H4V5ZM4 10h16M8 3v4M16 3v4M8 14h3M8 17h6',
 '实践':'M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3V6ZM9 3v15M15 6v15',
 '其他成果':'M12 3l3 6 6 1-4 5 1 6-6-3-6 3 1-6-4-5 6-1 3-6Z'
};
function growthHome(){growthCategory='全部';growthTab='active';growthQuery='';go('growth')}
function openGrowthCategory(value){
 const id='growth-category-'+growthCategories.indexOf(value),before=document.getElementById(id)?.getBoundingClientRect().top;
 growthCategory=growthCategory===value?'全部':value;growthTab='active';growthQuery='';renderGrowth();
 const target=document.getElementById(id);if(target&&before!==undefined)window.scrollBy(0,target.getBoundingClientRect().top-before);target?.focus({preventScroll:true});
}
function growthBack(){if(page==='growth'&&growthCategory!=='全部'){openGrowthCategory(growthCategory);return true}return false}
function renderGrowth(){
 document.getElementById('app').innerHTML=`${renderGrowthCandidatesEntry()}<section class="growth-category-grid" aria-label="成长分类"><h1 class="growth-sr-only">成长档案</h1>${growthCategories.map((name,i)=>{
  const count=growthRecords().filter(g=>g.state==='active'&&g.category===name).length,expanded=growthCategory===name;
  return `<section class="growth-category-section growth-tone-${i}${expanded?' is-expanded':''}"><button id="growth-category-${i}" class="growth-category-entry" aria-label="${name}，${count}条经历" aria-expanded="${expanded}" aria-controls="growth-panel-${i}" onclick="openGrowthCategory('${name}')"><span class="growth-category-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="${growthCategoryPaths[name]}"/></svg></span><strong>${name}</strong><span class="growth-entry-count">${count} 条</span><span class="growth-entry-chevron" aria-hidden="true">${expanded?'−':'+'}</span></button>${expanded?`<div id="growth-panel-${i}" class="growth-inline-panel" role="region" aria-label="${name}经历"><div class="growth-join"><span>是否加入新经历？</span><div class="growth-join-actions"><button class="growth-memory-button" onclick="growthMemoryEntry()">口头记忆</button><button class="growth-join-button" onclick="editGrowth()">加入经历</button></div></div><div id="growthList"></div></div>`:''}</section>`;
 }).join('')}</section>`;
 renderMainNav();if(growthCategory!=='全部')renderGrowthList();
}
function renderGrowthList(){
 const list=document.getElementById('growthList');if(!list)return;
 const all=growthRecords().filter(g=>g.category===growthCategory),deleted=all.filter(g=>g.state==='deleted').length;
 const rows=all.filter(g=>g.state===growthTab).sort((a,b)=>(b.date||'').localeCompare(a.date||'')||String(b.createdAt||'').localeCompare(String(a.createdAt||'')));let lastMonth=null;
 const tools=growthTab==='deleted'?`<div class="growth-inline-tools"><span>已移除</span><button class="textbtn" onclick="growthTab='active';renderGrowthList()">全部经历</button></div>`:deleted?`<div class="growth-inline-tools"><span>${rows.length} 条经历</span><button class="textbtn" onclick="growthTab='deleted';renderGrowthList()">已移除</button></div>`:'';
 list.innerHTML=tools+(rows.length?rows.map(g=>{const month=growthMonthLabel(g.date),heading=month!==lastMonth?`<h3 class="growth-month-heading">${esc(month)}</h3>`:'';lastMonth=month;return heading+`<article class="growth-inline-record"><div class="growth-inline-record-heading"><button class="growth-title" aria-label="查看经历：${esc(g.title)}" onclick="showGrowth('${esc(g.id)}')">${esc(g.title)}</button></div>${g.details?.award?`<p class="growth-result">${esc(g.details.award)}</p>`:g.details?.hours?`<p class="growth-result">志愿服务 ${esc(g.details.hours)} 小时</p>`:''}${g.result?`<p class="growth-result">${esc(g.result)}</p>`:''}${g.attachments?.length?`<span class="growth-proof-count">${g.attachments.length} 张证明图片</span>`:''}${g.state==='deleted'?`<button class="secondary" onclick="changeGrowthState('${esc(g.id)}','active')">恢复经历</button>`:''}</article>`}).join(''):`<p class="growth-inline-empty">${growthTab==='deleted'?'暂无已移除经历':'暂无'+esc(growthCategory)+'经历'}</p>`);
}
function growthSourceNote(g){
 if(!g.sourceCardId)return '<p class="small muted">手动记录的经历</p>';
 const c=data().cards.find(c=>c.id===g.sourceCardId),state=c?.status||g.sourceState;
 const warning=state==='done'?'':state==='deleted'?'来源事项已移入回收站；经历仍保留，请核对实际参与情况。':state?'来源事项当前未完成；经历仍保留，请核对实际参与情况。':'来源事项已不可用；经历仍独立保留。';
 return `<p class="small muted">来源事项：${esc(c?.title||g.sourceTitle||'原事项')}</p>${warning?`<p class="notice">${esc(warning)}</p>`:''}`;
}
const growthFormSpecs={
 '竞赛':{title:'竞赛名称',fields:[['award','奖项']],result:'参赛说明'},
 '志愿':{title:'活动名称',fields:[['organizer','服务单位'],['hours','服务时长（小时）']],result:'服务内容与收获'},
 '证书':{title:'证书名称',fields:[['issuer','颁发单位'],['number','证书编号']],result:'备注'},
 '项目':{title:'项目名称',fields:[['role','个人角色']],result:'项目成果'},
 '学生工作':{title:'职务或工作名称',fields:[['organization','组织单位']],result:'工作内容'},
 '校园活动':{title:'活动名称',fields:[['organizer','主办单位'],['role','参与身份']],result:'活动收获'},
 '实践':{title:'实践名称',fields:[['organization','实践单位']],result:'实践内容'},
 '其他成果':{title:'成果名称',fields:[],result:'成果说明'}
};
let growthDraftToken='',growthProofs=[],growthImageLoading=false,growthDetailsDrafts={},growthFieldsCategory='',growthOriginalDate='',growthMonthChanged=false;
function growthMonthLabel(value){const month=(value||'').slice(0,7);return /^\d{4}-\d{2}$/.test(month)?month.slice(0,4)+'年'+Number(month.slice(5))+'月':'时间待补充'}
function growthImageUrl(id,original=false){return 'growth-proof/'+encodeURIComponent(id)+(original?'?full=1':'')}
function growthProofMarkup(images,editable=false){return images.map((img,i)=>`<div class="growth-proof-thumb"><button type="button" aria-label="查看证明图片：${esc(img.name||'证明图片')}" onclick="openGrowthProof('${esc(img.id)}')"><img src="${growthImageUrl(img.id)}" alt="${esc(img.name||'证明图片')}" loading="lazy"></button>${editable?`<button type="button" class="growth-proof-remove" aria-label="移除证明图片：${esc(img.name||'证明图片')}" onclick="removeGrowthProof(${i})">×</button>`:''}</div>`).join('')}
function openGrowthProof(id){
 document.getElementById('growthProofView')?.remove();const view=document.createElement('section');view.id='growthProofView';view.className='growth-proof-view';view.setAttribute('role','dialog');view.setAttribute('aria-label','证明图片预览');view.tabIndex=-1;
 view.innerHTML=`<button type="button" class="growth-proof-close" aria-label="关闭图片预览" onclick="document.getElementById('growthProofView').remove()">关闭</button><img src="${growthImageUrl(id,true)}" alt="证明图片原图">`;
 let backdropDown=false;view.addEventListener('pointerdown',e=>{backdropDown=e.target===view});view.addEventListener('click',e=>{if(e.target===view&&backdropDown){e.stopPropagation();view.remove()}backdropDown=false});
 document.getElementById('modal').append(view);view.focus({preventScroll:true});
}
function showGrowth(id){
 const g=growthRecords().find(g=>g.id===id);if(!g)return toast('经历不存在，请刷新后重试');const spec=growthFormSpecs[g.category]||growthFormSpecs['其他成果'],details=g.details||{};
 modal(`<section id="growthDetail"><span class="tag">${esc(g.category)}</span><h2>${esc(g.title)}</h2><p class="muted">${esc(g.date&&g.date.length===10?g.date:growthMonthLabel(g.date))}</p><dl class="growth-detail-fields">${spec.fields.filter(([key])=>details[key]).map(([key,label])=>`<div><dt>${label}</dt><dd>${esc(details[key])}${key==='hours'?' 小时':''}</dd></div>`).join('')}</dl>${g.result?`<h3>${spec.result}</h3><p class="original">${esc(g.result)}</p>`:''}${g.attachments?.length?`<h3>证明图片</h3><div class="growth-proof-grid">${growthProofMarkup(g.attachments)}</div>`:''}${growthSourceNote(g)}<p id="saveError" class="error" role="alert" hidden></p><div class="toolbar">${g.state==='deleted'?`<button class="primary" onclick="changeGrowthState('${esc(id)}','active')">恢复经历</button>`:`<button class="primary" onclick="editGrowth('${esc(id)}')">编辑经历</button><button class="textbtn" onclick="removeGrowth('${esc(id)}')">移入已移除</button>`}</div></section>`);
}
function growthReadDetails(){const fields={};document.querySelectorAll('#growthSpecificFields [data-growth-field]').forEach(input=>fields[input.dataset.growthField]=input.value.trim());return fields}
function updateGrowthFields(){
 if(growthFieldsCategory)growthDetailsDrafts[growthFieldsCategory]=growthReadDetails();
 const category=document.getElementById('growthCategory').value,spec=growthFormSpecs[category];growthFieldsCategory=category;
 document.getElementById('growthFieldGrid').dataset.fieldCount=String(spec.fields.length);document.getElementById('growthTitleLabel').textContent=spec.title;document.getElementById('growthResultLabel').textContent=spec.result;
 document.getElementById('growthSpecificFields').innerHTML=spec.fields.map(([key,label])=>`<label class="growth-field"><span>${label}</span><input class="field" data-growth-field="${key}" maxlength="200" ${key==='hours'?'type="number" min="0" max="100000" step="0.01" inputmode="decimal"':key==='award'?'placeholder="未知可留空"':''}></label>`).join('');
 const values=growthDetailsDrafts[category]||{};document.querySelectorAll('#growthSpecificFields [data-growth-field]').forEach(input=>input.value=values[input.dataset.growthField]||'');
}
let growthCandidateId='';
function editGrowth(id='',sourceId='',draft=null){
 growthCandidateId=draft?.candidateId||'';
 const g=id?growthRecords().find(g=>g.id===id):draft?{...draft,sourceCardId:sourceId,attachments:[]}:null,c=sourceId?data().cards.find(c=>c.id===sourceId):null;
 if(id&&(!g||g.state!=='active'))return toast('经历不存在或已移除');
 if(sourceId){const old=growthForSource(sourceId);if(old)return showGrowth(old.id);if(!growthEligible(c)&&!(growthCandidateId&&c?.status==='done'&&c.category!=='课程'))return toast('请在实际参与完成后记录；报名不能直接转为经历')}
 growthEditingId=id;growthSourceId=g?.sourceCardId||sourceId;
 const selected=g?.category||(c?growthCategoryFor(c):growthCategory==='全部'?'其他成果':growthCategory);
 modal(`<form id="growthForm"><div class="growth-editor-heading"><h2 id="growthFormHeading" tabindex="-1">${id?'编辑记录':draft?'核对成长经历':'加入经历'}</h2><p>${draft?'已按描述填写，请核对后保存。':'按实际情况填写，证明图片可稍后补充。'}</p></div><label class="growth-field growth-kind"><span>经历类别</span><select class="field" id="growthCategory" aria-label="经历类别">${growthCategories.map(v=>`<option>${v}</option>`).join('')}</select></label><label class="growth-field"><span id="growthTitleLabel">经历名称</span><input class="field" id="growthTitle" maxlength="200" required placeholder="填写名称"></label><div class="growth-field-grid" id="growthFieldGrid"><div id="growthSpecificFields" class="growth-specific-fields"></div><label class="growth-field growth-month-field"><span>时间（年月）</span><input class="field" id="growthDate" type="month" min="0001-01" max="9999-12"></label></div>${g?.date?.length===10?`<p class="growth-field-hint">原记录日期 ${esc(g.date)}；不修改年月时保留原日期。</p>`:''}<label class="growth-field"><span id="growthResultLabel">结果与收获</span><textarea id="growthResult" maxlength="5000" placeholder="可选，填写真实的结果或说明"></textarea></label>${draft?`<details id="growthMemoryOriginal"><summary>${draft.candidateId?'来源事项要求':'口述原文'}</summary><p class="original">${esc(draft.original||'')}</p></details>`:''}<section class="growth-proof-section"><div class="growth-proof-heading"><h3>证明图片 <small>可选</small></h3><button type="button" class="textbtn" id="growthChooseImages" onclick="chooseGrowthImages()">添加证明图片</button></div><p class="growth-field-hint">可添加证明或证书图片，最多5张，每张10MB；不进行OCR识别。</p><div id="growthProofs" class="growth-proof-grid"></div></section>${c?`<p class="growth-field-hint">来源：${esc(c.title)}。请另行核对实际经历年月。</p><label class="growth-consent"><input id="growthConfirmed" type="checkbox">我已实际参与或完成这项经历</label>${growthCandidateId?'<p class="growth-field-hint">报名、截止或考试结束不代表已参赛、获奖或取得证书。请按真实情况修改名称并补充信息。</p>':''}`:''}<p id="saveError" class="error" role="alert" hidden></p><div class="growth-editor-actions"><button type="button" class="secondary" onclick="requestGrowthExit()">取消</button><button type="submit" class="primary" id="growthSave">保存经历</button></div></form>`);
 growthDraftToken='growth-'+Date.now()+'-'+Math.random().toString(36).slice(2);growthProofs=JSON.parse(JSON.stringify(g?.attachments||[]));act('growthDraftBegin',JSON.stringify({token:growthDraftToken,attachments:growthProofs}));growthImageLoading=false;
 growthOriginalDate=g?.date||'';growthMonthChanged=false;growthFieldsCategory='';growthDetailsDrafts={[selected]:{...(g?.details||{})}};
 document.getElementById('growthTitle').value=g?.title||c?.title||'';document.getElementById('growthCategory').value=selected;document.getElementById('growthDate').value=growthOriginalDate.slice(0,7);document.getElementById('growthResult').value=g?.result||'';
 updateGrowthFields();renderGrowthProofs();document.getElementById('growthCategory').onchange=updateGrowthFields;document.getElementById('growthDate').oninput=()=>growthMonthChanged=true;
 growthInitial=draft?'':JSON.stringify(growthValue());document.getElementById('growthForm').onsubmit=e=>{e.preventDefault();saveGrowth()};document.querySelector('#modal .close').onclick=()=>requestGrowthExit();document.getElementById('growthFormHeading').focus({preventScroll:true});document.getElementById('modal').scrollTop=0;
}
function growthValue(){const month=document.getElementById('growthDate').value;return {id:growthEditingId,sourceCardId:growthSourceId,title:document.getElementById('growthTitle').value.trim(),category:document.getElementById('growthCategory').value,date:!growthMonthChanged&&month===growthOriginalDate.slice(0,7)?growthOriginalDate:month,result:document.getElementById('growthResult').value.trim(),details:growthReadDetails(),attachments:JSON.parse(JSON.stringify(growthProofs)),draftToken:growthDraftToken,candidateId:growthCandidateId,participationConfirmed:!!document.getElementById('growthConfirmed')?.checked}}
function renderGrowthProofs(){const list=document.getElementById('growthProofs');if(!list)return;list.innerHTML=growthProofMarkup(growthProofs,true);const button=document.getElementById('growthChooseImages');button.disabled=growthImageLoading||growthProofs.length>=5;button.textContent=growthImageLoading?'正在读取图片…':'添加证明图片';document.getElementById('growthSave').disabled=growthImageLoading}
function chooseGrowthImages(){if(growthImageLoading||growthProofs.length>=5)return;growthImageLoading=true;renderGrowthProofs();act('growthImages',JSON.stringify({token:growthDraftToken,remaining:5-growthProofs.length}))}
function growthImagesReady(result){if(!document.getElementById('growthForm')||result.token!==growthDraftToken)return;growthImageLoading=false;if(!result.ok){showSaveError(result.error||'图片读取失败，请重试')}else{for(const img of result.images||[])if(growthProofs.length<5&&!growthProofs.some(g=>g.id===img.id))growthProofs.push(img)}renderGrowthProofs()}
window.growthImagesReady=growthImagesReady;
function removeGrowthProof(index){growthProofs.splice(index,1);renderGrowthProofs()}
function cancelGrowthDraft(){if(growthDraftToken)act('growthDraftCancel',growthDraftToken);growthDraftToken='';growthImageLoading=false}
function saveGrowth(){
 const value=growthValue();if(!value.title){showSaveError('请填写名称');return}if(growthImageLoading){showSaveError('图片正在读取，请稍候');return}
 if(!value.id&&value.sourceCardId&&!value.participationConfirmed){showSaveError('请确认已实际参与或完成这项经历');return}
 if(mutate('saveGrowth',value)){growthDraftToken='';document.getElementById('modal').close();growthTab='active';growthCategory=value.category;growthQuery='';if(page==='growth'){renderGrowth()}else go('growth');document.getElementById('growth-panel-'+growthCategories.indexOf(value.category))?.scrollIntoView({block:'nearest'});toast(value.id?'经历已更新':'成长经历已记录')}
}
function requestGrowthExit(after){
 const dialog=document.getElementById('modal');if(!document.getElementById('growthForm')||JSON.stringify(growthValue())===growthInitial){cancelGrowthDraft();dialog.close();if(after)after();return}
 document.getElementById('growthDiscard')?.remove();const prompt=document.createElement('section');prompt.id='growthDiscard';prompt.className='discard-prompt';prompt.setAttribute('role','alert');
 prompt.innerHTML='<h3>放弃尚未保存的经历？</h3><p class="small">当前修改尚未保存。</p><div class="toolbar"><button type="button" class="secondary" id="growthKeep">继续编辑</button><button type="button" class="danger" id="growthDiscardConfirm">放弃修改</button></div>';
 document.getElementById('growthForm').prepend(prompt);document.getElementById('growthKeep').onclick=()=>{prompt.remove();document.getElementById('growthTitle').focus()};document.getElementById('growthDiscardConfirm').onclick=()=>{cancelGrowthDraft();dialog.close();if(after)after()};prompt.scrollIntoView({block:'nearest'});document.getElementById('growthKeep').focus();
}
function removeGrowth(id){const g=growthRecords().find(g=>g.id===id);if(!g)return;modal(`<h2>移除这条经历？</h2><p>${esc(g.title)}</p><p class="small muted">移除后可以在成长的“已移除”中恢复，来源事项会保留。</p><p id="saveError" class="error" role="alert" hidden></p><div class="toolbar"><button class="secondary" onclick="showGrowth('${esc(id)}')">取消</button><button class="danger" onclick="changeGrowthState('${esc(id)}','deleted')">确认移除</button></div>`)}
function changeGrowthState(id,state){if(mutate('growthState',{id,state})){document.getElementById('modal').close();render();toast(state==='active'?'经历已恢复':'经历已移除，可以恢复')}}
document.getElementById('modal').addEventListener('cancel',e=>{if(document.getElementById('growthProofView')){e.preventDefault();e.stopImmediatePropagation();document.getElementById('growthProofView').remove();return}if(document.getElementById('growthForm')){e.preventDefault();requestGrowthExit()}});
