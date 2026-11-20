package dev.priceview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches the Bazaar price of ONE item at a time, only while the mouse is over it.
 * Nothing is downloaded when no item is hovered.
 */
public final class PriceData {
    private static final String URL = "https://sky.coflnet.com/api/bazaar/%s/snapshot";

    /** While hovering, download this item again every 10 seconds. */
    private static final long REFRESH_MS = 10_000;
    /** Items that are not on the Bazaar are remembered for 5 minutes. */
    private static final long NOT_SELLABLE_MS = 300_000;
    /** Safety: never send more than about one request per 0.7 s (server limit is 100/min). */
    private static final long MIN_GAP_MS = 700;

    public enum State { LOADING, SELLABLE, NOT_SELLABLE, ERROR }

    public record Info(State state, double buy, double sell) {}

    private static final Info NOT_SELLABLE = new Info(State.NOT_SELLABLE, 0, 0);

    private static final class Entry {
        volatile Info info = new Info(State.LOADING, 0, 0);
        volatile long fetchedAt = 0;
        volatile boolean inFlight = false;
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "priceview-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile long lastRequestAt = 0;

    private PriceData() {}

    /** Called while a tooltip is drawn. Returns the latest known info and refreshes it if it is old. */
    public static Info get(String id) {
        // Pets (TYPE;TIER) are never sold on the Bazaar.
        if (id.indexOf(';') >= 0) return NOT_SELLABLE;

        Entry e = CACHE.computeIfAbsent(id, k -> new Entry());
        long now = System.currentTimeMillis();
        long maxAge = e.info.state() == State.NOT_SELLABLE ? NOT_SELLABLE_MS : REFRESH_MS;

        if (!e.inFlight && now - e.fetchedAt >= maxAge && now - lastRequestAt >= MIN_GAP_MS) {
            e.inFlight = true;
            lastRequestAt = now;
            POOL.execute(() -> fetch(id, e));
        }
        return e.info;
    }

    private static void fetch(String id, Entry e) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(URL, id)))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "PriceView-Mod/1.0")
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();

            if (code == 404) {
                e.info = NOT_SELLABLE;
            } else if (code == 200) {
                JsonElement el = JsonParser.parseString(res.body());
                if (!el.isJsonObject()) {
                    e.info = NOT_SELLABLE;
                } else {
                    JsonObject o = el.getAsJsonObject();
                    double a = num(o, "buyPrice");
                    double b = num(o, "sellPrice");
                    if (a <= 0 && b <= 0) {
                        e.info = NOT_SELLABLE;
                    } else {
                        // instant buy = higher side, instant sell = lower side
                        e.info = new Info(State.SELLABLE, Math.max(a, b), Math.min(a, b));
                    }
                }
            } else {
                markErrorIfEmpty(e);
                PriceViewMod.LOGGER.warn("Price request for {} failed: HTTP {}", id, code);
            }
        } catch (Exception ex) {
            markErrorIfEmpty(e);
            PriceViewMod.LOGGER.warn("Price request for {} failed: {}", id, ex.toString());
        } finally {
            e.fetchedAt = System.currentTimeMillis();
            e.inFlight = false;
        }
    }

    private static void markErrorIfEmpty(Entry e) {
        if (e.info.state() == State.LOADING) {
            e.info = new Info(State.ERROR, 0, 0);
        }
    }

    private static double num(JsonObject o, String key) {
        JsonElement v = o.get(key);
        return (v == null || v.isJsonNull()) ? 0 : v.getAsDouble();
    }
}
