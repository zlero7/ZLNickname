package io.zlero.nIckName

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class SaveNicknameCommand(private val plugin: NicknamePlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("nickname.admin")) {
            sender.sendMessage("§c이 명령어를 사용할 권한이 없습니다.")
            return true
        }

        plugin.nicknameManager.saveNicknames()
        sender.sendMessage("§a닉네임 데이터를 저장했습니다.")
        return true
    }
}