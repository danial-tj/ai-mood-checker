package com.aimoodchecker.planner;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.aimoodchecker.planner.PlanStore.JSON;

/** Loopback-only development server; not an authenticated public deployment. */
public final class PlannerServer {
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(System.getProperty("planner.port","8471"));
        Path root=Path.of(System.getProperty("planner.root","proactive")).toAbsolutePath();
        PlanStore store=new PlanStore(Path.of(System.getProperty("planner.data",root.resolve("data/planner.db").toString())));
        PlanService service=new PlanService(store,Clock.systemUTC());
        String origin="http://127.0.0.1:"+port;
        GoogleCalendar google=new GoogleCalendar(System.getenv("GOOGLE_CLIENT_ID"),System.getenv("GOOGLE_CLIENT_SECRET"),origin+"/oauth/callback",Clock.systemUTC(),GoogleCalendar.network());
        if(!store.read().demo) service.syncFailed("disconnected");
        String csrf=UUID.randomUUID().toString();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);
        server.setExecutor(Executors.newFixedThreadPool(4));
        Object mutationLock=new Object();
        Runnable sync=()->{
            synchronized(mutationLock) {
                try {
                    long now=System.currentTimeMillis();
                    service.applyCalendar(google.sync(now-86400000L,now+14*86400000L),now,now+14*86400000L);
                } catch(Exception e) {
                    try {service.syncFailed("error");} catch(Exception ignored) {}
                    throw new IllegalArgumentException("Google sync failed. The previous calendar was kept. Check access and try Sync again.");
                }
            }
        };
        server.createContext("/",exchange->{
            try {
                String host=exchange.getRequestHeaders().getFirst("Host");
                if(!Objects.equals(host,"127.0.0.1:"+port)) {send(exchange,403,"text/plain","Open the app using 127.0.0.1.");return;}
                String path=exchange.getRequestURI().getPath();
                if(path.equals("/oauth/callback") && exchange.getRequestMethod().equals("GET")) {
                    synchronized(mutationLock) {google.finish(query(exchange.getRequestURI().getRawQuery()));sync.run();}
                    exchange.getResponseHeaders().set("Location","/#connections");exchange.sendResponseHeaders(303,-1);return;
                }
                if(path.equals("/api/state") && exchange.getRequestMethod().equals("GET")) {
                    Map<String,Object> result=new HashMap<>(service.view());result.put("csrf",csrf);
                    result.put("googleConfigured",google.configured());result.put("googleConnected",google.connected());
                    send(exchange,200,"application/json",JSON.writeValueAsString(result));return;
                }
                if(path.equals("/api/export") && exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=ai-mood-checker-plan.json");
                    send(exchange,200,"application/json",JSON.writerWithDefaultPrettyPrinter().writeValueAsString(store.read()));return;
                }
                if(path.equals("/api/action") && exchange.getRequestMethod().equals("POST")) {
                    if(!origin.equals(exchange.getRequestHeaders().getFirst("Origin")) || !csrf.equals(exchange.getRequestHeaders().getFirst("X-Planner-Token"))) {send(exchange,403,"application/json","{\"error\":\"Reload the app before making changes.\"}");return;}
                    byte[] bytes=exchange.getRequestBody().readNBytes(65537);
                    PlanService.require(bytes.length<=65536,"Request too large.");
                    var body=JSON.readTree(bytes);
                    PlanService.require(body!=null && body.isObject(),"Send a valid action.");
                    synchronized(mutationLock) {
                        String action=body.path("action").asText();
                        switch(action) {
                            case "google-connect" -> {
                                PlanService.require(!store.read().demo,"Start your own plan before connecting a personal calendar.");
                                send(exchange,200,"application/json",JSON.writeValueAsString(Map.of("url",google.begin())));return;
                            }
                            case "google-sync" -> sync.run();
                            case "google-disconnect" -> {google.disconnect();service.syncFailed("disconnected");}
                            default -> {if(action.equals("clear") || action.equals("reset")) google.disconnect();service.act(body);}
                        }
                    }
                    send(exchange,200,"application/json","{\"ok\":true}");return;
                }
                if(!exchange.getRequestMethod().equals("GET")) {send(exchange,405,"text/plain","Method not allowed");return;}
                Map<String,String> files=Map.of("/","index.html","/app.js","app.js","/style.css","style.css");
                if(!files.containsKey(path)) {send(exchange,404,"text/plain","Not found");return;}
                String content=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";
                send(exchange,200,content,Files.readString(root.resolve("web").resolve(files.get(path))));
            } catch(IllegalArgumentException e) {send(exchange,409,"application/json",JSON.writeValueAsString(Map.of("error",e.getMessage()==null?"Check your input and try again.":e.getMessage())));}
            catch(Exception e) {send(exchange,500,"application/json","{\"error\":\"The local app could not finish this request. Your saved plan was kept.\"}");}
            finally {exchange.close();}
        });
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(()->{try {service.tick();}catch(Exception ignored){}},10,10,TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(()->{if(google.connected())try{sync.run();}catch(Exception ignored){}},5,5,TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{worker.shutdownNow();server.stop(0);}));
        server.start();System.out.println("AI Mood Checker local prototype: "+origin+" (sample data; no phone push delivery)");
    }
    private static void send(HttpExchange e,int status,String type,String text) throws java.io.IOException {
        e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");
        e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.getResponseHeaders().set("Referrer-Policy","no-referrer");
        e.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);
    }
    private static Map<String,String> query(String raw) {
        Map<String,String> result=new HashMap<>();if(raw==null)return result;
        for(String item:raw.split("&")) {String[] pair=item.split("=",2);if(pair.length==2)result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
        return result;
    }
}
