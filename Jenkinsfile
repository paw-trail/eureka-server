// 파이프라인 본체는 Jenkins 공유 라이브러리에 있습니다.
// 이 파일에서는 파라미터 세 개만 채웁니다.
//
//   serviceName  서비스명 (레포명과 동일하게)
//   deployNode   배포 노드. edge / core / app 중 하나 (README 분류표 참고)
//   instances    띄울 인스턴스 개수
//
// 플랫폼 3종은 edge 노드에 배치합니다(nginx, gateway, eureka, config).
// 다만 배포 방식은 서로 다릅니다.
//   gateway  nginx blue-green
//   eureka   단독 교체
//   config   재시작
//
// 이 서비스는 단독 교체입니다.
// 다른 서비스들의 롤링 배포가 이 서비스를 거쳐 이루어지므로,
// 배포 순서에서 가장 먼저 처리하고 다른 서비스 배포 중에는 건드리지 않습니다.

@Library('pawtrail-pipeline') _

springServicePipeline(
    serviceName: 'eureka-server',
    deployNode : 'edge',
    instances  : 1
)
