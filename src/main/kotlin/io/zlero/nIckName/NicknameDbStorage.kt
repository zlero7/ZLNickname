package io.zlero.nIckName

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.core.component.annotation.Setup
import io.zlero.cRFramework.core.component.annotation.Teardown
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * DB 기반 닉네임 스토리지 (SQLite / MySQL / H2).
 * Exposed 대신 순수 JDBC를 사용하여 CRFramework Exposed 트랜잭션과의
 * 전역 기본 DB 충돌을 방지합니다.
 * storage.type == "yaml" 일 때는 모든 메서드가 no-op 입니다.
 */
@Component
class NicknameDbStorage(
    private val plugin: NicknamePlugin,
    private val config: NicknameConfig
) {

    private var hikari: HikariDataSource? = null
    private var singleConn: Connection? = null   // SQLite 전용 단일 커넥션

    val isActive: Boolean get() = config.storageType != "yaml"

    // ─────────────── 라이프사이클 ───────────────

    @Setup
    fun connect() {
        if (!isActive) return

        runCatching {
            when (config.storageType) {
                "sqlite" -> connectSqlite()
                "mysql"  -> connectMysql()
                "h2"     -> connectH2()
                else     -> {
                    plugin.logger.warning(
                        "알 수 없는 storage.type '${config.storageType}' — yaml 로 대체됩니다."
                    )
                    return
                }
            }
            createTable()
            plugin.logger.info("DB 스토리지 연결 완료 (${config.storageType})")
        }.onFailure { e ->
            plugin.logger.severe("DB 스토리지 연결 실패: ${e.message}")
            plugin.logger.severe("yaml 저장 방식으로 대체합니다.")
            hikari?.close()
            hikari = null
            singleConn?.close()
            singleConn = null
        }
    }

    @Teardown
    fun disconnect() {
        singleConn?.close()
        singleConn = null
        hikari?.close()
        hikari = null
    }

    // ─────────────── 공개 API ───────────────

    fun loadAll(): Map<UUID, NicknameStorage.Entry> {
        if (!isActive) return emptyMap()
        return withConnection { conn ->
            conn.prepareStatement("SELECT uuid, raw, full FROM crnickname").use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            val uuid = runCatching { UUID.fromString(rs.getString("uuid")) }
                                .getOrNull() ?: continue
                            put(uuid, NicknameStorage.Entry(rs.getString("raw"), rs.getString("full")))
                        }
                    }
                }
            }
        }
    }

    fun saveAll(data: Map<UUID, NicknameStorage.Entry>) {
        if (!isActive) return
        withConnection { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.createStatement().use { it.execute("DELETE FROM crnickname") }
                if (data.isNotEmpty()) {
                    conn.prepareStatement(
                        "INSERT INTO crnickname (uuid, raw, full) VALUES (?, ?, ?)"
                    ).use { stmt ->
                        data.forEach { (uuid, entry) ->
                            stmt.setString(1, uuid.toString())
                            stmt.setString(2, entry.raw)
                            stmt.setString(3, entry.full)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (e: Exception) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
        }
    }

    /** 단일 항목을 즉시 저장(upsert)합니다. DELETE+INSERT 트랜잭션으로 모든 DB 지원. */
    fun upsert(uuid: UUID, entry: NicknameStorage.Entry) {
        if (!isActive) return
        withConnection { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM crnickname WHERE uuid = ?").use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    "INSERT INTO crnickname (uuid, raw, full) VALUES (?, ?, ?)"
                ).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, entry.raw)
                    stmt.setString(3, entry.full)
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
        }
    }

    /** 단일 항목을 즉시 삭제합니다. */
    fun remove(uuid: UUID) {
        if (!isActive) return
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM crnickname WHERE uuid = ?").use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.executeUpdate()
            }
        }
    }

    // ─────────────── 내부 유틸 ───────────────

    /**
     * SQLite 는 단일 커넥션을 재사용하고, MySQL / H2 는 풀에서 빌려 블록 후 반환합니다.
     */
    private fun <T> withConnection(block: (Connection) -> T): T {
        val sc = singleConn
        return if (sc != null) {
            block(sc)
        } else {
            hikari!!.connection.use { block(it) }
        }
    }

    private fun createTable() {
        withConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS crnickname (
                        uuid VARCHAR(36) PRIMARY KEY,
                        raw  VARCHAR(256) NOT NULL,
                        full VARCHAR(512) NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private fun connectSqlite() {
        val dbFile = resolveFile("${config.dbFile}.db")
        // CRFramework 가 sqlite-jdbc 를 번들 — 별도 드라이버 로드 불필요
        singleConn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    private fun connectMysql() {
        val hc = HikariConfig().apply {
            jdbcUrl = buildString {
                append("jdbc:mysql://")
                append(config.mysqlHost).append(':').append(config.mysqlPort)
                append('/').append(config.mysqlDatabase)
                append("?useSSL=false&allowPublicKeyRetrieval=true")
                append("&serverTimezone=UTC&characterEncoding=utf8mb4")
            }
            driverClassName   = "com.mysql.cj.jdbc.Driver"
            username          = config.mysqlUsername
            password          = config.mysqlPassword
            maximumPoolSize   = config.mysqlPoolSize
            minimumIdle       = 1
            connectionTimeout = 10_000
        }
        hikari = HikariDataSource(hc)
    }

    private fun connectH2() {
        val dbFile = resolveFile(config.dbFile)
        val hc = HikariConfig().apply {
            jdbcUrl         = "jdbc:h2:file:${dbFile.absolutePath};DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=FALSE"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 2
            minimumIdle     = 1
        }
        hikari = HikariDataSource(hc)
    }

    private fun resolveFile(relativePath: String): File =
        File(plugin.dataFolder, relativePath).also { it.parentFile?.mkdirs() }
}
