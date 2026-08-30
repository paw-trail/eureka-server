package com.pawtrail.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 서비스 레지스트리입니다.
 *
 * 각 서비스는 기동할 때 자기 주소를 이곳에 등록하고, 이후 30초마다 살아 있음을 알립니다.
 * 게이트웨이와 서비스들은 상대의 주소를 직접 알지 않고 이곳에서 받아 갑니다.
 * 90초 동안 소식이 없는 인스턴스는 목록에서 지워집니다.
 *
 * 이 서비스 자신은 레지스트리에 등록하지 않습니다.
 * 자기가 레지스트리이므로 자기에게 등록할 이유가 없고 다른 서비스를 호출하지도 않습니다.
 * 그래서 대시보드 목록에는 이 서비스가 나타나지 않습니다.
 *
 * 설정은 config 저장소의 eureka-server.yml 에서 내려옵니다.
 * 설정 서버와 달리 이 서비스는 설정을 받는 쪽이며,
 * 자기 자신을 조회할 일이 없으므로 순환이 생기지 않습니다.
 *
 * 공통 모듈(com.pawtrail.common)을 의존하지 않습니다.
 * 플랫폼 3종에 공통으로 적용하는 규칙이며 이유는 README 4장에 있습니다.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }

}
