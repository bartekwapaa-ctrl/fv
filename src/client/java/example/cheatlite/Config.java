package example.cheatlite;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Ustawienia moda, zapisywane w config/cheatlite.properties. */
public final class Config {

    // HUD
    public static boolean hudEnabled = true;
    public static float hudScale = 1.0f;
    public static int hudX = 4;
    public static int hudY = 4;

    // WaterCleaner
    public static boolean waterCleaner = false;
    public static boolean waterCleanerRemoveLever = false; // czy niszczyć dźwignię po postawieniu
    public static boolean waterCleanerOnlySneak = false;   // działaj tylko podczas kucania
    public static int waterCleanerDelay = 2;                // ticki po wylaniu wody

    // LeverSwap
    public static boolean leverSwap = false;
    public static boolean leverSwapPickupWater = true;
    public static float leverSwapRange = 4.5f;              // zasięg w blokach

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cheatlite.properties");
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        Properties pr = new Properties();
        try (InputStream in = Files.newInputStream(p)) {
            pr.load(in);
        } catch (IOException e) {
            return;
        }
        hudEnabled = bool(pr, "hudEnabled", hudEnabled);
        hudScale = flt(pr, "hudScale", hudScale);
        hudX = (int) flt(pr, "hudX", hudX);
        hudY = (int) flt(pr, "hudY", hudY);
        waterCleaner = bool(pr, "waterCleaner", waterCleaner);
        waterCleanerRemoveLever = bool(pr, "waterCleanerRemoveLever", waterCleanerRemoveLever);
        waterCleanerDelay = (int) flt(pr, "waterCleanerDelay", waterCleanerDelay);
        waterCleanerOnlySneak = bool(pr, "waterCleanerOnlySneak", waterCleanerOnlySneak);
        leverSwap = bool(pr, "leverSwap", leverSwap);
        leverSwapPickupWater = bool(pr, "leverSwapPickupWater", leverSwapPickupWater);
        leverSwapRange = flt(pr, "leverSwapRange", leverSwapRange);
    }

    public static void save() {
        Properties pr = new Properties();
        pr.setProperty("hudEnabled", String.valueOf(hudEnabled));
        pr.setProperty("hudScale", String.valueOf(hudScale));
        pr.setProperty("hudX", String.valueOf(hudX));
        pr.setProperty("hudY", String.valueOf(hudY));
        pr.setProperty("waterCleaner", String.valueOf(waterCleaner));
        pr.setProperty("waterCleanerRemoveLever", String.valueOf(waterCleanerRemoveLever));
        pr.setProperty("waterCleanerDelay", String.valueOf(waterCleanerDelay));
        pr.setProperty("waterCleanerOnlySneak", String.valueOf(waterCleanerOnlySneak));
        pr.setProperty("leverSwap", String.valueOf(leverSwap));
        pr.setProperty("leverSwapPickupWater", String.valueOf(leverSwapPickupWater));
        pr.setProperty("leverSwapRange", String.valueOf(leverSwapRange));
        try (OutputStream out = Files.newOutputStream(file())) {
            pr.store(out, "CheatLite");
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    private static float flt(Properties p, String key, float def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
