# CRNickname

Paper 서버를 위한 닉네임 플러그인입니다.  
색상 코드 지원, 머리 위 ArmorStand 닉네임 표시, AnvilGUI 기반 설정 창을 제공합니다.

## 지원 버전

| 항목 | 버전 |
|------|------|
| Minecraft | 1.20.4 ~ 1.21.4 |
| Paper API | 1.20.4-R0.1-SNAPSHOT |
| Java | 21 |
| Kotlin | 2.3.10 |
| CRFramework | v1.0.6 |

## 기능

- **머리 위 닉네임 표시** — ArmorStand를 이용해 플레이어 머리 위에 닉네임 렌더링, 이동·웅크림 즉시 반영
- **색상 코드 지원** — `&` 색상 코드 사용 가능 (`&a녹색`, `&c빨강` 등)
- **AnvilGUI 설정 창** — 관리자가 대상 플레이어에게 모루 GUI를 열어 닉네임 직접 입력
- **명령어 닉네임 변환** — `/msg`, `/tp`, `/pay` 등 대상이 있는 명령어에서 닉네임으로 실제 플레이어 자동 검색
- **닉네임 유효성 검사** — 빈 값, 색상 제거 후 빈 값, 최대 글자 수(16자) 초과 차단
- **다중 저장 방식** — YAML / SQLite / MySQL / H2 중 선택 가능
- **이름표 숨김** — 닉네임 설정 시 기본 이름표를 숨기고 ArmorStand 닉네임만 표시

## 의존성

CRNickname은 아래 플러그인이 서버에 설치되어 있어야 합니다.

```yaml
# plugin.yml
depend: [CRFramework]
```

| 의존성 | 종류 | 용도 |
|--------|------|------|
| [CRFramework](https://github.com/zlero7/CRFramework) | 서버 플러그인 | DI, 명령어·이벤트 자동 등록, Exposed/HikariCP 런타임 |
| [AnvilGUI](https://github.com/WesJD/AnvilGUI) | 번들 (shadowJar) | 모루 GUI 입력 창 |
| SQLite JDBC | 번들 (shadowJar) | SQLite 저장 방식 |
| H2 | 번들 (shadowJar) | H2 저장 방식 |
| MySQL Connector/J | 번들 (shadowJar) | MySQL 저장 방식 |

## 설치

1. [CRFramework](https://github.com/zlero7/CRFramework/releases) JAR를 `plugins/` 폴더에 복사
2. `CRNickname-*.jar` 를 `plugins/` 폴더에 복사
3. 서버 시작
4. `plugins/CRNickname/config.yml` 에서 저장 방식 등 설정 후 재시작

---

## 명령어

모든 명령어는 `nickname.admin` 권한이 필요합니다.

| 명령어 | 설명 |
|--------|------|
| `/닉네임 설정 <플레이어>` | 해당 플레이어에게 AnvilGUI 닉네임 변경 창을 엶 |
| `/닉네임 초기화 <플레이어>` | 닉네임을 제거하고 원래 이름으로 복원 |
| `/닉네임 관리 <플레이어> <닉네임>` | GUI 없이 직접 닉네임 설정 (`&` 색상 코드 사용 가능) |
| `/닉네임 저장` | 현재 닉네임 데이터를 즉시 저장 |

### 사용 예시

```
/닉네임 설정 Steve
/닉네임 관리 Steve &a[VIP] &f스티브
/닉네임 초기화 Steve
/닉네임 저장
```

---

## 권한

| 권한 | 설명 | 기본값 |
|------|------|--------|
| `nickname.admin` | 모든 닉네임 명령어 사용 | OP만 |

---

## 명령어 닉네임 변환

아래 명령어에서 대상 인자에 닉네임을 입력하면 자동으로 실제 플레이어 이름으로 변환됩니다.

| 분류 | 명령어 |
|------|--------|
| 메시지 | `msg`, `tell`, `whisper`, `dm`, `pm` 등 |
| 텔레포트 | `tp`, `tpa`, `tpask`, `tphere` 등 |
| 이코노미 | `pay`, `givemoney`, `takemoney` |
| 관리 | `ban`, `kick`, `mute`, `warn`, `jail` 등 |
| 기타 | `invsee`, `trade`, `heal`, `ping`, `seen` 등 |

```
# Steve의 닉네임이 "스티브"인 경우
/msg 스티브 안녕하세요   →   /msg Steve 안녕하세요  (자동 변환)
/tpa 스티브              →   /tpa Steve
```

---

## 저장 방식

`plugins/CRNickname/config.yml` 의 `storage.type` 으로 선택합니다.

| 방식 | 설명 | 추천 환경 |
|------|------|-----------|
| `yaml` | `nicknames.yml` 파일에 저장 (기본값) | 소규모 서버 |
| `sqlite` | 플러그인 폴더 내 `.db` 파일 | 단일 서버 |
| `mysql` | 외부 MySQL/MariaDB 서버 | 다중 서버·대규모 |
| `h2` | 플러그인 폴더 내 H2 파일 DB | 단일 서버 (SQL 필요 시) |

### SQLite / H2 설정 예시

```yaml
storage:
  type: sqlite      # 또는 h2
  file: data/nicknames   # plugins/CRNickname/data/nicknames.db (또는 .h2)
```

### MySQL 설정 예시

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: "비밀번호"
    pool-size: 5
```

### YAML 데이터 파일 형식

저장 방식이 `yaml` 일 때 `plugins/CRNickname/nicknames.yml` 형식입니다.

```yaml
# UUID: 닉네임 데이터
550e8400-e29b-41d4-a716-446655440000:
  raw: '&a[VIP] &f스티브'    # & 코드 원본 (GUI 미리채우기용)
  full: '§a[VIP] §f스티브'   # § 코드 변환본 (표시용)
```

---

## 빌드

```bash
./gradlew shadowJar
```

결과물: `build/libs/CRNickname-1.0-SNAPSHOT-all.jar`

> `jar` 태스크가 아닌 **반드시 `shadowJar`** 를 사용해야 합니다.  
> AnvilGUI 및 JDBC 드라이버가 함께 번들링됩니다.

---

## 라이선스

MIT License © 2026 zlero — 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.
