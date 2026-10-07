package com.aimoodchecker.planner;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.aimoodchecker.planner.Plan.*;
import static com.aimoodchecker.planner.PlanService.*;

/** Optional, self-chosen wellbeing actions. These are not treatment or task completion. */
public final class Support {
    public static final long CONTEXT_LIFETIME=2*3600000L;
    private static final Set<String> MOODS=Set.of("unknown","low","flat","okay","good","mixed");
    private static final Set<String> ENERGIES=Set.of("any","low","focused");
    private static final Set<String> KINDS=Set.of("meaningful","movement","rest");
    private static final Set<String> OUTCOMES=Set.of("done","partly","skipped");
    private static final Set<String> HELPFULNESS=Set.of("unanswered","yes","little","no","unsure");
    public record View(SupportAction active,List<SupportAction> recent,CheckIn checkIn,boolean calendarChecked) {}

    public static View view(Plan p,long now) {
        SupportAction active=null;CheckIn checkIn=null;
        for(SupportAction a:p.supportActions) if(a.status.equals("planned") || a.status.equals("needs-review")) active=a;
        for(CheckIn c:p.checkIns) if(c.expires>now) checkIn=c;
        List<SupportAction> recent=new ArrayList<>(p.supportActions.subList(Math.max(0,p.supportActions.size()-5),p.supportActions.size()));
        Collections.reverse(recent);
        return new View(active,recent,checkIn,Planner.calendarChecked(p,now));
    }
    public static void checkIn(Plan p,JsonNode body,long now) {
        String mood=option(body,"mood","unknown",MOODS);
        String energy=option(body,"energy","any",ENERGIES);
        CheckIn c=new CheckIn();c.id=UUID.randomUUID().toString();c.mood=mood;c.energy=energy;
        c.created=now;c.expires=now+CONTEXT_LIFETIME;
        p.checkIns.add(c);if(p.checkIns.size()>100)p.checkIns.removeFirst();
        p.energy=energy;p.energyExpiresAt=c.expires;p.dismissed.clear();p.version++;
        invalidateFocusedSessions(p,energy);
        p.reason="Check-in saved for the next two hours. You choose what to do next.";
    }
    public static void invalidateFocusedSessions(Plan p,String energy) {
        if(!energy.equals("low"))return;
        for(Session s:p.sessions) if(s.status.equals("planned") && !step(task(p,s.taskId),s.stepId).energy.equals("low"))
            s.status="needs-review";
    }
    public static void create(Plan p,JsonNode body,long now) {
        String kind=option(body,"kind",null,KINDS);
        String title=plainText(body,"title",160,false);
        String cue=plainText(body,"cue",160,true);
        int minutes=integer(body,"minutes",1,60);
        require(minutes<=p.availableMinutes,"Choose an action within the time you have available, or update your availability.");
        require(p.supportActions.stream().noneMatch(a->a.status.equals("planned") || a.status.equals("needs-review")),
            "Respond to or skip your saved wellbeing action before choosing another.");
        SupportAction a=new SupportAction();a.id=UUID.randomUUID().toString();a.kind=kind;a.title=title;
        a.cue=cue;a.minutes=minutes;a.created=now;a.scheduleChecked=Planner.calendarChecked(p,now);
        if(a.scheduleChecked) {
            long[] window=Planner.freeWindows(p,now,Math.min(now+p.availableMinutes*60000L,p.horizon),true).stream()
                .filter(w->w[1]-w[0]>=minutes*60000L).findFirst().orElse(null);
            require(window!=null,"This action does not fit your available calendar time. Choose a shorter action or update your availability.");
            a.start=window[0];a.end=a.start+minutes*60000L;
        }
        // A disconnected calendar must never turn a personal choice into a claim of a free time slot.
        p.supportActions.add(a);if(p.supportActions.size()>100)p.supportActions.removeFirst();
        p.version++;
        p.reason=a.scheduleChecked?"Your wellbeing action has time reserved. Work suggestions are paused while you choose to do it.":
            "Your wellbeing action is saved without a calendar check or reserved time. Work suggestions are paused.";
    }
    public static void respond(Plan p,JsonNode body) {
        SupportAction a=find(p,body.path("id").asText());
        String status=option(body,"status",null,OUTCOMES);
        String helpfulness=option(body,"helpfulness","unanswered",HELPFULNESS);
        if(OUTCOMES.contains(a.status)) {
            require(a.status.equals(status),"This action already has a different response.");
            return; // Replaying completion must not overwrite the outcome or later feedback.
        }
        require(a.status.equals("planned") || a.status.equals("needs-review"),"This action cannot be updated.");
        a.status=status;a.helpfulness=helpfulness;p.version++;
        p.reason=status.equals("skipped")?"Skipped. There is no score or penalty, and task effort is unchanged.":
            "Your response is saved. Whether it helped is separate from whether you did it.";
    }
    public static void feedback(Plan p,JsonNode body) {
        SupportAction a=find(p,body.path("id").asText());
        String helpfulness=option(body,"helpfulness",null,HELPFULNESS);
        require(OUTCOMES.contains(a.status),"Respond to the action before recording whether it helped.");
        if(a.helpfulness.equals(helpfulness))return;
        a.helpfulness=helpfulness;p.version++;
    }
    private static SupportAction find(Plan p,String id) {
        return p.supportActions.stream().filter(a->a.id.equals(id)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Wellbeing action not found."));
    }
    private static String option(JsonNode n,String key,String fallback,Set<String> choices) {
        JsonNode value=n.get(key);
        String result=value==null || value.isNull()?fallback:value.isTextual()?value.asText():null;
        require(result!=null && choices.contains(result),"Choose a valid "+key+" option.");return result;
    }
    private static String plainText(JsonNode n,String key,int max,boolean optional) {
        JsonNode value=n.get(key);
        if(optional && (value==null || value.isNull()))return "";
        require(value!=null && value.isTextual(),"Enter "+key+" as text.");
        String text=value.asText().trim();require((optional || !text.isBlank()) && text.length()<=max,
            "Enter "+key+" (up to "+max+" characters).");return text;
    }
}
