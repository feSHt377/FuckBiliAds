/* DanmuJump module resource: worker-candidates.js */
try{const senders=[];
    for(const row of h.elems||[]){const id=String(row.idStr||row.id||''),hash=String(row.midHash||'');
        if(!id||!/^[0-9a-f]{1,8}$/i.test(hash))continue;
        senders.push({id:id,hash:hash});
        if(senders.length>=128)cron.log('[DanmuSenderData]'+JSON.stringify({videoId:String(i),rows:senders.splice(0)}));
        }if(senders.length)cron.log('[DanmuSenderData]'+JSON.stringify({videoId:String(i),rows:senders}));
    cron.log('[DanmuJumpJS] sender segment cached videoId='+String(i)+' total='+(h.elems||[]).length);
    }catch(error){cron.log('[DanmuJumpJS] sender cache error '+String(error));
    }
try{
 const rows=h.elems||[],selected=[];
    let numeric=0,accepted=0;
    
 if(__DJ_CONFIG.enabled)for(const row of rows){const text=String(row.content||''),times=globalThis.__danmuJumpParseTimes(text),appearance=Number(row.progress);
        if(times.length)numeric++;
        if(!Number.isFinite(appearance)||appearance<0)continue;
        for(const parsed of times){if(parsed.targetMs<=appearance)continue;
            accepted++;
            selected.push({id:String(row.idStr||row.id||'')+'@'+appearance,dmid:String(row.idStr||row.id||''),text:text,sentAtSeconds:Number(row.ctime)||null,appearanceMs:appearance,targetMs:parsed.targetMs,explicit:parsed.explicit,format:parsed.format});
            if(selected.length>=100)cron.log('[DanmuJumpData]'+JSON.stringify({workId:String(s),videoId:String(i),rows:selected.splice(0)}));
            }}
 if(selected.length)cron.log('[DanmuJumpData]'+JSON.stringify({workId:String(s),videoId:String(i),rows:selected}));
    
 cron.log('[DanmuJumpJS] segment '+JSON.stringify({workId:String(s),videoId:String(i),segmentIndex:t.index,total:rows.length,numericDanmaku:numeric,forwardCandidates:accepted}));
    
}catch(error){cron.log('[DanmuJumpJS] candidate pipeline error '+String(error));
    }
