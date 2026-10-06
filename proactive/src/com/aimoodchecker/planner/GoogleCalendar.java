package com.aimoodchecker.planner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.time.*;
import java.util.*;
import static com.aimoodchecker.planner.PlanStore.JSON;

/** Desktop OAuth with PKCE. Tokens stay in this process and are never returned to the browser. */
public final class GoogleCalendar {
    public interface Transport { JsonNode request(String method,String url,String body,String token) throws Exception; }
    private final Transport transport;
    private final String clientId,clientSecret,redirect;
    private final Clock clock;
    private String state,verifier,accessToken,refreshToken;
    private long stateExpires,tokenExpires;
    public GoogleCalendar(String id,String secret,String redirect,Clock clock,Transport transport) {
        clientId=id==null?"":id;clientSecret=secret==null?"":secret;this.redirect=redirect;this.clock=clock;this.transport=transport;
    }
    public boolean configured() {return !clientId.isBlank();}
    public synchronized boolean connected() {return accessToken!=null;}
    public synchronized String begin() throws Exception {
        PlanService.require(configured(),"Set GOOGLE_CLIENT_ID for a Desktop OAuth client, then restart the app. See the setup guide.");
        state=random();verifier=random();stateExpires=clock.millis()+10*60000L;
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        return "https://accounts.google.com/o/oauth2/v2/auth?"+form(Map.of("client_id",clientId,"redirect_uri",redirect,
            "response_type","code","scope","https://www.googleapis.com/auth/calendar.events.readonly",
            "state",state,"code_challenge",challenge,"code_challenge_method","S256","access_type","offline","prompt","consent"));
    }
    public synchronized void finish(Map<String,String> query) throws Exception {
        PlanService.require(state!=null && clock.millis()<stateExpires && MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),query.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)),"Google sign-in expired or did not match. Start again from the app.");
        state=null;
        PlanService.require(!query.containsKey("error") && query.containsKey("code"),"Google access was not granted. You can try again from Connections.");
        Map<String,String> fields=new HashMap<>(Map.of("client_id",clientId,"code",query.get("code"),"code_verifier",verifier,"redirect_uri",redirect,"grant_type","authorization_code"));
        if(!clientSecret.isBlank()) fields.put("client_secret",clientSecret);
        tokens(transport.request("POST","https://oauth2.googleapis.com/token",form(fields),null));verifier=null;
    }
    public synchronized void disconnect() {accessToken=null;refreshToken=null;verifier=null;state=null;tokenExpires=0;}
    private void tokens(JsonNode n) {
        PlanService.require(n.path("access_token").isTextual(),"Google did not provide access. Reconnect from the app.");
        accessToken=n.path("access_token").asText();tokenExpires=clock.millis()+Math.max(0,n.path("expires_in").asLong(3600)-60)*1000;
        if(n.path("refresh_token").isTextual()) refreshToken=n.path("refresh_token").asText();
    }
    public synchronized List<Plan.Event> sync(long start,long end) throws Exception {
        PlanService.require(connected(),"Connect Google Calendar first.");
        if(clock.millis()>=tokenExpires) {
            PlanService.require(refreshToken!=null,"Reconnect Google Calendar; this session expired.");
            Map<String,String> fields=new HashMap<>(Map.of("client_id",clientId,"refresh_token",refreshToken,"grant_type","refresh_token"));
            if(!clientSecret.isBlank()) fields.put("client_secret",clientSecret);
            tokens(transport.request("POST","https://oauth2.googleapis.com/token",form(fields),null));
        }
        // Bounded full snapshots avoid mixing syncToken with timeMin/timeMax. Replace only after every page succeeds.
        String base="https://www.googleapis.com/calendar/v3/calendars/primary/events?"+form(Map.of(
            "singleEvents","true","showDeleted","true","maxResults","250","timeMin",Instant.ofEpochMilli(start).toString(),"timeMax",Instant.ofEpochMilli(end).toString(),
            "fields","timeZone,nextPageToken,items(id,etag,summary,status,transparency,start,end,attendees(self,responseStatus))"));
        Map<String,Plan.Event> events=new TreeMap<>();String page="";Set<String> seen=new HashSet<>();
        for(int count=0;count<10;count++) {
            JsonNode data=transport.request("GET",base+(page.isEmpty()?"":"&pageToken="+encode(page)),null,accessToken);
            for(Plan.Event e:parse(data)) events.put(e.id(),e);
            page=data.path("nextPageToken").asText("");
            if(page.isEmpty()) return new ArrayList<>(events.values());
            PlanService.require(seen.add(page),"Google returned a repeated page. Your saved calendar is unchanged.");
        }
        throw new IllegalArgumentException("This calendar exceeds the prototype's sync limit. Your saved calendar is unchanged.");
    }
    public static List<Plan.Event> parse(JsonNode data) {
        PlanService.require(data.path("items").isArray(),"Google returned an incomplete calendar. Your saved calendar is unchanged.");
        ZoneId zone=ZoneId.of(data.path("timeZone").asText("UTC"));List<Plan.Event> events=new ArrayList<>();
        for(JsonNode e:data.path("items")) {
            if(e.path("status").asText().equals("cancelled") || e.path("transparency").asText().equals("transparent")) continue;
            boolean declined=false;
            for(JsonNode a:e.path("attendees")) if(a.path("self").asBoolean() && a.path("responseStatus").asText().equals("declined")) declined=true;
            if(declined) continue;
            String id=e.path("id").asText();PlanService.require(!id.isBlank(),"Calendar event is missing its source identifier.");
            long start=eventTime(e.path("start"),zone),end=eventTime(e.path("end"),zone);
            PlanService.require(end>start,"Calendar event has an invalid duration.");
            String title=e.path("summary").asText("Busy");
            events.add(new Plan.Event(id,title.substring(0,Math.min(300,title.length())),start,end,"google",e.path("etag").asText("")));
        }
        return events;
    }
    private static long eventTime(JsonNode n,ZoneId zone) {
        if(n.path("dateTime").isTextual()) return OffsetDateTime.parse(n.path("dateTime").asText()).toInstant().toEpochMilli();
        if(n.path("date").isTextual()) return LocalDate.parse(n.path("date").asText()).atStartOfDay(zone).toInstant().toEpochMilli();
        throw new IllegalArgumentException("Calendar event has no supported date.");
    }
    public static Transport network() {
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (method,url,body,token)->{
            HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json");
            if(token!=null) request.header("Authorization","Bearer "+token);
            if(method.equals("POST")) request.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
            var pending=client.sendAsync(request.build(),info->new LimitedBody());
            HttpResponse<byte[]> response;
            try {response=pending.get(25,TimeUnit.SECONDS);}
            finally {if(!pending.isDone())pending.cancel(true);}
            PlanService.require(response.statusCode()==200,"Google sync or authorization failed (HTTP "+response.statusCode()+"). Your saved calendar is unchanged. Reconnect if access was revoked.");
            return JSON.readTree(response.body());
        };
    }
    /** Complete the body within the request timeout and cancel before allocating an unbounded response. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() {return result;}
        public void onSubscribe(Flow.Subscription s) {subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> parts) {
            for(ByteBuffer part:parts) {
                if(buffer.size()+part.remaining()>1024*1024) {subscription.cancel();result.completeExceptionally(new IOException("Google response exceeded the size limit."));return;}
                byte[] bytes=new byte[part.remaining()];part.get(bytes);buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) {result.completeExceptionally(error);}
        public void onComplete() {result.complete(buffer.toByteArray());}
    }
    private static String random() {byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String encode(String s) {return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static String form(Map<String,String> data) {return data.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).sorted().collect(java.util.stream.Collectors.joining("&"));}
}
