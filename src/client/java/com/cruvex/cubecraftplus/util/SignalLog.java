package com.cruvex.cubecraftplus.util;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/** One signal log file per client run, created on the first signal. Every line is flushed. */
public final class SignalLog {

    private static final DateTimeFormatter FILE_NAME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    /** Milliseconds matter: the log itself is second-accurate, the order within a second is the point. */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT);

    /** Context of the signal (game, server id, connection), rendered after every line. */
    private final Supplier<String> context;

    private @Nullable Path path;
    private @Nullable BufferedWriter writer;
    private boolean broken;

    private long previousSignalAt;

    public SignalLog(Supplier<String> context) {
        this.context = context;
    }

    /** Path of the file being written, or null while nothing has been logged yet. */
    public synchronized @Nullable Path path() {
        return path;
    }

    public synchronized void write(String type, String detail, long sinceConnect) {
        BufferedWriter out = open();
        if (out == null) return;

        long now = System.currentTimeMillis();
        String delta = previousSignalAt == 0 ? "-" : duration(now - previousSignalAt);
        previousSignalAt = now;

        String line = String.format(Locale.ROOT, "%s %8s %7s  %-13s %s | %s",
                LocalDateTime.now().format(TIME),
                sinceConnect < 0 ? "-" : "+" + duration(sinceConnect),
                delta,
                type,
                detail,
                context.get());

        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            broken = true;
            CubeCraftPlusClient.LOGGER.warn("Signal probe could not write to {}", path, e);
        }
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Signal probe could not close {}", path, e);
        }
        writer = null;
        previousSignalAt = 0;
    }

    private @Nullable BufferedWriter open() {
        if (writer != null || broken) return writer;

        path = ModPaths.debug("signals-" + LocalDateTime.now().format(FILE_NAME) + ".log");
        try {
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            writeHeader(writer);
            CubeCraftPlusClient.LOGGER.info("Signal probe logging to {}", ModPaths.display(path));
        } catch (IOException e) {
            broken = true;
            CubeCraftPlusClient.LOGGER.warn("Signal probe could not open {}", path, e);
        }
        return writer;
    }

    private static void writeHeader(BufferedWriter out) throws IOException {
        out.write("# cubecraft-plus signal probe, started " + LocalDateTime.now());
        out.newLine();
        out.write("# minecraft " + version("minecraft") + ", mod " + version(CubeCraftPlusClient.MOD_ID));
        out.newLine();
        out.write("# time +since-connect delta TYPE detail | game/server context");
        out.newLine();
        out.flush();
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    /** Compact and fixed-width-ish, so columns stay readable: 350ms, 2.4s, 3m12s. */
    private static String duration(long millis) {
        if (millis < 1000) return millis + "ms";
        if (millis < 60_000) return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
        return String.format(Locale.ROOT, "%dm%02ds", millis / 60_000, millis % 60_000 / 1000);
    }
}
