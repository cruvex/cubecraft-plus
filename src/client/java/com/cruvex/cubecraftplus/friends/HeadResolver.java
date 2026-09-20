package com.cruvex.cubecraftplus.friends;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Friends' heads, and the Mojang uuids behind them, kept forever so a renamed friend keeps the right head. */
public class HeadResolver {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TypeToken<Map<String, UUID>> IDS = new TypeToken<>() {};
    /** How often a new head may start loading, so scrolling a long list does not burst Mojang's session server. */
    private static final long HEAD_INTERVAL_MS = 100;
    /** How long every new head waits after a failed profile request, which is usually a rate limit. */
    private static final long FAILURE_PAUSE_MS = 3000;
    /** Profile requests per uuid before it is given up on, a deleted account failing every time. */
    private static final int MAX_ATTEMPTS = 3;

    private static HeadResolver instance;

    /** Uuids by lowercase name; written on the lookup thread, read on the client thread. */
    private final Map<String, UUID> ids = new ConcurrentHashMap<>(load());
    private CompletableFuture<Void> lookup = CompletableFuture.completedFuture(null);
    /** Profiles fetched with their skin data, for vanilla to draw; written on fetch threads. */
    private final Map<UUID, ResolvableProfile> heads = new ConcurrentHashMap<>();
    private final Set<UUID> fetching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();
    private volatile long nextHeadAt;

    public static HeadResolver getInstance() {
        if (instance == null) {
            instance = new HeadResolver();
        }
        return instance;
    }

    public @Nullable UUID idFor(String name) {
        return ids.get(name.toLowerCase(Locale.ROOT));
    }

    /** The head to draw for a name, or null while it has no uuid or its profile is still loading. */
    public @Nullable ResolvableProfile headFor(String name) {
        UUID id = idFor(name);
        if (id == null) return null;

        ResolvableProfile head = heads.get(id);
        long now = System.currentTimeMillis();
        if (head == null && now >= nextHeadAt && failures.getOrDefault(id, 0) < MAX_ATTEMPTS && fetching.add(id)) {
            nextHeadAt = now + HEAD_INTERVAL_MS;
            fetchHead(id);
        }
        return head;
    }

    // Fetched here rather than by vanilla, which caches a failed lookup for 10 minutes and cannot retry
    private void fetchHead(UUID id) {
        MinecraftSessionService sessions = Minecraft.getInstance().services().sessionService();
        CompletableFuture.runAsync(() -> {
            ProfileResult result = sessions.fetchProfile(id, true);
            if (result != null) {
                heads.put(id, ResolvableProfile.createResolved(result.profile()));
            } else {
                failures.merge(id, 1, Integer::sum);
                nextHeadAt = System.currentTimeMillis() + FAILURE_PAUSE_MS;
                Debug.log("Heads: profile {} failed, pausing new heads", id);
            }
            fetching.remove(id);
        }, Util.nonCriticalIoPool());
    }

    /** Looks up the uuids of names that have none, unless a lookup is still running. */
    public void resolve(Collection<String> names) {
        String[] unknown = names.stream().filter(name -> idFor(name) == null).toArray(String[]::new);
        if (unknown.length == 0 || !lookup.isDone()) return;

        Debug.log("Heads: looking up {} names", unknown.length);
        GameProfileRepository repository = Minecraft.getInstance().services().profileRepository();
        lookup = CompletableFuture.runAsync(() -> {
            // Blocks while it pages through the names two at a time
            repository.findProfilesByNames(unknown, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(String name, UUID id) {
                    ids.put(name.toLowerCase(Locale.ROOT), id);
                }

                @Override
                public void onProfileLookupFailed(String name, Exception error) {
                }
            });
            save();
            Debug.log("Heads: found {} of {}", Arrays.stream(unknown).filter(name -> idFor(name) != null).count(), unknown.length);
        }, Util.nonCriticalIoPool());
    }

    private static Map<String, UUID> load() {
        Path path = ModPaths.playerIds();
        if (!Files.exists(path)) return Map.of();

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, UUID> loaded = GSON.fromJson(reader, IDS);
            return loaded == null ? Map.of() : loaded;
        } catch (IOException | JsonParseException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to read {}, ignoring it", ModPaths.display(path), e);
            return Map.of();
        }
    }

    private void save() {
        Path path = ModPaths.playerIds();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(ids, IDS.getType(), writer);
            }
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to save {}", ModPaths.display(path), e);
        }
    }
}
