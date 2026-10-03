# ClientWatch Companion 1.0.6

- Исправлена отправка отчёта на Paper/Bukkit: вместо `ClientPlayNetworking.send(...)` используется обычный vanilla `ServerboundCustomPayloadPacket`.
- Это обходится без `ClientPlayNetworking.canSend(...)`, который ориентируется на объявленные сервером Fabric payload-каналы; обычный Bukkit/Paper plugin channel при этом остаётся рабочим.
- Сохранены совместимость и зависимости: Fabric Loader `>=0.19.0`, Fabric API `>=0.152.0+26.2`.
