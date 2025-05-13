package io.zlero.nIckName

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class AdminNicknameResetCommand(private val plugin: NicknamePlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("nickname.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        if (args.size != 1) {
            sender.sendMessage("§c사용법: /닉네임초기화 <유저>")
            return true
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            sender.sendMessage("§c해당 유저를 찾을 수 없습니다.")
            return true
        }

        plugin.nicknameManager.resetNickname(target)
        sender.sendMessage("§a${target.name} 닉네임을 초기화했습니다.")
        target.sendMessage("§e관리자에 의해 닉네임이 초기화되었습니다.")
        return true
    }
}
