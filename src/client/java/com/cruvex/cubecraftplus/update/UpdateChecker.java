package com.cruvex.cubecraftplus.update;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.chat.Chat;
import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Compares this install with GitHub's latest release, and says in chat once per launch when there is a newer one. */
public class UpdateChecker {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final URI LATEST_RELEASE = URI.create("https://api.github.com/repos/cruvex/cubecraft-plus/releases/latest");
    private static final Gson GSON = new Gson();
    /** Lands after CubeCraft's welcome banner, which would otherwise push the message out of view. */
    private static final long JOIN_DELAY_MS = 5000;

    private static UpdateChecker instance;

    // All only touched on the client thread
    private @Nullable Result update;
    private boolean announced;
    private boolean joinDelayPassed;

    /** {@code latest} and {@code installed} leave out the "+26.2" a release jar's version carries. */
    public record Result(String latest, String installed, String minecraft, URI page, boolean newer, boolean forThisMinecraft) {
        public boolean updateAvailable() {
            return newer && forThisMinecraft;
        }
    }

    public static UpdateChecker getInstance() {
        if (instance == null) {
            instance = new UpdateChecker();
        }
        return instance;
    }

    public void init() {
        if (!ConfigManager.getInstance().getConfig().updateCheck.enabled) return;

        CubeEvents.CUBE_JOIN.register(this::onCubeJoin);
        fetchLatest()
                .thenAccept(result -> {
                    Debug.log("Update check: latest {}, running {}, jar for Minecraft {}: {}",
                            result.latest(), result.installed(), result.minecraft(), result.forThisMinecraft());
                    if (result.updateAvailable()) {
                        Minecraft.getInstance().execute(() -> {
                            update = result;
                            announce();
                        });
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.warn("Update check failed: {}", failureReason(ex));
                    return null;
                });
    }

    public CompletableFuture<Result> fetchLatest() {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CubeCraftPlus-fabric-mod")
                .GET()
                .build();

        return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("GitHub returned " + response.statusCode());
                    }
                    return compare(GSON.fromJson(response.body(), Release.class));
                });
    }

    private static Result compare(@Nullable Release release) {
        if (release == null || release.tag() == null || release.page() == null) {
            throw new IllegalStateException("GitHub returned no release");
        }

        String current = modVersion(CubeCraftPlusClient.MOD_ID);
        String latest = release.tag().replaceFirst("^[vV]", "");
        String minecraft = minecraftVersion();
        boolean newer;
        try {
            // Ignores the "+26.2" a release jar's version carries
            Version latestVersion = SemanticVersion.parse(latest);
            newer = latestVersion.compareTo(SemanticVersion.parse(current)) > 0;
        } catch (VersionParsingException e) {
            throw new IllegalStateException("Could not compare " + latest + " with " + current, e);
        }

        boolean forThisMinecraft = release.assets() != null && release.assets().stream()
                .anyMatch(asset -> asset.name() != null && asset.name().endsWith("+" + minecraft + ".jar"));
        return new Result(latest, installedVersion(), minecraft, URI.create(release.page()), newer, forThisMinecraft);
    }

    private void onCubeJoin() {
        joinDelayPassed = false;
        CompletableFuture.delayedExecutor(JOIN_DELAY_MS, TimeUnit.MILLISECONDS, Minecraft.getInstance()::execute)
                .execute(() -> {
                    joinDelayPassed = true;
                    announce();
                });
    }

    private void announce() {
        Result found = update;
        if (found == null || announced || !joinDelayPassed || !CubeCraftManager.getInstance().isOnCubeCraft()) return;
        if (!ConfigManager.getInstance().getConfig().updateCheck.enabled) return;

        announced = true;
        Chat.send(availableMessage(found));
    }

    public static Component availableMessage(Result result) {
        Component download = Component.translatable("cubecraftplus.update.download")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(result.page()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cubecraftplus.update.download.tooltip"))));
        return Component.translatable("cubecraftplus.update.available", result.latest(), result.installed(), download)
                .withStyle(ChatFormatting.GREEN);
    }

    public static String installedVersion() {
        return modVersion(CubeCraftPlusClient.MOD_ID).split("\\+")[0];
    }

    public static String minecraftVersion() {
        return modVersion("minecraft");
    }

    public static String failureReason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private record Release(@SerializedName("tag_name") String tag, @SerializedName("html_url") String page, List<Asset> assets) {}

    private record Asset(String name) {}
}
