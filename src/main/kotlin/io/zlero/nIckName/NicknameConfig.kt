package io.zlero.nIckName

import io.zlero.cRFramework.yaml.CRYamlConfiguration
import io.zlero.cRFramework.yaml.annotation.Configuration
import org.bukkit.plugin.java.JavaPlugin

@Configuration("config.yml")
class NicknameConfig(plugin: JavaPlugin) : CRYamlConfiguration(plugin, "config.yml") {

    /** 색상 코드 제거 후 최대 닉네임 글자 수 */
    val maxNicknameLength get() = int("nickname.max-length", 16)

    /** 기립 시 ArmorStand Y 오프셋 */
    val standOffsetStanding get() = double("armor-stand.offset-standing", 1.9)

    /** 웅크림 시 ArmorStand Y 오프셋 */
    val standOffsetSneaking get() = double("armor-stand.offset-sneaking", 1.6)
}
