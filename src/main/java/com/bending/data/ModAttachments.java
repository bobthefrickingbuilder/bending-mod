package com.bending.data;

import java.util.function.Supplier;

import com.bending.BendingMod;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BendingMod.MODID);

    /** Per-player bending data. Saved with the player and kept through death. */
    public static final Supplier<AttachmentType<BendingData>> BENDING = ATTACHMENT_TYPES.register("bending",
            () -> AttachmentType.builder(BendingData::new)
                    .serialize(BendingData.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {}
}
