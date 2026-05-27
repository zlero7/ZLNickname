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
import org.bukkit.configuration.file.YamlConfiguration
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
import java.io.File
import java.util.UUID

// ─── 파일 레벨 직렬화 상수 (NicknameData에서도 접근 가능) ───
private val LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand()
private val LEGACY_SECTION   = LegacyComponentSerializer.legacySection()
private val PLAIN_TEXT       = PlainTextComponentSerializer.plainText()

@Component
class NicknameManager(val plugin: NicknamePlugin) {

    /** raw: & 코드 원본 (GUI 미리채우기·저장), full: § 코드 변환본 (저장·메시지) */
    data class NicknameData(val raw: String, val full: String) {
        fun toTextComponent(): TextComponent = LEGACY_SECTION.deserialize(full)
        /** 색상 코드를 제거한 순수 텍스트 (역방향 캐시 키에 사용) */
        fun plainText(): String = PLAIN_TEXT.serialize(toTextComponent())
    }

    // UUID → 닉네임 데이터
    private val nicknameMap   = mutableMapOf<UUID, NicknameData>()
    // 순수 텍스트(소문자) → UUID  역방향 캐시 (O(1) 조회)
    // 버그 수정: 이전에는 § 코드 포함 full 문자열을 키로 사용해 색상 닉네임 조회 불가
    private val nickToUUID    = mutableMapOf<String, UUID>()
    // UUID → ArmorStand
    private val armorStandMap = mutableMapOf<UUID, ArmorStand>()

    // PDC 키: 플러그인이 생성한 ArmorStand 식별 (서버 재시작 후 잔여 엔티티 청소용)
    private val standKey = NamespacedKey(plugin, "nickname_stand")

    companion object {
        /** 색상 제거 후 최대 닉네임 글자 수 */
        const val MAX_NICK_LENGTH = 16
    }

    // ─────────────── 공개 API ───────────────

    /**
     * 닉네임 유효성 검사.
     * @return null 이면 유효, String 이면 오류 메시지
     */
    fun validateNickname(raw: String): String? {
        if (raw.isBlank()) return "닉네임은 비어있을 수 없습니다."
        val plain = PLAIN_TEXT.serialize(LEGACY_AMPERSAND.deserialize(raw))
        if (plain.isBlank()) return "색상 코드 제거 후 닉네임이 비어있습니다."
        if (plain.length > MAX_NICK_LENGTH)
            return "닉네임은 ${MAX_NICK_LENGTH}자를 초과할 수 없습니다. (현재 ${plain.length}자)"
        return null
    }

    fun setNickname(player: Player, raw: String) {
        val component = LEGACY_AMPERSAND.deserialize(raw)
        val full      = LEGACY_SECTION.serialize(component)  // § 코드 문자열 (저장용)

        // 기존 역방향 캐시 제거 (순수 텍스트 키 사용)
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
        player.displayName(null)       // null → 기본 플레이어 이름으로 복원
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

        val file = File(plugin.dataFolder, "nicknames.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)

        for (key in config.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrElse {
                plugin.logger.warning("잘못된 UUID: $key"); return@getOrElse null
            } ?: continue

            val raw  = config.getString("$key.raw")  ?: continue
            val full = config.getString("$key.full") ?: continue

            val data = NicknameData(raw, full)
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
        // 서버 종료 시 모든 ArmorStand 명시적 제거 (isPersistent=false 보조)
        armorStandMap.values.forEach { it.remove() }
        armorStandMap.clear()
        saveNicknames()
    }

    fun saveNicknames() {
        val file   = File(plugin.dataFolder, "nicknames.yml")
        val config = YamlConfiguration()
        nicknameMap.forEach { (uuid, data) ->
            config.set("$uuid.raw",  data.raw)
            config.set("$uuid.full", data.full)
        }
        runCatching { config.save(file) }
            .onFailure { plugin.logger.warning("닉네임 저장 실패: ${it.message}") }
    }

    // ─────────────── 이벤트 리스너 ───────────────

    @Subscribe
    fun onJoin(event: PlayerJoinEvent) {
        val joiningPlayer = event.player

        // 접속한 플레이어에게 기존 ArmorStand를 모두 보여줌
        // (disconnect 시 hideEntity 상태가 초기화되므로, 재접속 시에도 적용)
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
        // 실제 위치 변화 시에만 처리 (머리 회전 전용 틱 스킵)
        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        val stand = armorStandMap[event.player.uniqueId] ?: return
        val yOffset = if (event.player.isSneaking) 1.6 else 1.9
        // event.to 는 내부 레퍼런스 → clone 후 수정 (원본 수정 시 플레이어 이동 방향 오염)
        stand.teleport(to.clone().add(0.0, yOffset, 0.0))
    }

    @Subscribe
    fun onSneak(event: PlayerToggleSneakEvent) {
        val stand = armorStandMap[event.player.uniqueId] ?: return
        val yOffset = if (event.isSneaking) 1.6 else 1.9
        // player.location 은 항상 새 Location 반환 → clone 불필요
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

    /** 온라인 플레이어 중 current 로 시작하는 순수 텍스트 닉네임 또는 실제 이름 반환 */
    private fun onlinePlayerNames(current: String): List<String> =
        Bukkit.getOnlinePlayers()
            .map { p -> nicknameMap[p.uniqueId]?.plainText() ?: p.name }
            .filter { it.startsWith(current, ignoreCase = true) }

    // ─────────────── ArmorStand 관리 ───────────────

    private fun spawnOrUpdateArmorStand(player: Player, nickname: TextComponent) {
        removeArmorStand(player)

        val sneaking = player.isSneaking
        val yOffset  = if (sneaking) 1.6 else 1.9

        val stand = (player.world.spawnEntity(
            player.location.clone().add(0.0, yOffset, 0.0), EntityType.ARMOR_STAND
        ) as ArmorStand).apply {
            isCustomNameVisible = !sneaking                           // 웅크림 상태 초기 반영
            customName(nickname)                                      // Adventure API
            isInvisible  = true
            isMarker     = true
            isSmall      = true
            setGravity(false)
            isPersistent = false                                      // 월드 저장 시 제외
            persistentDataContainer.set(standKey, PersistentDataType.BYTE, 1) // 플러그인 소유 표시
        }
        armorStandMap[player.uniqueId] = stand
        player.hideEntity(plugin, stand)  // 닉네임 주인은 본인 머리 위 ArmorStand 안 보이게
    }

    private fun removeArmorStand(player: Player) {
        armorStandMap.remove(player.uniqueId)?.remove()
    }

    // ─────────────── 이름표 숨김/복원 ───────────────

    private fun hideNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        // 버그 수정: "nick_" + 이름 14자 = 최대 19자로 Bukkit 팀 이름 제한(16자) 초과 가능
        // UUID hex 앞 16자로 교체 → 고유성 보장 + 길이 제한 준수
        val teamName = player.uniqueId.toString().replace("-", "").take(16)
        val team     = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)  // deprecated API 교체
        team.addEntry(player.name)
    }

    private fun showNameTag(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        // UUID 기반 팀 이름으로 O(1) 직접 조회 후 팀 자체를 제거 (스코어보드 팀 누수 방지)
        val teamName = player.uniqueId.toString().replace("-", "").take(16)
        scoreboard.getTeam(teamName)?.unregister()
    }
}
