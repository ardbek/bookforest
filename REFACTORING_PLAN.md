# Bookforest Refactoring Plan

분석일: 2026-09-18. 코드 변경 전 조사한 기준선이다. 외부 Reference와 신규 라이브러리는 사용하지 않는다. [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md)를 적용한다.

## 조사 범위와 기준선

- main Java 163개 파일 / 8,960줄, test Java 4개 파일. 줄 수는 공백·주석을 포함한 물리적 줄 수다.
- domain: book 27, user 38, auth 12, library 8, platform 6, category 8, collection 3, ranking 6, searchkeyword 5. global 48, bootstrap 2.
- main/resources의 application 설정, Redis Lua 5개, Hibernate FunctionContributor SPI, Gradle, Dockerfile, GitHub Actions의 build/test 진입점을 조사했다.
- 기존 작업물 `.serena/`, `_workspace/`, `docs/`는 작업 시작부터 untracked였으며 수정 대상에서 제외한다. 기존 소스 변경은 없었다.
- `compileJava test check`: 성공. 첫 실행은 UP-TO-DATE였으므로 `test --rerun-tasks`로 재검증했다.
- 실제 재실행: **32 tests / 0 failures / 0 errors / 0 skipped**, BUILD SUCCESSFUL (49초). MySQL 통합 테스트 3, content type 5, ranking 11, searchkeyword 13.
- 별도 lint/static analyzer는 build.gradle에 없다. Gradle 10 호환성 deprecation 경고가 있으며 이번 작업에서 toolchain/plugin을 갱신하지 않는다.

## Architecture / Dependency 분석

대부분 `domain/<기능>` 안에 controller/service/repository/entity/dto가 있고 작은 ranking/searchkeyword는 flat 구조다. 따라서 대규모 package normalization은 필요하지 않다. Controller의 Repository 직접 호출, Controller transaction, Repository → Service, Entity → Controller 의존은 확인되지 않았다. Entity에 일반 setter를 무분별하게 사용한 흔적도 없다.

```text
BookController → BookSearchService → BookRepository / PlatformRepository
                                 → FavoriteBookRepository / FavoriteLibraryRepository
              → SearchKeywordService → Redis Lua / snapshot
FavoriteController → FavoriteService → User / Book / Library / Favorite repositories
LibraryController → LibraryService → LibraryRepository / PlatformRepository
RankingController → RankingService → Redis
RankingScheduler → RankingService → DataLibraryClient / BookRepository / Redis
AuthController → AuthService → TokenProvider / RefreshTokenStore / UserRepository
UserController → UserWithdrawalService → user repositories / RefreshTokenStore
                                       → task creation → AFTER_COMMIT listener
                                                         → processor → external client
                                                                     → state service → locked repository
```

상위 feature의 import 관계는 book → platform/library/user/searchkeyword/global, user → book/library/platform/auth/global, auth → user/global, ranking → book, library → platform, category → book/platform, collection → library/platform, searchkeyword → global, global.security/redis → auth/user다. book/category 관계에는 JPQL 문자열 참조도 있다. 이 그래프는 런타임 Bean 순환이 아니라 저장 모델과 인증 연동을 포함한 패키지 결합이다.

book/user 양방향 참조 중 book의 즐겨찾기 Repository 직접 접근은 읽기 정책을 user 밖에 둔다. 반면 LibraryService의 PlatformRepository와 RankingService의 BookRepository는 단순한 조회 협력이므로 우선 유지한다. UserWithdrawalService의 많은 의존성도 탈퇴라는 한 Use Case에서 필요하다.

## Complexity 분석

| Class | 줄 수 | 판단 |
|---|---:|---|
| BookSearchService | 474 | 가장 큰 Service. 일반 검색·상세와 플랫폼별 검색·상세·관련 도서·즐겨찾기가 혼재 |
| BookSearchRepositoryImpl | 431 | 가장 큰 Repository. 긴 JPQL 비중이 큼; 줄 수만으로 분리하지 않음 |
| TokenProvider | 271 | 발급/파싱/검증 하나의 보안 책임; 변경 위험이 크고 전용 테스트 없음 |
| SocialUnlinkTask | 223 | 상태 전이와 persistence 매핑; 단순 데이터 클래스와 구분 필요 |
| SearchKeywordService | 218 | Redis connection 수명, 해시, rate limit, 기록, snapshot, scheduler, 응답 record 혼재 |
| AuthService | 201 | 세션 정책; 분리보다 회전 계약 테스트가 우선 |
| RedisRefreshTokenStore | 197 | Redis 직렬화·원자 회전·세션 목록 관리; key/Lua 결합 |
| FavoriteService | 167 | public method 8, dependency 5; 유사한 두 자원 CRUD지만 현재는 읽기 쉬움 |
| OAuth2AuthenticationSuccessHandler | 162 | 실제 cookie manager 외에 미사용 cookie 생성 구현이 남음 |
| BookController / FavoriteController | 각각 113 | 가장 큰 Controller지만 대부분 요청 선언/위임/주석 |

주입 의존성은 UserWithdrawalService 8, SecurityConfig 7, FavoriteService/OAuth2AuthenticationSuccessHandler 5, BookSearchService/AuthService/RankingService 4다. SearchKeywordService는 저장하는 협력 객체가 Clock 포함 5개이며 연결 lifecycle 상태도 가진다.

긴 메서드의 물리 범위: searchPlatformBooks 78줄, SecurityConfig.securityFilterChain 73줄, getPlatformBook 60줄, BookSearchRepositoryImpl.buildWhereClause 65줄, DataLibraryClient.parse 54줄, SocialUnlinkTaskProcessor.process 51줄. 선언부·리터럴·주석 포함이며 순환 복잡도 점수는 아니다. 검색 플랫폼 선택과 응답 조립은 나눌 이유가 있지만 Security DSL과 query 선언은 길다고 잘라내지 않는다.

영향 범위가 큰 타입은 SocialProvider(main import 18곳), BaseTimeEntity(15), BookOrigin/Platform(각 10), ErrorCode/Library(각 8)다. 단순 import 수는 간접 호출·reflection·JPQL 사용을 전부 나타내지 않으므로 Entity 변경 위험은 더 크다.

## Quality 분석과 우선순위

| 우선 | 발견 | 근거 / 처리 |
|---|---|---|
| 1 | 도서 서비스 책임 혼재 | 4개 public Use Case 중 일반 API 2개는 BookRepository만 필요. 플랫폼 API만 platform/favorite 의존성을 사용. Phase 1 |
| 2 | 즐겨찾기 표시의 user 내부 저장소 노출 | book에서 두 Favorite Repository 조회와 익명 처리. 활성 회원 전용 FavoriteService와 정책이 달라 단순 대체하면 동작 변경. Phase 2 |
| 3 | 검색어 서비스의 기술/정책 책임 혼재 | 별도 Redis timeout/SSL/cluster/sentinel와 owned connection 수명까지 Service 안에 있음. 기존 실제 Redis 테스트는 있으나 구성별 증거 부족. 후속 조건부 작업 |
| 4 | 보안·탈퇴 regression 공백 | JWT/Auth/탈퇴/재시도 전용 테스트 없음. transaction/정책 변경을 이번 범위에서 제외 |
| 5 | 사용하지 않는 cookie 생성 중복 | OAuth success는 cookieManager.add만 호출. private addRefreshTokenCookie는 선언 외 참조·annotation 없음. callback 테스트 후 Phase 3에서 삭제 |
| 6 | 예외 응답 불일치 | common code/message, search message-only, 빈 400, OAuth redirect. 통일은 계약 변경이므로 제안만 기록 |
| 7 | DTO page wrapper 유사성 | JSON root 이름과 자원이 다름. generic wrapper로 교체하지 않음 |
| 8 | 간결/장황한 스타일 혼재 | searchkeyword/ranking의 한 줄 분기와 auth/user의 빈 Javadoc. 변경하는 주요 메서드에만 목적 주석 적용 |

### 유지할 좋은 구조

LibraryController/StandardCategoryService의 얇은 흐름, record DTO의 from 변환, Favorite Repository의 EntityGraph, SocialUnlinkTask의 명시적 변경, unlink 외부 호출/짧은 잠금 transaction 분리, ErrorResponseWriter와 GlobalExceptionHandler의 공통 오류는 내부 기준으로 채택한다. 자세한 채택/비채택 사항은 Architecture Rules의 Internal Reference 표에 있다.

### 추가로 확인한 문제와 보존 결정

- Repository의 두 소장 조회는 AVAILABLE 조건, 플랫폼 일치, 정렬이 다르다. 중복 제거 대상으로 보지 않는다.
- BookSearchService.groupPlatformSummaries는 book ID당 한 행을 전제로 한다. 여러 플랫폼 행이 오면 duplicate key 가능성이 있으나 schema/data 확인 없이 grouping 동작을 바꾸지 않는다.
- 일반 검색은 platform lazy 연관을 응답 생성에서 읽는다. 여러 플랫폼이 섞일 때 추가 SQL 가능성이 있으므로 N+1 **후보**이며 계측 전 확정하지 않는다. Favorite/Library 목록에는 EntityGraph가 이미 있다.
- 플랫폼 전체 조회와 사용자 즐겨찾기 전체 ID 조회는 현행 방식이다. 범위 제한 query/새 cache는 성능 개선으로 분리한다.
- Controller/private `@Transactional`은 없다. unlink processor는 외부 API 호출을 transaction 밖에서 처리하고 state service는 별도 Bean이므로 현재 self invocation 문제를 발견하지 않았다.
- 회원탈퇴는 DB transaction 안에서 Redis를 삭제한다. DB rollback이 Redis 삭제를 되돌리지 못한다. 보상 정책 변경 없이 순서를 옮기지 않는다.
- OAuth IllegalStateException/IllegalArgumentException은 adapter에서 오류 코드를 구분한다. GlobalExceptionHandler만 보고 치환하면 안 된다.
- SearchKeywordService/RankingService의 RuntimeException catch는 metric/log와 fallback을 남기는 의도된 장애 격리다. AuthService.logout의 잘못된 토큰 무시는 멱등적인 기존 계약이다.
- System.out/printStackTrace/new RuntimeException의 직접 사용은 발견하지 않았다. 일반 오류 로그에는 token 원문을 출력하지 않는다. GlobalExceptionHandler와 unlink processor의 예외 cause 전체 logging은 외부 오류에 비밀값이 포함되는지 추가 검증할 후보다.
- `CollectionRun`, 수집 관련 Entity 변경 메서드, ServletInitializer, Hibernate SPI, scheduler/listener는 호출 grep만으로 삭제하지 않는다.
- `getRefreshTokenExpiresInSeconds`, 일부 SocialAccount/Credential/Unlink Repository method는 직접 호출 없는 후보다. public API/Spring proxy와 외부 사용 확인이 부족하므로 보존한다.
- global.redis.RedisRefreshTokenStore, global.security의 unlink client는 특정 도메인에 속하지만 기존 통합 위치다. 새로운 global util은 만들지 않으며 보안 관련 package 이동은 후속 검증 후 한다.

## 실행 범위

아래 Phase 1~4는 현재 계약을 보존하며 실행 가능한 범위다. 모든 패키지/DTO/예외를 일괄 정리하는 Phase는 만들지 않는다. 각 Phase 전에 호출자와 테스트를 다시 확인하고, characterization → 수정 → 주석 → compile → compiler/의존성 점검 → test/check → regression/diff 검토 순서로 진행한다. 커밋은 하지 않고 목적별 diff를 유지한다.

## Phase 1 — 일반 도서와 플랫폼 도서 Use Case 분리

- **목적:** 가장 큰 Service에서 API별 변경 영향 범위를 분리한다.
- **대상/파일:** `book/service/BookSearchService`, 신규 `PlatformBookService`, `BookSearchFilters`, `book/controller/BookController`; `BookContentTypeTest`, `BookforestApplicationTests`, `SearchKeywordTest`와 신규 검색 계약 테스트.
- **현재 문제:** 일반 검색/상세가 플랫폼 목록·즐겨찾기·관련 도서 의존성과 함께 위치한다. 플랫폼 선택과 결과 조회가 긴 메서드 하나에 있다.
- **방향:** 기존 코드를 이동하고 Controller를 해당 Service에 연결한다. ID 정리는 book 내부의 작은 package-private 정책 함수만 공유한다. 플랫폼 선택은 이름 있는 private method로 구분한다. 검색 알고리즘과 DTO는 유지한다.
- **예상 영향:** Java 생성자/내부 호출자 변경. URL, method, parameter, JSON, query, transaction readOnly는 동일.
- **위험도:** MEDIUM.
- **필요 테스트:** searchType의 strict/lenient 차이, ID 정리, 플랫폼 capability 우선순위, 선택 ID/type 불일치, 그룹 preview 3개와 단일 플랫폼 paging, 익명/로그인 favorite, 도서 상세 에러, ISBN/제목·저자 관련 도서 순서, contentType. 기존 MySQL/Redis/HTTP 테스트 전체.
- **완료 조건:** characterization이 수정 전후 통과, 일반 Service에 platform/favorite 의존성 없음, Controller mapping/DTO/query 불변, 전체 compile/test/check 성공.

## Phase 2 — 즐겨찾기 조회 경계 정리

- **목적:** book이 user의 저장소와 익명 조회 규칙을 직접 관리하지 않게 한다.
- **대상/파일:** `PlatformBookService`, 신규 `user/service/FavoriteLookupService`, 해당 계약 테스트.
- **현재 문제:** favorite ID 조회가 book의 private method로 묻혀 있고 다른 user Service와 검증 정책이 다르다.
- **방향:** 기존 두 조회와 null-user 빈 집합 처리를 구체 Service 한 개로 이동한다. interface/port는 만들지 않는다. FavoriteService의 활성 회원 검증과 쓰기 흐름은 유지한다.
- **예상 영향:** 플랫폼 도서 Service dependency 4 → 3, book → user.repository 직접 import 제거. 조회 횟수/순서와 null/false 응답 보존.
- **위험도:** LOW.
- **필요 테스트:** 익명은 favorite 저장소 미호출, 로그인 ID 집합/중복 처리, library favorite null/false/true, 기존 검색·상세 회귀.
- **완료 조건:** 조회 정책 테스트와 전체 compile/test/check 성공, DB schema/query 변경 없음.

## Phase 3 — OAuth cookie 생성의 미사용 중복 제거

- **목적:** 실제 로그인 callback이 사용하는 cookie 구현을 한 곳으로 명확히 한다.
- **대상/파일:** `global/security/oauth/OAuth2AuthenticationSuccessHandler`, 신규 callback 테스트.
- **현재 문제:** 실제 호출되지 않는 private cookie 생성 메서드와 상수/import가 실제 CookieManager 구현 옆에 남아 있다.
- **방향:** callback의 credential 저장 → 세션 발급 → cookie → redirect 동작을 테스트한 후 미사용 private 코드만 삭제한다. handler 공개 생성자 계약은 유지한다.
- **예상 영향:** 동작 변경 없음. JWT/Origin/cookie 속성·이름·path·만료와 외부 provider 계약은 그대로다.
- **위험도:** LOW (보안 경로이므로 callback 테스트 필수).
- **필요 테스트:** 성공 callback의 단일 cookie, `/api` path, HttpOnly/Secure/SameSite, configured callback redirect, credential 저장 실패 때 세션/cookie 미발급.
- **완료 조건:** source/resources/config/test에서 삭제 대상 참조 없음, callback 및 전체 compile/test/check 성공.

## Phase 4 — 최종 일관성 / 회귀 검토

- **목적:** 범위가 커지지 않았는지와 문서/코드 일치를 확인한다.
- **대상/파일:** 변경 diff, main/test 전체 구조, 이 문서, Architecture Rules, Future Improvements.
- **현재 문제:** 구조 변경의 성공과 기능 동등성을 별도로 입증할 기록이 필요하다.
- **방향:** before/after 책임·dependency·대형 class·중복·테스트·가독성을 비교한다. 미해결 항목을 완료 처리하지 않는다.
- **예상 영향:** 보고서와 목적 주석 보완만. 추가 동작 변경 없음.
- **위험도:** LOW.
- **필요 테스트:** 마지막 코드 상태의 compile/test/check 결과, XML 실패/skip 수, 계약 파일 diff, `git diff --check`, main method 주석/호출자 검색.
- **완료 조건:** 모든 실행 Phase의 결과와 제한사항 기록, 외부 계약 불변 검토, 기존 사용자 작업 보존.

## 가장 위험한 변경 / 후속 작업의 선행 조건

| 변경 후보 | 위험도 | 착수 전 필요한 증거 |
|---|---|---|
| JWT/RefreshTokenStore/Lua 회전 또는 저장소 이동 | HIGH | 실제 Redis의 동시 회전, grace 만료/재사용, 전체 세션 폐기, JWT 서명/타입/만료 테스트 |
| 탈퇴/작업 생성/claim/AFTER_COMMIT 경계 변경 | HIGH | rollback, FK 삭제 순서, Redis 실패, 다중 worker, retry timeout·중복 처리 테스트 |
| 검색 JPQL/DTO/플랫폼 분류 정책 통일 | HIGH | 실제 MySQL 데이터 불변조건, SQL 결과/정렬/JSON 계약의 대조. 기능 변경이면 별도 승인 범위 |
| SearchKeywordService의 connection/config 분리 | MEDIUM | standalone/SSL/cluster/sentinel 설정 전달, Spring Redis auto-config와 인증 timeout 격리 테스트 |
| global의 도메인별 integration package 이동 | MEDIUM | Bean name, config binding, SPI/문자열/FQCN/생성자 참조, 보안 회귀 테스트 |

위 항목은 현재 안전한 실행 범위와 구분한 기술 부채다. 부족한 안전망을 무시하고 package 이름만 바꾸거나 transaction을 이동하지 않는다. 기능·계약·성능 변경 제안은 [FUTURE_IMPROVEMENTS.md](FUTURE_IMPROVEMENTS.md)에 둔다.

## Phase Result 기록

### Phase 1 Result

- **Changed:** 플랫폼 검색/상세를 PlatformBookService로 이동하고 Controller 및 모든 확인된 테스트 호출자를 연결했다. ID 정리 정책은 package-private BookSearchFilters에서 공유한다. 플랫폼 선택을 selectPlatforms로 구분했다.
- **Improved:** 일반 조회는 BookRepository 하나만 의존한다. 플랫폼 조회 변경이 일반 조회 Service의 검증/매핑 코드와 섞이지 않는다. 수정한 주요 메서드에 목적 주석을 작성했다.
- **Tests:** 변경 전 신규 characterization 14건 통과. 변경 후 `compileJava test check` 성공, 전체 46건, 실패/오류/skip 0. compiler와 dependency 검색, `git diff --check` 통과. 독립 static analyzer는 설치되어 있지 않다.
- **Regression:** NONE (검증 범위 내). 이동한 기존 메서드 11개의 본문을 공백/주석 제외하고 대조했으며 동일했다. 플랫폼 선택 추출은 기존 분기/순서를 유지하고 계약 테스트로 검증했다. Controller의 mapping/parameter annotation은 동일하다.
- **Remaining Issues:** 플랫폼 Service의 user Repository 직접 참조, 기존 검색 SQL/보안 테스트 부채는 남아 있다.
- **Next Phase:** Phase 2, 즐겨찾기 조회 경계 정리.

### Phase 2 Result

- **Changed:** FavoriteLookupService로 익명 사용자와 즐겨찾기 ID 조회를 이동했다. 플랫폼 도서 Service는 이 Service를 호출하며 FavoriteService의 기존 회원 검증/쓰기 정책은 유지했다.
- **Improved:** book의 user.repository 직접 참조를 제거했고 플랫폼 도서 Service의 주입 객체가 4개에서 3개가 됐다. 인터페이스/adapter는 추가하지 않았다.
- **Tests:** `compileJava test check` 성공, 48건, 실패/오류/skip 0. 새 조회 테스트는 익명 시 저장소 미호출과 중복 ID/순서 보존을 확인했다. Phase 1의 즐겨찾기 응답 테스트도 통과했다. compiler, 계층 의존성 검색, 계약 파일 불변 점검과 `git diff --check` 통과.
- **Regression:** NONE (검증 범위 내). 기존 null/false/true와 조회 조건·횟수를 유지했다. 새로운 readOnly 조회 transaction은 기존 플랫폼 조회 transaction에 참여한다.
- **Remaining Issues:** user Entity의 book 관계 및 단순 도메인 간 조회는 유지한다. 도메인 전체의 순환 import를 제거했다고 주장하지 않는다.
- **Next Phase:** Phase 3, OAuth callback 테스트와 미사용 cookie 구현 삭제.

### Phase 3 Result

- **Changed:** OAuth success handler의 호출 없는 private addRefreshTokenCookie, 전용 상수/import와 불필요해진 properties 필드를 삭제했다. 생성자 signature와 실제 callback 본문은 유지했다.
- **Improved:** 쿠키 생성 구현은 기존 RefreshTokenCookieManager에만 남는다. handler는 162줄에서 114줄이 됐다.
- **Tests:** 삭제 전 callback characterization 3건 통과. 삭제 후 `compileJava test check` 성공, 전체 51건, 실패/오류/skip 0. cookie 속성, 단일 발급, redirect, credential 저장 실패, authorized client 부재를 확인했다.
- **Regression:** NONE (검증 범위 내). callback과 남은 helper 3개의 본문을 원본과 대조해 동일함을 확인했다. 삭제 대상은 private이고 Bean/listener/scheduler/serialization annotation이나 source/resources/config/test의 호출 참조가 없다. compiler와 `git diff --check`, 신규 파일 공백 검사도 통과했다.
- **Remaining Issues:** 이 callback 테스트는 JWT 유효성/Lua 회전/탈퇴 transaction 테스트를 대체하지 않는다.
- **Next Phase:** Phase 4, 최종 구조·계약·검증 결과 대조.

### Phase 4 Result

- **Changed:** 전체 main/test 파일 수와 큰 클래스, 의존성 방향, 보호할 계약 파일 및 신규 파일을 다시 점검하고 문서에 전후 비교를 기록했다.
- **Improved:** 현재 탐색 경로와 허용된 경계 예외, 후속 고위험 작업의 선행 조건을 명시했다.
- **Tests:** 마지막 코드 변경 후 Phase 3에서 전체 51건이 실제 실행되어 통과했다. 최종 `compileJava test check`도 성공했으며 코드가 같아 UP-TO-DATE였다. 별도 정적 분석기는 없으며 compiler 진단, source 의존성 검사, 계약 diff, tracked/untracked 파일의 공백 검토를 수행했다.
- **Regression:** NONE (검증 범위 내). DTO/Entity/Repository/resources/보안 설정/Gradle/배포 설정은 변경하지 않았다. BookController의 HTTP annotation은 원본과 동일하다. 스케줄/Lua/캐시 키와 외부 Client도 그대로다.
- **Remaining Issues:** 아래 기술 부채를 보존했다. 운영 데이터 전체 또는 실제 소셜 공급자까지 검증했다고 주장하지 않는다.
- **Next Phase:** 이번 안전한 실행 범위는 완료. 후속 작업은 아래 테스트 선행 조건을 충족한 뒤 별도 변경 단위로 수행한다.

## Final Review — Before / After

| 관점 | Before | After |
|---|---|---|
| Architecture | 도메인 중심 모놀리스, 일반/플랫폼 도서 흐름이 같은 Service | 모놀리스·패키지 체계 유지, API별 Service를 명확히 선택 |
| Large Classes | BookSearchService 474줄, OAuth success handler 162줄 | BookSearchService 147줄 + PlatformBookService 345줄, OAuth handler 114줄. Repository 431줄·TokenProvider 271줄은 유지 |
| Responsibility | 일반 검색도 platform/favorite 조회와 같은 변경 단위 | 일반 조회는 BookRepository만, 플랫폼 조회는 플랫폼/소장/관련 도서를 담당, 즐겨찾기 조회 정책은 user가 소유 |
| Dependency | 도서 Service가 Repository 4개에 직접 의존 | 일반 Service 1개, 플랫폼 Service는 Book/Platform Repository와 FavoriteLookupService 3개. book → user.repository 제거 |
| Duplication | 실제 CookieManager 외에 미사용 cookie 생성 구현 존재 | 호출 없는 구현 제거. ID 필터 정책은 BookSearchFilters 한 곳. 의미가 다른 소장 query/DTO wrapper는 유지 |
| Testability | 파일 4개, 실행 32건. 검색 edge case·OAuth callback 전용 증거 부족 | 파일 7개, 실행 51건. 검색 14건·lookup 2건·callback 3건 추가, MySQL/Redis 기존 회귀 유지 |
| Readability | 플랫폼 선택·검색·상세·관련 도서가 일반 조회와 혼재 | 플랫폼 선택에 이름 부여, 수정한 주요 메서드의 목적 주석, 실제 경로/예외를 설명하는 문서 추가 |

main Java 파일은 163 → 166개, 전체 줄 수는 8,960 → 8,975다. 줄 수 감소 대신 실제 책임 경계와 테스트 가능성을 개선했다. 신규 production 타입은 PlatformBookService, FavoriteLookupService, package-private BookSearchFilters 3개이며 새 interface/abstract class/library는 없다.

```text
GET /api/book/search, /api/book/{id}
  → BookController → BookSearchService → BookRepository

GET /api/book/search/platforms, /api/book/platforms/{id}/books/{sourceId}
  → BookController → PlatformBookService → BookRepository / PlatformRepository
                                        → FavoriteLookupService → Favorite repositories

OAuth success callback
  → credential 저장 → AuthService → RefreshTokenCookieManager → configured redirect
```

### Remaining Technical Debt

1. **HIGH:** JWT/Redis 회전의 동시성·grace·재사용/세션 폐기 테스트와 탈퇴의 rollback·lock·retry 테스트가 부족하다. 인증/탈퇴 정책·transaction은 변경하지 않았다.
2. **MEDIUM:** SearchKeywordService의 Redis lifecycle/config/집계/HTTP 보조 책임이 혼재한다. 구성별 격리 테스트를 먼저 보강해야 한다.
3. **MEDIUM:** global.security/redis의 도메인 결합과 auth/user의 양방향 패키지 참조는 남아 있다. 이번 작업은 모든 도메인의 독립화를 목표로 하지 않는다.
4. **MEDIUM:** 실제 application 설정에 의존하는 MySQL/context 테스트, 테스트 시작 시 scheduler/외부 연동 가능성을 분리할 필요가 있다. 현재 실행 환경에서는 모든 테스트가 통과했다.
5. **LOW:** 기존 파일의 빈 Javadoc, 한 줄 분기와 naming 편차, public 미사용 후보는 다음 관련 수정 때 검토한다. 근거 없는 삭제와 전역 formatting은 하지 않았다.
6. **별도 정책/성능 작업:** 오류 JSON·searchType·canonicalBookId 명칭, query 계측과 복수 플랫폼 summary 전제는 [FUTURE_IMPROVEMENTS.md](FUTURE_IMPROVEMENTS.md)에 기록했다.

검증 범위는 저장소의 자동 테스트와 정적 diff 검토다. 실제 카카오/네이버 로그인을 호출하거나 운영 데이터를 수정하는 수동 테스트·배포·커밋은 수행하지 않았다.
