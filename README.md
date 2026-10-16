# Helldivers Ping

헬다이버즈 2 스타일의 팀 핑 시스템 모드입니다. Architectury로 만든 멀티로더 모드예요.

| Minecraft | 로더 | 필요한 모드 |
|---|---|---|
| 1.20.1 | Fabric / Forge | Architectury API 9.2.14+ (Fabric은 Fabric API도 필요) |
| 1.21 / 1.21.1 | Fabric / NeoForge | Architectury API 13.0.8+ (Fabric은 Fabric API도 필요) |

서버와 클라이언트 **둘 다** 설치해야 합니다.

## 사용법

- **핑 키 (기본 `Z`, 조작 설정에서 변경 가능)**: 조준한 대상에 핑을 찍습니다.
  - **블록**: 꺾쇠 사각형 안에 블록 아이콘, 아래에 거리, 오른쪽에 블록 이름이 표시됩니다.
  - **엔티티**: 다이아몬드 마커가 엔티티에 붙어 함께 움직이고, 엔티티가 죽거나 사라지면 핑도 사라집니다.
    조준이 정확하지 않아도 크로스헤어 근처(기본 3.5°)의 엔티티에 자석처럼 달라붙습니다.
  - **떨어진 아이템**: 아이템 아이콘과 이름(개수 포함)이 표시되고, 누가 주우면 사라집니다.
- **긍정 반응**: 팀원의 핑을 조준하고 핑 키를 누르면(또는 같은 대상에 핑을 찍으면) 핑이 **노란색**이 되고
  반응한 플레이어 이름이 표시됩니다. 긍정 반응한 핑은 더 오래 유지됩니다. 한 번 더 누르면 취소됩니다.
- **내 핑 취소**: 내가 찍은 핑을 조준하고 핑 키를 누르면 지워집니다.
- 핑을 조준하면 마커 옆에 `[Z] 취소` / `[Z] 확인` 힌트가 뜹니다.
- 핑은 개수 제한 없이 여러 개 찍을 수 있습니다 (`maxPingsPerPlayer`로 제한 가능).
- 화면 밖에 있는 핑은 화면 가장자리에 작게 표시됩니다.
- 블록 핑은 그 블록이 부서지면 사라집니다 (설정에서 끌 수 있음).

## 팀 규칙

바닐라 스코어보드 팀(`/team`)을 사용합니다.

- 같은 팀에 속한 플레이어끼리만 핑을 봅니다.
- 팀이 없는 플레이어끼리는 서로 핑을 공유합니다 (`shareWithoutTeam=false`로 끄면 자기 핑만 보임).
- 다른 차원에 있는 플레이어에게는 보이지 않습니다.

## 설정

`config/helldivers_ping.properties` (처음 실행 시 생성). 시간 단위는 틱(20틱 = 1초)입니다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `pingDurationTicks` | 160 | 일반 핑 유지 시간 (8초) |
| `acknowledgedDurationTicks` | 600 | 긍정 반응 이후 유지 시간 (30초) |
| `maxPingDistance` | 256 | 최대 핑 거리 (블록) |
| `pingCooldownTicks` | 4 | 핑 연타 쿨다운 |
| `shareWithoutTeam` | true | 팀 없는 플레이어끼리 공유할지 |
| `maxPingsPerPlayer` | 0 | 플레이어당 최대 핑 개수, 넘으면 가장 오래된 것부터 삭제. 0이면 무제한 |
| `removeWhenBlockBroken` | true | 블록이 부서지면 핑 제거 |
| `entityMagnetDegrees` | 3.5 | (클라) 엔티티 자석 조준 각도 |
| `markerHoverDegrees` | 2.5 | (클라) 기존 핑을 "조준했다"고 볼 각도 |
| `uppercaseNames` | false | (클라) 이름을 대문자로 표시 (헬다이버즈 스타일) |
| `soundVolume` | 0.6 | (클라) 핑 소리 볼륨, 0이면 끔 |
| `markerScale` | 1.0 | (클라) 마커 크기 |
| `backgroundOpacity` | 0.45 | (클라) 마커·글자 뒤 반투명 검정 배경 불투명도, 0이면 끔 |

서버 값은 서버(싱글이면 내장 서버)의 설정 파일이 적용됩니다.

## 빌드

```bash
./build-all.sh
```

결과 jar 4개가 `dist/`에 복사됩니다. JDK 17/21은 Gradle toolchain이 자동으로 받습니다.
Gradle은 JDK 17~25에서 실행해야 합니다 (예: `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2`).

> 두 버전을 **동시에** 빌드하면 Loom의 매핑 캐시가 섞여서 Forge jar가 망가질 수 있습니다.
> 그래서 `gradle.properties`에서 데몬을 끄고 `build-all.sh`는 순서대로 빌드합니다.

## 구조

```
1.20.1/  common + fabric + forge     (Java 17, Architectury 9)
1.21.1/  common + fabric + neoforge  (Java 21, Architectury 13)
```

두 버전의 `common` 코드는 아래 3개 파일을 제외하고 동일합니다. 로직을 고칠 때는 양쪽에 같이 반영하세요.

- `network/PingNetwork.java`: 1.20.1은 `FriendlyByteBuf`, 1.21.1은 `CustomPacketPayload` + `StreamCodec`
- `mixin/LevelRendererMixin.java`: `LevelRenderer.renderLevel` 시그니처 차이
- `helldivers_ping.mixins.json`: Java 버전

주요 클래스:

- `server/ServerPingManager`: 핑 상태 전부 관리 (생성/긍정 반응/취소/만료/팀 가시성/엔티티 추적)
- `client/PingTargeting`: 레이캐스트 + 엔티티 자석 조준
- `client/PingHudRenderer`: HUD 마커 그리기
- `client/ProjectionState` + `LevelRendererMixin`: 월드 좌표를 화면 좌표로 투영하는 데 쓰는 카메라 행렬
