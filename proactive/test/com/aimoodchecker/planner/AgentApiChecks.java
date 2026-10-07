package com.aimoodchecker.planner;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.aimoodchecker.planner.PlanStore.JSON;

/** Synthetic protocol, privacy projection and explicit-confirmation boundaries. */
public final class AgentApiChecks {
    private static int checks;
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
        checks++;System.out.println("PASS: "+message);
    }
    private static JsonNode request(AgentApi api,String method,Object params) {
        return api.handle(JSON.valueToTree(Map.of("jsonrpc","2.0","id",1,"method",method,"params",params)));
    }
    private static JsonNode call(AgentApi api,String tool,Object args) { return request(api,"tools/call",Map.of("name",tool,"arguments",args)); }
    public static void main(String[] args) throws Exception {
        Path directory=Path.of("proactive/target/agent-checks-"+UUID.randomUUID());Files.createDirectories(directory);
        PlanStore store=new PlanStore(directory.resolve("synthetic.db"));
        PlanService service=new PlanService(store,Clock.fixed(Instant.parse("2026-10-06T00:45:00Z"),ZoneOffset.UTC));
        AgentApi api=new AgentApi(service);
        JsonNode init=request(api,"initialize",Map.of("protocolVersion","2025-06-18","capabilities",Map.of(),"clientInfo",Map.of("name","synthetic","version","1")));
        check(init.path("result").path("protocolVersion").asText().equals("2025-06-18"),"Negotiates the supported protocol version");
        check(request(api,"tools/list",Map.of()).path("result").path("tools").size()==3,"Exposes exactly three tools");
        check(request(api,"delete_everything",Map.of()).path("error").path("code").asInt()==-32601,"Rejects unknown methods");
        check(api.handle(JSON.readTree("[]")).path("error").path("code").asInt()==-32600,"Rejects JSON-RPC batches");
        check(call(api,"get_next_action",Map.of("unexpected",true)).has("error"),"Rejects undeclared tool arguments");
        JsonNode minimal=call(api,"get_next_action",Map.of()).path("result").path("structuredContent");
        check(minimal.path("kind").asText().equals("work") && !minimal.has("title") && !minimal.has("taskTitle"),"Omits task details by default");
        check(!minimal.has("plan") && !minimal.has("mood") && !minimal.has("checkIn") && !minimal.has("events") && !minimal.has("history"),"Does not export mood, history, full plan or calendar events");
        check(call(api,"get_next_action",Map.of("shareTaskDetails",true)).has("error"),"Task detail sharing requires explicit confirmation");
        check(call(api,"get_next_action",Map.of("shareTaskDetails",true,"userConfirmed",true)).path("result").path("structuredContent").has("title"),"Explicit opt-in shares only the current task details");
        check(call(api,"record_checkin",Map.of("mood","low","energy","low")).has("error") && store.read().checkIns.isEmpty(),"Unconfirmed check-in cannot mutate data");
        check(call(api,"record_checkin",Map.of("userConfirmed","true","mood","low","energy","low")).has("error"),"Confirmation must be a boolean");
        check(call(api,"record_checkin",Map.of("userConfirmed",true,"mood","diagnosed","energy","low")).has("error"),"Rejects invented mood labels");
        check(api.handle(JSON.valueToTree(Map.of("jsonrpc","2.0","method","tools/call","params",Map.of("name","record_checkin","arguments",Map.of("userConfirmed",true,"mood","low","energy","low")))))==null
            && store.read().checkIns.isEmpty(),"Notifications cannot mutate app data");
        check(!call(api,"record_checkin",Map.of("userConfirmed",true,"mood","low","energy","low")).path("result").path("isError").asBoolean()
            && store.read().checkIns.size()==1,"Confirmed check-in reaches persistent storage");
        service.act(JSON.valueToTree(Map.of("action","support-create","kind","rest","title","Synthetic quiet break","cue","After putting the mug down","minutes",5)));
        JsonNode support=call(api,"get_next_action",Map.of()).path("result").path("structuredContent");
        check(support.path("kind").asText().equals("wellbeing") && !support.has("title") && !support.has("cue"),"Wellbeing projection also keeps descriptions private by default");
        String id=support.path("id").asText();
        int remaining=store.read().tasks.getFirst().remaining;
        check(call(api,"report_completion",Map.of("id",id,"status","done")).has("error"),"Completion requires confirmation");
        check(!call(api,"report_completion",Map.of("userConfirmed",true,"id",id,"status","done","helpfulness","no")).path("result").path("isError").asBoolean(),"Records the user's wellbeing outcome");
        check(store.read().supportActions.getFirst().status.equals("done") && store.read().supportActions.getFirst().helpfulness.equals("no")
            && store.read().tasks.getFirst().remaining==remaining,"Done can be unhelpful and never reduces task effort");
        check(call(api,"report_completion",Map.of("userConfirmed",true,"id","work-only","status","done")).path("result").path("isError").asBoolean(),"Completion tool cannot complete a work task");
        PlanStore sessionStore=new PlanStore(directory.resolve("replacement-session.db"));
        PlanService sessionService=new PlanService(sessionStore,Clock.fixed(Instant.parse("2026-10-06T00:45:00Z"),ZoneOffset.UTC));
        AgentApi sessionApi=new AgentApi(sessionService);
        String originalId=call(sessionApi,"get_next_action",Map.of()).path("result").path("structuredContent").path("id").asText();
        sessionService.act(JSON.valueToTree(Map.of("action","accept","id",originalId)));
        sessionService.act(JSON.valueToTree(Map.of("action","shift")));
        String replacementId=JSON.valueToTree(sessionService.view()).path("result").path("suggestion").path("id").asText();
        sessionService.act(JSON.valueToTree(Map.of("action","accept","id",replacementId)));
        JsonNode current=call(sessionApi,"get_next_action",Map.of()).path("result").path("structuredContent");
        check(current.path("id").asText().equals(replacementId) && current.path("status").asText().equals("planned"),
            "Current planned replacement takes priority over an older session needing review");
        sessionService.act(JSON.valueToTree(Map.of("action","skip","id",replacementId)));
        JsonNode unresolved=call(sessionApi,"get_next_action",Map.of()).path("result").path("structuredContent");
        check(unresolved.path("id").asText().equals(originalId) && unresolved.path("status").asText().equals("needs-review"),
            "Falls back to unresolved work only when no planned session remains");
        System.out.println("PASS: "+checks+" agent API checks; synthetic local data only.");
    }
}
