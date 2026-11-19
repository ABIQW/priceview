package dev.priceview;

import com.google.gson.JsonArray;
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
        ScheduledExecutorService ex = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "priceview-refresh");
            t.setDaemon(true);
            return t;
        });
        // Hypixel refreshes Bazaar data about every 10-20s, so 10s is the useful minimum.
        ex.scheduleWithFixedDelay(PriceData::refreshBazaar, 0, 10, TimeUnit.SECONDS);
        ex.scheduleWithFixedDelay(PriceData::refreshBins, 0, 60, TimeUnit.SECONDS);
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

    private static void refreshBazaar() {
        try {
            JsonObject root = JsonParser.parseString(fetch(BAZAAR_URL)).getAsJsonObject();
            if (root.has("success") && root.get("success").getAsBoolean()) {
                Map<String, double[]> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("products").entrySet()) {
                    JsonObject p = e.getValue().getAsJsonObject();
                    JsonObject qs = p.getAsJsonObject("quick_status");
                    double buy = qs.get("buyPrice").getAsDouble();
                    double sell = qs.get("sellPrice").getAsDouble();

                    // Top of the order book = the price you really pay / get for an instant trade.
                    JsonArray sellOrders = p.getAsJsonArray("sell_summary");
                    JsonArray buyOrders = p.getAsJsonArray("buy_summary");
                    if (sellOrders != null && sellOrders.size() > 0) {
                        buy = sellOrders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
                    }
                    if (buyOrders != null && buyOrders.size() > 0) {
                        sell = buyOrders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
                    }
                    map.put(e.getKey(), new double[]{buy, sell});
                }
                bazaar = map;
            }
        } catch (Exception ex) {
            PriceViewMod.LOGGER.warn("Bazaar refresh failed: {}", ex.toString());
        }
    }

    private static void refreshBins() {
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
