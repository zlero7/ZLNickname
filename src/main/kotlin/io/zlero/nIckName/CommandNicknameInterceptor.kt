package io.zlero.nIckName

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.listener.annotation.Subscribe
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent

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

    // 두 번째 인자도 플레이어 이름인 명령어 (/tp <대상> <목적지>)
    private val twoPlayerCommands = setOf("tp")

    /**
     * Paper 의 AsyncTabCompleteEvent 를 인터셉트해 닉네임으로 교체합니다.
     * Bukkit 의 TabCompleteEvent 는 EssentialsX 등 다른 플러그인이 AsyncTabCompleteEvent 에서
     * 먼저 완성을 주입하면 실질적으로 무시되기 때문에 이 이벤트를 사용합니다.
     * HIGH 우선순위로 등록하여 다른 플러그인이 넣은 실제 이름 완성을 닉네임으로 덮어씁니다.
     */
    @Subscribe(priority = EventPriority.HIGH)
    fun onTabComplete(event: AsyncTabCompleteEvent) {
        if (!event.isCommand) return
        val buffer = event.buffer
        if (!buffer.startsWith("/")) return

        // limit=4: [명령어, 첫째인자, 둘째인자, 나머지]
        val parts = buffer.substring(1).split(" ", limit = 4)
        if (parts.isEmpty()) return

        val cmdName = parts[0].lowercase().substringAfter(":")

        val names: List<String> = when (parts.size) {
            2 -> {
                if (cmdName !in targetFirstCommands) return
                nicknameManager.onlineDisplayNames(parts[1])
            }
            3 -> {
                if (cmdName !in twoPlayerCommands) return
                nicknameManager.onlineDisplayNames(parts[2])
            }
            else -> return
        }

        event.completions = names
        // handled = true: Brigadier 의 재처리를 막아 빨간색 표시 및 결과 덮어쓰기 방지
        event.isHandled = true
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
