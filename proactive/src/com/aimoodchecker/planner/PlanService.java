package com.aimoodchecker.planner;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.aimoodchecker.planner.Plan.*;

public final class PlanService {
    private final PlanStore store;
    private final Clock clock;
    public PlanService(PlanStore store,Clock clock) { this.store=store;this.clock=clock; }
    public long now(Plan p) { return p.demo?p.demoNow:((clock.millis()+59999)/60000)*60000; }
    public Map<String,Object> view() throws Exception {
        Plan p=store.read(); long now=now(p);
        return Map.of("plan",p,"result",Planner.suggest(p,now),"now",now,
            "currentEnergy",Planner.effectiveEnergy(p,now),"support",Support.view(p,now));
    }
    public void act(JsonNode body) throws Exception {
        String action=body.path("action").asText();
        if(action.equals("reset")) { Plan p=Plan.sample();p.version=store.read().version+1;store.replace(p);return; }
        if(action.equals("clear")) {
            Plan p=new Plan();p.demo=false;p.connection="disconnected";p.version=store.read().version+1;
            p.horizon=clock.millis()+14*86400000L;store.replace(p);return;
        }
        store.change(p->{
            long now=now(p);
            String id=body.path("id").asText();
            String responseKey=action+":"+id;
            if(Set.of("accept","complete","skip","dismiss").contains(action) && p.responses.contains(responseKey)) return null;
            switch(action) {
                case "checkin" -> Support.checkIn(p,body,now);
                case "support-create" -> Support.create(p,body,now);
                case "support-respond" -> Support.respond(p,body);
                case "support-feedback" -> Support.feedback(p,body);
                case "capacity" -> {
                    p.availableMinutes=integer(body,"minutes",5,180);
                    if(body.has("energy")) {
                        require(body.path("energy").isTextual() && Set.of("any","low","focused").contains(body.path("energy").asText()),"Choose an energy option.");
                        p.energy=body.path("energy").asText();p.energyExpiresAt=now+Support.CONTEXT_LIFETIME;
                    }
                    String energy=Planner.effectiveEnergy(p,now);
                    p.dismissed.clear();p.reason="You changed the time or energy you have available.";p.version++;
                    for(Session session:p.sessions) if(session.status.equals("planned") &&
                        (session.end>now+p.availableMinutes*60000L || (energy.equals("low") && !step(task(p,session.taskId),session.stepId).energy.equals("low"))))
                        session.status="needs-review";
                    for(SupportAction support:p.supportActions) if(support.status.equals("planned") && support.scheduleChecked && support.end>now+p.availableMinutes*60000L)
                        support.status="needs-review";
                }
                case "shift" -> {
                    require(p.demo,"The changed-shift button is only available in the sample day.");
                    Event shift=p.events.stream().filter(e->e.id().equals("shift")).findFirst().orElseThrow();
                    require(shift.etag().equals("1"),"The sample shift has already changed. Reset the sample to replay it.");
                    long end=shift.end()+35*60000L;
                    p.events.remove(shift);p.events.add(new Event(shift.id(),shift.title(),shift.start(),end,"sample","2"));
                    p.demoNow=end+5*60000L;p.lastSync=p.demoNow;p.availableMinutes=20;p.dismissed.clear();p.version++;
                    p.reason="Your shift ended 35 minutes later. Let's find what fits now.";
                    Planner.invalidateSessions(p);queue(p,p.demoNow);
                }
                case "accept", "dismiss" -> {
                    var suggestion=Planner.suggest(p,now).suggestion();
                    require(suggestion!=null && suggestion.id().equals(id),"This suggestion changed. Review the updated plan before accepting.");
                    Task t=task(p,suggestion.taskId());Step s=step(t,suggestion.stepId());
                    if(action.equals("accept")) {
                        p.sessions.add(new Session(id,t,s,suggestion.start()));
                        p.reason="Your next action is saved. Only marking it done reduces the remaining work.";
                    } else {p.dismissed.add(t.id+":"+s.id);p.reason="Suggestion dismissed. Nothing was marked complete.";}
                    p.responses.add(responseKey);p.version++;
                }
                case "complete", "skip" -> {
                    Session s=p.sessions.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Action not found."));
                    require(s.status.equals("planned") || s.status.equals("needs-review"),"This action already has a response.");
                    if(action.equals("complete")) {
                        Task t=task(p,s.taskId);Step step=step(t,s.stepId);
                        require(!step.done,"This step is already complete.");
                        step.done=true;t.remaining=Math.max(0,t.remaining-s.minutes);s.status="completed";
                        p.sessions.stream().filter(other->other!=s && other.taskId.equals(s.taskId) && other.stepId.equals(s.stepId)
                            && (other.status.equals("planned") || other.status.equals("needs-review"))).forEach(other->other.status="superseded");
                        p.reason="Completion recorded from your response. The rest of the work is still visible.";
                    } else {s.status="skipped";p.dismissed.add(s.taskId+":"+s.stepId);p.reason="Skipped for now. Your effort estimate is unchanged.";}
                    p.responses.add(responseKey);p.version++;
                    if(action.equals("skip")) queue(p,now);
                }
                case "task" -> {
                    Task t=id.isBlank()?new Task():task(p,id);
                    require(p.tasks.size()<100 || !id.isBlank(),"This prototype supports up to 100 tasks.");
                    t.title=text(body,"title",160);t.deadline=deadline(body.path("deadline").asText(),p.zone);
                    require(t.deadline>now,"Choose a future deadline.");
                    t.remaining=body.path("remaining").isNull()?null:integer(body,"remaining",1,10080);
                    require(p.sessions.stream().noneMatch(s->s.taskId.equals(t.id) && (s.status.equals("planned") || s.status.equals("needs-review"))),"Complete or skip the accepted action before editing its task.");
                    int confirmed=t.steps.stream().filter(s->s.confirmed && !s.done).mapToInt(s->s.minutes).sum();
                    require(t.remaining==null || confirmed<=t.remaining,"Remaining effort cannot be less than your unfinished confirmed steps.");
                    if(id.isBlank()) {t.id=UUID.randomUUID().toString();p.tasks.add(t);}
                    p.version++;p.dismissed.clear();p.reason="Task saved. Confirm a specific step and estimate to make it eligible.";
                    Planner.invalidateSessions(p);
                }
                case "step" -> {
                    Task t=task(p,body.path("taskId").asText());
                    require(t.remaining!=null,"Estimate the task's remaining effort first.");
                    int minutes=integer(body,"minutes",1,180);
                    require(t.steps.size()<50,"This prototype supports up to 50 steps per task.");
                    int allocated=t.steps.stream().filter(s->!s.done).mapToInt(s->s.minutes).sum();
                    require(allocated+minutes<=t.remaining,"The steps would exceed this task's remaining effort. Update its estimate first.");
                    String energy=body.path("energy").asText("low");
                    require(Set.of("low","focused").contains(energy),"Choose a step energy level.");
                    t.steps.add(new Step(UUID.randomUUID().toString(),text(body,"title",160),minutes,energy));
                    p.version++;p.dismissed.clear();p.reason="Your confirmed step is ready to consider.";
                }
                case "preferences" -> {
                    String zone=body.path("zone").asText(p.zone);ZoneId.of(zone);p.zone=zone;
                    p.bufferMinutes=integer(body,"buffer",0,30);
                    p.preferences.reminders=body.path("reminders").asBoolean();
                    p.preferences.paused=body.path("paused").asBoolean();
                    p.preferences.quietStart=integer(body,"quietStart",0,23);
                    p.preferences.quietEnd=integer(body,"quietEnd",0,23);
                    require(p.preferences.quietStart!=p.preferences.quietEnd,"Quiet hours need different start and end times.");
                    p.preferences.dailyLimit=integer(body,"dailyLimit",1,3);p.version++;
                    Planner.invalidateSessions(p);
                }
                case "snooze" -> {p.preferences.snoozeUntil=now+3600000L;}
                case "advance" -> {require(p.demo,"Only the sample clock can be advanced.");p.demoNow+=10*60000L;p.version++;queue(p,p.demoNow);}
                default -> throw new IllegalArgumentException("Unknown action.");
            }
            Planner.deliver(p,now(p));return null;
        });
    }
    public void applyCalendar(List<Event> events,long synced,long horizon) throws Exception {
        store.change(p->{
            boolean changed=!p.events.equals(events);
            p.demo=false;p.events=new ArrayList<>(events);p.lastSync=synced;p.horizon=horizon;p.connection="connected";
            if(changed) {
                p.version++;p.dismissed.clear();Planner.invalidateSessions(p);
                boolean needsReview=p.sessions.stream().anyMatch(s->s.status.equals("needs-review")) ||
                    p.supportActions.stream().anyMatch(a->a.status.equals("needs-review"));
                p.reason=needsReview?"Google Calendar changed. Your saved action needs review; it has not been automatically rescheduled.":
                    "Google Calendar changed. Your next action uses the updated schedule.";
                if(needsReview) queue(p,now(p));
            }
            return null;
        });
    }
    public void syncFailed(String status) throws Exception {store.change(p->{p.connection=status;return null;});}
    public void tick() throws Exception {store.change(p->{Planner.deliver(p,now(p));return null;});}
    private static void queue(Plan p,long now) {
        String key="change:"+p.version;
        if(p.jobs.stream().noneMatch(j->j.id.equals(key))) p.jobs.add(new Job(key,p.reason,p.version,now));
    }
    static Task task(Plan p,String id) {return p.tasks.stream().filter(t->t.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Task not found."));}
    static Step step(Task t,String id) {return t.steps.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow();}
    static void require(boolean valid,String message) {if(!valid) throw new IllegalArgumentException(message);}
    static String text(JsonNode n,String key,int max) {String s=n.path(key).asText().trim();require(!s.isBlank() && s.length()<=max,"Enter "+key+" (up to "+max+" characters).");return s;}
    static int integer(JsonNode n,String key,int min,int max) {require(n.path(key).isIntegralNumber() && n.path(key).canConvertToInt(),"Enter a whole number for "+key+".");int v=n.path(key).asInt();require(v>=min && v<=max,key+" must be between "+min+" and "+max+".");return v;}
    static long deadline(String value,String zone) {
        try {
            LocalDateTime local=LocalDateTime.parse(value);
            var offsets=ZoneId.of(zone).getRules().getValidOffsets(local);
            require(offsets.size()==1,"This time is ambiguous or skipped by daylight saving. Choose a different local time.");
            return local.toInstant(offsets.getFirst()).toEpochMilli();
        } catch(DateTimeException e) {throw new IllegalArgumentException("Enter a valid deadline in your selected timezone.");}
    }
}
