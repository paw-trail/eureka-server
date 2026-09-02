# eureka-server

**함께하개의 서비스 레지스트리입니다.** 어느 서비스가 어느 주소에 떠 있는지를
관리합니다.

```
서비스들  ──▶  이 서버   이름 → 주소 장부
   │              │
   │              ├──▶  auth-service     192.168.1.161:8081   UP
   │              ├──▶  place-service    172.18.0.7:8084      UP
   │              └──▶  gateway-server   172.18.0.5:8080      UP
   │
   ├──▶  기동할 때 자기 주소를 등록
   ├──▶  30초마다 살아 있다고 알림 (하트비트)
   └──▶  남을 부를 때 이름으로 주소를 물어봄

        90초 소식이 없으면 목록에서 지움
```

<br><br>

---

## 0. 이 서비스가 하는 일

**레지스트리가 없으면 이렇게 됩니다.**

| | 있으면 | 없으면 |
|---|---|---|
| 게이트웨이가 서비스를 찾을 때 | 이름으로 물어봄 | **주소를 코드나 설정에 적어야 함** |
| 서비스가 포트를 바꾸면 | 저절로 반영 | 게이트웨이를 고침 |
| 인스턴스를 늘리면 | 저절로 번갈아 감 | 목록을 손으로 관리 |
| 서비스가 죽으면 | 90초 뒤 목록에서 빠짐 | **계속 그리로 요청을 보냄** |

---

**숫자로 보면 이렇습니다.**

| | 값 |
|---|---|
| 자바 파일 | **1개** — `EurekaServerApplication` |
| 포트 | 8761 |
| 의존성 | 5개 (Eureka Server · Config Client · Actuator · Prometheus · Loki) |
| DB · Redis · Kafka | **안 씀** |
| 공통 모듈 | **안 씀** |
| config 저장소 파일 | **3개** — 2계층 1개 + **4계층 2개** |

---

**코드가 이게 전부입니다.**

```java
@SpringBootApplication
@EnableEurekaServer          // *Initializr 가 붙여 주지 않음
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

<br><br>

---

### 0-1. 게이트웨이가 실제로 쓰는 방식

```
게이트웨이가 lb://place-service 를 만나면

  ① 유레카에게 물어봄        "place-service 어디 있어?"
        │
        ▼
  ② 주소를 받음             172.18.0.7:8084
        │                   (여러 개면 번갈아 — 로드밸런싱)
        ▼
  ③ 그 주소로 전달


  주소를 코드에 안 적으므로
    서비스가 어느 포트에 뜨든 게이트웨이는 몰라도 됨
    인스턴스를 늘려도 게이트웨이를 안 고쳐도 됨
```

---

**등록과 하트비트입니다.**

```
서비스 기동   ──▶  자기 주소를 등록                     UP
                    │
                    ├── 30초마다 하트비트
                    │
                    ▼
90초 소식 없음 ──▶  목록에서 지움                        만료
```

> **정상 종료해도 즉시 사라지지 않습니다.** 서비스가 내려갈 때 등록 해제를 보내지만
> 캐시 때문에 **대시보드에서 사라지는 데 시간이 걸립니다.**

<br><br>

---

### 0-2. 이 서비스는 목록에 나타나지 않습니다

```yaml
eureka:
  client:
    register-with-eureka: false     # 자기가 레지스트리이므로 자기에게 등록하지 않음
    fetch-registry: false           # 다른 서비스를 호출할 일이 없음
```

**대시보드 목록에 `EUREKA-SERVER` 가 없는 것이 정상입니다.**
살아 있다는 것은 **대시보드 페이지가 열리는 것**으로 확인됩니다.

> `config-server` 는 반대로 **등록만 합니다.** 대시보드가 *"지금 무엇이 떠 있나"* 를
> 보는 단일 화면인데 플랫폼 3개 중 하나만 빠지면 확인 경로가 갈라지기 때문입니다.
> **정작 주인이 빠지는 모양이지만 감수합니다.**

<br><br>

---

### 먼저 알아 두면 좋은 것 3가지

---

**① 레지스트리란 — 전화번호부**

```
게이트웨이가 place-service 를 부르려면 주소(IP:포트)를 알아야 함
        │
        ├── 게이트웨이 설정에 적어 둠      →  place 가 포트를 바꾸면 게이트웨이도 고쳐야 함
        │
        └── 전화번호부에서 찾음           →  place 가 기동하며 자기 번호를 등록
                                            게이트웨이는 이름으로 찾음
                                            *그 전화번호부가 이 서비스
```

---

**② 인스턴스란 — 같은 서비스를 여러 개 띄운 것**

```
verdict-service   인스턴스 3개    172.18.0.7:8086 · 172.18.0.8:8086 · 172.18.0.9:8086
        │
        └── 유레카 장부에 셋 다 등록됨
              게이트웨이가 "verdict 어디 있어?" 하면 셋 중 하나를 번갈아 줌
```

**"서비스" 는 이름 하나, "인스턴스" 는 실제로 떠 있는 프로세스 하나입니다.**
지금 로컬은 전부 1개씩이고, 배포에서 verdict 3개 · search 2개로 늘립니다.

---

**③ "계층" 이란**

이 서비스의 설정은 `config` 저장소에서 옵니다. 거기 파일이 네 부류이고 **겹쳐서 하나가 됩니다.**

```
1계층  application.yml           모든 서비스 공통
2계층  eureka-server.yml         이 서비스만          ← 포트 · 자기등록 · 자기보호
3계층  application-local.yml     local 환경만
4계층  eureka-server-local.yml   이 서비스 · local 만  ← *my-url
                                                        숫자가 클수록 이김
```

**이 서비스는 4계층을 실제로 쓰는 유일한 사례입니다.** 이유는 [3장](#3-my-url--기동-실패를-막는-값) 에 있습니다.

<br><br>

---

### 이 문서를 읽는 순서

| 지금 하려는 일 | 볼 곳 |
|---|---|
| 띄워서 확인하고 싶다 | [1장](#1-로컬에서-띄우기) |
| 대시보드를 읽는 법 | [2장](#2-대시보드-읽기) |
| **기동이 실패한다** | [3장](#3-my-url--기동-실패를-막는-값) → [6장](#6-막히기-쉬운-자리) |
| 설정이 어디 있는지 | [4장](#4-설정은-config-저장소에-있습니다) |
| 이미지를 굽거나 배포해야 한다 | [5장](#5-컨테이너와-배포) |
| 뭔가 안 된다 | [6장](#6-막히기-쉬운-자리) |
| "왜 이렇게 만들었지" | [7장](#7-왜-이렇게-만들었나) |
| 모르는 말이 나온다 | [9장](#9-용어) |

<br><br>

---

## 1. 로컬에서 띄우기

**`config-server` 가 먼저 떠 있어야 합니다.**

```
① config-server 실행                        8888
        │
        ▼
② EurekaServerApplication 실행               IntelliJ
        │
        ├──▶  config 에서 eureka-server.yml 을 받음        포트 · 자기등록 · 자기보호
        └──▶  eureka-server-local.yml 도 받음             *my-url
        │
        ▼
③ 기동 로그                                 Tomcat started on port 8761
        │
        ▼
④ 브라우저로 http://localhost:8761
```

<br><br>

---

### 1-1. 기동 로그에서 볼 것

```
The following 1 profile is active: "local"
Located environment: name=eureka-server, profiles=[local]     *config 를 받았음
Tomcat started on port 8761                                   *8080 이면 config 미수신
Client configured to neither register nor query for data.     register·fetch false 적용
The replica size seems to be empty.                           *정상 — 3장 참고
Started EurekaServerApplication in 4.4 seconds
```

---

**⚠ `Tomcat started on port 8080` 이면 config 를 못 받은 것입니다.**

```
spring.config.import 에 optional: 이 붙어 있어
config-server 가 없어도 기동은 됨
        │
        └── 대신 포트를 못 받아 기본값 8080 으로 뜸
              register-with-eureka 도 안 내려와
              자기에게 등록을 시도하다 Cannot execute request on any known server 를 반복
```

**대시보드가 8761 에 없으면 이것부터 의심합니다.**

---

**⚠ `Replica node URL:` 이 이어지면 정상이 아닙니다.**

```
The replica size seems to be empty.              ← 여기까지만 나오면 정상
Replica node URL: http://localhost:8761/eureka/  ← 이 줄이 나오면 자기를 피어로 오인한 것
```

[3장](#3-my-url--기동-실패를-막는-값) 을 봅니다.

<br><br>

---

## 2. 대시보드 읽기

```
http://localhost:8761
```

<br><br>

---

### 2-1. 어디를 보나

| 자리 | 정상 | 뜻 |
|---|---|---|
| **Instances currently registered with Eureka** | 뜬 서비스들이 `UP` | **이 서버 자신은 없어야 정상** |
| **DS Replicas** | **비어 있음** | 값이 있으면 자기를 피어로 오인한 것 |
| registered-replicas | **비어 있음** | 같음 |
| unavailable-replicas | **비어 있음** | 같음 |
| 붉은 문구 `THE SELF PRESERVATION MODE IS TURNED OFF` | **정상** | 경고가 아니라 **꺼졌다는 확인 표시** |
| `Environment: test` · `Data center: default` | 정상 | Netflix Eureka 기본값. 표시용일 뿐 |
| `Renews threshold` | 무시 | 자기보호를 껐으므로 판정에 안 쓰임 |

<br><br>

---

### 2-2. 등록 이름이 환경마다 다릅니다

```
local (IntelliJ)     host.docker.internal:auth-service:8081
dev   (컨테이너)      172.18.0.7:auth-service:8081
```

| | `local` | `dev` |
|---|---|---|
| 설정 | `hostname: host.docker.internal` · `prefer-ip-address: false` | `prefer-ip-address: true` |
| 등록 이름 | `host.docker.internal` | 도커 네트워크 IP |

---

**`host.docker.internal` 을 쓰는 이유입니다.**

```
IntelliJ 로 띄운 서비스가 localhost 로 등록하면
        │
        └── ⛔ 컨테이너 안의 게이트웨이가 그 주소를 자기 자신으로 해석해
              스스로를 찌름

host.docker.internal 로 등록하면
        │
        ├── 호스트에서       hosts 파일의 LAN IP    192.168.1.161
        └── 컨테이너 안에서   도커 내부 게이트웨이     192.168.65.254
                    │
                    └── *둘 다 결국 호스트를 가리킴
                          한 이름이 양쪽에서 통함
```

**그 덕에 컨테이너의 게이트웨이가 IntelliJ 로 띄운 서비스를 호출할 수 있습니다.**

> ⚠ **Docker Desktop 이 설치·실행 중이어야 합니다.** 그 hosts 항목이 없으면
> 머신 호스트명으로 등록되고 컨테이너가 그 이름을 못 풉니다.

---

**설정을 안 준 서비스도 그 이름으로 등록됩니다.**

`config-server` 는 config 저장소에서 설정을 안 받아 `eureka.instance.hostname` 을
받은 적이 없는데도 `host.docker.internal:config-server:8888` 로 뜹니다.

```
유레카는 hostname 이 없으면 로컬 IP 를 역방향 조회해 호스트명을 얻음
        │
        └── Docker Desktop 이 hosts 에 넣어 둔 그 이름이 돌아옴
```

<br><br>

---

### 2-3. 상태 확인

```bash
curl http://localhost:8761/actuator/health
```

```powershell
curl.exe http://localhost:8761/actuator/health
```

**등록 목록을 JSON 으로 보려면**

```bash
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

<br><br>

---

## 3. `my-url` — 기동 실패를 막는 값

**이 레포에서 제일 중요한 설정입니다.** 없으면 기동 자체가 안 됩니다.

```
유레카는 eureka.client.service-url 을 피어(다른 유레카 서버) 목록으로 읽음
        │
        └── 그중 자기 자신은 목록에서 뺌
              자기인지 판단할 때 호스트명을 문자열로 비교함


⛔ my-url 이 없으면

  eureka.instance.hostname      host.docker.internal      3계층이 줌
  defaultZone 의 호스트           localhost                 3계층이 줌
                                       │
                                       └── 문자열이 달라 자기를 남으로 봄
                                             │
                                             ├──▶  자기에게 복제하려고
                                             │      Jersey 복제 클라이언트를 만듦
                                             │
                                             └──▶  NoClassDefFoundError
                                                    기동 실패


✅ my-url 을 defaultZone 과 정확히 같게 주면

  문자열 비교를 건너뛰고 자기로 인식
        │
        └── 피어가 0개  →  복제 클라이언트를 아예 안 만듦
              WARN The replica size seems to be empty  ← 한 대만 두는 구성에서는 정상
```

<br><br>

---

### 3-1. 실제로 겪은 실패

```
Cannot Create new Replica Node :Jersey3ReplicationClient: http://localhost:8761/eureka/apps/
        │
        └── NoClassDefFoundError: org/apache/http/conn/socket/ConnectionSocketFactory
              기동 실패
```

**연쇄가 3단이었습니다.**

```
① Adding new peer nodes [http://localhost:8761/eureka/]     자기를 피어로 등록
② 그 피어에 복제하려고 Jersey3ReplicationClient 를 만듦
③ 그 클라이언트가 요구하는 클래스를 못 찾음
```

> **Boot 3까지는 화면상 표시로 끝났습니다.** 대시보드 `unavailable-replicas` 에
> 자기 주소가 뜨는 정도였는데, **Boot 4 에서 기동 실패로 드러났습니다.**

<br><br>

---

### 3-2. 그래서 4계층 파일이 둘 있습니다

```
config 저장소
├── eureka-server.yml           2계층   포트 · 자기등록 · 자기보호
├── eureka-server-local.yml     4계층   my-url: http://localhost:8761/eureka/
└── eureka-server-dev.yml       4계층   my-url: http://eureka-server:8761/eureka/
```

**우리 프로젝트에서 4계층을 쓰는 유일한 사례입니다.**

---

**왜 4계층이어야 하나**

```
2계층에 적으면    3계층(application-{env}.yml)이 2계층을 이기므로 덮임
2계층에 한 번만?  값이 환경마다 다름
        │
        └── 4계층밖에 자리가 없음
```

| 계층 | 파일 | 세기 |
|---|---|---|
| 1 | `application.yml` | 제일 약함 |
| 2 | `eureka-server.yml` | |
| 3 | `application-local.yml` | |
| **4** | **`eureka-server-local.yml`** | **제일 셈** |

---

**⚠ `defaultZone` 을 고치면 `my-url` 도 함께 고칩니다.**

```
3계층 application-local.yml    defaultZone: http://localhost:8761/eureka/
4계층 eureka-server-local.yml  my-url:      http://localhost:8761/eureka/
                                              ▲
                                              └── 문자열까지 정확히 같아야 함
```

**어긋나면 기동이 실패하고 원인이 로그에 직접 드러나지 않습니다.**

> **AWS 배포 때 `eureka-server-prod.yml` 도 만들어야 합니다.**
> `application-prod.yml` 의 `defaultZone`(edge 노드 사설 IP)이 정해지는 시점이며
> **두 값이 정확히 같아야 합니다.**

<br><br>

---

## 4. 설정은 config 저장소에 있습니다

**이 저장소의 `application.yml` 에는 세 줄뿐입니다.**

```yaml
spring:
  application:
    name: eureka-server
  config:
    import: "optional:configserver:http://${CONFIG_HOST:localhost}:8888"
  profiles:
    default: local
```

> **`config-server` 와 갈리는 지점입니다.** 그쪽은 닭-달걀 문제로 설정을 못 받지만
> **유레카는 자기 자신을 조회할 일이 없어 순환이 안 생깁니다.**
> 예외를 `config-server` 하나로 줄이는 것이 이득입니다.

<br><br>

---

### 4-1. 2계층에 있는 것

```yaml
# config/eureka-server.yml
server:
  port: 8761

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false      # *아래
```

---

**자기보호 모드를 끄는 이유입니다.**

```
자기보호란
  들어오는 하트비트가 기대치의 85% 아래로 떨어질 때
  "저들이 죽은 것이 아니라 나와의 네트워크가 끊긴 것" 으로 보고 만료를 통째로 멈춤
        │
        └── 인스턴스가 수백 개인 환경을 겨냥한 안전장치
```

**우리 규모에서는 반대로 작동합니다.**

| | |
|---|---|
| 로컬에 3~5개만 띄움 | **하나만 꺼도 20~30% 가 빠져 임계치를 즉시 넘음** |
| 결과 | 껐던 서비스가 목록에 `UP` 인 채로 남음 → 게이트웨이가 그리로 요청을 보냄 |

**결정적인 것은 무중단 배포와의 충돌입니다.**

```
core 노드를 유레카 OUT_OF_SERVICE 기반 롤링으로 교체하기로 했는데
        │
        └── 자기보호가 켜져 있으면 내린 인스턴스가 지워지지 않음
              *무중단 배포를 검증하려는 자리에서 검증 자체가 방해받음
```

> **감수하는 것** — 진짜 네트워크 분단이 나면 멀쩡한 인스턴스까지 지워집니다.
> EC2 6대가 한 VPC 안이고 인스턴스가 20개 남짓이라 **그 상황이 성립하지 않는다고 봅니다.**

<br><br>

---

### 4-2. 1계층 값이 그대로 내려오지만 무해합니다

```
1계층 application.yml 은 모든 서비스에 내려감
        │
        ├── spring.datasource.password: ${SERVICE_DB_PASSWORD}
        ├── spring.jpa.*
        ├── spring.flyway.*
        └── spring.kafka.*
                    │
                    └── 이 서비스에는 그 의존성 자체가 없음
                          값을 읽는 코드가 없어 아무 일도 일어나지 않음
```

**`SERVICE_DB_PASSWORD` 환경변수 없이도 뜨는 것이 실물로 확인됐습니다.**
플레이스홀더 미해결 오류가 나지 않습니다.

> **verdict · congestion 같은 무상태 도메인 서비스에도 그대로 적용됩니다.**

---

**3계층에서 내려오는 값 중 일부도 안 쓰입니다.**

```
eureka.client.service-url   등록하는 쪽을 위한 값
eureka.instance.*           같음
        │
        └── ⚠ 단 service-url 은 my-url 과 짝을 맞춰야 하므로
              "안 쓰인다" 고 무시하면 안 됨 (3장)
```

<br><br>

---

## 5. 컨테이너와 배포

<br><br>

---

### 5-1. compose 서비스명이 `eureka-server` 여야 합니다

```yaml
# infra/docker-compose.yml
  eureka-server:                    # *이 이름이 그대로 호스트명이 됨
    image: ghcr.io/paw-trail/eureka-server:latest
    environment:
      SPRING_PROFILES_ACTIVE: dev
      CONFIG_HOST: config-server
```

```
config 의 application-dev.yml 에 이미 박혀 있음
        defaultZone: http://eureka-server:8761/eureka/

config 의 eureka-server-dev.yml 에도
        my-url: http://eureka-server:8761/eureka/
```

**이름을 바꾸면 두 곳을 함께 고쳐야 합니다.**

---

**`LOKI_HOST` 는 필요 없습니다.**

```
config-server   자기 설정을 못 받음  →  EUREKA_HOST · LOKI_HOST 를 직접 받아야 함
이 서버          config 를 받음      →  CONFIG_HOST 하나면 됨
                                        나머지는 application-dev.yml 이 줌
```

<br><br>

---

### 5-2. 이미지 굽고 올리기

```powershell
cd C:\Tour_Prj\eureka-server
.\gradlew clean build

$env:GPR_TOKEN | docker login ghcr.io -u <GitHub 아이디> --password-stdin
docker build -t ghcr.io/paw-trail/eureka-server:latest .
docker push ghcr.io/paw-trail/eureka-server:latest
```

```bash
cd <infra 경로>
docker compose pull eureka-server
docker compose up -d
```

> `up -d` 만으로는 **이미지를 다시 받지 않습니다.** `pull` 이 먼저입니다.

<br><br>

---

### 5-3. 배포 방식 — 단독 교체

| 서비스 | 방식 | 왜 |
|---|---|---|
| gateway-server | blue-green | nginx 가 upstream 으로 지목 |
| config-server | 재시작 | 아무도 안 찾음 |
| **eureka-server** | **단독 교체** | 아래 |

```
이 서버가 잠깐 내려가면
        │
        ├── 이미 주소를 받아 둔 게이트웨이   캐시로 잠시 버팀
        └── 새로 뜨는 서비스               등록에 실패하고 재시도
                    │
                    └── 짧게 내리는 것은 감수 가능
```

<br><br>

---

### 5-4. 기동 순서

```
config-server  ──▶  eureka-server  ──▶  gateway-server  ──▶  도메인 14개
```

| | |
|---|---|
| `config-server` 가 먼저 | 이 서버가 설정을 거기서 받음 |
| 이 서버가 게이트웨이보다 먼저 | 게이트웨이가 주소를 여기서 받음 |
| 도메인 서비스가 마지막 | 등록할 곳이 있어야 함 |

> **순서가 어긋나도 결국 붙습니다.** 유레카가 늦게 떠도 서비스들이 등록을 재시도합니다.
> 다만 그동안 게이트웨이가 **503** 을 냅니다.

<br><br>

---

## 6. 막히기 쉬운 자리

<br><br>

---

### 6-1. 정상인데 경고처럼 보이는 것

| 로그·화면 | 왜 정상 |
|---|---|
| `The replica size seems to be empty. Check the route 53 DNS Registry` | **유레카를 여러 대로 묶지 않아서.** 단독 구성에서 불필요하게 나오는 것이 spring-cloud-netflix #4251 에 올라와 있음 |
| 대시보드의 붉은 `THE SELF PRESERVATION MODE IS TURNED OFF` | **꺼졌다는 확인 표시** — 경고가 아님 |
| 목록에 `EUREKA-SERVER` 가 없음 | `register-with-eureka: false` — 설계대로 |
| `Failed to set up a Bean Validation provider` | hibernate-validator 가 없음. 유레카는 검증을 안 씀 |
| `Spring Cloud LoadBalancer is currently working with the default cache` | Caffeine 미사용 |

<br><br>

---

### 6-2. 기동이 안 될 때

| 증상 | 원인 |
|---|---|
| **`Cannot Create new Replica Node`** + `NoClassDefFoundError` | **`my-url` 이 안 내려옴.** [3장](#3-my-url--기동-실패를-막는-값) |
| `Tomcat started on port 8080` | **config 미수신.** `config-server` 가 떠 있는지 · `CONFIG_HOST` |
| `Cannot execute request on any known server` 반복 | 같음 — `register-with-eureka: false` 도 안 내려와 자기에게 등록을 시도 |
| `Replica node URL:` 이 로그에 나옴 | `my-url` 과 `defaultZone` 이 문자열까지 같은지 |
| IntelliJ 에서만 `NoClassDefFoundError` | **IntelliJ 클래스패스 문제.** 아래 |

---

**같은 코드가 `bootRun` 에서는 뜨는 경우가 있었습니다.**

```
IntelliJ 실행     NoClassDefFoundError: ConnectionSocketFactory
gradlew bootRun   정상 기동
        │
        └── 설정이 클래스 존재 여부를 바꿀 수는 없음
              → IntelliJ 실행 구성의 런타임 클래스패스에 httpclient 4.5.13 이 빠진 것
```

**1순위 대응은 Gradle 툴 창의 `Reload All Gradle Projects` 후 재실행입니다.**

> **`my-url` 을 넣은 뒤로는 재현되지 않습니다.** 복제 클라이언트를 아예 안 만들어
> **기동 경로가 `httpclient` 존재 여부와 무관해졌기 때문**입니다.
> 다만 **다른 서비스에서 비슷한 `NoClassDefFoundError` 가 나면 이것을 1순위로 봅니다.**

<br><br>

---

### 6-3. 서비스가 안 보이거나 안 사라질 때

| 증상 | 원인 |
|---|---|
| 서비스가 목록에 없음 | 그 서비스가 config 를 못 받았거나 `register-with-eureka: false` 로 떴는지 |
| 껐는데도 `UP` 으로 남음 | **90초를 기다릴 것.** 자기보호를 껐으므로 결국 사라짐 |
| 등록 주소가 `localhost` | 3계층의 `eureka.instance.hostname` 이 안 내려옴 → **컨테이너 게이트웨이가 자기를 찌름** |
| 게이트웨이가 503 | 유레카에 그 이름이 있는지 · 라우트의 `lb://` 이름이 맞는지 |
| 등록 이름이 IP 가 아님 | **정상** — [2-2](#2-2-등록-이름이-환경마다-다릅니다) |

<br><br>

---

### 6-4. 설정을 바꿨는데 반영이 안 될 때

```
① config 저장소에 push 했나
        │
        ▼
② curl :8888/eureka-server/local 에 새 값이 있나
        propertySources 가 4개 내려와야 함
          eureka-server-local.yml → application-local.yml
          → eureka-server.yml → application.yml
        │
        ▼
③ 이 서버를 재기동
```

> **포트·자기등록 같은 값은 `refresh` 로 안 바뀝니다.** 기동 시점에 쓰이는 값이라
> **재기동해야 합니다.**

<br><br>

---

### 6-5. 환경

| | 주의 |
|---|---|
| PowerShell `curl` | `Invoke-WebRequest` 별칭 → **`curl.exe`** |
| 대시보드 보기 | **브라우저가 제일 쉬움** — `http://localhost:8761` |
| `bootRun` | **80% 에서 멈춘 것처럼 보이는 것이 정상** |
| 첫 빌드가 느림 | `eureka` 의 전이 의존성 트리가 큼 (jersey · archaius · xstream) |
| IntelliJ 가 Gradle 프로젝트로 인식 못 함 | `build.gradle` 을 **파일째 `Open as Project`** |

<br><br>

---

## 7. 왜 이렇게 만들었나

<br><br>

---

### 7-1. 왜 공통 모듈을 안 쓰나

**플랫폼 3개 공통 규칙입니다.**

```
공통 모듈의 존재 이유   "도메인 서비스가 전부 쓰는 것"
플랫폼 3개            인프라 성격이라 그 기준 밖
```

**이 서버도 성격이 같습니다.**

```
공통 모듈의 TraceIdResponseAdvice 는 모든 응답을 래퍼로 감쌈
        │
        └── 이 서버는 REST 응답으로 레지스트리를 주고받음
              config-server 만큼 치명적이지는 않으나 성격이 같음
```

| 공통 모듈이 주는 것 | 이 서버에서 |
|---|---|
| `BaseEntity` · 감사 | JPA 가 없음 |
| `ErrorCode` · 예외 처리기 | 도메인 API 가 없음 |
| `HeaderAuthenticationFilter` | 게이트웨이 뒤가 아님 |
| Outbox · Inbox | DB · Kafka 가 없음 |

**유일하게 쓸모 있던 것이 Loki appender 하나**라 **loki4j 를 직접 선언**했습니다.

<br><br>

---

### 7-2. 왜 config 는 받나

**`config-server` 와 갈리는 지점입니다.**

| | `config-server` | 이 서버 |
|---|---|---|
| 닭-달걀 | **있음** — 저장소 주소를 알아야 저장소를 읽음 | 없음 |
| 자기 자신 조회 | — | **할 일이 없어 순환이 안 생김** |
| 결과 | 자기 `application.yml` 에 전부 | **3줄만 두고 나머지는 config 에서** |

> **예외를 `config-server` 하나로 줄이는 것이 이득입니다.**
> 예외가 둘이면 *"왜 얘는 되고 쟤는 안 되지"* 를 매번 떠올려야 합니다.

<br><br>

---

### 7-3. 왜 피어 복제를 안 하나

**유레카를 한 대만 둡니다.**

```
피어 복제란
  유레카 서버 여러 대가 서로 등록해 목록을 복제하는 것
        │
        └── 한 대가 죽어도 다른 대가 목록을 갖고 있음
```

| | 우리 |
|---|---|
| 인스턴스 규모 | 20개 남짓 |
| 유레카가 잠깐 죽으면 | **게이트웨이가 캐시로 잠시 버팀** |
| 복제를 하면 | `my-url` 문제 · 피어 설정 · 노드가 하나 더 |

**규모에 비해 얻는 것이 적습니다.**

> 필요해지면 `defaultZone` 에 서로의 주소를 넣고 `register-with-eureka: true` 로
> 바꾸면 됩니다. **그때 `my-url` 도 함께 봐야 합니다.**

<br><br>

---

### 7-4. 왜 Initializr 로 만들었나

**`service-template` 에서 복제하지 않았습니다.**

| 걷어낼 것 | 남는 것 |
|---|---|
| JPA · QueryDSL · hibernate-spatial · Flyway · PostgreSQL · Kafka · Redis · springdoc | Dockerfile · Jenkinsfile · `.github/` · `.coderabbit.yaml` |
| 4계층 골격 · `V20__template.sql` · README | **넷뿐이고 복사하면 됨** |

**결정적인 이유는 Boot 4 의 정확한 아티팩트 이름을 알려 준다는 것입니다.**

> ⚠ **Initializr 가 `@EnableEurekaServer` 를 붙여 주지 않습니다.**
> `@EnableConfigServer` 를 안 붙여 준 전례가 있어 미리 챙겼습니다.

---

**아티팩트명은 그대로입니다.**

```
spring-cloud-starter-netflix-eureka-server     Spring Cloud 2025.1 에서도 그대로
@EnableEurekaServer                            그대로
```

**이름이 바뀐 것은 게이트웨이뿐**입니다 (`spring-cloud-gateway-server-web{flux,mvc}`).

<br><br>

---

## 8. 아직 안 한 것

| 언제 | 무엇 |
|---|---|
| **AWS 배포 때** | **`eureka-server-prod.yml` 신설** — `application-prod.yml` 의 `defaultZone`(edge 사설 IP)과 **문자열까지 같게** |
| | `application-prod.yml` 의 `defaultZone` 자체가 아직 TODO |
| `dev` 컨테이너 기동 때 | **logback 이 config 값을 읽는지** 확인 |
| 판단 | 유레카를 여러 대로 둘지 — 지금은 한 대 |

---

**logback 확인이 필요한 이유입니다.**

```
app.logging.loki.url 이 config 저장소에서 내려옴
        │
        └── <springProperty> 가 그 값을 못 읽으면
              defaultValue 인 http://localhost:3100/... 으로 조용히 대체됨
                    │
                    └── ⚠ 컨테이너 안에서 localhost 는 자기 자신
                          로그가 아무 데도 안 가는데 경고도 안 뜸
```

**"경고가 없다" 로 판정하면 안 됩니다.**

```bash
curl http://localhost:3100/loki/api/v1/labels
```

**응답에 `data` 필드가 있는지로 봅니다.**


<br><br>

---

## 9. 용어

| 용어 | 뜻 |
|---|---|
| **레지스트리** | 서비스 이름 → 주소 장부. 이 서비스 |
| **등록** | 서비스가 기동하며 자기 주소를 장부에 올리는 것 |
| **하트비트** | 등록한 서비스가 30초마다 "살아 있음" 을 알리는 신호 |
| **만료** | 90초 소식이 없는 인스턴스를 장부에서 지우는 것 |
| **인스턴스** | 실제로 떠 있는 프로세스 하나. 한 서비스에 여럿일 수 있음 |
| **`register-with-eureka`** | 자기를 장부에 올릴지. 이 서비스는 `false` |
| **`fetch-registry`** | 장부를 받아 올지. 이 서비스는 `false` |
| **자기보호 모드** | 하트비트가 85% 아래로 떨어지면 만료를 멈추는 장치. **우리는 끔** |
| **피어** | 다른 유레카 서버. 여러 대로 묶을 때 서로를 이렇게 부름. 우리는 1대 |
| **피어 복제** | 유레카 서버끼리 장부를 복사하는 것. 안 함 |
| **`my-url`** | "내 주소는 이것" 이라고 알려 자기를 피어 목록에서 빼는 값. **없으면 기동 실패** |
| **`defaultZone`** | 등록하는 쪽이 유레카를 찾아가는 주소. `my-url` 과 문자열까지 같아야 함 |
| **`host.docker.internal`** | 컨테이너 안에서 호스트를 가리키는 이름. local 등록 이름 |
| **`prefer-ip-address`** | 호스트명 대신 IP 로 등록할지. dev 는 `true` |
| **4계층** | `eureka-server-{env}.yml`. 이 서비스만 실제로 씀 |
| **DS Replicas** | 대시보드의 피어 목록. **비어 있어야 정상** |
