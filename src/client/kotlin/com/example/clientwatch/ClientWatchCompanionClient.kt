package com.example.clientwatch

import com.example.clientwatch.network.ModsPayload
import com.example.clientwatch.network.RefreshPayload
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.ModContainer
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ClientWatchCompanionClient : ClientModInitializer {
    const val MOD_ID = "clientwatch-companion"
    const val VERSION = "1.0.6"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ClientWatch-Companion-Scanner").apply { isDaemon = true }
    }

    // Пассивный режим: нет команд, экрана, клавиш и настроек. Только приём запроса от сервера.
    override fun onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(ModsPayload.TYPE, ModsPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(RefreshPayload.TYPE, RefreshPayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(RefreshPayload.TYPE) { payload, _ ->
            requestReport(payload.nonce)
        }
    }

    private fun requestReport(nonce: String) {
        CompletableFuture.supplyAsync({ buildReport(nonce) }, executor)
            .orTimeout(60, TimeUnit.SECONDS)
            .whenComplete { json, error ->
                if (error != null || json == null) return@whenComplete
                try {
                    val connection = Minecraft.getInstance().connection
                    if (connection != null) {
                        // Отправляем обычный vanilla CustomPayload напрямую.
                        // ClientPlayNetworking.send(...) проверяет ClientPlayNetworking.canSend(...):
                        // Paper/Bukkit может не объявлять Fabric-приёмник этим способом,
                        // хотя плагин зарегистрировал обычный plugin channel.
                        // Прямой ServerboundCustomPayloadPacket совместим с таким Paper-каналом.
                        connection.send(ServerboundCustomPayloadPacket(ModsPayload(json)))
                    }
                } catch (_: Throwable) {
                    // The server may have disconnected while hashes were calculated.
                }
            }
    }

    private fun buildReport(nonce: String): String {
        val root = JsonObject()
        root.addProperty("protocol", 1)
        root.addProperty("nonce", nonce)
        root.addProperty("companionVersion", VERSION)
        val launcher = detectLauncher()
        root.addProperty("launcher", launcher.first)
        root.addProperty("launcherConfidence", launcher.second)
        root.addProperty("minecraftVersion", detectModVersion("minecraft"))
        root.addProperty("fabricLoaderVersion", detectModVersion("fabricloader"))

        val mods = JsonArray()
        for (mod in FabricLoader.getInstance().allMods) {
            val id = mod.metadata.id
            if (id == "minecraft" || id == "fabricloader" || id == MOD_ID) continue

            val item = JsonObject()
            item.addProperty("id", id)
            item.addProperty("name", mod.metadata.name.ifBlank { id })
            item.addProperty("version", mod.metadata.version.friendlyString)
            val hashes = findHashes(mod)
            item.addProperty("sha512", hashes.first)
            item.addProperty("sha1", hashes.second)
            mods.add(item)
        }
        root.add("mods", mods)
        return root.toString()
    }

    private fun findHashes(mod: ModContainer): Pair<String, String> {
        for (path in mod.origin.paths) {
            if (!Files.isRegularFile(path)) continue
            if (!path.fileName.toString().lowercase().endsWith(".jar")) continue
            try {
                val size = Files.size(path)
                // Avoid turning an enormous local file or a non-JAR development origin into a lag spike.
                if (size > 512L * 1024L * 1024L) return "" to ""
                return sha512(path) to sha1(path)
            } catch (_: IOException) {
                return "" to ""
            }
        }
        return "" to ""
    }

    private fun sha512(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-512")
        BufferedInputStream(Files.newInputStream(path), 64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }


    private fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        BufferedInputStream(Files.newInputStream(path), 64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun detectModVersion(id: String): String {
        return try {
            FabricLoader.getInstance().getModContainer(id).orElse(null)
                ?.metadata?.version?.friendlyString ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Только локальное определение по безопасным признакам. Сырые аргументы запуска
     * на сервер никогда не отправляются: они могут содержать пути, логины и другие данные.
     * Поэтому лаунчер может остаться "не определён".
     */
    private fun detectLauncher(): Pair<String, String> {
        val args = try {
            FabricLoader.getInstance().getLaunchArguments(true)
                .joinToString(" ") { it.lowercase() }
        } catch (_: Throwable) {
            ""
        }
        val props = try {
            listOf(
                System.getProperty("java.vm.name", ""),
                System.getProperty("sun.java.command", ""),
                System.getProperty("java.class.path", "")
            ).joinToString(" ").lowercase()
        } catch (_: Throwable) {
            ""
        }
        val signal = "$args $props"
        val checks = listOf(
            "prism" to "Prism Launcher",
            "multimc" to "MultiMC",
            "polymc" to "PolyMC",
            "atlauncher" to "ATLauncher",
            "gdlauncher" to "GDLauncher",
            "curseforge" to "CurseForge",
            "overwolf" to "Overwolf / CurseForge",
            "modrinth" to "Modrinth App",
            "lunarclient" to "Lunar Client",
            "badlion" to "Badlion Client",
            "feather" to "Feather Client",
            "labymod" to "LabyMod"
        )
        for ((needle, name) in checks) {
            if (signal.contains(needle)) return name to "определён по локальным признакам"
        }
        return "" to "не определён"
    }

}
