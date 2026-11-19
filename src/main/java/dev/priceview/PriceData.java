package dev.priceview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Downloads and caches Bazaar + lowest-BIN prices in the background. */
public final class PriceData {
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    // Community-run lowest BIN feed (item id -> lowest BIN price).
    private static final String LOWEST_BIN_URL = "https://moulberry.codes/lowestbin.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** id -> {instantBuyPrice, instantSellPrice} */
    private static volatile Map<String, double[]> bazaar = Map.of();
    /** id -> lowest BIN */
    private static volatile Map<String, Double> bins = Map.of();

    private PriceData() {}

    public static void start() {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "priceview-refresh");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(PriceData::refresh, 0, 3, TimeUnit.MINUTES);
    }

    public static boolean isReady() {
        return !bazaar.isEmpty() || !bins.isEmpty();
    }

    public static double[] bazaar(String id) {
        return bazaar.get(id);
    }

    public static Double bin(String id) {
        return bins.get(id);
    }

    private static void refresh() {
        try {
            JsonObject root = JsonParser.parseString(fetch(BAZAAR_URL)).getAsJsonObject();
            if (root.has("success") && root.get("success").getAsBoolean()) {
                Map<String, double[]> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("products").entrySet()) {
                    JsonObject qs = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
                    map.put(e.getKey(), new double[]{
                            qs.get("buyPrice").getAsDouble(),
                            qs.get("sellPrice").getAsDouble()});
                }
                bazaar = map;
            }
        } catch (Exception ex) {
            PriceViewMod.LOGGER.warn("Bazaar refresh failed: {}", ex.toString());
        }
        try {
            JsonObject root = JsonParser.parseString(fetch(LOWEST_BIN_URL)).getAsJsonObject();
            Map<String, Double> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                map.put(e.getKey(), e.getValue().getAsDouble());
            }
            bins = map;
        } catch (Exception ex) {
            PriceViewMod.LOGGER.warn("Lowest BIN refresh failed: {}", ex.toString());
        }
    }

    private static String fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "PriceView-Mod/1.0")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + url);
        }
        return res.body();
    }
}
