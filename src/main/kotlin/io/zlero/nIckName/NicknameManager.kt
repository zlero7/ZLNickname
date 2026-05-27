package io.zlero.nIckName

import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.core.component.annotation.Setup
import io.zlero.cRFramework.core.component.annotation.Teardown
import io.zlero.cRFramework.listener.annotation.Subscribe
import net.kyori.adventure.text.Component as TextComponent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.server.TabCompleteEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Team
import java.util.UUID

// ─── 파일 레벨 직렬화 상수 (NicknameData에서도 접근 가능) ───
private val LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand()
private val LEGACY_SECTION   = LegacyComponentSerializer.legacySection()
private val PLAIN_TEXT       = PlainTextComponentSerializer.plainText()

@Component
class NicknameManager(
    val plugin: NicknamePlugin,
    private val config: NicknameConfig,
    private val storage: NicknameStorage
) {

    /** raw: & 코드 원본 (GUI 미리채우기·저장), full: § 코드 변환본 (저장·메시지) */
    data class NicknameData(val raw: String, val full: String) {
        fun toTextComponent(): TextComponent = LEGACY_SECTION.deserialize(full)
        /** 색상 코드를 제거한 순수 텍스트 (역방향 캐시 키에 사용) */
        fun plainText(): String = PLAIN_TEXT.serialize(toTextComponent())
    }

    // UUID → 닉네임 데이터
    private val nicknameMap   = mutableMapOf<UUID, NicknameData>()
    // 순수 텍스트(소문자) → UUID  역방향 캐시 (O(1) 조회)
    private val nickToUUID    = mutableMapOf<String, UUID>()
    // UUID → ArmorStand
    private val armorStandMap = mutableMapOf<UUID, ArmorStand>()

    // PDC 키: 플러그인이 생성한 ArmorStand 식별 (서버 재시작 후 잔여 엔티티 청소용)
    private val standKey = NamespacedKey(plugin, "nickname_stand")

    // ─────────────── 공개 API ───────────────

    /**
     * 닉네임 유효성 검사.
     * @return null 이면 유효, String 이면 오류 메시지
     */
    fun validateNickname(raw: String): String? {
        if (raw.isBlank()) return "닉네임은 비어있을 수 없습니다."
        val plain = PLAIN_TEXT.serialize(LEGACY_AMPERSAND.deserialize(raw))
        if (plain.isBlank()) return "색상 코드 제거 후 닉네임이 비어있습니다."
        val max = config.maxNicknameLength
        if (plain.length > max)
            return "닉네임은 ${max}자를 초과할 수 없습니다. (현재 ${plain.length}자)"
        return null
    }

    fun setNickname(player: Player, raw: String) {
        val component = LEGACY_AMPERSAND.deserialize(raw)
        val full      = LEGACY_SECTION.serialize(component)

        nicknameMap[player.uniqueId]?.let { nickToUUID.remove(it.plainText().lowercase()) }

        val data = NicknameData(raw, full)
        nicknameMap[player.uniqueId] = data
        nickToUUID[data.plainText().lowercase()] = player.uniqueId

        player.displayName(component)
        player.playerListName(component)
        hideNameTag(player)
        spawnOrUpdateArmorStand(player, component)
    }

    fun resetNickname(player: Player) {
        nicknameMap.remove(player.uniqueId)?.let { nickToUUID.remove(it.plainText().lowercase()) }
        player.displayName(null)
        player.playerListName(null)
        showNameTag(player)
        removeArmorStand(player)
    }

    /** § 코드 포함 닉네임 문자열 (메시지 조합용) */
    fun getNickname(player: Player): String? = nicknameMap[player.uniqueId]?.full

    /** 원본 닉네임 (& 코드 포함, GUI 미리채우기용) */
    fun getRawNickname(player: Player): String? = nicknameMap[player.uniqueId]?.raw

    /** 실제 이름 또는 닉네임(색상 제거)으로 온라인 플레이어 검색 */
    fun findPlayer(nameOrNick: String): Player? =
        Bukkit.getPlayerExact(nameOrNick)
            ?: nickToUUID[nameOrNick.lowercase()]?.let { Bukkit.getPlayer(it) }

    // ─────────────── 라이프사이클 ───────────────

    @Setup
    fun loadNicknames() {
        // 이전 버전(isPersistent=true)에서 저장된 잔여 ArmorStand 제거
        Bukkit.getWorlds().forEach { world ->
            world.entities
                .filterIsInstance<ArmorStand>()
                .filter { it.persistentDataContainer.has(standKey, PersistentDataType.BYTE) }
                .forEach { it.remove() }
        }

        val entries = runCatching { storage.loadAll() }
            .onFailure { plugin.logger.warning("닉네임 로드 실패: ${it.message}") }
            .getOrDefault(emptyMap())

        entries.forEach { (uuid, entry) ->
            val data = NicknameData(entry.raw, entry.full)
            nicknameMap[uuid] = data
            nickToUUID[data.plainText().lowercase()] = uuid

            Bukkit.getPlayer(uuid)?.apply {
                val comp = data.toTextComponent()
                displayName(comp)
                playerListName(comp)
                spawnOrUpdateArmorStand(this, comp)
            }
        }
        plugin.logger.info("닉네임 ${nicknameMap.size}개 로드 완료")
    }

    @Teardown
    fun onDisable() {
        armorStandMap.values.forEach { it.remove() }
        armorStandMap.clear()
        saveNicknames()
    }

    fun saveNicknames() {
        val entries = nicknameMap.mapValues { (_, data) ->
            NicknameStorage.Entry(data.raw, data.full)
        }
        runCatching { storage.saveAll(entries) }
            .onFailure { plugin.logger.warning("닉네임 저장 실패: ${it.message}") }
    }

    // ─────────────── 이벤트 리스너 ───────────────

    @Subscribe
    fun onJoin(event: PlayerJoinEvent) {
        val joiningPlayer = event.player

        armorStandMap.forEach { (ownerUUID, stand) ->
            if (ownerUUID != joiningPlayer.uniqueId) {
                joiningPlayer.showEntity(plugin, stand)
            }
        }

        nicknameMap[joiningPlayer.uniqueId]?.let { data ->
            val comp = data.toTextComponent()
            joiningPlayer.displayName(comp)
            joiningPlayer.playerListName(comp)
            spawnOrUpdateArmorStand(joiningPlayer, comp)
        }
    }

    @Subscribe
    fun onQuit(event: PlayerQuitEvent) = removeArmorStand(event.player)

    @Subscribe
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to   = event.to
        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        val stand = armorStandMap[event.player.uniqueId] ?: return
        val yOffset = if (event.player.isSneaking) config.standOffsetSneaking else config.standOffsetStanding
        stand.teleport(to.clone().add(0.0, yOffset, 0.0))
    }

    @Subscribe
    fun onSneak(event: PlayerToggleSneakEvent) {
        val stand = armorStandMap[event.player.uniqueId] ?: return
        val yOffset = if (event.isSneaking) config.standOffsetSneaking else config.standOffsetStanding
        stand.teleport(event.player.location.add(0.0, yOffset, 0.0))
        stand.isCustomNameVisible = !event.isSneaking
    }

    /** /닉네임 명령어 탭 완성 (admin 전용) */
    @Subscribe(priority = EventPriority.HIGH)
    fun onTabComplete(event: TabCompleteEvent) {
        val parts = event.buffer.split(" ")
        if (parts[0].removePrefix("/") != "닉네임") return
        if (parts.size >= 2) {
            event.completions = buildSubcompletions(parts.drop(1), event.sender)
        }
    }

    // ─────────────── 탭 완성 내부 로직 ───────────────

    private fun buildSubcompletions(args: List<String>, sender: CommandSender): List<String> {
        if (!sender.hasPermission("nickname.admin")) return emptyList()

        val subs    = listOf("설정", "초기화", "관리", "저장")
        val current = args.lastOrNull() ?: ""

        return when (args.size) {
            1 -> subs.filter { it.startsWith(current) }
            2 -> when (args[0]) {
                "설정", "초기화", "관리" -> onlinePlayerNames(current)
                else                    -> emptyList()
            }
            3 -> if (args[0] == "관리") {
                findPlayer(args[1])
                    ?.let { nicknameMap[it.uniqueId]?.raw }
                    ?.takeIf { it.startsWith(current, ignoreCase = true) }
                    ?.let { listOf(it) }
                    ?: emptyList()
            } else emptyList()
            else -> emptyList()
        }
    }

    private fun onlinePlayerNames(current: String): List<String> =
        Bukkit.getOnlinePlayers()
            .map { p -> nicknameMap[p.uniqueId]?.plainText() ?: p.name }
            .filter { it.startsWith(current, ignoreCase = true) }

    // ─────────────── ArmorStand 관리 ───────────────

    private fun spawnOrUpdateArmorStand(player: Player, nickname: TextComponent) {
        removeArmorStand(player)

        val sneaking = player.isSneaking
        val yOffset  = if (sneaking) config.standOffsetSneaking else config.standOffsetStanding

        val stand = (player.world.spawnEntity(
            player.location.clone().add(0.0, yOffset, 0.0), EntityType.ARMOR_STAND
        ) as ArmorStand).apply {
            isCustomNameVisible = !sneaking
            customName(nickname)
            isInvisible  = true
            isMarker     = true
            isSmall      = true
            setGravity(false)
            isPersistent = false
            persistentDataContainer.set(standKey, PersistentDataType.BYTE, 1)
        }
        armorStandMap[player.uniqueId] = stand
        player.hideEntity(plugin, stand)
    }

    private fun removeArmorStand(player: Player) {
        armorStandMap.remove(player.uniqueId)?.remove()
    }

    // ─────────────── 이름표 숨김/복원 ───────────────

    private fun hideNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val teamName   = player.uniqueId.toString().replace("-", "").take(16)
        val team       = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
        team.addEntry(player.name)
    }

    private fun showNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val teamName   = player.uniqueId.toString().replace("-", "").take(16)
        scoreboard.getTeam(teamName)?.unregister()
    }
}
