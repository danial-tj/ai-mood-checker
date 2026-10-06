import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { resolve } from 'node:path';
import { get as httpGet } from 'node:http';

const [java,classpath,root]=process.argv.slice(2);
const port=18471, origin=`http://127.0.0.1:${port}`;
const server=spawn(java,['--enable-native-access=ALL-UNNAMED','--add-modules','jdk.httpserver',`-Dplanner.port=${port}`,`-Dplanner.root=${root}`,`-Dplanner.data=${resolve(root,'target',`http-${randomUUID()}.db`)}`,'-cp',classpath,'com.aimoodchecker.planner.PlannerServer'],{windowsHide:true,stdio:['ignore','pipe','pipe'],env:{...process.env,GOOGLE_CLIENT_ID:'',GOOGLE_CLIENT_SECRET:''}});
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
  r=await request('/api/export');const exported=await r.json();check(exported.tasks[0].remaining===70 && !JSON.stringify(exported).includes('csrf'),'Data export includes saved work without the app token');
  r=await action({action:'google-connect'});check(r.status===409,'Personal calendar cannot mix with the sample plan');
  r=await action({action:'clear'});check(r.ok,'Explicit planner deletion works');
  r=await action({action:'google-connect'});check(r.status===409 && (await r.json()).error.includes('GOOGLE_CLIENT_ID'),'Missing OAuth configuration gives an actionable response');
  console.log(`PASS: ${checks} HTTP checks; isolated local database, no external requests.`);
} finally {server.kill();}
