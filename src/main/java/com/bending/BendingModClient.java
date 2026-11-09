package com.bending;

import com.bending.client.BendingHud;
import com.bending.client.BendingHands;
import com.bending.client.ClientInput;
import com.bending.client.ShoulderCamera;
import com.bending.client.particle.CelParticle;
import com.bending.client.render.ChargeOrbRenderer;
import com.bending.client.render.FireBlastRenderer;
import com.bending.entity.FireBlastEntity;
import com.bending.entity.ModEntities;
import com.bending.particle.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

// Client-only entry point: never loaded on a dedicated server, so client classes
// (rendering, keybinds, screens) are safe to use here.
@Mod(value = BendingMod.MODID, dist = Dist.CLIENT)
public class BendingModClient {
    public BendingModClient(IEventBus modEventBus) {
        modEventBus.addListener(BendingModClient::registerKeys);
        modEventBus.addListener(BendingModClient::registerHud);
        modEventBus.addListener(BendingModClient::registerRenderers);
        modEventBus.addListener(BendingModClient::registerParticles);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(com.bending.client.MoveAnimations::init));
        NeoForge.EVENT_BUS.addListener(ClientInput::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.bending.client.HandEmbers::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.bending.client.ImpactEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.bending.client.ImpactEffects::onCameraAngles);
        com.bending.client.ImpactEffects.init();
        NeoForge.EVENT_BUS.addListener(ClientInput::onInteractionKey);
        BendingHands.register(NeoForge.EVENT_BUS);
        // Keep fireball trail fire out of the viewer's face (same distance the ball fades in from).
        FireBlastEntity.tooCloseToCamera = p -> {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            return camera.isInitialized() && camera.getPosition().distanceToSqr(p)
                    < FireBlastRenderer.FADE_START * FireBlastRenderer.FADE_START;
        };
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (var key : ClientInput.SLOT_KEYS) {
            event.register(key);
        }
        event.register(ClientInput.MENU_KEY);
        event.register(ClientInput.TOGGLE_KEY);
        event.register(ClientInput.SHOULDER_KEY);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "impact_flash"),
                com.bending.client.ImpactEffects::renderFlash);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "bending_hud"), BendingHud::render);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "shoulder_crosshair"),
                ShoulderCamera::renderCrosshair);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FIRE_BLAST.get(), FireBlastRenderer::new);
        event.registerEntityRenderer(ModEntities.CHARGE_ORB.get(), ChargeOrbRenderer::new);
        // Drawn entirely with particles, so no model.
        event.registerEntityRenderer(ModEntities.FIRESTORM.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_ARC.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.WALL_OF_FIRE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_ARC.get(), com.bending.client.render.LightningArcRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        // All bending particles use the cel-shaded CelParticle; the Kind sets size, life and motion.
        register(event, ModParticles.FIRE_WISP.get(), CelParticle.Kind.WISP);
        register(event, ModParticles.FIRE_CORE.get(), CelParticle.Kind.CORE);
        register(event, ModParticles.FIRE_SPARK.get(), CelParticle.Kind.SPARK);
        register(event, ModParticles.EMBER.get(), CelParticle.Kind.EMBER);
        register(event, ModParticles.FIRE_SMOKE.get(), CelParticle.Kind.SMOKE);
        register(event, ModParticles.STEAM.get(), CelParticle.Kind.STEAM);
        register(event, ModParticles.LIGHTNING_ARC.get(), CelParticle.Kind.ARC);
        register(event, ModParticles.HEAT_HAZE.get(), CelParticle.Kind.HEAT);
        register(event, ModParticles.FIRE_CLOUD.get(), CelParticle.Kind.CLOUD);
        register(event, ModParticles.BLUE_FIRE_CLOUD.get(), CelParticle.Kind.CLOUD, true);
        // Blue Fire: same shapes, blue colour ramp.
        register(event, ModParticles.BLUE_FIRE_WISP.get(), CelParticle.Kind.WISP, true);
        register(event, ModParticles.BLUE_FIRE_CORE.get(), CelParticle.Kind.CORE, true);
        register(event, ModParticles.BLUE_FIRE_SPARK.get(), CelParticle.Kind.SPARK, true);
        register(event, ModParticles.BLUE_EMBER.get(), CelParticle.Kind.EMBER, true);
        register(event, ModParticles.HAND_EMBER.get(), CelParticle.Kind.HAND_EMBER);
        register(event, ModParticles.BLUE_HAND_EMBER.get(), CelParticle.Kind.HAND_EMBER, true);
    }

    private static void register(RegisterParticleProvidersEvent event, SimpleParticleType type, CelParticle.Kind kind) {
        register(event, type, kind, false);
    }

    private static void register(RegisterParticleProvidersEvent event, SimpleParticleType type, CelParticle.Kind kind, boolean blue) {
        event.registerSpriteSet(type, sprites -> (t, level, x, y, z, xd, yd, zd) ->
                new CelParticle(level, x, y, z, xd, yd, zd, sprites, kind, blue));
    }
}
