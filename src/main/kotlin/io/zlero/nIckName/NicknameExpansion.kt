package io.zlero.nIckName

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI 확장.
 * PAPI 미설치 시 이 클래스는 절대 로드되지 않으므로 NoClassDefFoundError 없음.
 *
 * %crnickname_name%    — 색상 제거 순수 텍스트 닉네임 (또는 실제 이름)
 * %crnickname_raw%     — & 코드 포함 원본 닉네임 (또는 실제 이름)
 * %crnickname_display% — § 코드 변환본 닉네임 (또는 실제 이름)
 */
class NicknameExpansion(
    private val plugin: NicknamePlugin,
    private val manager: NicknameManager
) : PlaceholderExpansion() {

    override fun getIdentifier() = "crnickname"
    override fun getAuthor()     = "zlero"
    @Suppress("DEPRECATION")
    override fun getVersion()    = plugin.description.version
    override fun persist()       = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        player ?: return null
        val data = manager.getNicknameData(player.uniqueId)
        val fallback = player.name ?: player.uniqueId.toString()
        return when (params.lowercase()) {
            "name"    -> data?.let {
                PlainTextComponentSerializer.plainText().serialize(it.toTextComponent())
            } ?: fallback
            "raw"     -> data?.raw     ?: fallback
            "display" -> data?.full    ?: fallback
            else      -> null
        }
    }
}
