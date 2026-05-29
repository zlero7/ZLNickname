package io.zlero.nIckName

import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.core.component.annotation.Setup
import io.zlero.cRFramework.core.component.annotation.Teardown
import org.bukkit.Bukkit

@Component
class NicknamePlaceholderExpansion(
    private val plugin: NicknamePlugin,
    private val manager: NicknameManager
) {

    private var expansion: NicknameExpansion? = null

    @Setup
    fun register() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return
        expansion = NicknameExpansion(plugin, manager)
        expansion!!.register()
        plugin.logger.info("PlaceholderAPI 연동 완료")
    }

    @Teardown
    fun unregister() {
        expansion?.unregister()
        expansion = null
    }
}
