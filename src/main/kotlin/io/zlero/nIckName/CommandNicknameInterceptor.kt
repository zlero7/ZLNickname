package io.zlero.nIckName

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
