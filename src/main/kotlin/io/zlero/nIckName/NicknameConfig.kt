package io.zlero.nIckName

import io.zlero.cRFramework.yaml.CRYamlConfiguration
import io.zlero.cRFramework.yaml.annotation.Configuration
import org.bukkit.plugin.java.JavaPlugin

@Configuration("config.yml")
class NicknameConfig(plugin: JavaPlugin) : CRYamlConfiguration(plugin, "config.yml") {

    // ─────────────── 닉네임 ───────────────

    /** 색상 코드 제거 후 최대 닉네임 글자 수 */
    val maxNicknameLength get() = int("nickname.max-length", 16)

    // ─────────────── 스토리지 ───────────────

    /** 저장 방식: yaml | sqlite | mysql | h2 (소문자 정규화) */
    val storageType  get() = (config.getString("storage.type", "yaml") ?: "yaml").lowercase()

    /** SQLite / H2 파일 경로 (플러그인 데이터 폴더 기준, 확장자 제외) */
    val dbFile       get() = config.getString("storage.file", "data/nicknames") ?: "data/nicknames"

    /** MySQL 호스트 */
    val mysqlHost     get() = config.getString("storage.mysql.host", "localhost") ?: "localhost"

    /** MySQL 포트 */
    val mysqlPort     get() = int("storage.mysql.port", 3306)

    /** MySQL 데이터베이스 이름 */
    val mysqlDatabase get() = config.getString("storage.mysql.database", "minecraft") ?: "minecraft"

    /** MySQL 사용자 이름 */
    val mysqlUsername get() = config.getString("storage.mysql.username", "root") ?: "root"

    /** MySQL 비밀번호 */
    val mysqlPassword get() = config.getString("storage.mysql.password", "") ?: ""

    /** MySQL/H2 HikariCP 최대 풀 크기 */
    val mysqlPoolSize get() = int("storage.mysql.pool-size", 5)
}
