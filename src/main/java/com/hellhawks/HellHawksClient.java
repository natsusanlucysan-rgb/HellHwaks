package com.hellhawks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class HellHawksClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        HellHawksState.init(MinecraftClient.getInstance());
        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.hellhawks.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.hellhawks"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new HellHawksScreen(null));
            }
            HellHawksState.tick();
        });

        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receptionTimestamp) ->
                HellHawksParser.handle(message.getString(), false));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                HellHawksParser.handle(message.getString(), overlay));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HellHawksState.save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> HellHawksState.save());
    }

    /** Envía un comando (con o sin la barra inicial). */
    public static void command(String command) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || command == null || command.isBlank()) return;
        String cmd = command.trim();
        c.player.networkHandler.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    /** Envía un mensaje al chat general (o un comando si empieza por "/"). */
    public static void chat(String text) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || text == null || text.isBlank()) return;
        if (text.startsWith("/")) command(text);
        else c.player.networkHandler.sendChatMessage(text);
    }

    /** Rellena una plantilla de comando como "msg {player} {msg}". */
    public static String fill(String template, String player, String msg) {
        return template.replace("{player}", player == null ? "" : player)
                       .replace("{msg}", msg == null ? "" : msg);
    }
}
