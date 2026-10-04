package com.hellhawks;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class HellHawksState {
    public enum Channel { GENERAL, PRIVADO, CLAN, ALIANZA, COMBATES, TPA }
    public record Message(Channel channel, String sender, String text, long time, boolean incoming) {}
    public record Notification(String type, String sender, String preview, long time) {}

    public static final int MAX_HISTORY = 800;
    public static final int MAX_NOTIFICATIONS = 40;
    private static final Logger LOG = LoggerFactory.getLogger("hellhawks");

    public static final List<Message> HISTORY = new ArrayList<>();
    public static final List<Notification> NOTIFICATIONS = new ArrayList<>();

    public static boolean notifications = true;
    public static boolean sounds = true;
    public static boolean timestamps = true;
    public static String privateTarget = "";

    // Comandos del servidor (editables en config/hellhawks.json). {player} y {msg} se sustituyen.
    public static String cmdWhisper = "msg {player} {msg}";
    public static String cmdClan = "clan chat {msg}";
    public static String cmdAlly = "ally chat {msg}";
    public static String cmdTpAccept = "tpaccept";

    /** Se incrementa en cada cambio de historial/notificaciones (para refrescar cachés de la pantalla). */
    public static int revision = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file;
    private static boolean dirty = false;
    private static int ticks = 0;

    private HellHawksState() {}

    public static void init(MinecraftClient client) {
        file = client.runDirectory.toPath().resolve("config").resolve("hellhawks.json");
        load();
    }

    public static void addMessage(Channel channel, String sender, String text, boolean incoming) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        // Evita duplicados: el servidor suele devolver como eco lo que acabamos de enviar.
        for (int i = HISTORY.size() - 1, n = 0; i >= 0 && n < 15; i--, n++) {
            Message o = HISTORY.get(i);
            if (now - o.time() > 3000) break;
            if (o.channel() != channel) continue;
            if (incoming && !o.incoming() && text.contains(o.text())) return;
            if (!incoming && !o.incoming() && text.equals(o.text())) return;
        }
        HISTORY.add(new Message(channel, sender == null ? "" : sender, text, now, incoming));
        while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
        revision++;
        dirty = true;
    }

    public static void notify(String type, String sender, String preview) {
        NOTIFICATIONS.add(0, new Notification(type, sender, preview, System.currentTimeMillis()));
        while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) NOTIFICATIONS.remove(NOTIFICATIONS.size() - 1);
        revision++;
        dirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        if (notifications) {
            String p = preview.length() > 70 ? preview.substring(0, 67) + "..." : preview;
            SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal(type + (sender.isBlank() ? "" : " · " + sender)), Text.literal(p));
        }
        if (sounds) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.6F));
        }
    }

    public static List<Message> channel(Channel channel) {
        return HISTORY.stream().filter(m -> m.channel() == channel).toList();
    }

    public static void clearHistory() {
        HISTORY.clear();
        NOTIFICATIONS.clear();
        revision++;
        save();
    }

    /** Guarda en disco cada ~5 s si hay cambios (evita escribir 800 mensajes por cada línea de chat). */
    public static void tick() {
        if (dirty && ++ticks >= 100) save();
    }

    public static void save() {
        dirty = false;
        ticks = 0;
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("notifications", notifications);
            root.addProperty("sounds", sounds);
            root.addProperty("timestamps", timestamps);
            root.addProperty("privateTarget", privateTarget);
            JsonObject cmds = new JsonObject();
            cmds.addProperty("whisper", cmdWhisper);
            cmds.addProperty("clan", cmdClan);
            cmds.addProperty("ally", cmdAlly);
            cmds.addProperty("tpaccept", cmdTpAccept);
            root.add("commands", cmds);
            JsonArray arr = new JsonArray();
            for (Message m : HISTORY) {
                JsonObject o = new JsonObject();
                o.addProperty("channel", m.channel().name());
                o.addProperty("sender", m.sender());
                o.addProperty("text", m.text());
                o.addProperty("time", m.time());
                o.addProperty("incoming", m.incoming());
                arr.add(o);
            }
            root.add("history", arr);
            Path tmp = file.resolveSibling("hellhawks.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOG.warn("No se pudo guardar hellhawks.json", e);
        }
    }

    public static void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            notifications = getBool(root, "notifications", true);
            sounds = getBool(root, "sounds", true);
            timestamps = getBool(root, "timestamps", true);
            privateTarget = getString(root, "privateTarget", "");
            if (root.has("commands") && root.get("commands").isJsonObject()) {
                JsonObject c = root.getAsJsonObject("commands");
                cmdWhisper = getString(c, "whisper", cmdWhisper);
                cmdClan = getString(c, "clan", cmdClan);
                cmdAlly = getString(c, "ally", cmdAlly);
                cmdTpAccept = getString(c, "tpaccept", cmdTpAccept);
            }
            HISTORY.clear();
            if (root.has("history")) {
                for (JsonElement e : root.getAsJsonArray("history")) {
                    try { // una entrada dañada no debe borrar todo el historial
                        JsonObject o = e.getAsJsonObject();
                        HISTORY.add(new Message(Channel.valueOf(o.get("channel").getAsString()),
                                getString(o, "sender", ""), getString(o, "text", ""),
                                o.get("time").getAsLong(), getBool(o, "incoming", false)));
                    } catch (Exception ignored) {}
                }
            }
            while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
            revision++;
        } catch (Exception e) {
            LOG.warn("No se pudo leer hellhawks.json", e);
        }
    }

    private static boolean getBool(JsonObject o, String k, boolean d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : d;
    }
    private static String getString(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
    }
}
