# 후속 개선 제안 — 이번 리팩토링과 분리

아래 항목은 이번 작업에서 동작을 변경하지 않는다. 버그 확정과 정책 변경 제안을 구분한다.

| 후보 | 현재 근거 | 별도 작업에서 확인할 것 |
|---|---|---|
| 검색 입력/오류 계약 통일 | 일반 API와 플랫폼 API의 ALL 처리, interceptor와 advice의 오류 JSON이 다름 | 소비자 호환성, 명시적 API 변경 범위 |
| 대표 도서 명칭 | platform DTO의 canonicalBookId가 BookOrigin ID를 전달 | 외부 필드 변경 여부와 migration; 현행 이름 유지 |
| 검색 SQL/N+1 계측 | 일반 검색 응답에서 lazy platform 접근, 사용자 전체 favorite ID 조회 | 실제 SQL 횟수·데이터 크기 측정 후 필요한 query만 개선 |
| 도서별 복수 플랫폼 summary | groupPlatformSummaries가 book ID당 하나를 전제 | schema/data가 복수 행을 허용하는지 확인 후 응답 정책 결정 |
| 회원탈퇴 DB/Redis 실패 일관성 | DB transaction이 Redis 세션 삭제를 rollback하지 못함 | 장애 시 세션 폐기 정책과 보상·재시도 필요성; 무조건 분산 transaction 도입 금지 |
| 인증/연동 해제 회귀 테스트 | 전용 테스트 부족 | Lua grace/동시성, 토큰 파싱, rollback, 작업 상태·lock·retry를 먼저 고정 |
| 테스트 환경 격리 | 전체 context test가 실제 application 설정을 로드 | 전용 MySQL 설정과 scheduler/외부 호출 격리; H2로 FULLTEXT를 대체하지 않기 |
| 검색어 서비스 구성 분리 | 서비스가 Lettuce connection을 소유/종료 | SSL/cluster/sentinel 및 인증 Redis와의 timeout 격리를 먼저 검증 |
| 오류 cause 로그 노출 검증 | 공통 handler와 unlink processor가 stack trace를 기록 | provider exception의 URI/응답/token 포함 가능성을 재현하고 필요한 최소 마스킹만 도입 |
| Gradle deprecation | 현재 빌드는 성공하지만 Gradle 10 관련 경고 발생 | warning-mode all로 원인 분류 후 별도의 빌드 도구 갱신 |

신규 기능, schema/column/index 변경, 신규 cache, 외부 API 교체, 새 라이브러리 추가는 포함하지 않는다.
