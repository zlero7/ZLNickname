package io.zlero.nIckName

import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.util.*

class NicknameManager(val plugin: NicknamePlugin) : Listener {
    data class NicknameData(val raw: String, val full: String)

    private val nicknameMap = mutableMapOf<UUID, NicknameData>()
    private val armorStandMap = mutableMapOf<UUID, ArmorStand>()

    fun setNickname(player: Player, raw: String) {
        val full = ChatColor.translateAlternateColorCodes('&', raw)
        nicknameMap[player.uniqueId] = NicknameData(raw, full)

        player.setDisplayName(full)
        player.setPlayerListName(full)

        // 기본 이름표 숨기기
        hideNameTag(player)

        // ArmorStand로 이름 표시
        spawnOrUpdateArmorStand(player, full)
    }

    fun getNickname(player: Player): String? = nicknameMap[player.uniqueId]?.full

    fun resetNickname(player: Player) {
        nicknameMap.remove(player.uniqueId)

        player.setDisplayName(player.name)
        player.setPlayerListName(player.name)

        // 기본 이름표 다시 보이기
        showNameTag(player)

        // ArmorStand 제거
        removeArmorStand(player)
    }

    fun saveNicknames() {
        val file = File(plugin.dataFolder, "nicknames.yml")
        val config = YamlConfiguration()
        for ((uuid, data) in nicknameMap) {
            config.set("${uuid}.raw", data.raw)
            config.set("${uuid}.full", data.full)
        }
        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.warning("Error saving nicknames: ${e.message}")
        }
    }

    fun loadNicknames() {
        val file = File(plugin.dataFolder, "nicknames.yml")
        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)
        for (key in config.getKeys(false)) {
            val uuid: UUID = try {
                UUID.fromString(key)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid UUID format for key: $key")
                continue
            }

            val raw = config.getString("${key}.raw") ?: continue
            val full = config.getString("${key}.full") ?: continue

            nicknameMap[uuid] = NicknameData(raw, full)

            Bukkit.getPlayer(uuid)?.let { player ->
                player.setDisplayName(full)
                player.setPlayerListName(full)
                spawnOrUpdateArmorStand(player, full)
            }
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        nicknameMap[event.player.uniqueId]?.let {
            event.player.setDisplayName(it.full)
            event.player.setPlayerListName(it.full)
            spawnOrUpdateArmorStand(event.player, it.full)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        removeArmorStand(event.player)
    }

    private fun spawnOrUpdateArmorStand(player: Player, nickname: String) {
        removeArmorStand(player) // 기존 것이 있다면 제거

        val stand = player.world.spawnEntity(player.location.add(0.0, 1.9, 0.0), EntityType.ARMOR_STAND) as ArmorStand
        stand.isCustomNameVisible = true
        stand.customName = nickname
        stand.isInvisible = true
        stand.isMarker = true
        stand.isSmall = true
        stand.setGravity(false)

        armorStandMap[player.uniqueId] = stand

        // 자기 자신에게는 ArmorStand 숨기기
        player.hideEntity(plugin, stand)

        // ArmorStand가 플레이어를 따라다니도록 반복 작업
        object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !armorStandMap.containsKey(player.uniqueId)) {
                    cancel()
                    return
                }

                val yOffset = if (player.isSneaking) 1.6 else 1.9 // 쉬프트시 Y축 보정
                val targetLocation = player.location.clone().add(0.0, yOffset, 0.0)

                stand.teleport(targetLocation)

                // 쉬프트 중이면 숨기기
                stand.isCustomNameVisible = !player.isSneaking
            }
        }.runTaskTimer(plugin, 0L, 1L) // 매 틱마다 동기화 (빠르게 반응)
    }

    private fun hideNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val teamName = "nick_${player.name.take(14)}" // 팀 이름은 16자 제한

        val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
        team.nameTagVisibility = org.bukkit.scoreboard.NameTagVisibility.NEVER
        team.addEntry(player.name)
    }

    private fun showNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        scoreboard.teams.filter { it.hasEntry(player.name) }.forEach { it.removeEntry(player.name) }
    }

    private fun removeArmorStand(player: Player) {
        armorStandMap.remove(player.uniqueId)?.remove()
    }
}