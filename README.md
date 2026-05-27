# CRNickname

Paper 서버를 위한 닉네임 플러그인입니다.  
색상 코드 지원, 머리 위 ArmorStand 닉네임 표시, AnvilGUI 기반 설정 창을 제공합니다.

## 지원 버전

| 항목 | 버전 |
|------|------|
| Minecraft | 1.20.4 |
| Paper API | 1.20.4-R0.1-SNAPSHOT |
| Java | 17 |
| Kotlin | 2.3.0 |

## 기능

- **머리 위 닉네임 표시** — ArmorStand를 이용해 플레이어 머리 위에 닉네임 렌더링, 이동·웅크림 즉시 반영
- **색상 코드 지원** — `&` 색상 코드 사용 가능 (`&a녹색`, `&c빨강` 등)
- **AnvilGUI 설정 창** — 관리자가 대상 플레이어에게 모루 GUI를 열어 닉네임 직접 입력
- **명령어 닉네임 변환** — `/msg`, `/tp`, `/pay` 등 대상이 있는 명령어에서 닉네임으로 실제 플레이어 자동 검색
- **닉네임 유효성 검사** — 빈 값, 색상 제거 후 빈 값, 최대 글자 수(16자) 초과 차단
- **데이터 영속성** — `nicknames.yml`에 자동 저장·로드, 서버 재시작 후에도 유지
- **이름표 숨김** — 닉네임 설정 시 기본 이름표를 숨기고 ArmorStand 닉네임만 표시

## 의존성

CRNickname은 아래 플러그인이 서버에 설치되어 있어야 합니다.

```yaml
# plugin.yml
depend: [CRFramework]
```

| 의존성 | 종류 | 용도 |
|--------|------|------|
| [CRFramework](https://github.com/zlero7/CRFramework) | 서버 플러그인 | DI, 명령어·이벤트 자동 등록 |
| [AnvilGUI](https://github.com/WesJD/AnvilGUI) | 번들 (shadowJar) | 모루 GUI 입력 창 |

## 설치

1. [CRFramework](https://github.com/zlero7/CRFramework/releases) JAR를 `plugins/` 폴더에 복사
2. `CRNickname-*.jar` 를 `plugins/` 폴더에 복사
3. 서버 시작

---

## 명령어

모든 명령어는 `nickname.admin` 권한이 필요합니다.

| 명령어 | 설명 |
|--------|------|
| `/닉네임 설정 <플레이어>` | 해당 플레이어에게 AnvilGUI 닉네임 변경 창을 엶 |
| `/닉네임 초기화 <플레이어>` | 닉네임을 제거하고 원래 이름으로 복원 |
| `/닉네임 관리 <플레이어> <닉네임>` | GUI 없이 직접 닉네임 설정 (`&` 색상 코드 사용 가능) |
| `/닉네임 저장` | 현재 닉네임 데이터를 파일에 즉시 저장 |

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

## 데이터 파일

닉네임은 `plugins/CRNickname/nicknames.yml`에 저장됩니다.

```yaml
# UUID: 닉네임 데이터
550e8400-e29b-41d4-a716-446655440000:
  raw: '&a[VIP] &f스티브'    # & 코드 원본 (GUI 미리채우기용)
  full: '§a[VIP] §f스티브'   # § 코드 변환본 (표시용)
```

서버 종료 시 자동 저장, 시작 시 자동 로드됩니다.  
`/닉네임 저장` 으로 즉시 저장할 수 있습니다.

---

## 빌드

```bash
./gradlew shadowJar
```

결과물: `build/libs/CRNickname-1.0-SNAPSHOT-all.jar`

> `jar` 태스크가 아닌 **반드시 `shadowJar`** 를 사용해야 합니다.  
> AnvilGUI가 함께 번들링됩니다.

---

## 라이선스

MIT License © 2026 zlero — 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.
