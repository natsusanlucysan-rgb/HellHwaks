package com.hellhawks;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clasifica las líneas de chat del servidor en canales.
 * Todos los patrones están anclados al inicio de la línea para no confundir chat normal con susurros, clan, etc.
 * Si el formato de Diosesmon es distinto, ajusta aquí los patrones.
 */
public final class HellHawksParser {
    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String PFX = "^(?:\\[[^\\]]{1,24}\\]\\s*)*";   // prefijos tipo [Rango] [Clan]
    private static final String NAME = "([A-Za-z0-9_]{2,16})";
    private static final String SELF = "(?:yo|me|tú|tu|you)";

    // Susurros recibidos: grupo 1 = jugador, grupo 2 = mensaje
    private static final Pattern[] WHISPER_IN = {
        Pattern.compile("^\\[\\s*" + NAME + "\\s*(?:->|→|»|>)\\s*" + SELF + "\\s*\\]\\s*(.+)$", F),
        Pattern.compile("^\\[?\\s*(?:de|from|susurro de|whisper from)\\s+" + NAME + "(?:\\]\\s*[:»>\\-]*|\\s*[:»>\\-]+)\\s*(.+)$", F),
        Pattern.compile(PFX + NAME + "\\s+(?:whispers to you|te susurra|te ha susurrado)\\s*[:»>\\-]*\\s*(.+)$", F),
    };
    // Susurros enviados (eco del servidor)
    private static final Pattern[] WHISPER_OUT = {
        Pattern.compile("^\\[\\s*" + SELF + "\\s*(?:->|→|»|>)\\s*" + NAME + "\\s*\\]\\s*(.+)$", F),
        Pattern.compile("^\\[?\\s*(?:a|to|para|susurro a|whisper to)\\s+" + NAME + "(?:\\]\\s*[:»>\\-]*|\\s*[:»>\\-]+)\\s*(.+)$", F),
        Pattern.compile("^you whisper to\\s+" + NAME + "\\s*[:»>\\-]*\\s*(.+)$", F),
    };

    private static final Pattern TPA_A = Pattern.compile(PFX + NAME + "\\s.*?(?:te ha enviado una solicitud de (?:tpa|teletransporte|teleport)|quiere teletransportarse|has requested to teleport|sent you a (?:tpa|teleport) request|wants to teleport)", F);
    private static final Pattern TPA_B = Pattern.compile("(?:solicitud de (?:tpa|teleport\\w*)|(?:tpa|teleport) request)\\s+(?:de|from)\\s+" + NAME, F);

    private static final Pattern CLAN_TAG = Pattern.compile("^\\W{0,2}\\[\\s*(?:clan|clán)\\b[^\\]]*\\]", F);
    private static final Pattern ALLY_TAG = Pattern.compile("^\\W{0,2}\\[\\s*(?:ally|alianza|aliado|aliados)\\b[^\\]]*\\]", F);
    private static final Pattern CHAT_A = Pattern.compile(PFX + "<" + NAME + ">\\s*[:»]?\\s*(.*)$", F);   // <Steve> hola
    private static final Pattern CHAT_B = Pattern.compile(PFX + NAME + "\\s*[:»]\\s*(.*)$", F);          // Steve: hola
    private static final Pattern COMBAT = Pattern.compile("combate|battle|debilitad|debilitó|fainted", F);

    private HellHawksParser() {}

    public static void handle(String raw, boolean overlay) {
        if (overlay || raw == null) return; // la action bar se repite constantemente: se ignora
        String s = raw.replaceAll("§[0-9A-FK-ORXa-fk-orx]", "").trim();
        if (s.isEmpty()) return;

        for (Pattern p : WHISPER_IN) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                HellHawksState.privateTarget = m.group(1);
                add(HellHawksState.Channel.PRIVADO, m.group(1), m.group(2).trim(), true);
                return;
            }
        }
        for (Pattern p : WHISPER_OUT) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                add(HellHawksState.Channel.PRIVADO, "Tú → " + m.group(1), m.group(2).trim(), false);
                return;
            }
        }

        Matcher tpa = TPA_A.matcher(s);
        if (!tpa.find()) { tpa = TPA_B.matcher(s); if (!tpa.find()) tpa = null; }
        if (tpa != null) {
            add(HellHawksState.Channel.TPA, tpa.group(1), s, true);
            return;
        }

        Matcher chat = CHAT_A.matcher(s);
        boolean isChat = chat.find();
        if (!isChat) { chat = CHAT_B.matcher(s); isChat = chat.find(); }
        String sender = isChat ? chat.group(1) : "";
        String body = isChat ? chat.group(2).trim() : s;

        if (CLAN_TAG.matcher(s).find()) {
            add(HellHawksState.Channel.CLAN, sender.isEmpty() ? "Clan" : sender, body, true);
            return;
        }
        if (ALLY_TAG.matcher(s).find()) {
            add(HellHawksState.Channel.ALIANZA, sender.isEmpty() ? "Alianza" : sender, body, true);
            return;
        }
        // Los mensajes de combate son del sistema: si parece chat de jugador, no se clasifica como combate
        if (!isChat && COMBAT.matcher(s.toLowerCase(Locale.ROOT)).find()) {
            add(HellHawksState.Channel.COMBATES, "Sistema", s, true);
            return;
        }
        add(HellHawksState.Channel.GENERAL, sender, body, true);
    }

    private static void add(HellHawksState.Channel c, String sender, String text, boolean incoming) {
        HellHawksState.addMessage(c, sender, text, incoming);
        boolean notifiable = c == HellHawksState.Channel.PRIVADO || c == HellHawksState.Channel.CLAN
                || c == HellHawksState.Channel.ALIANZA || c == HellHawksState.Channel.TPA;
        if (incoming && notifiable && !isSelf(sender)) {
            HellHawksState.notify(c.name(), sender, text);
        }
    }

    private static boolean isSelf(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && name != null && name.equalsIgnoreCase(mc.getSession().getUsername());
    }
}
