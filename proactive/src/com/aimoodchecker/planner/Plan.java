package com.aimoodchecker.planner;

import java.time.*;
import java.util.*;

/** Local, single-person prototype. Timestamps are epoch milliseconds; dates use an explicit zone. */
public final class Plan {
    public long version = 1;
    public boolean demo = true;
    public long demoNow;
    public String zone = "America/Vancouver";
    public int availableMinutes = 40;
    public String energy = "any";
    public long energyExpiresAt;
    public List<CheckIn> checkIns = new ArrayList<>();
    public List<SupportAction> supportActions = new ArrayList<>();
    public int bufferMinutes = 5;
    public String reason = "A little room to move your day forward.";
    public String connection = "sample";
    public long lastSync;
    public long horizon;
    public List<Event> events = new ArrayList<>();
    public List<Task> tasks = new ArrayList<>();
    public List<Session> sessions = new ArrayList<>();
    public List<Job> jobs = new ArrayList<>();
    public Set<String> responses = new HashSet<>();
    public Set<String> dismissed = new HashSet<>();
    public Preferences preferences = new Preferences();

    public static final class Preferences {
        public boolean reminders = false;
        public boolean paused = false;
        public int quietStart = 21;
        public int quietEnd = 9;
        public int dailyLimit = 1;
        public long snoozeUntil;
    }
    public record Event(String id, String title, long start, long end, String source, String etag) {}
    public static final class Task {
        public String id;
        public String title;
        public long deadline;
        public Integer remaining;
        public List<Step> steps = new ArrayList<>();
        public Task() {}
        public Task(String id, String title, long deadline, Integer remaining, Step... steps) {
            this.id=id; this.title=title; this.deadline=deadline; this.remaining=remaining;
            this.steps.addAll(List.of(steps));
        }
    }
    public static final class Step {
        public String id;
        public String title;
        public int minutes;
        public String energy;
        public boolean confirmed;
        public boolean done;
        public List<String> dependsOn = new ArrayList<>();
        public Step() {}
        public Step(String id, String title, int minutes, String energy, String... dependsOn) {
            this.id=id; this.title=title; this.minutes=minutes; this.energy=energy; this.confirmed=true;
            this.dependsOn.addAll(List.of(dependsOn));
        }
    }
    public static final class Session {
        public String id, taskId, stepId, title, status;
        public long start, end;
        public int minutes;
        public Session() {}
        public Session(String id, Task task, Step step, long start) {
            this.id=id; taskId=task.id; stepId=step.id; title=step.title; status="planned";
            this.start=start; minutes=step.minutes; end=start+minutes*60000L;
        }
    }
    /** User-reported context only; no mood score or diagnosis is inferred. */
    public static final class CheckIn {
        public String id, mood, energy;
        public long created, expires;
    }
    public static final class SupportAction {
        public String id, kind, title, cue, status="planned", helpfulness="unanswered";
        public int minutes;
        public long start, end, created;
        public boolean scheduleChecked;
    }
    public static final class Job {
        public String id, reason, status="pending";
        public long version, due, expires, delivered;
        public Job() {}
        public Job(String id, String reason, long version, long due) {
            this.id=id; this.reason=reason; this.version=version; this.due=due; expires=due+4*3600000L;
        }
    }
    public static Plan sample() {
        Plan p=new Plan();
        p.demoNow=ZonedDateTime.of(2026,10,5,17,45,0,0,ZoneId.of(p.zone)).toInstant().toEpochMilli();
        p.lastSync=p.demoNow; p.horizon=p.demoNow+14*86400000L;
        long day=LocalDate.of(2026,10,5).atStartOfDay(ZoneId.of(p.zone)).toInstant().toEpochMilli();
        p.events.add(new Event("shift","Café shift",day+14*3600000L,day+17*3600000L+30*60000,"sample","1"));
        p.events.add(new Event("dinner","Dinner with Sam",day+18*3600000L+35*60000,day+19*3600000L+5*60000,"sample","1"));
        p.tasks.add(new Task("report","Research methods report",day+20*3600000L,90,
            new Step("outline","Outline the three key findings",20,"low"),
            new Step("draft","Write the findings section",40,"focused","outline"),
            new Step("review","Review and submit the report",30,"focused","draft")));
        p.tasks.add(new Task("stats","Statistics problem set",day+44*3600000L,50,
            new Step("question","Work through question one",25,"focused"),
            new Step("notes","Review the worked example",10,"low")));
        return p;
    }
}
