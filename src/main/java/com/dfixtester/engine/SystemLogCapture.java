package com.dfixtester.engine;

import org.springframework.stereotype.Component;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SystemLogCapture {
    private static final List<LogEntry> logEntries = new CopyOnWriteArrayList<>();
    private static int logIdCounter = 0;

    public static class LogEntry {
        public final int id;
        public final String message;
        public LogEntry(int id, String message) {
            this.id = id;
            this.message = message;
        }
    }

    public SystemLogCapture() {
        System.setOut(createInterceptor(System.out));
        System.setErr(createInterceptor(System.err));
    }

    private PrintStream createInterceptor(PrintStream original) {
        return new PrintStream(new OutputStream() {
            final StringBuilder buffer = new StringBuilder();

            @Override
            public void write(byte[] b, int off, int len) {
                original.write(b, off, len);
                String str = new String(b, off, len);
                synchronized (buffer) {
                    buffer.append(str);
                    int idx;
                    while ((idx = buffer.indexOf("\n")) != -1) {
                        String line = buffer.substring(0, idx).replaceAll("\r", "");
                        logEntries.add(new LogEntry(++logIdCounter, line));
                        if (logEntries.size() > 1000) logEntries.remove(0);
                        buffer.delete(0, idx + 1);
                    }
                }
            }

            @Override
            public void write(int b) {
                write(new byte[]{(byte) b}, 0, 1);
            }
        }, true);
    }

    public static List<LogEntry> getLogEntries() { return logEntries; }
    
    public static void clear() { logEntries.clear(); }
}