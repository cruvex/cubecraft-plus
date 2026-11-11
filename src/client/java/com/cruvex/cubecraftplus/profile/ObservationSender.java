package com.cruvex.cubecraftplus.profile;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Posts new observations to the tracker API in batches. Client thread only. */
public class ObservationSender {

    /** The API base URL, e.g. {@code -Dcubecraftplus.api=http://localhost:8080}; nothing is sent without it. */
    private static final String API_PROPERTY = "cubecraftplus.api";
    private static final long FLUSH_INTERVAL_MS = 5000;
    private static final int MAX_BATCH = 500;

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static ObservationSender instance;

    private final @Nullable URI endpoint = endpoint();
    private final List<PlayerObservation> pending = new ArrayList<>();
    private long nextFlushAt;

    public static ObservationSender getInstance() {
        if (instance == null) {
            instance = new ObservationSender();
        }
        return instance;
    }

    public void init() {
        // Logged directly: at init there is no client yet to mirror Debug output to
        CubeCraftPlusClient.LOGGER.info("Observations: sending to {}",
                endpoint == null ? "nowhere, no API configured" : endpoint);
        ClientTickEvents.END_CLIENT_TICK.register(client -> flush());
    }

    public void add(PlayerObservation observation) {
        if (endpoint != null && observation.uuid() != null) {
            pending.add(observation);
        }
    }

    private void flush() {
        long now = System.currentTimeMillis();
        if (pending.isEmpty() || now < nextFlushAt) return;

        nextFlushAt = now + FLUSH_INTERVAL_MS;
        List<PlayerObservation> head = pending.subList(0, Math.min(pending.size(), MAX_BATCH));
        List<Entry> batch = head.stream().map(Entry::of).toList();
        head.clear();

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("User-Agent", "CubeCraftPlus-fabric-mod")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(batch)))
                .build();

        // Failed batches are dropped; the profile store offers them again once they are due a refresh
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null) {
                Debug.log("Observations: sending {} failed: {}", batch.size(), error.toString());
            } else {
                Debug.log("Observations: sent {}, API answered {} {}", batch.size(), response.statusCode(), response.body());
            }
        });
    }

    private static @Nullable URI endpoint() {
        String base = System.getProperty(API_PROPERTY);
        return base == null ? null : URI.create(base.replaceAll("/+$", "") + "/api/observations");
    }

    private record Entry(String uuid, String name, List<String> ranks, boolean legacy, int level, String observedAt) {

        static Entry of(PlayerObservation observation) {
            return new Entry(observation.uuid().toString(), observation.name(),
                    observation.ranks().stream().map(Rank::name).toList(), observation.legacy(),
                    observation.level(), observation.observedAt().toString());
        }
    }
}
