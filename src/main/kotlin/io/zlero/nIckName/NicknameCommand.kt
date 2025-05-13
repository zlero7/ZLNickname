package io.zlero.nIckName

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.ChatColor

class NicknameCommand(private val plugin: NicknamePlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("nickname.admin")) {
            sender.sendMessage("§c권한이 없습니다.")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§c사용법: /닉네임 <유저> <닉네임>")
            return true
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            sender.sendMessage("§c해당 유저를 찾을 수 없습니다.")
            return true
        }

        val raw = args.drop(1).joinToString(" ")
        plugin.nicknameManager.setNickname(target, raw)
        sender.sendMessage("§a${target.name}의 닉네임을 '${ChatColor.translateAlternateColorCodes('&', raw)}§r§a' 으로 설정했습니다.")
        target.sendMessage("§e관리자에 의해 닉네임이 변경되었습니다.")
        return true
    }
}
