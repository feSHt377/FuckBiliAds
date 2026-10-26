/* DanmuJump module resource: time-parser.js */
globalThis.__danmuJumpParseTimes=function(input){
 const digits={'零':0,'〇':0,'一':1,'二':2,'两':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9};
    
 function chinese(word){if(/[百千万亿]/.test(word))return word;
        if(!/[十百千]/.test(word))return word.split('').map(c=>digits[c]).join('');
        let value=0,last=0;
        for(const c of word){if(c in digits)last=digits[c];
            else{const unit=c==='十'?10:c==='百'?100:1000;
                value+=(last||1)*unit;
                last=0;
                }}return String(value+last);
        }
 const text=String(input).replace(/[０-９]/g,c=>String(c.charCodeAt(0)-65296)).replace(/：/g,':').replace(/[零〇一二两三四五六七八九十百千万亿]+/g,chinese),found=[],ranges=[];
    
 function add(start,end,seconds,format,explicit){ranges.push([start,end]);
        if(/[*xX]\s*$/.test(text.slice(0,start))||/^\s*[百千万亿年]/.test(text.slice(end)))return;
        const ms=seconds*1000;
        if(Number.isSafeInteger(ms)&&ms>60000&&ms<=2147483647&&!found.some(r=>r.targetMs===ms))found.push({targetMs:ms,format:format,explicit:explicit});
        }
 let match;
    const clock=/\d+(?:[:.]\d+){1,2}/g;
    
 while((match=clock.exec(text))!==null){const token=match[0],parts=token.split(/[:.]/).map(Number),start=match.index,end=clock.lastIndex;
        ranges.push([start,end]);
        if((token.match(/\.\d+/g)||[]).some(part=>part.length!==3))continue;
        if(/[\d:.]/.test(text[end]||'')||((text[start-1]===':'||text[start-1]==='.')&&/[\d:.]/.test(text[start-2]||'')))continue;
        if(parts[parts.length-1]>=60||(parts.length===3&&parts[1]>=60))continue;
        add(start,end,parts.length===3?parts[0]*3600+parts[1]*60+parts[2]:parts[0]*60+parts[1],token.includes('.')?'dot':'clock',true);
        }
 const units=/(?:(\d+)\s*(?:小时|时)\s*)?(?:(\d+)\s*(?:分钟|分)\s*)?(?:(\d+)\s*(?:秒钟|秒))?/g;
    
 while((match=units.exec(text))!==null){if(!match[0]){units.lastIndex++;
            continue;
            }let end=units.lastIndex,seconds=Number(match[3]||0);
        if(match[2]&&!match[3]){const tail=/^\s*(\d+)(?!\d)/.exec(text.slice(end));
            if(tail){seconds=Number(tail[1]);
                end+=tail[0].length;
                units.lastIndex=end;
                }}ranges.push([match.index,end]);
        if((match[1]&&Number(match[2]||0)>=60)||((match[1]||match[2])&&seconds>=60))continue;
        add(match.index,end,Number(match[1]||0)*3600+Number(match[2]||0)*60+seconds,'units',true);
        }
 const bare=/\d+/g;
    while((match=bare.exec(text))!==null){const start=match.index,end=bare.lastIndex;
        if(ranges.some(r=>start<r[1]&&end>r[0])||/[:.]/.test(text[start-1]||'')||/[:.]/.test(text[end]||''))continue;
        const token=match[0];
        if(token.length===3||token.length===4){const seconds=Number(token.slice(-2));
            if(seconds<60)add(start,end,Number(token.slice(0,-2))*60+seconds,'compact',false);
            }}
 return found.slice(0,12);
    
};
