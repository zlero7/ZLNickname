package io.zlero.nIckName

import org.jetbrains.exposed.sql.Table

/**
 * 닉네임 데이터 테이블 (SQLite / MySQL / H2 공용).
 * uuid: 플레이어 UUID 문자열 (PK)
 * raw:  & 코드 원본 (GUI 미리채우기·저장용)
 * full: § 코드 변환본 (디스플레이·메시지용)
 */
object NicknameTable : Table("crnickname") {
    val uuid = varchar("uuid", 36)
    val raw  = varchar("raw",  256)
    val full = varchar("full", 512)

    override val primaryKey = PrimaryKey(uuid)
}
