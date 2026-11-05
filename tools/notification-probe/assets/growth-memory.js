/* Dictated experiences have their own request lifecycle; no todo mutations. */
let growthMemoryCategory='',growthMemoryToken='',growthMemorySequence=0;
function cancelGrowthMemory(){growthMemoryToken='';growthMemorySequence++;act('growthMemoryCancel')}
function growthMemoryEntry(category=growthCategory){
 if(!growthCategories.includes(category))return toast('请先展开一个成长分类');
 if(document.getElementById('modal').open&&document.getElementById('growthForm'))return requestGrowthExit(()=>growthMemoryEntry(category));
 if(document.getElementById('modal').open&&document.getElementById('growthMemoryForm'))return requestGrowthMemoryExit(()=>growthMemoryEntry(category));
 cancelGrowthMemory();growthMemoryCategory=category;
 modal(`<section id="growthMemoryForm"><h2 id="growthMemoryHeading" tabindex="-1">${esc(category)} · 口头记忆</h2><p class="small muted">描述一条已经发生的经历，也可以用输入法语音输入。整理后先核对，再保存。</p><label class="formlabel" for="growthMemoryBody">经历描述</label><textarea id="growthMemoryBody" maxlength="5000" placeholder="说说你参加了什么、何时发生，以及已知的结果…"></textarea><p id="growthMemoryError" class="error" role="alert" hidden></p><div class="toolbar"><button class="secondary" onclick="requestGrowthMemoryExit()">取消</button><button class="primary" id="growthMemoryRead" onclick="readGrowthMemory()">DeepSeek整理</button></div><p class="small muted">点击整理才发送文字；图片留在本地。未提到的年月、奖项和时长留空。</p></section>`);
 document.getElementById('growthMemoryBody').oninput=()=>{cancelGrowthMemory();const button=document.getElementById('growthMemoryRead');button.disabled=false;button.textContent='DeepSeek整理';document.getElementById('growthMemoryError').hidden=true;document.getElementById('growthMemoryDiscard')?.remove()};
 document.querySelector('#modal .close').onclick=()=>requestGrowthMemoryExit();document.getElementById('growthMemoryHeading').focus({preventScroll:true});
}
function readGrowthMemory(){
 const text=document.getElementById('growthMemoryBody').value.trim(),error=document.getElementById('growthMemoryError');
 if(!text||text.length>5000){error.textContent='请填写1至5000字的经历描述';error.hidden=false;return}
 if(!native){error.textContent='请在手机上整理经历；输入内容仍保留';error.hidden=false;return}
 cancelGrowthMemory();growthMemoryToken='growth-memory-'+(++growthMemorySequence)+'-'+Date.now();
 const button=document.getElementById('growthMemoryRead');button.disabled=true;button.textContent='正在整理…';error.hidden=true;
 act('growthMemoryPreview',JSON.stringify({text,category:growthMemoryCategory,requestToken:growthMemoryToken}));
}
function growthMemoryReady(result){
 if(!document.getElementById('modal').open||!document.getElementById('growthMemoryForm')||!growthMemoryToken||result.requestToken!==growthMemoryToken||result.category!==growthMemoryCategory)return;
 const button=document.getElementById('growthMemoryRead'),error=document.getElementById('growthMemoryError');button.disabled=false;button.textContent='DeepSeek整理';
 if(!result.ok||!result.record||typeof result.record.title!=='string'||!result.record.title.trim()){
  growthMemoryToken='';error.hidden=false;error.textContent=result.error||'整理结果不完整，请重试；输入内容仍保留';return;
 }
 const original=document.getElementById('growthMemoryBody').value.trim(),category=growthMemoryCategory;
 cancelGrowthMemory();editGrowth('','',{...result.record,category,original});
}
function requestGrowthMemoryExit(after){
 const dialog=document.getElementById('modal'),input=document.getElementById('growthMemoryBody');cancelGrowthMemory();
 if(!input||!input.value.trim()){dialog.close();if(after)after();return}
 const read=document.getElementById('growthMemoryRead');read.disabled=false;read.textContent='DeepSeek整理';
 document.getElementById('growthMemoryDiscard')?.remove();const prompt=document.createElement('section');prompt.id='growthMemoryDiscard';prompt.className='discard-prompt';prompt.setAttribute('role','alert');
 prompt.innerHTML='<h3>放弃尚未保存的经历描述？</h3><div class="toolbar"><button type="button" class="secondary" id="growthMemoryKeep">继续编辑</button><button type="button" class="danger" id="growthMemoryDiscardConfirm">放弃修改</button></div>';
 document.getElementById('growthMemoryForm').prepend(prompt);document.getElementById('growthMemoryKeep').onclick=()=>{prompt.remove();input.focus()};document.getElementById('growthMemoryDiscardConfirm').onclick=()=>{dialog.close();if(after)after()};prompt.scrollIntoView({block:'nearest'});document.getElementById('growthMemoryKeep').focus();
}
window.growthMemoryReady=growthMemoryReady;
document.getElementById('modal').addEventListener('cancel',e=>{if(document.getElementById('growthMemoryForm')){e.preventDefault();requestGrowthMemoryExit()}});
document.getElementById('modal').addEventListener('close',()=>{if(!document.getElementById('modal').open&&document.getElementById('growthMemoryForm'))cancelGrowthMemory()});
