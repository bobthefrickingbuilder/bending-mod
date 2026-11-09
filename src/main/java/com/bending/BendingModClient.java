package com.bending;

import com.bending.client.BendingHud;
import com.bending.client.BendingHands;
import com.bending.client.ClientInput;
import com.bending.entity.ModEntities;
import com.bending.particle.ModParticles;

import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.LavaParticle;
import net.minecraft.client.particle.PlayerCloudParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
        NeoForge.EVENT_BUS.addListener(ClientInput::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientInput::onInteractionKey);
        BendingHands.register(NeoForge.EVENT_BUS);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (var key : ClientInput.SLOT_KEYS) {
            event.register(key);
        }
        event.register(ClientInput.MENU_KEY);
        event.register(ClientInput.TOGGLE_KEY);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "bending_hud"), BendingHud::render);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // These entities are drawn entirely with particles, so they have no model.
        event.registerEntityRenderer(ModEntities.FIRE_BLAST.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRESTORM.get(), NoopRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        // Each named particle borrows a vanilla particle's behaviour for now.
        // Replace a provider here to change how that particle moves, fades or glows.
        event.registerSpriteSet(ModParticles.FIRE_WISP.get(), FlameParticle.Provider::new);
        event.registerSpriteSet(ModParticles.FIRE_SPARK.get(), FlameParticle.SmallFlameProvider::new);
        event.registerSpriteSet(ModParticles.EMBER.get(), LavaParticle.Provider::new);
        event.registerSpriteSet(ModParticles.FIRE_SMOKE.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.STEAM.get(), PlayerCloudParticle.Provider::new);
    }
}
