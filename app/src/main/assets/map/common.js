'use strict';
const koreaBounds=L.latLngBounds([32.5,124],[39,132.5]);
function insideKorea(p){return koreaBounds.contains(p);}
const map=L.map('map',{attributionControl:true,minZoom:6,maxBounds:koreaBounds.pad(0.08),maxBoundsViscosity:1}).setView([36.1,127.8],7);
const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
 maxZoom:19,minZoom:6,noWrap:true,bounds:koreaBounds,
 attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
});
let mapFailure='';
let tileTimer;
function showMapStatus(){
 const visible=Object.values(tiles._tiles || {});
 const good=visible.filter(t=>t.loaded && t.el.naturalWidth>0).length;
 const bad=visible.filter(t=>t.el.dataset.failed==='true').length;
 const status=document.getElementById('status');
 status.textContent=good>0?(bad?'일부 배경 지도가 표시되지 않았습니다. 다시 불러올 수 있습니다.':'한국 지도 · 확대하거나 이동해 위치를 확인하세요.'):
  (mapFailure || '배경 지도를 불러오지 못했습니다. 다시 불러오기를 눌러 주세요.');
 status.classList.toggle('error',good===0 || bad>0);
}
window.mapLoadError=message=>{mapFailure=message;showMapStatus();};
tiles.on('loading',()=>{
 clearTimeout(tileTimer);document.getElementById('status').textContent='한국 지도를 불러오는 중…';
 tileTimer=setTimeout(showMapStatus,15000);
});
tiles.on('tileerror',e=>{e.tile.dataset.failed='true';showMapStatus();});
tiles.on('tileload',e=>{delete e.tile.dataset.failed;showMapStatus();});
tiles.on('load',()=>{clearTimeout(tileTimer);showMapStatus();});
tiles.addTo(map);
document.getElementById('retry').onclick=()=>{mapFailure='';tiles.redraw();};
window.addEventListener('online',()=>{mapFailure='';tiles.redraw();});
new ResizeObserver(()=>map.invalidateSize()).observe(document.getElementById('map'));
