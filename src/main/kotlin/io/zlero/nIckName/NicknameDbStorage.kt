package io.zlero.nIckName

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zlero.cRFramework.core.component.annotation.Component
import io.zlero.cRFramework.core.component.annotation.Setup
import io.zlero.cRFramework.core.component.annotation.Teardown
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID

/**
 * DB 기반 닉네임 스토리지 (SQLite / MySQL / H2).
 * storage.type == "yaml" 일 때는 @Setup/@Teardown 모두 no-op 이며,
 * loadAll() / saveAll() 은 항상 빈 결과를 반환/무시합니다.
 * NicknameManager 가 config.storageType 을 보고 어느 스토리지를 사용할지 결정합니다.
 */
@Component
class NicknameDbStorage(
    private val plugin: NicknamePlugin,
    private val config: NicknameConfig
) {

    private var dataSource: HikariDataSource? = null
    private var database: Database? = null

    /** YAML 이외의 DB 스토리지가 활성화되어 있으면 true */
    val isActive: Boolean
        get() = config.storageType != "yaml"

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
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(NicknameTable)
            }
            plugin.logger.info("DB 스토리지 연결 완료 (${config.storageType})")
        }.onFailure {
            plugin.logger.severe("DB 스토리지 연결 실패: ${it.message}")
            plugin.logger.severe("yaml 저장 방식으로 대체합니다.")
            dataSource?.close()
            dataSource = null
            database = null
        }
    }

    @Teardown
    fun disconnect() {
        dataSource?.close()
        dataSource = null
        database = null
    }

    // ─────────────── 공개 API ───────────────

    fun loadAll(): Map<UUID, NicknameStorage.Entry> {
        val db = database ?: return emptyMap()
        return transaction(db) {
            NicknameTable.selectAll().associate { row ->
                UUID.fromString(row[NicknameTable.uuid]) to
                    NicknameStorage.Entry(row[NicknameTable.raw], row[NicknameTable.full])
            }
        }
    }

    fun saveAll(data: Map<UUID, NicknameStorage.Entry>) {
        val db = database ?: return
        transaction(db) {
            NicknameTable.deleteAll()
            if (data.isNotEmpty()) {
                NicknameTable.batchInsert(data.entries) { (uuid, entry) ->
                    this[NicknameTable.uuid] = uuid.toString()
                    this[NicknameTable.raw]  = entry.raw
                    this[NicknameTable.full] = entry.full
                }
            }
        }
    }

    // ─────────────── 내부 연결 메서드 ───────────────

    private fun connectSqlite() {
        val dbFile = resolveFile("${config.dbFile}.db")
        // SQLite 는 단일 연결로 충분 — HikariCP 없이 직접 연결
        database = Database.connect(
            url    = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
    }

    private fun connectMysql() {
        val hc = HikariConfig().apply {
            jdbcUrl = buildString {
                append("jdbc:mysql://")
                append(config.mysqlHost)
                append(':')
                append(config.mysqlPort)
                append('/')
                append(config.mysqlDatabase)
                append("?useSSL=false&allowPublicKeyRetrieval=true")
                append("&serverTimezone=UTC&characterEncoding=utf8mb4")
            }
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username        = config.mysqlUsername
            password        = config.mysqlPassword
            maximumPoolSize = config.mysqlPoolSize
            minimumIdle     = 1
            connectionTimeout = 10_000
        }
        dataSource = HikariDataSource(hc)
        database   = Database.connect(dataSource!!)
    }

    private fun connectH2() {
        val dbFile = resolveFile(config.dbFile)
        val hc = HikariConfig().apply {
            jdbcUrl         = "jdbc:h2:file:${dbFile.absolutePath};DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=FALSE"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 2
            minimumIdle     = 1
        }
        dataSource = HikariDataSource(hc)
        database   = Database.connect(dataSource!!)
    }

    /** 플러그인 데이터 폴더 기준 파일 경로를 반환하고 부모 디렉터리를 보장합니다. */
    private fun resolveFile(relativePath: String): File =
        File(plugin.dataFolder, relativePath).also { it.parentFile?.mkdirs() }
}
