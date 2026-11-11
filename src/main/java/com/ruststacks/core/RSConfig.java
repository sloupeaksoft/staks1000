package com.ruststacks.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Чтение config/ruststacks.cfg. Не трогает классы Minecraft (грузится до игры). */
public final class RSConfig {

    public static final int MAX_SIZE = 100000;

    public static final Map<String, Integer> SIZES = new HashMap<>();
    /** Размер стака для всех остальных предметов, у которых обычно 64 (64 = не менять). */
    public static int defaultSize = 64;
    /** Лимит слота для сундуков/инвентаря (максимум из всех размеров, минимум 64). */
    public static int slotLimit = 64;
    public static String status = "not loaded";
    private static boolean loaded;

    private RSConfig() {}

    private static String g(int size, String... names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append("minerustaddons:").append(n).append('=').append(size).append('\n');
        return sb.toString();
    }

    private static final String DEFAULTS =
        "# Rust Stacks 3.0. Формат: modid:item=размер\n"
      + "# После изменения перезапусти игру. На сервере размеры автоматически отправляются игрокам,\n"
      + "# но мод должен стоять и на сервере, и у каждого игрока.\n"
      + "# default = размер для всех остальных предметов, у которых обычно стак 64 (64 = не менять)\n"
      + "default=64\n\n"
      + "# --- ресурсы (Rust: 1000) ---\n"
      + g(1000, "piecewood", "piecestone", "smallstones", "metalore", "metalfragments", "sulfur", "sulfurore",
              "cloth", "leather", "animalfat", "rescoal", "coal2", "resgunpowder", "scrap", "resbone")
      + "\n# --- МВК / взрывчатка (Rust: 100) ---\n"
      + g(100, "mhq", "mhqore", "muq", "muqore", "explosive")
      + "\n# --- топливо / нефть (Rust: 500) ---\n"
      + g(500, "flq", "fhq", "ultrafuelquality", "oilraw")
      + "\n# --- патроны ---\n"
      + g(128, "rifleammo", "pistolammo", "nails", "nailgunnailsammo")
      + g(64, "handmadeammo", "shotgunammo")
      + "\n# --- компоненты ---\n"
      + g(50, "rope", "strongrope", "seedcorn", "seedhemp", "seedpumpkin")
      + g(20, "tarp", "sewingkit", "improvedsewingkit", "pipe", "gears", "metalspring", "blades", "roadsign",
              "metalsheet", "ducttape", "wires", "bolts", "electricfuse")
      + g(10, "glue", "superglue", "riflebody", "smgbody", "semiautomaticbody", "surveycharge")
      + "\n# --- еда ---\n"
      + g(20, "bearmeatcooked", "bearmeatraw", "boarmeatcooked", "boarmeatraw", "chickenmeatcooked", "chickenmeatraw",
              "horsemeatcooked", "horsemeatraw", "humanmeatcooked", "humanmeatraw", "wolfmeatcooked", "wolfmeatraw",
              "fishmeatcooked", "fishmeatraw", "beefcooked", "rawfish", "blueberries", "blackberry", "corn", "pumpkin")
      + g(10, "mushroom", "beanscan", "fishcan", "chocolatebar", "foodapple", "crackers")
      + "\n# --- медицина / метательное ---\n"
      + g(10, "antiradiationpills")
      + g(5, "grenadebeancan", "flare")
      + g(3, "bandage")
      + g(2, "syringe")
      + g(1, "largemedkit")
      + "\n# ванильные предметы (раскомментируй, чтобы включить):\n"
      + "# minecraft:cobblestone=1000\n"
      + "# minecraft:arrow=64\n";

    public static synchronized void load(File mcDir) {
        if (loaded) return;
        loaded = true;
        File dir = new File(mcDir != null ? mcDir : new File("."), "config");
        File file = new File(dir, "ruststacks.cfg");
        try {
            if (!file.exists()) {
                dir.mkdirs();
                try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                    w.write(DEFAULTS);
                }
            }
            int max = 64;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.lastIndexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    int size;
                    try {
                        size = Integer.parseInt(line.substring(eq + 1).trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    size = Math.max(1, Math.min(MAX_SIZE, size));
                    if (key.equals("default")) defaultSize = size;
                    else SIZES.put(key, size);
                    max = Math.max(max, size);
                }
            }
            slotLimit = max;
            status = "loaded " + SIZES.size() + " items from " + file.getPath() + ", slot limit " + slotLimit;
        } catch (Throwable t) {
            status = "config error: " + t;
        }
        System.out.println("[RustStacks] " + status);
    }
}
