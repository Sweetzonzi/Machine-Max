/* OrderedBeltSolver.java#solve 的浏览器 / Node 对照实现。 */
(function (global) {
    'use strict';
    function solve(circles, turnEpsilon, sign, api) {
        const E = 1e-8, TAU = Math.PI * 2;
        const add = (a,b) => ({x:a.x+b.x,y:a.y+b.y});
        const sub = (a,b) => ({x:a.x-b.x,y:a.y-b.y});
        const mul = (a,k) => ({x:a.x*k,y:a.y*k});
        const dot = (a,b) => a.x*b.x+a.y*b.y;
        const cross = (a,b) => a.x*b.y-a.y*b.x;
        const dist = (a,b) => Math.hypot(a.x-b.x,a.y-b.y);
        const unit = a => mul(a,1/Math.hypot(a.x,a.y));
        const angle = a => Math.atan2(a.y,a.x);
        const pos = a => (a%TAU+TAU)%TAU;
        const out = a => ({x:sign*a.y,y:-sign*a.x});
        const point = (c,a) => add(c,{x:c.r*Math.cos(a),y:c.r*Math.sin(a)});
        const segDist = (p,a,b) => {
            const ab=sub(b,a),t=Math.max(0,Math.min(1,dot(sub(p,a),ab)/dot(ab,ab)));
            return dist(p,add(a,mul(ab,t)));
        };
        const visible = (a,b) => circles.every(c=>segDist(c,a,b)>=c.r-E);
        const passesWheel = (wheel,from,to,radius) => {
            const direction=sub(to,from),t=dot(sub(wheel,from),direction)/dot(direction,direction);
            return t>E&&t<1-E&&segDist(wheel,from,to)<radius+wheel.r;
        };
        const arc = p => Math.abs(p.sweep)>E;
        const crossed = points => {
            for(let i=0;i<points.length;i++) for(let j=i+2;j<points.length;j++) {
                if((j+1)%points.length===i) continue;
                const a=points[i],b=points[(i+1)%points.length],c=points[j],d=points[(j+1)%points.length];
                const ab=sub(b,a),cd=sub(d,c),den=cross(ab,cd);
                if(Math.abs(den)<=E)continue;
                const t=cross(sub(c,a),cd)/den,u=cross(sub(c,a),ab)/den;
                if(t>E&&t<1-E&&u>E&&u<1-E)return true;
            }
            return false;
        };
        const pins=circles.map((c,i)=>{
            const incoming=unit(sub(c,circles[(i+circles.length-1)%circles.length]));
            const outgoing=unit(sub(circles[(i+1)%circles.length],c));
            const turn=Math.atan2(cross(incoming,outgoing),dot(incoming,outgoing));
            const a=out(incoming),b=out(outgoing);
            let normal;
            const prev=circles[(i+circles.length-1)%circles.length],next=circles[(i+1)%circles.length];
            const doublesBack=Math.abs(turn)>Math.PI*.75&&(passesWheel(prev,c,next,c.r)||passesWheel(next,c,prev,c.r));
            if(i===0||i===circles.length-1||doublesBack) {
                const middle=angle(a)+sign*pos(sign*(angle(b)-angle(a)))*.5;
                normal={x:Math.cos(middle),y:Math.sin(middle)};
            } else {
                normal=add(a,b);
                normal=Math.hypot(normal.x,normal.y)<=E?b:unit(normal);
            }
            return {wheel:i,point:add(c,mul(normal,c.r))};
        });
        function route(source,target) {
            const nodes=[],edges=[],rings=circles.map(()=>[]);
            function node(w,p) {
                for(const id of rings[w])if(dist(nodes[id].point,p)<=E)return id;
                const id=nodes.length;nodes.push({wheel:w,point:p});edges.push([]);rings[w].push(id);return id;
            }
            function line(wa,a,wb,b) {
                const length=dist(a,b);
                if(length<=E||!visible(a,b))return;
                edges[node(wa,a)].push({to:node(wb,b),piece:{wa,wb,a,b,start:0,sweep:0,length}});
            }
            const start=node(source.wheel,source.point),end=node(target.wheel,target.point);
            for(let i=0;i<circles.length;i++)for(let j=0;j<circles.length;j++)if(i!==j) {
                const a=circles[i],b=circles[j],u=unit(sub(b,a)),alpha=(a.r-b.r)/dist(a,b);
                if(Math.abs(alpha)>=1)continue;
                const normal=add(mul(u,alpha),mul(out(u),Math.sqrt(1-alpha*alpha)));
                line(i,add(a,mul(normal,a.r)),j,add(b,mul(normal,b.r)));
            }
            for(const [pin,fromPoint] of [[source,true],[target,false]]) {
                circles.forEach((c,i)=>{
                    const d=dist(pin.point,c);if(d<=c.r+E)return;
                    const base=angle(sub(pin.point,c)),alpha=Math.acos(Math.min(1,c.r/d));
                    for(const branch of [-1,1]) {
                        const p=point(c,base+branch*alpha),radial=unit(sub(p,c));
                        const tangent={x:-sign*radial.y,y:sign*radial.x};
                        const direction=unit(fromPoint?sub(p,pin.point):sub(pin.point,p));
                        if(dot(direction,tangent)<1-E)continue;
                        if(fromPoint)line(pin.wheel,pin.point,i,p);else line(i,p,pin.wheel,pin.point);
                    }
                });
            }
            line(source.wheel,source.point,target.wheel,target.point);
            rings.forEach((ring,w)=>{
                const c=circles[w];
                ring.sort((a,b)=>pos(sign*angle(sub(nodes[a].point,c)))-pos(sign*angle(sub(nodes[b].point,c))));
                if(ring.length<2)return;
                ring.forEach((from,j)=>{
                    const to=ring[(j+1)%ring.length],a=nodes[from].point,b=nodes[to].point;
                    const begin=angle(sub(a,c)),sweep=sign*pos(sign*(angle(sub(b,c))-begin));
                    edges[from].push({to,piece:{wa:w,wb:w,a,b,start:begin,sweep,length:Math.abs(sweep)*c.r}});
                });
            });
            const distances=nodes.map(()=>Infinity),done=nodes.map(()=>false),previous=[],hops=[];
            distances[start]=0;
            for(let step=0;step<nodes.length;step++) {
                let at=-1;
                for(let i=0;i<nodes.length;i++)if(!done[i]&&(at<0||distances[i]<distances[at]))at=i;
                if(at<0||!Number.isFinite(distances[at]))break;
                if(at===end)break;
                done[at]=true;
                for(const hop of edges[at])if(distances[at]+hop.piece.length<distances[hop.to]-1e-12) {
                    distances[hop.to]=distances[at]+hop.piece.length;previous[hop.to]=at;hops[hop.to]=hop.piece;
                }
            }
            if(!Number.isFinite(distances[end]))throw new RangeError('no clear ordered belt span');
            const pieces=[];for(let at=end;at!==start;at=previous[at])pieces.push(hops[at]);
            return pieces.reverse();
        }
        const mergeable=(a,b)=>arc(a)&&arc(b)&&a.wa===b.wa&&dist(a.b,b.a)<=E;
        const merge=(a,b)=>({...a,b:b.b,sweep:a.sweep+b.sweep,length:a.length+b.length});
        function assemble(pieces) {
            const points=[],arcs=[],contacts=[],vertices=[],ordered=[];
            const push=p=>{if(!points.length||dist(points[points.length-1],p)>E)points.push(p);};
            pieces.forEach((p,i)=>{
                push(p.a);
                if(arc(p)) {
                    const c=circles[p.wa],count=Math.max(2,Math.ceil(Math.abs(p.sweep)/(Math.PI/180)));
                    const samples=[],startIndex=points.length-1;
                    for(let k=0;k<=count;k++) {
                        const value=k===count?p.b:point(c,p.start+p.sweep*k/count);samples.push(value);push(value);
                    }
                    arcs.push(api.arcRecord(p.wa,c,p.start,p.sweep,samples,startIndex,points.length-1));
                    vertices.push({wheelIndex:p.wa,wheelId:c.id,type:'convex',turn:p.sweep,support:point(c,p.start+p.sweep*.5)});
                    ordered.push(c);
                } else {
                    push(p.b);const next=pieces[(i+1)%pieces.length];
                    if(!arc(next)&&p.wb===next.wa) {
                        const a=unit(sub(p.b,p.a)),b=unit(sub(next.b,next.a));
                        const turn=Math.atan2(cross(a,b),dot(a,b)),c=circles[p.wb];
                        contacts.push({wheelIndex:p.wb,wheelId:c.id,point:p.b,contactLength:0});
                        vertices.push({wheelIndex:p.wb,wheelId:c.id,type:Math.abs(turn)<=turnEpsilon?'straight':'concave',turn,support:p.b});
                        ordered.push(c);
                    }
                }
            });
            if(points.length>1&&dist(points[0],points[points.length-1])<=E)points.pop();
            const first=vertices.findIndex(v=>v.wheelIndex===0);
            if(first>0){vertices.push(...vertices.splice(0,first));ordered.push(...ordered.splice(0,first));}
            return api.fromData(circles,ordered,vertices,points,arcs,contacts,false);
        }
        for(let attempt=0;attempt<circles.length*4;attempt++) {
            const spans=[],pieces=[];
            pins.forEach((from,i)=>{const to=pins[(i+1)%pins.length],path=route(from,to);spans.push({from,to,path});pieces.push(...path);});
            const merged=[];
            for(const p of pieces) {
                if(p.length<=E)continue;
                if(merged.length&&mergeable(merged[merged.length-1],p))merged[merged.length-1]=merge(merged[merged.length-1],p);
                else merged.push(p);
            }
            if(merged.length>1&&mergeable(merged[merged.length-1],merged[0]))merged[0]=merge(merged.pop(),merged[0]);
            const result=assemble(merged),outside=circles.findIndex(c=>!result.containsPoint(c));
            if(outside<0) {
                if(crossed(result.points)||sign*result.signedArea<=E)throw new RangeError('ordered contacts cross');
                return result;
            }
            const c=circles[outside];let best=-1,normal=null,bestDistance=Infinity;
            spans.forEach((span,i)=>{
                if(span.from.wheel===outside||span.to.wheel===outside)return;
                for(const p of span.path) {
                    if(arc(p)||p.length<=E)continue;
                    const delta=sub(p.b,p.a),t=dot(sub(c,p.a),delta)/dot(delta,delta),n=out(unit(delta));
                    if(t<=E||t>=1-E||dot(sub(c,p.a),n)<=E)continue;
                    const d=segDist(c,p.a,p.b);if(d<bestDistance){bestDistance=d;best=i;normal=n;}
                }
            });
            if(best<0)throw new RangeError('wheel outside ordered belt');
            const pin={wheel:outside,point:add(c,mul(normal,c.r))};
            if(pins.some(p=>p.wheel===outside&&dist(p.point,pin.point)<E))throw new RangeError('repeated enclosure contact');
            pins.splice(best+1,0,pin);
        }
        throw new RangeError('ordered enclosure did not converge');
    }
    if(typeof module!=='undefined'&&module.exports)module.exports=solve;
    global.OrderedBeltSolver=solve;
})(typeof globalThis!=='undefined'?globalThis:this);
