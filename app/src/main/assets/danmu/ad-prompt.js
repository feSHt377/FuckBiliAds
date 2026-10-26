/* DanmuJump module resource: ad-prompt.js */
const __dj={context:null,activity:null,previous:null,store:new Map(),prompt:null,logSequence:0,
 key(){const w=this.context&&this.context.workinfoManager.workInfo;
        return w?String(w.workId)+'/'+String(w.videoId):'';
        },
 bucket(key){let b=this.store.get(key);
        if(!b){b={rows:new Map(),targets:new Set(),blocks:[]};
            this.store.set(key,b);
            if(this.store.size>8)this.store.delete(this.store.keys().next().value);
            }return b;
        },
 close(){if(this.prompt&&!this.prompt.isReleased())this.prompt.release();
        this.prompt=null;
        },
 add(args){if(!args||!Array.isArray(args.rows))return;
        const b=this.bucket(String(args.workId)+'/'+String(args.videoId));
        for(const row of args.rows){if(Number.isFinite(row.appearanceMs)&&Number.isFinite(row.targetMs)&&row.targetMs>row.appearanceMs)b.rows.set(String(row.id)+'/'+row.targetMs,row);
            }cron.log('[DanmuJumpJS] candidates received count='+args.rows.length);
        },
 bind(context,activity){this.context=context;
        this.activity=activity;
        context.videoPlayer.addListener({onTimeUpdated:sec=>this.tick(sec),onSeeking:()=>{this.close();
                this.previous=null;
                },onSeeked:()=>{this.close();
                this.previous=null;
                }});
        context.workinfoManager.addListener({onWorkWillChange:()=>{this.close();
                this.previous=null;
                },onCurrentWorkChanged:()=>{this.close();
                this.previous=null;
                }});
        cron.log('[DanmuJumpJS] prompt pipeline ready');
        },
 audit(group,info,now,duration){try{const batchId=String(Date.now())+'-'+String(++this.logSequence)+'-'+this.key(),emit=record=>cron.log('[DanmuJumpVote]'+JSON.stringify(record));
            emit({event:'prompt',batchId:batchId,workId:String(info.workId),videoId:String(info.videoId),id:group.id,currentMs:now,durationMs:duration,targetMs:group.targetMs,votes:group.votes,explicitVotes:group.explicitVotes,minVotes:__DJ_CONFIG.minVotes||3,targetToleranceSeconds:__DJ_CONFIG.targetToleranceSeconds||5,sourceWindowSeconds:__DJ_CONFIG.sourceWindowSeconds||30});
            for(let index=0;
            index<group.voters.length;
            index++){const row=group.voters[index];
                emit({event:'vote',batchId:batchId,workId:String(info.workId),videoId:String(info.videoId),voteIndex:index+1,id:row.id,dmid:row.dmid,text:row.text,sentAtSeconds:row.sentAtSeconds,appearanceMs:row.appearanceMs,targetMs:row.targetMs,format:row.format,explicit:row.explicit});
                }}catch(error){cron.log('[DanmuJumpJS] vote audit error '+String(error));
            }},
 tick(sec){if(!__DJ_CONFIG.enabled||!Number.isFinite(sec))return;
        const now=sec*1000;
        if(this.previous!==null&&now>=this.previous&&now-this.previous<200)return;
        const previous=this.previous;
        this.previous=now;
        if(this.context.videoPlayer.playbackRate<=0)return;
        if(previous!==null&&(now<previous||now-previous>2000)){this.close();
            return;
            }if(this.prompt&&!this.prompt.isReleased())return;
        this.prompt=null;
        const info=this.context.workinfoManager.workInfo,duration=Number(info.duration),key=this.key(),b=this.store.get(key);
        if(!b||!Number.isFinite(duration)||duration<=0)return;
        
 const tolerance=(__DJ_CONFIG.targetToleranceSeconds||5)*1000,sourceWindow=(__DJ_CONFIG.sourceWindowSeconds||30)*1000,minVotes=__DJ_CONFIG.minVotes||3;
        
 const eligible=Array.from(b.rows.values()).filter(row=>row.targetMs<duration&&row.targetMs>now+1000&&!Array.from(b.targets).some(target=>Math.abs(target-row.targetMs)<=tolerance)&&!b.blocks.some(range=>row.appearanceMs>=range[0]&&row.appearanceMs<=range[1]));
        
 let best=null;
        for(const anchor of eligible){if(anchor.appearanceMs>now||anchor.appearanceMs<now-1000||(previous!==null&&anchor.appearanceMs<previous))continue;
            const votes=new Map();
            for(const row of eligible){if(Math.abs(row.targetMs-anchor.targetMs)>tolerance||Math.abs(row.appearanceMs-anchor.appearanceMs)>sourceWindow)continue;
                const old=votes.get(row.id);
                if(!old||(!old.explicit&&row.explicit))votes.set(row.id,row);
                }if(votes.size<minVotes)continue;
            const members=Array.from(votes.values()),values=members.map(row=>row.targetMs).sort((a,b)=>a-b),target=values[Math.floor(values.length/2)],agree=members.filter(row=>Math.abs(row.targetMs-target)<=tolerance),explicitVotes=agree.filter(row=>row.explicit).length;
            if(agree.length<minVotes)continue;
            
 const group={id:anchor.id,appearanceMs:anchor.appearanceMs,targetMs:target,votes:agree.length,explicitVotes:explicitVotes,voters:agree};
            if(!best||group.votes>best.votes||(group.votes===best.votes&&group.explicitVotes>best.explicitVotes))best=group;
            }if(!best)return;
        
 const controller=this.activity.fetchLayouter('airborne');
        if(!controller)return;
        if(controller._airborneToast&&!controller._airborneToast.isReleased())return;
        
 b.targets.add(best.targetMs);
        const prompt=new __DJ_TOAST(controller.size,controller._suggestedFactor,'检测到广告，点击跳转');
        this.prompt=prompt;
        controller._airborneToast=prompt;
        
 prompt.onClick=()=>{this.prompt=null;
            const current=this.context.videoPlayer.currentTime*1000,newDuration=Number(this.context.workinfoManager.workInfo.duration);
            if(this.key()!==key||best.targetMs<=current+1000||!Number.isFinite(newDuration)||best.targetMs>=newDuration){cron.log('[DanmuJumpJS] prompt click rejected: context or target changed');
                return true;
                }b.blocks.push([Math.max(0,current-1000),best.targetMs]);
            controller.__danmuJump(best.targetMs/1000);
            return true;
            };
        controller.view.addSubView(prompt);
        prompt.show();
        this.audit(best,info,now,duration);
        cron.log('[DanmuJumpJS] prompt shown '+JSON.stringify({videoId:String(info.videoId),id:best.id,appearanceMs:best.appearanceMs,targetMs:best.targetMs,currentMs:now,durationMs:duration,votes:best.votes,explicitVotes:best.explicitVotes}));
        }
};

__DJ_RPC.setMessageHandler('DanmuJump_AddCandidates',request=>{__dj.add(request.args);
    return new __DJ_RESPONSE({accepted:true});
    });
