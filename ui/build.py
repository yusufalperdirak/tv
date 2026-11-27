import re,sys
SP='/tmp/claude-0/-home-claude/16800ff8-2564-5f0a-93aa-3811c48f3751/scratchpad/'
src=open(SP+'cid3/index.html').read()
css=src[src.index('<style>')+7:src.index('</style>')]
css=css.replace("--f:'Roboto Condensed','Arial Narrow',sans-serif","--f:'Roboto Condensed','sans-serif-condensed','Arial Narrow',sans-serif")
css+=r'''
/* uygulama eklemeleri */
html,body{background:#000}
*{-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent}
button{touch-action:manipulation}
.lrow{width:100%;text-align:left;color:var(--y)}
.prog{left:262px;width:500px;top:276px;height:8px;border:2px solid var(--ydim);display:none}.prog i{display:block;height:100%;background:var(--y);width:0}
.spd{left:44px;top:18px;display:flex;flex-direction:column}.spd b{font-size:72px;font-weight:700;line-height:1}.spd span{font-size:26px;color:var(--y2)}
.kl .iz .n{flex:0 0 300px}.kl .iz .w{font-weight:700;color:var(--y);opacity:1;font-size:22px}
.kbtns{display:flex;gap:8px;flex:0 0 auto}
.kl .iz{height:40px}.kl .iz .key{height:38px;min-width:0;padding:0 14px;font-size:19px;border-radius:7px}
.warn{left:44px;right:44px;top:62px;height:50px;display:none;align-items:center;justify-content:space-between;border:2px solid var(--y);border-radius:8px;padding:0 6px 0 16px;font-size:22px}
.warn .key{height:38px;font-size:19px;min-width:0;border-radius:7px}
.kv b.small{font-size:26px}
.blink{animation:bk 1s steps(1) infinite}@keyframes bk{50%{opacity:.3}}
.key[disabled]{opacity:.45}
.izhd h3{position:absolute;left:0;right:0;text-align:center;pointer-events:none}
'''
body=open('ui/body.html').read()
js=open('ui/app.js').read()
html='<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><title>GurbetMap</title><style>'+css+'</style></head><body>\n'+body+'<script>\n'+js+'</script></body></html>'
open('app/src/main/assets/index.html','w').write(html)
print(len(html))
