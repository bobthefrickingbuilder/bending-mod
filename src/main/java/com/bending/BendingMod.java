package com.bending;

import org.slf4j.Logger;

import com.bending.data.ModAttachments;
import com.bending.entity.ModEntities;
import com.bending.network.BendingNetwork;
import com.bending.particle.ModParticles;
import com.bending.server.BendingServerEvents;
import com.bending.sound.ModSounds;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

// Main mod class. The value must match mod_id in gradle.properties.
@Mod(BendingMod.MODID)
public class BendingMod {
    public static final String MODID = "bending";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Runs first when the mod loads. NeoForge passes in the mod event bus.
    public BendingMod(IEventBus modEventBus) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);

        modEventBus.addListener(BendingNetwork::register);
        modEventBus.addListener(this::commonSetup);

        BendingServerEvents.register(NeoForge.EVENT_BUS);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Bending Mod loaded");
    }
}
