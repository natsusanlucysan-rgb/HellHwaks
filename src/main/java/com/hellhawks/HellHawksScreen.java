package com.hellhawks;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HellHawksScreen extends Screen {
    private enum Mode { CHAT, NOTIFS, SETTINGS }
    private record Line(OrderedText text, int color) {}

    private static final Identifier LOGO = Identifier.of("hellhawks", "textures/gui/logo.png");
    private static final int BG = 0xF008080B, PANEL = 0xF014151A, PANEL2 = 0xF01E2027;
    private static final int RED = 0xFFD51F35, RED2 = 0xFFFF4B5E, TEXT = 0xFFF6EDEF, MUTED = 0xFFAFA3A8;
    private static final int LINE_H = 11;

    private static final String[] TABS = {"GENERAL", "SUSURROS", "CLAN", "ALIANZA", "TPA", "COMBATES"};
    private static final HellHawksState.Channel[] CHANNELS = {
        HellHawksState.Channel.GENERAL, HellHawksState.Channel.PRIVADO, HellHawksState.Channel.CLAN,
        HellHawksState.Channel.ALIANZA, HellHawksState.Channel.TPA, HellHawksState.Channel.COMBATES};

    private final Screen parent;
    private Mode mode = Mode.CHAT;
    private int tab = 0, scroll = 0, visibleLines = 10;

    // diseño (se recalcula en init)
    private int l, t, w, h;
    private TextFieldWidget input, target;
    private ButtonWidget sendBtn, tpaBtn, notifBtn, clearNotifsBtn;
    private final List<ButtonWidget> tabButtons = new ArrayList<>();
    private final List<ButtonWidget> settingsButtons = new ArrayList<>();

    // caché de líneas ajustadas al ancho
    private final List<Line> lines = new ArrayList<>();
    private int cacheRev = -1, cacheTab = -1, cacheW = -1;
    private boolean cacheTs;

    public HellHawksScreen(Screen parent) {
        super(Text.literal("Hells Hawks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String oldInput = input != null ? input.getText() : "";
        String oldTarget = target != null ? target.getText() : HellHawksState.privateTarget;
        tabButtons.clear();
        settingsButtons.clear();

        w = Math.min(620, width - 16);
        h = Math.min(390, height - 16);
        l = (width - w) / 2;
        t = (height - h) / 2;
        int r = l + w, b = t + h;

        // pestañas
        int tabW = (w - 36) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int n = i;
            tabButtons.add(addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]), btn -> {
                tab = n; mode = Mode.CHAT; scroll = 0; updateVisibility();
            }).dimensions(l + 18 + i * tabW, t + 44, tabW - 4, 20).build()));
        }

        // cabecera
        addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> close())
                .dimensions(r - 18 - 24, t + 14, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("AJUSTES"), btn -> toggleMode(Mode.SETTINGS))
                .dimensions(r - 18 - 24 - 4 - 64, t + 14, 64, 20).build());
        notifBtn = addDrawableChild(ButtonWidget.builder(Text.literal("NOTIF"), btn -> toggleMode(Mode.NOTIFS))
                .dimensions(r - 18 - 24 - 4 - 64 - 4 - 80, t + 14, 80, 20).build());

        // zona inferior
        int rowY = b - 34, targetY = b - 58;
        int sendW = 90;
        int inputW = w - 48 - sendW - 6;
        input = new TextFieldWidget(textRenderer, l + 24, rowY, inputW, 20, Text.literal("Mensaje"));
        input.setMaxLength(256);
        input.setPlaceholder(Text.literal("Escribe un mensaje... (Enter para enviar)"));
        input.setText(oldInput);
        addDrawableChild(input);

        target = new TextFieldWidget(textRenderer, l + 24, targetY, 170, 20, Text.literal("Jugador"));
        target.setMaxLength(16);
        target.setPlaceholder(Text.literal("Jugador para susurro"));
        target.setText(oldTarget);
        addDrawableChild(target);

        sendBtn = addDrawableChild(ButtonWidget.builder(Text.literal("ENVIAR"), btn -> send())
                .dimensions(r - 24 - sendW, rowY, sendW, 20).build());
        tpaBtn = addDrawableChild(ButtonWidget.builder(Text.literal("ACEPTAR TPA"),
                btn -> HellHawksClient.command(HellHawksState.cmdTpAccept))
                .dimensions(l + 24, targetY, 120, 20).build());
        clearNotifsBtn = addDrawableChild(ButtonWidget.builder(Text.literal("LIMPIAR NOTIFICACIONES"), btn -> {
            HellHawksState.NOTIFICATIONS.clear(); HellHawksState.revision++;
        }).dimensions(l + 24, targetY, 170, 20).build());

        // ajustes (se crean una sola vez aquí, no en render)
        int sx = l + 24, sy = t + 84;
        settingsButtons.add(addDrawableChild(ButtonWidget.builder(label("Notificaciones", HellHawksState.notifications), btn -> {
            HellHawksState.notifications = !HellHawksState.notifications;
            btn.setMessage(label("Notificaciones", HellHawksState.notifications)); HellHawksState.save();
        }).dimensions(sx, sy, 250, 20).build()));
        settingsButtons.add(addDrawableChild(ButtonWidget.builder(label("Sonidos", HellHawksState.sounds), btn -> {
            HellHawksState.sounds = !HellHawksState.sounds;
            btn.setMessage(label("Sonidos", HellHawksState.sounds)); HellHawksState.save();
        }).dimensions(sx, sy + 26, 250, 20).build()));
        settingsButtons.add(addDrawableChild(ButtonWidget.builder(label("Marcas de tiempo", HellHawksState.timestamps), btn -> {
            HellHawksState.timestamps = !HellHawksState.timestamps;
            btn.setMessage(label("Marcas de tiempo", HellHawksState.timestamps)); HellHawksState.save();
        }).dimensions(sx, sy + 52, 250, 20).build()));
        settingsButtons.add(addDrawableChild(ButtonWidget.builder(Text.literal("Limpiar historial"), btn -> {
            HellHawksState.clearHistory(); scroll = 0;
        }).dimensions(sx, sy + 78, 250, 20).build()));

        updateVisibility();
        if (input.visible) setInitialFocus(input);
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "SÍ" : "NO"));
    }

    private void toggleMode(Mode m) {
        mode = mode == m ? Mode.CHAT : m;
        scroll = 0;
        updateVisibility();
    }

    private void updateVisibility() {
        boolean chat = mode == Mode.CHAT;
        HellHawksState.Channel c = CHANNELS[tab];
        boolean writable = chat && c != HellHawksState.Channel.COMBATES && c != HellHawksState.Channel.TPA;
        setShown(input, writable);
        setShown(sendBtn, writable);
        setShown(target, chat && c == HellHawksState.Channel.PRIVADO);
        setShown(tpaBtn, chat && c == HellHawksState.Channel.TPA);
        setShown(clearNotifsBtn, mode == Mode.NOTIFS);
        for (ButtonWidget b : settingsButtons) setShown(b, mode == Mode.SETTINGS);
    }

    private void setShown(net.minecraft.client.gui.widget.ClickableWidget wdg, boolean on) {
        wdg.visible = on;
        wdg.active = on;
        if (!on) wdg.setFocused(false);
    }

    private void send() {
        String msg = input.getText().trim();
        if (msg.isEmpty()) return;
        HellHawksState.Channel c = CHANNELS[tab];
        switch (c) {
            case PRIVADO -> {
                String p = target.getText().trim();
                if (p.isEmpty()) return;
                HellHawksClient.command(HellHawksClient.fill(HellHawksState.cmdWhisper, p, msg));
                HellHawksState.privateTarget = p;
                HellHawksState.addMessage(c, "Tú → " + p, msg, false);
            }
            case CLAN -> {
                HellHawksClient.command(HellHawksClient.fill(HellHawksState.cmdClan, "", msg));
                HellHawksState.addMessage(c, "Tú", msg, false);
            }
            case ALIANZA -> {
                HellHawksClient.command(HellHawksClient.fill(HellHawksState.cmdAlly, "", msg));
                HellHawksState.addMessage(c, "Tú", msg, false);
            }
            case GENERAL -> HellHawksClient.chat(msg); // el servidor devuelve el eco y se registra solo
            default -> { return; }
        }
        input.setText("");
        scroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && input != null && input.visible && (input.isFocused() || (target != null && target.isFocused()))) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        HellHawksState.save();
        if (client != null) client.setScreen(parent);
    }

    // ---------- dibujo ----------

    /** Dibujamos el panel aquí: Screen.render() llama a renderBackground() una sola vez, antes de los widgets. */
    @Override
    public void renderBackground(DrawContext d, int mx, int my, float delta) {
        super.renderBackground(d, mx, my, delta);
        int r = l + w, b = t + h;
        d.fill(l, t, r, b, BG);
        d.fill(l, t, r, t + 5, RED);
        d.fill(l + 10, t + 10, r - 10, b - 10, PANEL);
        d.drawTexture(LOGO, l + 16, t + 12, 28, 28, 0f, 0f, 256, 256, 256, 256);
        d.drawText(textRenderer, Text.literal("HELLS HAWKS"), l + 52, t + 15, RED2, true);
        d.drawText(textRenderer, Text.literal("SOCIAL HUD"), l + 52, t + 27, MUTED, false);

        int areaTop = t + 72, areaBottom = b - (CHANNELS[tab] == HellHawksState.Channel.PRIVADO || mode != Mode.CHAT ? 64 : 40);
        if (mode == Mode.SETTINGS) areaBottom = b - 16;
        d.fill(l + 18, areaTop, r - 18, areaBottom, PANEL2);

        switch (mode) {
            case CHAT -> renderMessages(d, areaTop, areaBottom);
            case NOTIFS -> renderNotifications(d, areaTop, areaBottom);
            case SETTINGS -> renderSettings(d);
        }
    }

    @Override
    public void render(DrawContext d, int mx, int my, float delta) {
        notifBtn.setMessage(Text.literal("NOTIF (" + HellHawksState.NOTIFICATIONS.size() + ")"));
        super.render(d, mx, my, delta);
        if (mode == Mode.CHAT) { // subrayado de la pestaña activa
            ButtonWidget a = tabButtons.get(tab);
            d.fill(a.getX(), a.getY() + a.getHeight() + 1, a.getX() + a.getWidth(), a.getY() + a.getHeight() + 3, RED);
        }
    }

    private void rebuildLines() {
        int wrapW = w - 60;
        if (cacheRev == HellHawksState.revision && cacheTab == tab && cacheW == wrapW && cacheTs == HellHawksState.timestamps) return;
        cacheRev = HellHawksState.revision; cacheTab = tab; cacheW = wrapW; cacheTs = HellHawksState.timestamps;
        lines.clear();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        for (HellHawksState.Message m : HellHawksState.channel(CHANNELS[tab])) {
            String who = m.sender().isBlank() ? "Sistema" : m.sender();
            String line = (HellHawksState.timestamps ? "[" + f.format(new Date(m.time())) + "] " : "") + who + ": " + m.text();
            for (OrderedText ot : textRenderer.wrapLines(Text.literal(line), wrapW)) {
                lines.add(new Line(ot, m.incoming() ? TEXT : RED2));
            }
        }
    }

    private void renderMessages(DrawContext d, int top, int bottom) {
        rebuildLines();
        d.drawText(textRenderer, Text.literal(TABS[tab]), l + 30, top + 6, TEXT, true);
        int y0 = top + 22;
        visibleLines = Math.max(1, (bottom - y0 - 4) / LINE_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visibleLines)));
        if (lines.isEmpty()) {
            d.drawText(textRenderer, Text.literal("No hay mensajes en este canal."), l + 30, y0, MUTED, false);
            return;
        }
        int start = Math.max(0, lines.size() - visibleLines - scroll);
        int end = Math.min(lines.size(), start + visibleLines);
        int y = y0;
        for (int i = start; i < end; i++) {
            Line ln = lines.get(i);
            d.drawText(textRenderer, ln.text(), l + 30, y, ln.color(), false);
            y += LINE_H;
        }
        if (scroll > 0) d.drawText(textRenderer, Text.literal("Desplazado (rueda hacia abajo para volver)"), l + w - 30 - 230, top + 6, MUTED, false);
    }

    private void renderNotifications(DrawContext d, int top, int bottom) {
        d.drawText(textRenderer, Text.literal("CENTRO DE NOTIFICACIONES"), l + 30, top + 6, TEXT, true);
        int y = top + 22;
        int max = Math.max(1, (bottom - y - 4) / LINE_H);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        if (HellHawksState.NOTIFICATIONS.isEmpty()) {
            d.drawText(textRenderer, Text.literal("Sin notificaciones."), l + 30, y, MUTED, false);
            return;
        }
        for (int i = 0; i < Math.min(max, HellHawksState.NOTIFICATIONS.size()); i++) {
            HellHawksState.Notification n = HellHawksState.NOTIFICATIONS.get(i);
            String s = "[" + f.format(new Date(n.time())) + "] " + n.type() + (n.sender().isBlank() ? "" : " · " + n.sender()) + ": " + n.preview();
            d.drawText(textRenderer, textRenderer.trimToWidth(s, w - 60), l + 30, y, TEXT, false);
            y += LINE_H;
        }
    }

    private void renderSettings(DrawContext d) {
        d.drawText(textRenderer, Text.literal("AJUSTES DE HELLS HAWKS"), l + 30, t + 76, TEXT, true);
        int x = l + 30 + 250 + 24, y = t + 88;
        d.drawText(textRenderer, Text.literal("Historial: hasta " + HellHawksState.MAX_HISTORY + " mensajes"), x, y, MUTED, false);
        d.drawText(textRenderer, Text.literal("Tecla: K (cámbiala en Controles)"), x, y + 14, MUTED, false);
        d.drawText(textRenderer, Text.literal("Comandos del servidor"), x, y + 40, TEXT, false);
        d.drawText(textRenderer, Text.literal("(edita config/hellhawks.json):"), x, y + 52, MUTED, false);
        d.drawText(textRenderer, Text.literal("Susurro: /" + HellHawksState.cmdWhisper), x, y + 68, MUTED, false);
        d.drawText(textRenderer, Text.literal("Clan: /" + HellHawksState.cmdClan), x, y + 80, MUTED, false);
        d.drawText(textRenderer, Text.literal("Alianza: /" + HellHawksState.cmdAlly), x, y + 92, MUTED, false);
        d.drawText(textRenderer, Text.literal("Aceptar TPA: /" + HellHawksState.cmdTpAccept), x, y + 104, MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mode != Mode.CHAT) return false;
        scroll += vertical > 0 ? 3 : -3;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visibleLines)));
        return true;
    }
}
