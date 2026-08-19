package EndZone.services;

import EndZone.models.ModmailLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ModmailLogServer {
    private static final Logger logger = LoggerFactory.getLogger(ModmailLogServer.class);
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(EASTERN);

    private final List<Integer> ports;
    private final List<HttpServer> servers = new ArrayList<>();

    public ModmailLogServer(int port) {
        this(List.of(port));
    }

    public ModmailLogServer(List<Integer> ports) {
        this.ports = ports == null || ports.isEmpty()
                ? List.of(8890)
                : List.copyOf(ports);
    }

    public void start() {
        if (!servers.isEmpty()) return;
        for (int port : ports) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                server.createContext("/logs/", this::handleLog);
                server.setExecutor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "modmail-log-server-" + port);
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
                servers.add(server);
                logger.info("[ModmailLogs] Serving ticket logs on port {}", port);
            } catch (IOException e) {
                logger.error("[ModmailLogs] Failed to start log server on port {}: {}", port, e.getMessage());
            }
        }
        if (servers.isEmpty()) {
            logger.error("[ModmailLogs] No log servers started — check MODMAIL_LOGS_PORTS");
        } else if (servers.size() < ports.size()) {
            logger.warn("[ModmailLogs] Started {}/{} ports (some may be in use)", servers.size(), ports.size());
        }
    }

    public void stop() {
        for (HttpServer server : servers) {
            server.stop(0);
        }
        if (!servers.isEmpty()) {
            logger.info("[ModmailLogs] Log servers stopped");
        }
        servers.clear();
    }

    public List<Integer> getPorts() {
        return ports;
    }

    private void handleLog(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String prefix = "/logs/";
            if (path == null || !path.startsWith(prefix) || path.length() <= prefix.length()) {
                send(exchange, 404, "text/plain; charset=utf-8", "Not Found");
                return;
            }

            String uuid = path.substring(prefix.length()).trim();
            int q = uuid.indexOf('?');
            if (q >= 0) uuid = uuid.substring(0, q);
            if (uuid.contains("/") || uuid.isBlank()) {
                send(exchange, 404, "text/plain; charset=utf-8", "Not Found");
                return;
            }

            ModmailService modmail = ServiceManager.getModmailService();
            if (modmail == null) {
                send(exchange, 503, "text/plain; charset=utf-8", "Service Unavailable");
                return;
            }

            ModmailLog log = modmail.findLogByUuid(uuid);
            if (log == null) {
                send(exchange, 404, "text/html; charset=utf-8", notFoundHtml());
                return;
            }

            send(exchange, 200, "text/html; charset=utf-8", renderLogHtml(log));
        } catch (Exception e) {
            logger.error("[ModmailLogs] Failed to serve log: {}", e.getMessage(), e);
            try {
                send(exchange, 500, "text/plain; charset=utf-8", "Internal Server Error");
            } catch (Exception ignored) {
            }
        }
    }

    private String renderLogHtml(ModmailLog log) {
        String category = log.getCategory() != null && !log.getCategory().isBlank()
                ? escape(log.getCategory())
                : "Ticket";
        String closer = log.getClosedByName() != null ? escape(log.getClosedByName()) : "staff";
        String opened = TIME_FMT.format(Instant.ofEpochMilli(log.getCreatedAt()));
        String closed = TIME_FMT.format(Instant.ofEpochMilli(log.getClosedAt()));
        String body = escape(log.getTranscript() == null || log.getTranscript().isBlank()
                ? "(No messages in transcript)"
                : log.getTranscript());

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\"/>\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n"
                + "  <title>EndZone Ticket Log</title>\n"
                + "  <style>\n"
                + "    body { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;\n"
                + "           margin: 0; background: #0f1115; color: #e8eaed; }\n"
                + "    header { padding: 1.25rem 1.5rem; border-bottom: 1px solid #2a2f3a; background: #161a22; }\n"
                + "    h1 { margin: 0 0 .5rem; font-size: 1.15rem; font-weight: 600; }\n"
                + "    .meta { color: #9aa3b2; font-size: .9rem; line-height: 1.5; }\n"
                + "    pre { margin: 0; padding: 1.5rem; white-space: pre-wrap; word-break: break-word;\n"
                + "          font-size: .92rem; line-height: 1.45; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header>\n"
                + "    <h1>EndZone — " + category + "</h1>\n"
                + "    <div class=\"meta\">\n"
                + "      User ID: " + escape(log.getUserId()) + "<br/>\n"
                + "      Opened: " + opened + "<br/>\n"
                + "      Closed: " + closed + " by " + closer + "\n"
                + "    </div>\n"
                + "  </header>\n"
                + "  <pre>" + body + "</pre>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String notFoundHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"/><title>Log not found</title>
                <style>body{font-family:sans-serif;background:#0f1115;color:#e8eaed;padding:2rem}</style>
                </head><body><h1>Log not found</h1><p>This ticket log does not exist.</p></body></html>
                """;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
