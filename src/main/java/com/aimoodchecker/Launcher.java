package com.aimoodchecker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Packaged desktop entry point; never stores journals beside the installed program. */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) {
        List<String> applicationArguments = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            if ("--offline".equals(args[index])) System.setProperty("aimoodchecker.offline", "true");
            else if ("--data-dir".equals(args[index])) {
                if (++index >= args.length) throw new IllegalArgumentException("--data-dir requires a directory");
                System.setProperty("aimoodchecker.dataDir", Path.of(args[index]).toAbsolutePath().normalize().toString());
            } else applicationArguments.add(args[index]);
        }
        if (System.getProperty("aimoodchecker.dataDir") == null) {
            String local = System.getenv("LOCALAPPDATA");
            Path data = local != null && !local.isBlank() ? Path.of(local) : Path.of(System.getProperty("user.home"), ".local", "share");
            System.setProperty("aimoodchecker.dataDir", data.resolve("AIMoodChecker").toString());
        }
        Main.main(applicationArguments.toArray(String[]::new));
    }
}
