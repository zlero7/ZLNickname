package io.zlero.nIckName

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class NicknamePlugin : JavaPlugin() {
    lateinit var nicknameManager: NicknameManager

    override fun onEnable() {
        nicknameManager = NicknameManager(this)

        getCommand("닉네임")?.setExecutor(NicknameCommand(this))
        getCommand("닉네임초기화")?.setExecutor(AdminNicknameResetCommand(this))
        getCommand("닉네임저장")?.setExecutor(SaveNicknameCommand(this))

        server.pluginManager.registerEvents(nicknameManager, this)
        nicknameManager.loadNicknames()
    }

    override fun onDisable() {
        nicknameManager.saveNicknames()
    }
}
