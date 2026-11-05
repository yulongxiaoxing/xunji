/* Treat backdrop taps like Escape so every existing form/cancel guard still runs. */
(()=>{const dialog=document.getElementById('modal');let outsideDown=false;
 const outside=e=>{const r=dialog.getBoundingClientRect();return e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom;};
 dialog.addEventListener('pointerdown',e=>{outsideDown=e.isPrimary!==false&&outside(e)});
 dialog.addEventListener('pointercancel',()=>outsideDown=false);
 dialog.addEventListener('close',()=>outsideDown=false);
 dialog.addEventListener('click',e=>{const dismiss=outsideDown&&outside(e);outsideDown=false;if(!dialog.open||!dismiss)return;e.preventDefault();e.stopImmediatePropagation();const cancel=new Event('cancel',{cancelable:true});if(dialog.dispatchEvent(cancel)&&dialog.open)dialog.close();});
})();
