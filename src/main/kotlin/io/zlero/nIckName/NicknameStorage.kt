package io.zlero.nIckName

import io.zlero.cRFramework.yaml.CRYamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * 닉네임 데이터를 nicknames.yml 에 저장·로드하는 스토리지.
 * CRYamlConfiguration 기반으로 파일 생성·관리를 프레임워크에 위임합니다.
 */
class NicknameStorage(plugin: JavaPlugin) : CRYamlConfiguration(plugin, "nicknames.yml") {

    data class Entry(val raw: String, val full: String)

    /**
     * 파일에서 모든 닉네임 데이터를 읽어 반환합니다.
     * 잘못된 UUID 키나 값 누락 항목은 건너뜁니다.
     */
    fun loadAll(): Map<UUID, Entry> = buildMap {
        config.getKeys(false).forEach { key ->
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            val raw  = config.getString("$key.raw")  ?: return@forEach
            val full = config.getString("$key.full") ?: return@forEach
            put(uuid, Entry(raw, full))
        }
    }

    /**
     * 모든 닉네임 데이터를 파일에 일괄 저장합니다.
     * 기존 내용을 전부 지우고 새로 씁니다.
     */
    fun saveAll(data: Map<UUID, Entry>) {
        config.getKeys(false).forEach { config.set(it, null) }
        data.forEach { (uuid, entry) ->
            config.set("$uuid.raw",  entry.raw)
            config.set("$uuid.full", entry.full)
        }
        save()
    }
}
