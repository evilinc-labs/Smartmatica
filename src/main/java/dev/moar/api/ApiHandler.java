package dev.moar.api;

import com.sun.net.httpserver.HttpExchange;
import dev.moar.MoarMod;
import dev.moar.chest.ChestManager;
import dev.moar.stash.StashDatabase;
import dev.moar.stash.StashManager;
import dev.moar.stash.StashManager.ContainerEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

// HTTP request handlers for the embedded API.
// All responses are JSON except /metrics (Prometheus text format).
public final class ApiHandler {

    private static final String JSON_TYPE = "application/json; charset=utf-8";
    private static final String TEXT_TYPE = "text/plain; charset=utf-8";

    private final MoarProperties config;

    ApiHandler(MoarProperties config) {
        this.config = config;
    }

    // --- Helpers ---

    private StashManager stash()    { return MoarMod.getStashManager(); }
    private ChestManager chests()   { return MoarMod.getChestManager(); }
    private StashDatabase db()      { return MoarMod.getDatabase(); }

    // --- GET /api/v1/status ---

    public void handleStatus(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        StringBuilder sb = jsonObject();
        jsonField(sb, "state", stash().getState().name());
        jsonField(sb, "index_size", stash().getIndexedCount());
        jsonField(sb, "containers_found", stash().getTotalFound());
        jsonField(sb, "containers_indexed", stash().getTotalIndexed());
        jsonField(sb, "containers_failed", stash().getTotalSkipped());
        jsonField(sb, "containers_pending", stash().getRemainingCount());
        jsonField(sb, "database_connected", db().isOpen());

        var c1 = stash().getCorner1();
        var c2 = stash().getCorner2();
        if (c1 != null && c2 != null) {
            jsonField(sb, "region_pos1", posJson(c1.getX(), c1.getY(), c1.getZ()));
            jsonField(sb, "region_pos2", posJson(c2.getX(), c2.getY(), c2.getZ()));
        }
        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- GET /api/v1/containers?page=1&size=50 ---

    public void handleContainers(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        Map<String, String> params = parseQuery(ex.getRequestURI());
        int page = Math.max(1, intParam(params, "page", 1));
        int size = Math.max(1, Math.min(intParam(params, "size", 50), 200));

        var allEntries = new ArrayList<>(stash().getIndex().values());
        int totalCount = allEntries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / size));
        int from = (page - 1) * size;
        int to = Math.min(from + size, totalCount);
        var pageEntries = (from < totalCount) ? allEntries.subList(from, to) : List.<ContainerEntry>of();

        StringBuilder sb = jsonObject();
        jsonField(sb, "page", page);
        jsonField(sb, "page_size", size);
        jsonField(sb, "total_count", totalCount);
        jsonField(sb, "total_pages", totalPages);
        sb.append("\"containers\":[");
        for (int i = 0; i < pageEntries.size(); i++) {
            if (i > 0) sb.append(',');
            containerJson(sb, pageEntries.get(i));
        }
        sb.append(']');
        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- GET /api/v1/search?item=diamond ---

    public void handleSearch(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        String item = parseQuery(ex.getRequestURI()).get("item");
        if (item == null || item.isBlank()) {
            sendError(ex, 400, "Missing required parameter: item");
            return;
        }

        String lower = item.toLowerCase(Locale.ROOT);
        List<ContainerEntry> matches = new ArrayList<>();
        int totalQty = 0;
        for (ContainerEntry entry : stash().getIndex().values()) {
            for (var e : entry.items().entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).contains(lower)) {
                    matches.add(entry);
                    totalQty += e.getValue();
                    break;
                }
            }
        }

        StringBuilder sb = jsonObject();
        jsonField(sb, "query", item);
        jsonField(sb, "total_item_count", totalQty);
        jsonField(sb, "container_count", matches.size());
        sb.append("\"containers\":[");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append(',');
            containerJson(sb, matches.get(i));
        }
        sb.append(']');
        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- GET /api/v1/stats ---

    public void handleStats(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        int totalContainers = stash().getIndexedCount();
        long totalItems = 0;
        Set<String> uniqueTypes = new HashSet<>();
        int shulkerCount = 0;
        Map<String, Integer> byType = new LinkedHashMap<>();

        for (ContainerEntry entry : stash().getIndex().values()) {
            totalItems += entry.totalItemCount();
            uniqueTypes.addAll(entry.items().keySet());
            shulkerCount += entry.shulkerCount();
            byType.merge(entry.blockType(), 1, Integer::sum);
        }

        // Overlay DB stats if available
        if (db().isOpen()) {
            totalContainers = Math.max(totalContainers, db().countContainers());
            int dbItems = db().countTotalItems();
            if (dbItems > totalItems) totalItems = dbItems;
            int dbTypes = db().countItemTypes();
            if (dbTypes > uniqueTypes.size()) uniqueTypes.clear(); // replaced below
        }

        StringBuilder sb = jsonObject();
        jsonField(sb, "total_containers", totalContainers);
        jsonField(sb, "total_items", totalItems);
        jsonField(sb, "unique_item_types", uniqueTypes.isEmpty() && db().isOpen()
                ? db().countItemTypes() : uniqueTypes.size());
        jsonField(sb, "total_shulkers", shulkerCount);
        jsonField(sb, "scanner_state", stash().getState().name());
        jsonField(sb, "scan_containers_found", stash().getTotalFound());
        jsonField(sb, "scan_containers_indexed", stash().getTotalIndexed());
        jsonField(sb, "scan_containers_failed", stash().getTotalSkipped());
        jsonField(sb, "scan_containers_pending", stash().getRemainingCount());

        // containers by type
        sb.append("\"containers_by_type\":{");
        int i = 0;
        for (var e : byType.entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append('"').append(escape(e.getKey())).append("\":").append(e.getValue());
        }
        sb.append('}');

        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- GET /api/v1/metrics (Prometheus format) ---

    public void handleMetrics(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        StringBuilder sb = new StringBuilder(1024);

        metric(sb, "moar_containers_total", "gauge",
                "Total number of indexed containers", stash().getIndexedCount());

        metric(sb, "moar_scanner_state", "gauge",
                "Scanner state (0=IDLE,1=ZONE_SCANNING,2=WALKING,3=OPENING,4=READING,5=WALKING_TO_ZONE,6=DONE)",
                stash().getState().ordinal());

        metric(sb, "moar_scan_containers_found", "gauge",
                "Containers found in current/last scan", stash().getTotalFound());

        metric(sb, "moar_scan_containers_indexed", "gauge",
                "Containers successfully indexed", stash().getTotalIndexed());

        metric(sb, "moar_scan_containers_failed", "gauge",
                "Containers failed to index", stash().getTotalSkipped());

        metric(sb, "moar_scan_containers_pending", "gauge",
                "Containers remaining in current scan", stash().getRemainingCount());

        metric(sb, "moar_database_connected", "gauge",
                "Whether the database is connected (1=yes, 0=no)", db().isOpen() ? 1 : 0);

        // DB-backed metrics
        if (db().isOpen()) {
            metric(sb, "moar_items_total", "gauge",
                    "Total items across all containers", db().countTotalItems());
            metric(sb, "moar_unique_item_types", "gauge",
                    "Distinct item types", db().countItemTypes());
            metric(sb, "moar_db_containers", "gauge",
                    "Containers persisted in database", db().countContainers());
        }

        // Organizer metrics
        var org = stash().getOrganizer();
        metric(sb, "moar_organizer_active", "gauge",
                "Whether the organizer is running (1=yes, 0=no)", org.isActive() ? 1 : 0);
        metric(sb, "moar_organizer_tasks_completed", "gauge",
                "Organizer tasks completed", org.getCompletedTasks());
        metric(sb, "moar_organizer_tasks_total", "gauge",
                "Organizer tasks planned", org.getTotalTasks());

        // Supply chest metrics
        metric(sb, "moar_supply_chests", "gauge",
                "Number of registered supply chests", chests().supplyChestCount());

        sendText(ex, 200, sb.toString());
    }

    // --- GET /api/v1/organizer ---

    public void handleOrganizer(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!checkGet(ex)) return;

        var org = stash().getOrganizer();
        StringBuilder sb = jsonObject();
        jsonField(sb, "state", org.getState().name());
        jsonField(sb, "active", org.isActive());
        jsonField(sb, "completed_tasks", org.getCompletedTasks());
        jsonField(sb, "total_tasks", org.getTotalTasks());
        jsonField(sb, "status", org.getStatus());
        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- POST /api/v1/webhook/test ---

    public void handleWebhookTest(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed. Expected: POST");
            return;
        }

        StringBuilder sb = jsonObject();
        jsonField(sb, "status", "ok");
        jsonField(sb, "message", "Webhook connectivity confirmed");
        jsonField(sb, "timestamp", System.currentTimeMillis());
        jsonField(sb, "scanner_state", stash().getState().name());
        jsonClose(sb);
        sendJson(ex, 200, sb);
    }

    // --- Webhook dispatch (called externally after scan completes) ---

    public static void fireScanComplete(MoarProperties config, StashManager stash) {
        String url = config.getWebhookUrl();
        if (url == null || url.isBlank()) return;

        StringBuilder sb = jsonObject();
        jsonField(sb, "event", "scan_complete");
        jsonField(sb, "containers_found", stash.getTotalFound());
        jsonField(sb, "containers_indexed", stash.getTotalIndexed());
        jsonField(sb, "containers_failed", stash.getTotalSkipped());
        jsonField(sb, "timestamp", System.currentTimeMillis());
        jsonClose(sb);

        // Fire-and-forget POST on a daemon thread
        String body = sb.toString();
        Thread.ofVirtual().name("moar-webhook").start(() -> {
            try {
                var conn = (java.net.HttpURLConnection) new java.net.URI(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Content-Type", "application/json");
                try (var out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode(); // consume response
                conn.disconnect();
            } catch (Exception e) {
                MoarMod.LOGGER.warn("Webhook POST failed: {}", e.getMessage());
            }
        });
    }

    // --- Auth ---

    private boolean checkAuth(HttpExchange ex) throws IOException {
        String key = config.getApiKey();
        if (key == null || key.isBlank()) return true;

        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && key.equals(auth.substring(7))) {
            return true;
        }
        sendError(ex, 401, "Unauthorized: invalid or missing API key");
        return false;
    }

    private boolean checkGet(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) return true;
        sendError(ex, 405, "Method not allowed. Expected: GET");
        return false;
    }

    // --- JSON helpers (no dependency on Gson) ---

    private static StringBuilder jsonObject() {
        return new StringBuilder(512).append('{');
    }

    private static void jsonClose(StringBuilder sb) {
        // Remove trailing comma if present, then close
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        sb.append('}');
    }

    private static void jsonField(StringBuilder sb, String key, String val) {
        sb.append('"').append(key).append("\":\"").append(escape(val)).append("\",");
    }

    private static void jsonField(StringBuilder sb, String key, long val) {
        sb.append('"').append(key).append("\":").append(val).append(',');
    }

    private static void jsonField(StringBuilder sb, String key, boolean val) {
        sb.append('"').append(key).append("\":").append(val).append(',');
    }

    // For raw JSON values (nested objects)
    private static void jsonField(StringBuilder sb, String key, StringBuilder raw) {
        sb.append('"').append(key).append("\":").append(raw).append(',');
    }

    private static StringBuilder posJson(int x, int y, int z) {
        return new StringBuilder().append("{\"x\":").append(x)
                .append(",\"y\":").append(y).append(",\"z\":").append(z).append('}');
    }

    private void containerJson(StringBuilder sb, ContainerEntry entry) {
        sb.append("{\"x\":").append(entry.pos().getX());
        sb.append(",\"y\":").append(entry.pos().getY());
        sb.append(",\"z\":").append(entry.pos().getZ());
        sb.append(",\"block_type\":\"").append(escape(entry.blockType())).append('"');
        sb.append(",\"is_double\":").append(entry.isDouble());
        sb.append(",\"total_items\":").append(entry.totalItemCount());
        sb.append(",\"shulker_count\":").append(entry.shulkerCount());
        sb.append(",\"timestamp\":").append(entry.timestamp());

        sb.append(",\"items\":[");
        int i = 0;
        for (var e : entry.items().entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append("{\"id\":\"").append(escape(e.getKey())).append('"');
            sb.append(",\"quantity\":").append(e.getValue()).append('}');
        }
        sb.append("]}");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void metric(StringBuilder sb, String name, String type, String help, Number val) {
        sb.append("# HELP ").append(name).append(' ').append(help).append('\n');
        sb.append("# TYPE ").append(name).append(' ').append(type).append('\n');
        sb.append(name).append(' ').append(val).append('\n');
    }

    private static void sendJson(HttpExchange ex, int status, StringBuilder body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", JSON_TYPE);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", TEXT_TYPE);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void sendError(HttpExchange ex, int status, String msg) throws IOException {
        StringBuilder sb = jsonObject();
        jsonField(sb, "error", msg);
        jsonClose(sb);
        sendJson(ex, status, sb);
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String q = uri.getRawQuery();
        if (q == null) return params;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(
                        URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static int intParam(Map<String, String> params, String key, int def) {
        String v = params.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return def; }
    }
}
