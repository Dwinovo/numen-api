package com.dwinovo.numen;

import com.dwinovo.numen.agent.skill.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.nio.file.Path;

/**
 * Client entry point for Forge 1.20.4. Forge keeps separate mod and game event
 * buses (mirroring the NeoForge reference this was ported from): registration
 * events (key mappings / GUI overlays / reload listeners) go on the mod bus,
 * while the per-tick / world-render / disconnect hooks go on the game bus
 * ({@link MinecraftForge#EVENT_BUS}).
 *
 * <p>Invoked from {@link NumenMod} only when {@code FMLEnvironment.dist} is the
 * physical client, so none of these client-only types load on a dedicated server.
 */
public final class NumenForgeClient {

    private NumenForgeClient() {}

    /** Wire every client listener. {@code modBus} is the mod event bus from the constructor. */
    public static void init(IEventBus modBus) {
        // 读回上次选择的 GUI 主题(config/numen/ui.json)。
        com.dwinovo.numen.client.screen.UiTheme.init(
                Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("numen"));
        // Mod bus — registration events.
        modBus.addListener(NumenForgeClient::registerKeyMappings);
        modBus.addListener(NumenForgeClient::registerGuiOverlays);
        modBus.addListener(NumenForgeClient::registerReloadListeners);
        modBus.addListener(NumenForgeClient::registerShaders);
        // Game bus — per-tick / world-render / disconnect.
        MinecraftForge.EVENT_BUS.addListener(NumenForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(NumenForgeClient::onLoggingOut);
    }

    static void registerShaders(net.minecraftforge.client.event.RegisterShadersEvent event) {
        // GUI 圆角 SDF shader;加载失败仅告警——RoundRect 会自动降级成方角 fill。
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(
                            event.getResourceProvider(),
                            new net.minecraft.resources.ResourceLocation(
                                    Constants.MOD_ID, "rendertype_round_rect"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR),
                    com.dwinovo.numen.client.ui.RoundRect::setShader);
        } catch (Exception e) {
            Constants.LOG.warn("round rect shader failed to load, falling back to square corners", e);
        }
    }

    static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        // G → companion roster panel (chat entry + settings/reset live in there).
        event.register(com.dwinovo.numen.client.NumenKeys.OPEN_ROSTER);
    }

    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        com.dwinovo.numen.client.NumenKeys.tick();
        com.dwinovo.numen.client.hud.NumenToasts.tick();
        com.dwinovo.numen.client.agent.AgentLoopRegistry.tickAll();
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.dwinovo.numen.client.data.ClientNumenInventory.clear();
        com.dwinovo.numen.client.agent.KnownSkins.clear();
        com.dwinovo.numen.client.hud.NumenToasts.clear();
        com.dwinovo.numen.client.agent.ClientDeaths.clearAll();
    }

    static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        // HUD: advancement-style activity toasts (top-right) when not watching a panel.
        // Forge 1.20.4 overlay API: register above the whole vanilla HUD chat layer.
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "numen_toasts",
                (gui, g, partialTick, screenWidth, screenHeight) ->
                        com.dwinovo.numen.client.hud.NumenToasts.render(g));
    }

    static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        Path numenConfigRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve(Constants.MOD_ID);
        Path skillsDir = numenConfigRoot.resolve("skills");

        // MCP client: connect to any external MCP servers listed in
        // config/numen/mcp_clients.json and register their tools so the built-in
        // brain can call them.
        com.dwinovo.numen.mcp.client.McpClientManager.initClient(numenConfigRoot);

        // MCP server: the other direction — a loopback MCP server letting an external
        // agent drive companions directly, bypassing the built-in brain.
        // Off unless enabled in config/numen/mcp_server.json.
        com.dwinovo.numen.mcp.server.NumenMcp.initClient(
                Minecraft.getInstance().gameDirectory.toPath().resolve("config"));

        event.registerReloadListener((ResourceManagerReloadListener) rm -> {
            SkillRegistry.instance().scan(skillsDir);
        });
    }
}
