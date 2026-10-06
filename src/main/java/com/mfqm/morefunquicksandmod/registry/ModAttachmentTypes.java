package com.mfqm.morefunquicksandmod.registry;

import com.mfqm.morefunquicksandmod.MFQM;
import com.mfqm.morefunquicksandmod.gameplay.SinkingState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MFQM.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SinkingState>> SINKING_STATE =
            ATTACHMENTS.register("sinking_state", () -> AttachmentType.serializable(SinkingState::new)
                    .sync(SinkingState.STREAM_CODEC).build());
    private ModAttachmentTypes() {}
}
