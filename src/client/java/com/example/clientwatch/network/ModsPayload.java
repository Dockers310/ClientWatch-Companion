package com.example.clientwatch.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ModsPayload(String json) implements CustomPacketPayload {
    public static final Type<ModsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("clientwatch", "mods"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ModsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ModsPayload::json,
            ModsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
