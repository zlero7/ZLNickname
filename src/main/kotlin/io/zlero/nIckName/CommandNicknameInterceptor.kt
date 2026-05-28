package io.zlero.nIckName

import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.listener.annotation.Subscribe
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.TabCompleteEvent

@Component
class CommandNicknameInterceptor(private val nicknameManager: NicknameManager) {

    private val targetFirstCommands = setOf(
        // 메시지
        "msg", "m", "tell", "t", "w", "whisper", "dm", "pm", "message", "emsg", "etell",
        // 텔레포트
        "tpa", "tpask", "tpahere", "tphere", "tp",
        // 이코노미
        "pay", "givemoney", "takemoney",
        // 어드민
        "ban", "tempban", "unban", "kick", "mute", "unmute", "tempmute", "warn", "jail", "unjail",
        // 인벤토리/상호작용
        "invsee", "ec", "enderchest", "trade", "duel", "heal", "feed", "freeze", "unfreeze",
        // 기타
        "seen", "ping", "profile"
    )

    /**
     * targetFirstCommands 의 첫 번째 인자(플레이어 이름) 탭 완성을 닉네임으로 교체합니다.
     * 서버 기본 완성(실제 이름)을 완전히 대체하므로 CommandNicknameInterceptor 가
     * 닉네임 → 실제 이름 변환을 이미 담당하고 있는 명령어에 한해서만 적용합니다.
     */
    // 두 번째 인자도 플레이어 이름인 명령어 (/tp <대상> <목적지>)
    private val twoPlayerCommands = setOf("tp")

    @Subscribe(priority = EventPriority.HIGH)
    fun onTabComplete(event: TabCompleteEvent) {
        val buffer = event.buffer
        if (!buffer.startsWith("/")) return

        // limit=4 으로 분리: [명령어, 첫째인자, 둘째인자, 나머지]
        val parts = buffer.substring(1).split(" ", limit = 4)
        if (parts.isEmpty()) return

        val cmdName = parts[0].lowercase().substringAfter(":")

        when (parts.size) {
            2 -> {
                // 첫 번째 인자 완성
                if (cmdName !in targetFirstCommands) return
                event.completions = nicknameManager.onlineDisplayNames(parts[1])
            }
            3 -> {
                // 두 번째 인자 완성 (/tp <대상> <목적지>)
                if (cmdName !in twoPlayerCommands) return
                event.completions = nicknameManager.onlineDisplayNames(parts[2])
            }
        }
    }

    @Subscribe(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val raw = event.message
        if (!raw.startsWith("/")) return

        // parts[0]=명령어, parts[1]=첫 인자(대상), parts[2]=나머지
        val parts = raw.substring(1).split(" ", limit = 3)
        if (parts.size < 2) return

        // /minecraft:tell 같은 네임스페이스 명령도 처리
        val cmdName = parts[0].lowercase().substringAfter(":")
        if (cmdName !in targetFirstCommands) return

        val input = parts[1]
        val target = nicknameManager.findPlayer(input) ?: return

        // 이미 실제 이름이면 그냥 통과
        if (target.name.equals(input, ignoreCase = true)) return

        val rest = if (parts.size > 2) " ${parts[2]}" else ""
        event.message = "/${parts[0]} ${target.name}$rest"
    }
}
