package com.aimoodchecker.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.function.Function;

/** One atomic versioned aggregate in a separate SQLite database. Never opens mood.db. */
public final class PlanStore {
    public static final ObjectMapper JSON=new ObjectMapper();
    private final String url;
    public PlanStore(Path file) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        url="jdbc:sqlite:"+file.toAbsolutePath();
        try(Connection c=open(); Statement s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS plan (id INTEGER PRIMARY KEY CHECK(id=1), body TEXT NOT NULL)");
            try(PreparedStatement q=c.prepareStatement("INSERT OR IGNORE INTO plan(id,body) VALUES(1,?)")) {
                q.setString(1,JSON.writeValueAsString(Plan.sample()));q.executeUpdate();
            }
        }
    }
    private Connection open() throws SQLException {
        Connection c=DriverManager.getConnection(url);
        try(Statement s=c.createStatement()) {s.execute("PRAGMA busy_timeout=5000");}
        return c;
    }
    public synchronized Plan read() throws Exception {
        try(Connection c=open();Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT body FROM plan WHERE id=1")) {
            if(!r.next()) throw new IllegalStateException("Local plan is missing.");
            return JSON.readValue(r.getString(1),Plan.class);
        }
    }
    public synchronized <T> T change(Function<Plan,T> edit) throws Exception {
        try(Connection c=open()) {
            c.setAutoCommit(false);
            try {
                Plan p;
                try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT body FROM plan WHERE id=1")) {
                    if(!r.next()) throw new IllegalStateException("Local plan is missing.");
                    p=JSON.readValue(r.getString(1),Plan.class);
                }
                T result=edit.apply(p);
                try(PreparedStatement q=c.prepareStatement("UPDATE plan SET body=? WHERE id=1")) {
                    q.setString(1,JSON.writeValueAsString(p));q.executeUpdate();
                }
                c.commit();return result;
            } catch(Exception e) { c.rollback(); throw e; }
        }
    }
    public synchronized void replace(Plan plan) throws Exception {
        try(Connection c=open();PreparedStatement q=c.prepareStatement("UPDATE plan SET body=? WHERE id=1")) {
            q.setString(1,JSON.writeValueAsString(plan));q.executeUpdate();
        }
    }
}
