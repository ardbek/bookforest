# Bookforest Architecture Rules

분석일: 2026-09-18. 외부 Reference 없이 현재 `src/main`, `src/test`, Gradle 설정과 배포 진입점을 기준으로 작성했다. 이 문서는 목표와 허용된 기존 예외를 함께 설명한다. 분석 근거와 단계별 작업은 [REFACTORING_PLAN.md](REFACTORING_PLAN.md)에 기록한다.

## 1. Project Architecture

Java 17 / Spring Boot 4.0.5의 단일 애플리케이션이다. Spring MVC, JPA/MySQL, Redis, Spring Security OAuth2/JWT를 사용한다. Gradle의 `bootJar`가 `app.jar`를 만들며 WAR 진입점도 존재한다. 모놀리스를 유지한다.

```text
com.ebook.bookforest
├── domain
│   ├── book             도서 검색, 플랫폼별 도서 상세와 소장 정보
│   ├── library          공개 도서관 조회
│   ├── platform         플랫폼 조회
│   ├── category         표준 분류 조회와 매핑 Entity
│   ├── user             소셜 회원, 즐겨찾기, 탈퇴와 연동 해제
│   ├── auth             로그인 세션, 토큰 재발급, 로그아웃
│   ├── ranking          외부 인기/급상승 도서와 캐시
│   ├── searchkeyword    검색어 집계, 인기 검색어와 요청 제한
│   └── collection       수집 실행 이력 Entity/enum
└── global
    ├── config           JPA, 실행기, Hibernate 함수 등록
    ├── entity           생성/수정 시각
    ├── exception        공통 오류 응답
    ├── redis            Redis 설정과 기존 토큰 저장 구현
    └── security         인증 필터, OAuth 연동, JWT, 암호화
```

`domain`이라는 한 단계는 탐색에 문제가 없으므로 제거하지 않는다. Clean/Hexagonal Architecture, CQRS, 신규 이벤트 체계, 계층별 interface를 도입하지 않는다.

## 2. Domain Boundary

| 영역 | 소유하는 책임 | 협력 대상과 주의점 |
|---|---|---|
| book | 원천 도서와 검색·소장 조회 | platform 정보, user 즐겨찾기 조회, searchkeyword 기록 |
| library / platform / category | 각 목록과 저장 모델 | 단순 조회를 위해 무조건 Service wrapper를 만들지 않는다 |
| user | 회원 상태, 즐겨찾기 쓰기/읽기, 탈퇴·연동 해제 작업 | auth 세션 폐기, 외부 unlink client |
| auth | 세션 발급·회전·폐기 | user 활성 여부, JWT, Redis 저장소 |
| ranking | 외부 순위 수집·도서 연결·스냅샷 | book 조회와 Redis; 외부 호출을 DB transaction에 넣지 않는다 |
| searchkeyword | 정규화·집계·노출·요청 제한 | HTTP와 Redis의 장애 격리 정책을 보존한다 |
| collection | 수집 이력 매핑 | 여기에는 수집 실행 Service/Controller가 없다. 새로 만들지 않는다 |

Entity의 도메인 간 FK 관계와 Service 의존성은 별개다. `book ↔ user`, `auth ↔ user ↔ global.security`의 패키지 참조를 순환 Bean 의존성으로 단정하지 않는다. 실제 생성자와 호출 경로를 확인한다.

## 3. Package Rule

기존 `domain/<feature>/{controller,service,repository,entity,dto,enums,exception}`를 유지한다. 파일 5~6개의 `ranking`, `searchkeyword`는 평평한 구조로 충분하다. `api/application/domain/infrastructure`를 일괄 추가하지 않는다.

새 코드는 변경 이유가 있는 기능에 둔다. 소규모 공용 검색 필터 정책은 `book.service` 내부에서 공유하고 `global.util`로 올리지 않는다. `user` 안의 즐겨찾기는 먼저 조회 경계를 개선하며 관련 Entity/DTO 전부를 이동하지 않는다.

## 4. Dependency Rule

기본 흐름은 `Controller → Service → Repository / 외부 Client`다. Controller → Repository, Repository → Service, Entity → Controller/Service 의존은 추가하지 않는다. 생성자 주입을 사용한다.

다른 도메인의 상태 변경이나 정책은 해당 Service를 통한다. 현재 `LibraryService → PlatformRepository`, `RankingService → BookRepository`, `FavoriteService → BookRepository/LibraryRepository`는 짧고 명확한 조회이므로 허용한다. 이를 없애기 위한 단순 위임 계층은 만들지 않는다.

book의 즐겨찾기 표시처럼 익명 사용자 처리를 포함한 조회 책임은 user의 `FavoriteLookupService`가 소유한다. 기존 `FavoriteService`의 활성 회원 검증을 그 경로에 섞지 않는다. Security의 `UserRepository` 접근과 `RefreshTokenStore` 구현 위치는 기존 예외이며, 인증 계약 테스트 없이 변경하지 않는다.

## 5. Controller Rule

HTTP 파라미터·principal·cookie/header 처리와 Service 호출, 응답 반환에 한정한다. `LibraryController`, `StandardCategoryController`가 내부 기준이다. HTTP Origin 검증과 cookie 갱신/만료는 HTTP 경계 책임이므로 AuthController에 있는 것만으로 위반이 아니다.

BookController의 검색 결과 후 `searchKeywords.record` 호출은 짧은 결과 전달이다. 집계 허용 여부는 SearchKeywordService에 둔다. 이를 위해 또 하나의 orchestration Service를 추가하지 않는다.

## 6. Service Rule

일반 도서 검색/상세는 `BookSearchService`, 플랫폼별 검색/상세는 `PlatformBookService`가 맡는다. 두 API의 필터, 즐겨찾기, 소장 정보 정책이 다르므로 하나의 boolean flag로 합치지 않는다. 검색 필터 선정처럼 이름이 의도를 드러내는 단계만 메서드로 분리한다.

길이나 주입 개수만으로 분리하지 않는다. `UserWithdrawalService`의 8개 협력 객체는 하나의 탈퇴 흐름에 필요하다. `SocialUnlinkTaskProcessor`와 `SocialUnlinkTaskStateService`의 분리는 외부 호출과 transaction 경계를 나누므로 유지한다.

## 7. Repository Rule

JPA 조회, query parameter 바인딩, projection에 집중한다. JPQL constructor expression이 참조하는 projection의 FQCN은 이동 시 문자열 참조까지 확인한다. Query method 이름은 길더라도 조건이 명확하면 유지한다.

`findLibraryHoldings`는 AVAILABLE 조건과 플랫폼 정렬을 사용한다. `findAllLibraryHoldings`는 다른 상태도 포함하고 원천 플랫폼 일치를 요구한다. 비슷한 SELECT라는 이유로 합치지 않는다. 동적 검색의 FULLTEXT/LIKE, OR 조건, 정렬, pagination/count를 변경하지 않는다.

`EntityGraph`와 묶음 projection 조회를 내부 기준으로 삼는다. N+1 여부는 SQL 계측으로 확인하며 이번 구조 개선에 임의의 fetch join/성능 변경을 끼워 넣지 않는다.

## 8. Entity Rule

기존 `@Getter`, protected 기본 생성자, builder와 명시적 변경 메서드를 따른다. Entity에 일반 `@Setter`나 `@Data`를 추가하지 않는다. `User.withdraw`, `SocialUnlinkTask.startProcessing/markSucceeded/scheduleRetry`처럼 상태 변경 목적을 이름으로 표현한다.

단순 Persistence 모델도 허용한다. table/column/enum 저장값, FK, index, nullable, 기본값, builder 계약은 유지한다. 호출이 안 보이는 Entity도 Hibernate scanning과 schema validation 대상일 수 있다.

## 9. DTO Rule

HTTP 응답은 기존 Java record를 기준으로 한다. Entity/Repository projection은 Service 안에서 응답 DTO로 변환한다. 단순 변환은 기존 `from`/`of`를 사용하며 repository 호출을 DTO에 넣지 않는다.

`books`, `libraries`, `favoriteBooks`, `favoriteLibraries`의 page wrapper는 JSON 이름이 다르다. 공통 Page DTO 도입으로 응답 모양을 바꾸지 않는다. `canonicalBookId`는 현재 BookOrigin ID를 전달하는 외부 필드이므로 이름의 부정확함을 리팩토링으로 고치지 않는다. null과 false 차이도 계약이다.

## 10. Exception Rule

HTTP business 오류는 기존 `BusinessException + 도메인 ErrorCode + GlobalExceptionHandler`를 재사용한다. 필터는 `ErrorResponseWriter`를 사용한다. 설정 오류·외부 응답 파싱 실패까지 BusinessException으로 바꾸지 않는다.

현재 검색 interceptor의 `{message}` 오류, 인기 검색어의 빈 400 응답, OAuth redirect 오류는 기존 계약이다. `{code,message}`로 일괄 통일하지 않는다. OAuthService의 IllegalStateException/IllegalArgumentException은 CustomOAuth2UserService가 별도 오류로 변환하므로 타입 변경 전에 이 경로를 테스트한다.

## 11. Validation Rule

HTTP 형식/범위는 Controller나 HTTP interceptor, 업무 규칙은 Service/Entity가 담당한다. 현행 RequestParam 기반 GET에 형식만 맞추려는 Request DTO를 추가하지 않는다. 현재 없는 validation 라이브러리도 추가하지 않는다.

일반 검색은 알 수 없는 searchType을 ALL로 취급하지만 플랫폼 검색은 명시적인 ALL도 오류 처리한다. 유효하지 않은 ID 목록 원소는 제거한다. 이 현재 동작을 테스트로 고정하고 별도 기능 합의 없이 엄격하게 바꾸지 않는다.

## 12. Transaction Rule

DB 읽기 Use Case는 Service public method의 `@Transactional(readOnly = true)`, 쓰기는 업무 경계의 `@Transactional`을 사용한다. Controller/private method에 transaction을 추가하지 않는다. 프록시를 우회하는 self invocation으로 새 transaction을 기대하지 않는다.

탈퇴 작업 생성의 MANDATORY, 작업 상태의 REQUIRES_NEW, AFTER_COMMIT listener와 실행기 이름을 유지한다. 외부 unlink 호출은 DB transaction 밖에 둔다. 회원탈퇴 중 Redis 세션 폐기는 DB와 원자적이지 않으므로 순서만 바꾸어 문제를 해결한 것으로 간주하지 않는다.

## 13. Common Code Rule

`global`은 여러 기능에 필요한 기술 책임을 위한 영역이다. 현재 global.security의 OAuth client와 global.redis의 인증 저장 구현은 이미 도메인에 결합한 예외다. 전체 이동보다 호출 경계와 테스트를 먼저 확보한다.

SearchKeywordRules의 정규화는 book 검색에서도 쓰는 현재 검색 정책이므로 기존 소유 위치를 유지한다. LibraryService의 trim은 NFKC 기반 도서 정규화와 의미가 다르므로 합치지 않는다. Cookie는 `RefreshTokenCookieManager` 한 곳에서 생성한다.

## 14. Testing Rule

수정 전 호출자·Bean·resources·테스트를 확인한다. 동작이 있는 부분은 characterization test를 먼저 실행하고, 리팩토링 뒤 같은 테스트를 실행한다. JUnit 5 / Mockito와 현재의 실제 Redis 테스트 방식을 재사용한다. 신규 프레임워크를 추가하지 않는다.

기준 명령은 `./gradlew compileJava test check --console=plain`이다. 기준선 재실행은 `./gradlew test --rerun-tasks --console=plain`으로 확인한다. 현재 별도 Checkstyle/PMD/SpotBugs task는 없다. 따라서 `check` 성공을 독립 정적 분석기 통과라고 보고하지 않는다. compiler 진단, 의존성 검색, 계약 diff, `git diff --check`를 함께 검토한다.

BookforestApplicationTests는 실제 설정의 MySQL와 application context를 사용하고 쓰기 테스트는 rollback한다. SearchKeywordTest는 일회용 redis-server를 실행하며 바이너리가 없으면 skip될 수 있다. 성공뿐 아니라 XML의 failures/errors/skipped를 확인한다. 외부 설정 값이나 비밀키를 보고서에 복사하지 않는다.

## 15. Naming Convention

도메인명 + 역할명을 따른다. Controller, Service, Repository, Response, Condition, Client를 구분한다. `searchBooks`, `getPlatformBook`, `findBookOriginIds`처럼 대상과 동작을 쓴다. 기존 JpaRepository의 CRUD 명명은 유지한다.

현재 `SearchKeywordService.get/refresh`, `SocialUnlinkTaskProcessor.process`, `SocialUnlinkTasksCreatedListener.handle`은 문맥 의존적이다. 해당 책임을 실제 수정할 때 이름을 개선하고 전체 호출자를 함께 변경한다. 이름 교정만을 위한 전역 diff는 만들지 않는다.

## 16. Method Comment Rule

직접 작성·수정하는 주요 메서드 위에 한 줄 목적 주석을 둔다. 예: `// 플랫폼별 도서를 검색하고 소장 정보와 즐겨찾기를 함께 반환한다.`

빈 @param/@return 문서, 코드 동작을 그대로 반복하는 주석은 수정 범위에서 정리한다. 단순 생성자/getter/setter, CRUD 선언, 단순 위임에는 주석을 강제하지 않는다. Override는 HTTP callback, 상태 전이 등 특별한 책임이 있을 때 목적을 적는다.

## 17. Anti-patterns

- 클래스 줄 수만 보고 분리하거나 작은 기능에 계층 4개를 강제하기.
- 도메인 간 단순 조회마다 interface/adapter를 추가하기.
- 다른 응답/오류/검색 규칙을 같은 이름으로 합쳐 동작을 바꾸기.
- 미사용처럼 보이는 Entity, scheduler, listener, Hibernate SPI를 삭제하기.
- Redis key/TTL, Lua 순서, 인증 예외, 외부 API payload를 구조 변경과 함께 수정하기.
- 민감한 token, 원문 검색어, API key, 공급자 응답 전체를 로그로 남기기.
- 테스트를 생략하거나 기존 실패/skip을 리팩토링 성공으로 표시하기.
- 이미 안전한 짧은 Service를 늘리거나 파일 전체를 재포맷하기.

## Internal Reference

| 파일 | 채택할 점 | 그대로 복제하지 않을 점 |
|---|---|---|
| `domain/library/controller/LibraryController.java` | HTTP → Service 단일 흐름 | 비어 있는 Javadoc 태그 |
| `domain/category/service/StandardCategoryService.java` | 읽기 transaction과 명확한 DTO 변환 | 모든 기능을 같은 크기로 억지 분리 |
| `domain/platform/dto/PlatformResponse.java` | record와 명시적 from 변환 | 응답 필드의 비즈니스 의미는 기능별 유지 |
| `domain/user/entity/SocialUnlinkTask.java` | 허용 상태 검사와 명시적 상태 전이 | 모든 Entity를 풍부한 모델로 만들기 |
| `domain/user/service/SocialUnlinkTaskProcessor.java` / `SocialUnlinkTaskStateService.java` | 외부 통신과 잠금 transaction 분리 | 단순 CRUD에도 이만큼의 Service 추가 |
| `global/exception/GlobalExceptionHandler.java` | HTTP business 오류 공통 처리 | 기존 별도 오류 계약까지 일괄 교체 |
| `domain/ranking/RankingService.java` | 외부 장애 때 이전 snapshot 보존, 안전한 로그 | get/refresh 같은 이름을 새 코드에 남발 |
| `src/test/.../domain/ranking/RankingTest.java` | 외부 계약·장애·순서·cache 보존 중심 assertions | 하나의 테스트 파일에 모든 관심사 누적 |
| `src/test/.../domain/searchkeyword/SearchKeywordTest.java` | 고정 Clock, 실제 Lua/동시성, 일회용 Redis | 단순 normalization 테스트까지 Redis 시작에 종속 |
