package com.aimoodchecker.planner;

import java.time.*;
import java.util.*;
import static com.aimoodchecker.planner.Plan.*;

/** Pure deterministic rules: deadline, then task ID, then declared step order. No model calls. */
public final class Planner {
    public record Suggestion(String id, long version, String taskId, String stepId, String title,
        String taskTitle, int minutes, long start, long end, long deadline, String explanation) {}
    public record Warning(String taskId, String message) {}
    public record Result(Suggestion suggestion, String message, List<Warning> warnings, int windowMinutes) {}

    public static Result suggest(Plan p, long now) {
        List<Warning> warnings=new ArrayList<>();
        List<Task> tasks=p.tasks.stream().filter(t->t.remaining==null || t.remaining>0)
            .sorted(Comparator.comparingLong((Task t)->t.deadline).thenComparing(t->t.id)).toList();
        long end=now+p.availableMinutes*60000L;
        List<long[]> free=freeWindows(p,now,end,true);
        int window=free.stream().mapToInt(w->(int)((w[1]-w[0])/60000)).max().orElse(0);
        int cumulative=0;
        for (Task t:tasks) {
            if(t.remaining==null) { warnings.add(new Warning(t.id,"Add an effort estimate to plan this task.")); continue; }
            cumulative+=t.remaining;
            long until=Math.min(t.deadline,p.horizon);
            long total=freeWindows(p,now,until,false).stream().mapToLong(w->(w[1]-w[0])/60000).sum();
            if(t.deadline<=now) warnings.add(new Warning(t.id,"The deadline has passed. Choose a new deadline yourself or contact your instructor."));
            else if(total<cumulative) warnings.add(new Warning(t.id,"Known calendar space before this deadline is at least "+(cumulative-total)+" min short of the estimated work due by then. Revise the scope or deadline."));
            if(t.deadline>p.horizon) warnings.add(new Warning(t.id,"This deadline is beyond the calendar's 14-day planning horizon."));
        }
        if (p.sessions.stream().anyMatch(s->s.status.equals("planned")))
            return new Result(null,"You have an accepted action. Complete or skip it before choosing another.",warnings,window);
        if(!p.demo && (!p.connection.equals("connected") || now-p.lastSync>15*60000L))
            return new Result(null,"Sync Google Calendar before choosing an action; the saved schedule may be out of date.",warnings,window);
        for(Task t:tasks) {
            if(t.remaining==null || t.deadline<=now) continue;
            Set<String> completed=new HashSet<>();
            t.steps.stream().filter(s->s.done).forEach(s->completed.add(s.id));
            for(Step s:t.steps) {
                if(!s.confirmed || s.done || s.minutes<1 || s.minutes>t.remaining || p.dismissed.contains(t.id+":"+s.id)
                    || !completed.containsAll(s.dependsOn) || (p.energy.equals("low") && !s.energy.equals("low"))) continue;
                for(long[] w:free) {
                    long finish=w[0]+s.minutes*60000L;
                    if(finish>w[1] || finish>t.deadline) continue;
                    String id=p.version+":"+t.id+":"+s.id+":"+w[0];
                    String explanation="This confirmed "+s.minutes+"-minute step fits your "+p.availableMinutes+
                        "-minute window, including a "+p.bufferMinutes+"-minute buffer around calendar events. " +
                        "The earliest eligible deadline is prioritized. Effort is your estimate.";
                    return new Result(new Suggestion(id,p.version,t.id,s.id,s.title,t.title,s.minutes,w[0],finish,t.deadline,explanation),"",warnings,window);
                }
            }
        }
        String message=tasks.isEmpty()?"Nothing left to plan. Add a task or try the sample day.":window==0?
            "Your fixed commitments fill this window. Choose a later time or more availability.":
            "No confirmed step fits this window. Try more time, another energy setting, or add a smaller confirmed step.";
        return new Result(null,message,warnings,window);
    }

    public static List<long[]> freeWindows(Plan p,long start,long end,boolean includeSessions) {
        if(end<=start) return List.of();
        List<long[]> blocked=new ArrayList<>();
        long buffer=p.bufferMinutes*60000L;
        for(Event e:p.events) blocked.add(new long[]{e.start()-buffer,e.end()+buffer});
        if(includeSessions) for(Session s:p.sessions) if(s.status.equals("planned")) blocked.add(new long[]{s.start,s.end});
        blocked.sort(Comparator.comparingLong(a->a[0]));
        List<long[]> free=new ArrayList<>(); long cursor=start;
        for(long[] b:blocked) {
            if(b[1]<=cursor || b[0]>=end) continue;
            if(b[0]>cursor) free.add(new long[]{cursor,Math.min(end,b[0])});
            cursor=Math.max(cursor,b[1]); if(cursor>=end) break;
        }
        if(cursor<end) free.add(new long[]{cursor,end});
        return free;
    }

    public static void invalidateSessions(Plan p) {
        for(Session s:p.sessions) if(s.status.equals("planned")) {
            Task t=p.tasks.stream().filter(x->x.id.equals(s.taskId)).findFirst().orElse(null);
            if(t==null || s.end>t.deadline || p.events.stream().anyMatch(e->s.start<e.end()+p.bufferMinutes*60000L && s.end>e.start()-p.bufferMinutes*60000L))
                s.status="needs-review";
        }
    }

    /** Durable demo inbox delivery. Sending a prompt never completes an action. */
    public static void deliver(Plan p,long now) {
        var prefs=p.preferences;
        ZoneId zone=ZoneId.of(p.zone);
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        int hour=Instant.ofEpochMilli(now).atZone(zone).getHour();
        boolean quiet=prefs.quietStart>prefs.quietEnd ? hour>=prefs.quietStart || hour<prefs.quietEnd : hour>=prefs.quietStart && hour<prefs.quietEnd;
        long count=p.jobs.stream().filter(j->j.delivered>0 && Instant.ofEpochMilli(j.delivered).atZone(zone).toLocalDate().equals(today)).count();
        for(Job j:p.jobs) {
            if(!j.status.equals("pending")) continue;
            if(j.version!=p.version || j.expires<=now) { j.status="expired"; continue; }
            if(j.due>now || !prefs.reminders || prefs.paused || now<prefs.snoozeUntil || quiet || count>=prefs.dailyLimit) continue;
            if(suggest(p,now).suggestion()==null) {j.status="expired"; continue;}
            j.status="inbox"; j.delivered=now; count++;
        }
    }
}
