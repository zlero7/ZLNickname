package io.zlero.nIckName

import io.zlero.cRFramework.command.CommandContext
import io.zlero.cRFramework.command.annotation.Command
import io.zlero.cRFramework.core.component.annotation.Component
import net.wesjd.anvilgui.AnvilGUI

@Component
class NicknameCommand(
    private val nicknameManager: NicknameManager,
    private val config: NicknameConfig
) {

    private val plugin get() = nicknameManager.plugin

    /**
     * 모든 서브커맨드는 nickname.admin 권한 필요
     *
     * /닉네임 설정 <플레이어>            → 해당 플레이어에게 모루 GUI 열기
     * /닉네임 초기화 <플레이어>          → 닉네임 초기화
     * /닉네임 관리 <플레이어> <닉네임>   → GUI 없이 직접 닉네임 설정
     * /닉네임 저장                       → 파일 저장
     */
    @Command(name = "닉네임", description = "닉네임 관리 명령어", usage = "/닉네임 <설정|초기화|관리|저장>")
    fun handle(ctx: CommandContext) {
        if (!ctx.sender.hasPermission("nickname.admin")) {
            ctx.sender.sendMessage("§c권한이 없습니다.")
            return
        }
        if (ctx.size == 0) { sendHelp(ctx); return }

        when (ctx.string(0).lowercase()) {
            "설정"   -> handleSet(ctx)
            "초기화" -> handleReset(ctx)
            "관리"   -> handleAdmin(ctx)
            "저장"   -> handleSave(ctx)
            "리로드" -> handleReload(ctx)
            else     -> { ctx.sender.sendMessage("§c알 수 없는 서브 명령어입니다."); sendHelp(ctx) }
        }
    }

    // ─────────────── 설정: GUI 열기 ───────────────

    private fun handleSet(ctx: CommandContext) {
        if (ctx.size < 2) {
            ctx.sender.sendMessage("§c사용법: /닉네임 설정 <플레이어>")
            return
        }

        val target = nicknameManager.findPlayer(ctx.string(1))
            ?: run { ctx.sender.sendMessage("§c플레이어를 찾을 수 없습니다."); return }

        val prefill = nicknameManager.getRawNickname(target) ?: ""

        target.sendMessage("§e관리자가 닉네임 변경 창을 열었습니다. 입력 후 클릭해 주세요.")

        AnvilGUI.Builder()
            .plugin(plugin)
            .title("닉네임 설정: ${target.name}")
            .text(prefill)
            .onClick { slot, snapshot ->
                if (slot != AnvilGUI.Slot.OUTPUT) return@onClick emptyList()

                if (!target.isOnline) {
                    return@onClick listOf(AnvilGUI.ResponseAction.replaceInputText("플레이어가 오프라인입니다."))
                }

                val newNick = snapshot.text.trim()
                val error   = nicknameManager.validateNickname(newNick)
                if (error != null) {
                    return@onClick listOf(AnvilGUI.ResponseAction.replaceInputText(error))
                }

                nicknameManager.setNickname(target, newNick)
                val display = nicknameManager.getNickname(target)
                target.sendMessage("§a닉네임이 '${display}§r§a' 으로 변경되었습니다.")

                if (ctx.isPlayer && ctx.player.uniqueId != target.uniqueId) {
                    ctx.sender.sendMessage("§a${target.name}의 닉네임을 '${display}§r§a' 으로 설정했습니다.")
                }

                listOf(AnvilGUI.ResponseAction.close())
            }
            .open(target)

        if (!ctx.isPlayer || ctx.player.uniqueId != target.uniqueId) {
            ctx.sender.sendMessage("§a${target.name}에게 닉네임 변경 창을 열었습니다.")
        }
    }

    // ─────────────── 초기화 ───────────────

    private fun handleReset(ctx: CommandContext) {
        if (ctx.size < 2) {
            ctx.sender.sendMessage("§c사용법: /닉네임 초기화 <플레이어>")
            return
        }

        val target = nicknameManager.findPlayer(ctx.string(1))
            ?: run { ctx.sender.sendMessage("§c플레이어를 찾을 수 없습니다."); return }

        nicknameManager.resetNickname(target)
        ctx.sender.sendMessage("§a${target.name}의 닉네임을 초기화했습니다.")
        target.sendMessage("§e관리자에 의해 닉네임이 초기화되었습니다.")
    }

    // ─────────────── 관리: GUI 없이 직접 설정 ───────────────

    private fun handleAdmin(ctx: CommandContext) {
        if (ctx.size < 3) {
            ctx.sender.sendMessage("§c사용법: /닉네임 관리 <플레이어> <닉네임>")
            return
        }

        val target = nicknameManager.findPlayer(ctx.string(1))
            ?: run { ctx.sender.sendMessage("§c플레이어를 찾을 수 없습니다."); return }

        val raw   = ctx.joinFrom(2)
        val error = nicknameManager.validateNickname(raw)
        if (error != null) {
            ctx.sender.sendMessage("§c$error")
            return
        }

        nicknameManager.setNickname(target, raw)
        val display = nicknameManager.getNickname(target)
        ctx.sender.sendMessage("§a${target.name}의 닉네임을 '${display}§r§a' 으로 설정했습니다.")
        target.sendMessage("§e관리자에 의해 닉네임이 변경되었습니다.")
    }

    // ─────────────── 저장 ───────────────

    private fun handleSave(ctx: CommandContext) {
        nicknameManager.saveNicknames()
        ctx.sender.sendMessage("§a닉네임 데이터를 저장했습니다.")
    }

    // ─────────────── 리로드 ───────────────

    private fun handleReload(ctx: CommandContext) {
        nicknameManager.reload()
        ctx.sender.sendMessage("§a설정과 닉네임 데이터를 다시 불러왔습니다.")
    }

    // ─────────────── 도움말 ───────────────

    private fun sendHelp(ctx: CommandContext) {
        val s   = ctx.sender
        val max = config.maxNicknameLength
        s.sendMessage("§e══════ 닉네임 명령어 (관리자) ══════")
        s.sendMessage("§f/닉네임 설정 §7<플레이어>  §f- §7해당 플레이어에게 닉네임 변경 GUI")
        s.sendMessage("§f/닉네임 초기화 §7<플레이어>  §f- §7닉네임 초기화")
        s.sendMessage("§f/닉네임 관리 §7<플레이어> <닉네임>  §f- §7직접 닉네임 설정 §7(§e&§7색상코드 가능, 최대 §e${max}§7자)")
        s.sendMessage("§f/닉네임 저장  §f- §7데이터 파일 저장")
        s.sendMessage("§f/닉네임 리로드  §f- §7config.yml 및 닉네임 데이터 리로드")
    }
}
