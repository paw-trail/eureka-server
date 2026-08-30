# eureka-server

함께하개(paw-trail)의 **서비스 레지스트리**입니다. 각 서비스가 자기 주소를 등록하고, 다른 서비스와 게이트웨이가 그 주소를 받아 갑니다.

서비스가 늘어나거나 인스턴스 주소가 바뀌어도 호출하는 쪽의 설정을 고치지 않아도 되는 것이 이 서비스를 두는 이유입니다. 게이트웨이는 `place-service` 라는 **이름**으로 요청하고, 실제 주소는 이곳에서 받습니다.

---

## 1. 이 서비스가 하는 일

### 1-1. 등록과 하트비트

```
place-service 기동
   │
   ├─→ "나는 place-service, 주소는 10.0.1.20:8084" 라고 등록
   │
   ├─→ 이후 30초마다 "아직 살아 있음" 을 알림 (하트비트)
   │
   └─→ 90초 동안 소식이 없으면 목록에서 지워짐 (만료)

게이트웨이
   │
   └─→ "place-service 주소 목록을 줘" → 받아서 그중 하나로 요청
```

만료가 있어야 서버가 갑자기 죽었을 때 게이트웨이가 죽은 주소로 요청을 보내지 않습니다. 인스턴스가 여러 개인 서비스(verdict 3개, search 2개)에서는 이 목록이 곧 로드밸런싱 대상이 됩니다.

### 1-2. 이 서비스는 목록에 나타나지 않습니다

대시보드를 열면 `config-server`, `gateway-server`, 도메인 서비스들은 보이지만 **`eureka-server` 자신은 보이지 않습니다.** 자기가 레지스트리이므로 자기에게 등록할 이유가 없고, 다른 서비스를 호출하지도 않아 레지스트리를 받아 올 필요도 없기 때문입니다.

```yaml
eureka:
  client:
    register-with-eureka: false   # 자기에게 등록하지 않음
    fetch-registry: false         # 목록을 받아 오지 않음
```

이 서비스가 살아 있다는 것은 **대시보드 페이지가 열린다는 것 자체**로 확인되며, 화면 상단의 System Status 와 Instance Info 에 자기 정보가 표시됩니다.

### 1-3. 자기보호 모드를 꺼 두었습니다

유레카에는 자기보호(self-preservation)라는 기능이 있습니다. 들어오는 하트비트가 기대치의 85% 아래로 떨어지면 **"인스턴스들이 죽은 것이 아니라 나와 저들 사이의 네트워크가 끊긴 것"** 으로 판단하고 **만료를 통째로 멈춥니다.**

인스턴스가 수백 개인 환경을 겨냥한 안전장치입니다. 네트워크가 잠깐 흔들렸다고 멀쩡한 인스턴스를 대량으로 지워 버리면 게이트웨이가 붙을 곳을 잃기 때문에, 낡은 목록을 들고 있는 편이 낫다는 판단입니다.

**이 프로젝트에서는 반대로 작동합니다.** 로컬에서 서비스를 3~5개만 띄우므로 하나만 꺼도 기대치의 20~30%가 빠져 임계치를 즉시 넘습니다. 그러면 껐던 서비스가 목록에 `UP` 인 채로 남고, 게이트웨이가 그 주소로 요청을 보내다 실패합니다.

무중단 배포와도 충돌합니다. core 노드는 인스턴스를 하나씩 `OUT_OF_SERVICE` 로 내리고 교체하는 롤링 방식인데, 자기보호가 켜져 있으면 내린 인스턴스가 목록에서 지워지지 않습니다.

그래서 껐습니다. 감수하는 것은 **진짜로 네트워크가 끊겼을 때 멀쩡한 인스턴스까지 지워진다**는 점이며, EC2 6대가 한 VPC 안에 있고 인스턴스가 20개 남짓인 규모에서는 그 상황이 성립하지 않는다고 보았습니다.

---

## 2. 로컬 실행

### 2-1. 준비

설정을 `config-server` 에서 받으므로 **먼저 그것을 띄워 두는 편이 좋습니다.**

```powershell
git clone https://github.com/paw-trail/eureka-server.git
cd eureka-server
.\gradlew bootRun
```

프로파일을 지정하지 않으면 `local` 로 동작하며 Loki 전송이 꺼집니다.

`bootRun` 은 앱이 떠 있는 동안 끝나지 않는 작업이므로 **Gradle 진행률이 80% 근처에서 멈춘 것처럼 보이는 것이 정상입니다.** 콘솔에 `Started EurekaServerApplication` 이 찍혔는지로 판단합니다. IntelliJ 의 실행 버튼으로 띄우면 콘솔이 평범하게 나오고 중지도 쉽습니다.

### 2-2. 환경변수

| 이름 | 기본값 | 언제 지정하는가 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (없음, `local` 로 동작) | 컨테이너에서 `dev` 또는 `prod` |
| `CONFIG_HOST` | `localhost` | 컨테이너에서 `config-server` 또는 노드 주소 |

기본값을 붙인 것은 의도입니다. 로컬에서는 언제나 `localhost` 이므로 기본값이 정답이고, 없으면 개발자마다 실행 구성에 환경변수를 넣어야 합니다. 이 서비스에는 비밀 값이 없습니다.

`LOKI_HOST` 는 이 서비스에 없습니다. Loki 주소가 자기 설정이 아니라 config 저장소의 `application-{env}.yml` 에서 내려오기 때문이며, 설정을 받지 못하는 `config-server` 와 갈리는 지점입니다.

### 2-3. 기동 순서

```
config-server  →  eureka-server  →  gateway-server  →  도메인 서비스 14개
```

`config-server` 는 유레카가 없어도 죽지 않고 등록만 조용히 재시도합니다. 반대로 이 서비스는 `config-server` 가 없으면 설정을 받지 못하므로, **순서를 지키지 않으면 포트부터 어긋납니다.** 6장을 참고합니다.

---

## 3. 제대로 도는지 확인하기

### 3-1. 대시보드

```
http://localhost:8761
```

브라우저로 엽니다. **Instances currently registered with Eureka** 표에 지금 떠 있는 서비스들이 보입니다.

| 열 | 의미 |
|---|---|
| Application | 서비스 이름. `spring.application.name` 이 대문자로 표시됩니다 |
| Availability Zones | 인스턴스 개수 |
| Status | `UP` 과 그 옆의 등록 주소 |

`config-server` 를 함께 띄웠다면 그것이 목록에 있어야 합니다. 이 서비스 자신은 나타나지 않으며 그 이유는 1-2에 있습니다.

### 3-2. 등록 이름을 확인합니다

Status 열에 표시되는 주소가 **호출 가능한 주소인지** 확인합니다. IntelliJ 에서 실행한 서비스가 `localhost` 로 등록되면, 컨테이너 안에서 도는 게이트웨이가 그 주소를 **자기 자신으로 해석해 스스로를 호출합니다.**

그래서 `local` 프로파일에서는 `host.docker.internal` 로 등록하도록 config 저장소에 지정해 두었습니다. Docker Desktop 이 호스트를 가리키도록 넣어 주는 이름이라 호스트에서도 컨테이너에서도 풀립니다.

`dev` 프로파일은 같은 도커 네트워크 안이므로 컨테이너 IP 로 등록합니다.

### 3-3. 상태 확인

```powershell
curl.exe http://localhost:8761/actuator/health
```

PowerShell 의 `curl` 은 `Invoke-WebRequest` 의 별칭이라 응답이 객체로 감싸집니다. 원문을 보려면 확장자까지 적습니다.

---

## 4. 공통 모듈을 의존하지 않습니다

플랫폼 3종(`config-server`, `eureka-server`, `gateway-server`)은 공통 모듈(`com.pawtrail.common`)을 사용하지 않습니다. 공통 모듈은 도메인 서비스가 공유하는 것들을 담고 있고, 플랫폼은 성격이 다릅니다.

들어 있는 것 중 이 서비스에 쓰일 것이 없습니다. `BaseEntity` 와 감사 컬럼은 데이터베이스가 있어야 하고, 에러 코드와 예외 처리기는 도메인 API 가 있어야 하며, 헤더 인증 필터는 게이트웨이 뒤에 있는 서비스를 위한 것이고, Outbox 와 Inbox 는 데이터베이스와 Kafka 가 필요합니다.

오히려 넣으면 위험한 것이 있습니다. 공통 모듈의 `TraceIdResponseAdvice` 는 응답을 감싸는데, 이 서비스가 내려주는 것은 **레지스트리 목록**이므로 그것까지 감싸이면 받아 가는 쪽이 읽지 못합니다. 오류가 아니라 파싱 실패로 나타나므로 원인을 찾기 어렵습니다.

공통 모듈에서 유일하게 필요했던 Loki 전송 설정은 `logback-spring.xml` 에 직접 적었습니다.

---

## 5. 설정은 config 저장소에 있습니다

이 저장소의 `application.yml` 에는 세 줄만 있습니다.

```yaml
spring:
  application:
    name: eureka-server
  config:
    import: "optional:configserver:http://${CONFIG_HOST:localhost}:8888"
  profiles:
    default: local
```

나머지는 `paw-trail/config` 에서 내려옵니다.

| 계층 | 파일 | 이 서비스가 받는 값 |
|:---:|---|---|
| 1 | `application.yml` | 액추에이터 노출 범위, graceful shutdown, 로깅 레벨 |
| 2 | `eureka-server.yml` | 포트 8761, 자기 등록 여부, 자기보호 모드 |
| 3 | `application-{env}.yml` | Loki · Zipkin 주소 |

1계층에는 데이터베이스와 Kafka 설정도 들어 있고 그것이 이 서비스에도 내려옵니다. 다만 해당 의존성을 넣지 않았으므로 그 값을 읽는 코드가 없어 아무 일도 일어나지 않습니다.

**값을 고칠 때 이 저장소를 건드리지 않습니다.** `config` 저장소에 커밋하고 이 서비스를 다시 띄우면 됩니다.

---

## 6. 트러블슈팅

### 기동 로그의 경고 두 가지는 정상입니다

유레카를 한 대만 두고 운영하기 때문에 나타나는 것이며 고칠 필요가 없습니다.

```
WARN c.n.eureka.cluster.PeerEurekaNodes : The replica size seems to be empty.
                                          Check the route 53 DNS Registry
```

유레카를 여러 대로 묶어 서로 복제하도록 구성하지 않았기 때문입니다. 단독 운영에서는 복제 대상이 없는 것이 정상이며, 이 경고가 단독 구성에서 불필요하게 나온다는 점은 `spring-cloud-netflix` 저장소에도 개선 요청으로 올라와 있습니다.

대시보드 하단의 **unavailable-replicas** 에 자기 주소가 표시되는 것도 같은 이유입니다. 유레카는 어떤 주소가 자기 자신인지 판단할 때 호스트명을 문자열로 비교하는데, `localhost:8761` 과 `{호스트명}:8761` 을 서로 다른 것으로 보기 때문입니다. `register-with-eureka: false` 와는 무관하게 나타납니다.

### 대시보드가 8761 에 없습니다

`config-server` 가 떠 있지 않아 포트를 받지 못한 것입니다. `spring.config.import` 에 `optional:` 이 붙어 있어 설정 없이도 기동되며, 그때는 **기본값인 8080 으로 뜹니다.**

기동 로그의 `Tomcat started on port` 줄을 봅니다. 8080 이면 `config-server` 를 먼저 띄우고 다시 실행합니다.

### 껐는데도 목록에 남아 있습니다

만료까지 최대 90초가 걸립니다. 그 이상 남아 있다면 자기보호 모드가 켜진 것이며, 대시보드 상단에 붉은 경고 문구가 표시됩니다. 이 프로젝트는 자기보호를 끄도록 설정했으므로(1-3), 경고가 보인다면 `config` 저장소의 `eureka-server.yml` 이 제대로 내려왔는지 확인합니다.

### 서비스가 유레카를 찾지 못합니다

로그에 연결 거부 스택트레이스가 반복된다면 주소를 확인합니다. 그 서비스가 받은 `eureka.client.service-url.defaultZone` 은 config 저장소의 `application-{env}.yml` 에 있으며, `local` 은 `localhost:8761`, `dev` 는 `eureka-server:8761` 입니다.

컨테이너에서 도는 서비스가 `localhost:8761` 을 보고 있다면 프로파일이 `dev` 로 잡히지 않은 것입니다.

### 등록된 주소가 `localhost` 입니다

IntelliJ 에서 실행한 서비스에서 나타납니다. 컨테이너 안의 게이트웨이가 그 주소를 자기 자신으로 해석하게 되므로 3-2를 확인합니다.

### 설정을 바꿨는데 반영되지 않습니다

`config` 저장소에 커밋했는지 먼저 확인합니다. 설정 서버는 작업 디렉터리가 아니라 저장소를 읽습니다. 설정 서버가 실제로 내려주는 값은 아래로 확인합니다.

```powershell
curl.exe http://localhost:8888/eureka-server/local
```

---

## 7. 디렉터리 구조

```
eureka-server/
├── src/main/java/com/pawtrail/eurekaserver/
│   └── EurekaServerApplication.java     @EnableEurekaServer
├── src/main/resources/
│   ├── application.yml                  세 줄 (이름 · 설정 서버 주소 · 기본 프로파일)
│   └── logback-spring.xml               콘솔과 Loki appender
├── src/test/java/com/pawtrail/eurekaserver/
│   └── EurekaServerApplicationTests.java
├── build.gradle
├── gradle.properties
├── settings.gradle
├── Dockerfile
├── Jenkinsfile
├── .gitattributes
├── .editorconfig
├── .gitignore
├── .coderabbit.yaml
└── .github/
    ├── ISSUE_TEMPLATE/issue_template.md
    └── pull_request_template.md
```
