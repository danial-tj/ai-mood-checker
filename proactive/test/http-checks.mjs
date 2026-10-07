import assert from 'node:assert/strict';
import { configuration, createForwarder, createSession } from '../agent-bridge.mjs';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import { get as httpGet } from 'node:http';

const [java,classpath,root]=process.argv.slice(2);
const agentToken=randomUUID()+randomUUID();
const port=18471, origin=`http://127.0.0.1:${port}`;
const server=spawn(java,['--enable-native-access=ALL-UNNAMED','--add-modules','jdk.httpserver',`-Dplanner.port=${port}`,`-Dplanner.root=${root}`,`-Dplanner.data=${resolve(root,'target',`http-${randomUUID()}.db`)}`,'-cp',classpath,'com.aimoodchecker.planner.PlannerServer'],{windowsHide:true,stdio:['ignore','pipe','pipe'],env:{...process.env,GOOGLE_CLIENT_ID:'',GOOGLE_CLIENT_SECRET:'',MOOD_AGENT_TOKEN:agentToken}});
let checks=0;
const check=(condition,message)=>{assert.ok(condition,message);checks++;console.log(`PASS: ${message}`);};
const request=(path,options={})=>fetch(origin+path,{...options,signal:AbortSignal.timeout(10000)});
try {
  await new Promise((ok,fail)=>{
    const timeout=setTimeout(()=>fail(Error('Local test server startup timed out.')),15000);
    server.on('error',fail);server.on('exit',code=>{clearTimeout(timeout);fail(Error(`Local test server exited ${code}.`));});
    server.stdout.on('data',data=>{if(data.toString().includes('local prototype:')){clearTimeout(timeout);ok();}});
  });
  let r=await request('/');check(r.status===200 && (await r.text()).includes('AI Mood Checker'),'Server serves the web application');
  check(r.headers.get('content-security-policy').includes("frame-ancestors 'none'"),'Page has a restrictive content security policy');
  let state=await (await request('/api/state')).json();check(state.plan.demo && state.result.suggestion.minutes===20,'API exposes a runnable synthetic scenario');
  const original=state.result.suggestion.id;
  const action=body=>request('/api/action',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json','X-Planner-Token':state.csrf},body:JSON.stringify(body)});
  r=await request('/api/action',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'{"action":"clear"}'});check(r.status===403,'Mutation without the app token is denied');
  r=await request('/api/action',{method:'POST',headers:{Origin:'https://foreign.example','Content-Type':'application/json','X-Planner-Token':state.csrf},body:'{"action":"clear"}'});check(r.status===403,'Mutation from another origin is denied');
  const foreignHostStatus=await new Promise((ok,fail)=>{const req=httpGet(origin+'/api/state',{headers:{Host:'foreign.example'}},res=>{res.resume();ok(res.statusCode);});req.on('error',fail);});
  check(foreignHostStatus===403,'Foreign Host header is rejected');
  r=await action({action:'shift'});check(r.ok,'Changed-shift API action succeeds');
  r=await action({action:'accept',id:original});check(r.status===409,'API rejects an obsolete suggestion');
  state=await (await request('/api/state')).json();const id=state.result.suggestion.id;
  await action({action:'accept',id});await action({action:'accept',id});state=await (await request('/api/state')).json();
  check(state.plan.sessions.length===1 && state.plan.tasks[0].remaining===90,'HTTP retries do not duplicate an accepted action or complete it');
  await action({action:'complete',id});await action({action:'complete',id});state=await (await request('/api/state')).json();
  check(state.plan.tasks[0].remaining===70,'Completion through HTTP is counted once');

  const rpc=(method,params,headers={})=>request('/api/agent',{method:'POST',headers:{Authorization:'Bearer '+agentToken,'Content-Type':'application/json',...headers},body:JSON.stringify({jsonrpc:'2.0',id:1,method,params})});
  r=await request('/api/agent',{method:'POST',body:'{}'});check(r.status===403,'Agent RPC denies a missing bearer token');
  r=await rpc('tools/list',{}, {Origin:origin});check(r.status===403,'Agent RPC rejects browser-origin requests even with its token');
  r=await request('/api/agent',{headers:{Authorization:'Bearer '+agentToken}});check(r.status===405,'Agent RPC only accepts POST');
  r=await rpc('tools/list',{});const listed=await r.json();check(listed.result.tools.length===3,'Authenticated agent RPC exposes only three scoped tools');

  const agent=createSession(createForwarder(configuration({MOOD_PLANNER_URL:origin,MOOD_AGENT_TOKEN:agentToken})));
  const init=await agent({jsonrpc:'2.0',id:2,method:'initialize',params:{protocolVersion:'2025-06-18',capabilities:{},clientInfo:{name:'synthetic-http-check',version:'1'}}});
  check(init.result.protocolVersion==='2025-06-18','MCP bridge initializes against the real isolated Java server');
  await agent({jsonrpc:'2.0',method:'notifications/initialized'});
  const bridged=await agent({jsonrpc:'2.0',id:3,method:'tools/call',params:{name:'get_next_action',arguments:{}}});
  check(bridged.result.isError===false&&!bridged.result.structuredContent.taskDetailsShared,'MCP bridge retrieves a minimal action through authenticated HTTP');

  r=await rpc('tools/call',{name:'get_next_action',arguments:{}});const minimal=await r.json();
  check(!JSON.stringify(minimal).includes('Research methods')&&!JSON.stringify(minimal).includes('Café')&&!JSON.stringify(minimal).includes('csrf'),'Agent response omits task titles, calendar titles, and app token by default');
  r=await rpc('tools/call',{name:'record_checkin',arguments:{mood:'low',energy:'low',userConfirmed:false}});check((await r.json()).error.code===-32602,'Agent cannot record a check-in without explicit confirmation');
  state=await (await request('/api/state')).json();check(state.plan.checkIns.length===0,'Rejected agent write leaves no check-in');
  r=await rpc('tools/call',{name:'record_checkin',arguments:{mood:'low',energy:'focused',userConfirmed:true}});check((await r.json()).result.isError===false,'Confirmed mood and energy are saved independently');
  state=await (await request('/api/state')).json();const expiry=state.plan.energyExpiresAt;
  await action({action:'capacity',minutes:20});state=await (await request('/api/state')).json();check(state.currentEnergy==='focused'&&state.plan.energyExpiresAt===expiry,'Changing time alone preserves the existing energy expiry');
  r=await action({action:'support-create',kind:'rest',title:'Take a quiet break',minutes:5,cue:'I close my laptop'});check(r.ok,'A chosen support action can be saved through HTTP');
  state=await (await request('/api/state')).json();const supportId=state.support.active.id;
  check(state.result.suggestion===null&&state.support.active.scheduleChecked,'Saved support pauses new work and records a checked calendar slot');
  r=await rpc('tools/call',{name:'report_completion',arguments:{userConfirmed:true,id:supportId,status:'done',helpfulness:'no'}});check((await r.json()).result.isError===false,'Agent can record explicit support completion separately from helpfulness');
  state=await (await request('/api/state')).json();check(state.plan.tasks[0].remaining===70&&state.support.recent[0].helpfulness==='no','Support completion preserves work effort and does not assume benefit');
  r=await request('/api/agent',{method:'POST',headers:{Authorization:'Bearer '+agentToken},body:'x'.repeat(65537)});check(r.status===413,'Oversized agent bodies are rejected');

  r=await request('/api/export');const exported=await r.json();check(exported.tasks[0].remaining===70 && !JSON.stringify(exported).includes('csrf'),'Data export includes saved work without the app token');
  r=await action({action:'google-connect'});check(r.status===409,'Personal calendar cannot mix with the sample plan');
  r=await action({action:'clear'});check(r.ok,'Explicit planner deletion works');
  r=await action({action:'google-connect'});check(r.status===409 && (await r.json()).error.includes('GOOGLE_CLIENT_ID'),'Missing OAuth configuration gives an actionable response');
  console.log(`PASS: ${checks} HTTP checks; isolated local database, no external requests.`);
} finally {server.kill();}
