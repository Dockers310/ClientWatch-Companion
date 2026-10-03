package com.example.clientwatch.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RefreshPayload(String nonce) implements CustomPacketPayload {
    public static final Type<RefreshPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("clientwatch", "request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RefreshPayload::nonce,
            RefreshPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
