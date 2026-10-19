# BookForest 리팩토링 계획

> 작성일: 2026-10-15
>
> [코딩 컨벤션](./coding_convention.md)을 기존 코드에 적용하는 작업의 순서, 진행 상황, 확인이 필요한 사항을 관리한다.
> 컨벤션은 계속 지킬 원칙이고, 이 문서는 작업 이력이다. 작업이 진행되면 이 문서를 갱신한다.

---

## 1. 진행 방식

1. **0단계, 테스트 인프라**: 리팩토링 전에 테스트 의존성, 테스트 DB 스키마, 로컬·CI 실행 환경을 확보한다. 운영과 동일하게 맞춘 로컬 MySQL을 스키마와 설정의 기준으로 쓴다.
2. **기준 구현**: 플랫폼 조회 기능을 컨벤션에 맞춰 먼저 리팩토링한다. 이후 작업은 이 코드를 예시로 삼는다.
3. **도메인 순서**: 플랫폼 → 도서관 → 카테고리 → 즐겨찾기 → 인증과 사용자 → 소셜 로그인과 연동 해제 → 도서 검색 → 랭킹, 검색어 → 공통 설정 → 보안 순으로 작은 기능부터 진행한다.
4. **기능 단위 순서**: 테스트 확보 → Controller → Service → Repository → DTO, VO → Entity 순으로 고친다.
5. **커밋 단위**: 검토하기 좋은 크기로 나눈다. 큰 기능은 테스트 확보, 구조 변경, 문서 정리를 각각 커밋할 수 있다.
6. **동작 변경 분리**: 구조 변경 커밋에는 동작 변경을 섞지 않는다. 동작 변경은 4장 목록에 올리고 별도 커밋으로 진행한다.

---

## 2. 진행 상황

| 단계 | 대상 | 상태 | 비고 |
|---|---|---|---|
| 0-A | 단위·Controller 테스트 환경 | 완료 | WebMvc 테스트 모듈 추가, 기준선 기록 |
| 0-B | MySQL 테스트 환경 | 완료 | Testcontainers, 스키마 파일, 실제 자원 접근 차단 |
| 1 | 플랫폼 | 완료 | 기준 구현, 6장 참고 |
| 2 | 도서관 | 완료 | 7장 참고 |
| 3 | 카테고리 | 완료 | 8장 참고 |
| 4 | 즐겨찾기 | 완료 | 9장 참고 |
| 5 | 인증과 사용자 | 완료 | 10장 참고 |
| 6 | 소셜 로그인과 연동 해제 | 완료 | 11장 참고 |
| 7 | 도서 검색 | 완료 | 13장 참고 |
| 8 | 랭킹, 검색어 | 완료 | 14장 참고 |
| 9 | 공통 설정 | 완료 | 15장 참고, 동작 변경 3건은 결정 대기(4장) |
| 10 | 보안 | 완료 | 16장 참고 |

상태 값은 `대기`, `진행 중`, `완료`, `미검증 완료`(코드는 바꿨지만 필요한 검증을 못 한 상태)를 쓴다.

### 2.1 남은 단계의 범위 (2026-10-15)

| 단계 | 대상 파일 | 주요 작업 |
|---|---|---|
| 5 인증과 사용자 | `domain/auth` 전체 12개, `domain/user`의 `User`, `UserController`, `UserRepository`, `UserWithdrawalService`, `UserErrorCode`, `UserException`, `UserRole` | `AuthService`의 `UserRepository` 직접 사용을 사용자 Service로 전환. 탈퇴 시 토큰 삭제를 탈퇴 이벤트로 분리(동작 변경, 별도 커밋). `User` 엔티티 생성 방식 정리 |
| 6 소셜 로그인과 연동 해제 | `domain/user`의 `OAuthService`, `SocialAccount`, `SocialOAuthCredential*`, `SocialUnlink*`, `SocialProvider`, `ProviderTokenType`, `SocialUnlinkInProgressException` 등 19개 | 엔티티 생성 방식과 주석 정리. 연동 해제의 상태 전이와 재시도 구조는 컨벤션 11.2의 기준 예시라 동작을 바꾸지 않고 형식만 맞춤 |
| 7 도서 검색 | `domain/book` 28개 | 474줄 `BookSearchService` 정리. `PlatformRepository`, 즐겨찾기 Repository 직접 사용 전환. 도서와 사용자 도메인 순환 해소 방식 결정(9.3). `BookOrigin` 유니크 제약 불일치 결정(10장). FULLTEXT Repository 테스트 |
| 8 랭킹, 검색어 | `domain/ranking` 6개, `domain/searchkeyword` 5개 | 평면 패키지를 계층 구조로 이동. 한 줄 압축 코드가 많은 `SearchKeywordService` 정리. `SearchKeywordRules`의 개인 태그 주석 정리 |
| 9 공통 설정 | `global/config`, `global/entity`, `global/exception`, `global/redis` 12개와 애플리케이션 클래스 2개 | `GlobalExceptionHandler` 정리. 동작 변경 목록(4장)의 필수 파라미터 400 응답, `errors` 배열, 4xx 로그 레벨은 이 단계에서 별도 커밋으로 진행 |
| 10 보안 | `global/security` 37개 (JWT, OAuth 처리기, 토큰 암호화, 소셜 연동 해제 클라이언트) | 주석과 형식 정리. 인증 흐름과 쿠키, 토큰 정책은 바꾸지 않음. 플랫폼 단계에서 넘긴 보안 설정 단위 테스트(비로그인 허용 경로 검증) 추가 |

- 5, 6단계는 같은 `domain/user` 패키지지만 결합도가 다른 두 묶음이라 나눈다. 회원 탈퇴가 소셜 연동 해제 작업을 만들기 때문에 5단계를 먼저 한다.
- 9, 10단계의 global 패키지는 모든 도메인이 의존하므로, 도메인 리팩토링을 마친 뒤 진행해 변경 범위를 좁힌다.

---

## 3. 0단계: 테스트 인프라

### 3.1 테스트 DB 기준 (2026-10-15, 로컬 MySQL에서 확인)

운영과 동일하게 맞춘 로컬 MySQL을 기준으로 삼았다.

| 항목 | 값 |
|---|---|
| MySQL 버전 | 8.0.42, 테스트 이미지 `mysql:8.0.42` |
| 문자셋, collation | `utf8mb4`, `utf8mb4_0900_ai_ci` |
| FULLTEXT 인덱스 | `book_origin.ft_book_origin_search (title_norm, author_norm, publisher_norm) WITH PARSER ngram` |
| ngram 설정 | `ngram_token_size=2` (기본값) |
| 기타 | `sql_mode`, 격리 수준(`REPEATABLE-READ`), 대소문자 설정 모두 MySQL 8.0 기본값 |

- 스키마 파일: `src/test/resources/db/bookforest_schema.sql` (19개 테이블, 데이터 없음)
- 컨테이너 설정: `src/test/java/com/ebook/bookforest/support/MySqlTestContainerConfig.java`
- 스키마 파일은 컨테이너의 `/docker-entrypoint-initdb.d`에서 mysql 클라이언트로 실행한다. ngram 파서 지정이 `/*!50100 ... */` 조건부 주석 안에 있어서, 주석을 제거하는 Java 스크립트 실행기로 실행하면 파서 설정이 사라진다.

### 3.2 완료한 작업

- [x] `build.gradle`에 테스트 의존성 추가: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-mysql` (Testcontainers 2.0.4)
- [x] 로컬 MySQL에서 데이터 없이 DDL 추출, 테스트 스키마 파일로 저장
- [x] FULLTEXT 검증 테스트 `BookSearchRepositoryFullTextTest` 추가. 커밋된 데이터는 단어 중간의 두 글자 키워드로 검색되고, 커밋 전 데이터는 검색되지 않음을 확인
- [x] `BookforestApplicationTests`를 H2 설정에서 Testcontainers MySQL로 전환. 기대값은 바꾸지 않음
- [x] 테스트 JVM에서 실제 자원 접근 차단: `MYSQL_HOST`, `REDIS_HOST`를 접속 불가 주소로, `DATA4LIBRARY_API_KEY`를 빈 값으로 지정. 시스템 프로퍼티라 `.env`와 CI 환경 변수보다 우선함
- [x] 전체 테스트 기준선 기록(3.3)

### 3.3 테스트 기준선

| 시점 | 결과 | 비고 |
|---|---|---|
| 작업 전 | 컴파일 실패 | 미추적 파일 `BookDetailAssemblyTest`가 바뀐 `BookSearchService` 생성자를 따라가지 못함 |
| 컴파일 수정 후 | 169개 중 3개 실패 | `BookforestApplicationTests` 3개, H2 드라이버 없음 |
| 0단계 완료 후 | 169개 모두 통과 | `./gradlew test`, 로컬 실행 |

- `BookDetailAssemblyTest`는 생성자에 빠진 Repository 3개를 mock으로 넘기도록만 고쳤다. 검증 내용은 바꾸지 않았다.

### 3.4 남은 사항

- [ ] CI 러너에서 Testcontainers(Docker 소켓 접근) 동작 확인. 로컬에서만 검증함
- [x] CI 테스트 단계의 `MYSQL_HOST`, `MYSQL_PORT` 삭제(2026-10-19). 예전 CI 테스트는 운영 MySQL에 접속해 DB 상태와 스키마 일치를 함께 확인하는 효과가 있었으나, 9월 H2 전환과 0단계 차단 설정으로 이미 사라진 기능. 별도 DB 확인 단계는 두지 않기로 결정. 필요하면 배포 후 앱 상태 확인(`/actuator/health`) 단계 추가 권장
- [x] Redis 컨테이너 설정 추가(`RedisTestContainer`, 8단계와 9단계에서 사용)

---

## 4. 동작 변경 목록

구조 리팩토링과 분리해서 별도 커밋으로 진행할 변경이다. 진행 전에 영향을 확인한다.

| 변경 | 영향 | 상태 |
|---|---|---|
| 회원 탈퇴 시 리프레시 토큰 삭제를 탈퇴 이벤트로 분리 | 토큰 삭제 시점이 탈퇴 커밋 이후로 바뀜. 재발급은 비활성 사용자를 이미 막고 있음 | 완료(2026-10-19, 16.6) |
| 오류 응답에 검증 실패용 `errors` 배열 추가 | 응답 필드 추가. 기존 필드는 그대로 | 완료(2026-10-19, 16.6) |
| `GlobalExceptionHandler`의 4xx 비즈니스 예외 로그를 `warn`에서 `info`로 변경 | 운영 로그 레벨 변경 | 완료(2026-10-19, 16.6) |
| 플랫폼 도서 상세의 콘텐츠 종류 중복 처리 | 같은 플랫폼 도서 ID의 전자책과 오디오북이 함께 있으면 상세 조회가 예외로 실패(13.3). 요청에 콘텐츠 종류를 받거나 우선순위를 정하면 API나 응답이 바뀜 | 완료(2026-10-19, 16.6, 전자책 우선) |
| `SearchKeyword` VO 도입 | 보류. 공백 제거만 하는 정규화는 도서관 한 곳뿐이고, 도서 검색은 유니코드 정규화와 소문자 변환을 하는 다른 규칙을 씀. 두 규칙을 합치면 동작이 바뀜 | 종료(2026-10-19, 도입하지 않기로 결정) |
| 필수 요청 파라미터 누락 시 500 대신 400 응답 | `GlobalExceptionHandler`가 `MissingServletRequestParameterException`을 처리하지 않아 `/api/library`에 `platformId`가 없으면 500(`internal_server_error`)으로 응답함. 400(`invalid_request`)으로 바꾸면 상태 코드가 바뀜 | 완료(2026-10-19, 16.6) |
| 비로그인 허용 경로의 토큰 오류를 비로그인 요청으로 처리 | 프론트가 로그인 상태에서 플랫폼별 검색에도 토큰을 붙이고, 이 호출은 401에 재발급하지 않아 Access Token 만료 시 검색이 실패했음. 허용 경로에서는 형식 오류, 만료, 위조, 비활성 사용자 토큰을 무시하고 비로그인으로 응답(즐겨찾기 표시 false). 로그인 필요 경로의 오류 코드는 그대로. 허용 경로 목록은 `PublicRequestMatcher` 한 곳에서 관리 | 완료(2026-10-15, 사용자 결정) |

---

## 5. 도메인 경계 전환 대상

컨벤션 2장에 따라 다른 도메인의 Repository를 직접 쓰는 코드를 전환한다. 2026-10-15 기준 8곳이다.

| 호출하는 쪽 | 직접 쓰는 Repository | 용도 | 전환 방식 |
|---|---|---|---|
| `LibraryService` | `PlatformRepository` | 플랫폼 존재, 도서관 지원 여부 확인 | `PlatformService.getPlatformInfo` (완료) |
| `BookSearchService` | `PlatformRepository` | 플랫폼 목록, 단건 확인 | `PlatformService.getPlatformInfos`, `findPlatformInfo` (완료) |
| `BookSearchService` | `FavoriteBookRepository` | 즐겨찾기 도서 ID 집합 | 도서 도메인의 읽기 전용 조회 `BookSearchRepository.findFavoriteBookOriginIds` (완료, 9.3 결정) |
| `BookSearchService` | `FavoriteLibraryRepository` | 즐겨찾기 도서관 ID 집합 | 도서 도메인의 읽기 전용 조회 `BookSearchRepository.findFavoriteLibraryIds` (완료, 9.3 결정) |
| `FavoriteService` | `BookRepository` | 즐겨찾기 연관관계 설정 | `BookService.getBookOriginReference` (완료) |
| `FavoriteService` | `LibraryRepository` | 즐겨찾기 연관관계 설정 | `LibraryService.getLibraryReference` (완료) |
| `AuthService` | `UserRepository` | 활성 사용자 확인 | `UserService.findActiveUserInfo` (완료) |
| `UserWithdrawalService` | `RefreshTokenStore` | 탈퇴 시 토큰 삭제 | 탈퇴 이벤트 + 인증 도메인 리스너 (완료, 16.6) |
| `JwtAuthenticationFilter` | `UserRepository` | 요청마다 활성 사용자 확인 | `UserService.isActiveUser` (완료, 16장) |

> 즐겨찾기 전환으로 사용자 도메인이 도서 도메인을 호출하게 됐다. 도서 검색이 즐겨찾기 ID를 얻으려고 즐겨찾기 Service를 호출하면 도서와 사용자 도메인 사이에 순환이 생긴다. 도서 검색 단계에서 결정이 필요하다(9.3).

---

## 6. 플랫폼 기준 구현 (2026-10-15)

다음 도메인을 리팩토링할 때 이 파일들을 예시로 삼는다.

| 구분 | 파일 | 기준이 되는 점 |
|---|---|---|
| Controller | `PlatformController` | 외부 파라미터명 유지(`isSubscribe`), 내부 변수명 규칙 적용 |
| Service | `PlatformService` | 메서드 단위 읽기 전용 트랜잭션, 다른 도메인용 `getPlatformInfo` |
| DTO | `PlatformResponse`, `PlatformListResponse`, `PlatformInfo` | `@JsonProperty`로 JSON 필드명 유지, `from` 정적 팩토리 |
| 예외 | `PlatformErrorCode`, `PlatformException` | Lombok으로 줄인 오류 코드, 자기 도메인 코드만 받는 예외 |
| Entity | `Platform` | 생성자 `@Builder`, DB 기준 필수값 판단 |
| 테스트 | `PlatformServiceTest`, `PlatformControllerTest` | 동작 고정 후 리팩토링, `excludeFilters`를 쓴 `@WebMvcTest` |

### 6.1 결과

- 동작 유지: API 응답과 파라미터, DB 매핑 변경 없음. Controller 테스트로 JSON 필드 5개와 이름, 자료형을 고정함
- 테스트: 리팩토링 전 새 테스트 7개 통과 확인, 리팩토링 후 전체 178개 통과(`./gradlew test`, 로컬)
- 엔티티 `id`를 빌더에서 빼면서 다른 도메인 테스트 4개 파일의 준비 코드를 `EntityIds.withId`로 바꿈. 검증 내용은 바꾸지 않음
- `getPlatformInfo`와 `PlatformErrorCode.PLATFORM_NOT_FOUND`는 도서관 단계에서 처음 쓰인다. 오류 코드 문자열, HTTP 상태, 메시지는 기존 `LibraryErrorCode.PLATFORM_NOT_FOUND`와 같아서 전환해도 API 응답이 바뀌지 않음

### 6.2 다음 단계로 넘긴 사항

- `PlatformRepository`의 파생 쿼리 이름은 도서관, 도서 검색 서비스가 직접 쓰고 있어 유지함. 해당 도메인이 `PlatformService`로 전환된 뒤 정리
- `PlatformControllerTest`는 보안 필터를 제외하므로 `/api/platform`이 로그인 없이 열려 있는지는 검증하지 않음. 보안 설정 단위의 테스트가 필요

## 7. 도서관 리팩토링 (2026-10-15)

### 7.1 변경 내용

| 구분 | 변경 |
|---|---|
| Controller, Service | 메서드 이름을 `getLibraries`에서 `searchLibraries`로 변경(3.1 동사 규칙). URL과 파라미터는 그대로 |
| 도메인 경계 | `PlatformRepository` 직접 사용을 `PlatformService.getPlatformInfo`로 전환 |
| 오류 코드 | `LibraryErrorCode.PLATFORM_NOT_FOUND` 삭제, `PlatformErrorCode.PLATFORM_NOT_FOUND` 사용. 코드 문자열, HTTP 상태, 메시지가 같아 응답은 그대로 |
| Repository | 조건 4~5개짜리 파생 쿼리 2개를 `@Query`로 바꾸고 이름을 `findVisibleByPlatform`, `findVisibleByPlatformAndName`으로 줄임. 대소문자 무시와 LIKE 특수문자 이스케이프를 유지 |
| DTO | `favoriteYn`을 내부 이름 `favorite`으로 바꾸고 `@JsonProperty`로 JSON 필드명 유지 |
| Entity | 생성자 `@Builder`로 변경. DB `NOT NULL`인 플랫폼, 도서관 코드, 도서관명만 필수 검증. 노출 여부는 상태 값이라 생성자에서 빼고 기본값 `true` 유지 |

### 7.2 검증

- 리팩토링 전 새 테스트 12개 통과 확인: `LibraryServiceIntegrationTest` 9개, `LibraryControllerTest` 3개
- `LibraryServiceIntegrationTest`는 실제 Repository와 컨테이너 DB로 Service를 검증한다. 의존성과 Repository 메서드가 바뀌어도 테스트 준비 코드가 그대로라 동작 고정 역할을 함
- `@Query`에서 이스케이프를 빼면 `%`, `_` 검색 테스트 2개가 실패함을 확인한 뒤 되돌림
- 리팩토링 후 전체 190개 통과(`./gradlew test`, 로컬)
- 메서드 이름 변경에 맞춰 테스트의 호출 이름만 바꿈. 검증 내용은 바꾸지 않음
- 다른 도메인 테스트 `FavoriteServiceIntegrationTest`의 도서관 준비 코드를 `EntityIds.withId`로 변경

### 7.3 알려진 차이

- 플랫폼이 없을 때 던지는 예외 클래스가 `LibraryException`에서 `PlatformException`으로 바뀌어, `GlobalExceptionHandler`의 경고 로그에 찍히는 `exceptionType` 값이 달라짐. HTTP 응답은 같음

## 8. 카테고리 리팩토링 (2026-10-15)

### 8.1 변경 내용

| 구분 | 변경 |
|---|---|
| Controller, Service, Repository | 주석 정리, `@GetMapping`의 배열 표기 제거. URL과 동작은 그대로 |
| DTO | 필드 주석 추가, 부모 카테고리 ID 변환을 지역 변수로 풀어 씀 |
| Entity | `StandardCategory`, `PlatformCategory`, `BookOriginCategory`의 와일드카드 import 제거, 생성자 `@Builder`로 변경. DB `NOT NULL` 값만 필수 검증. 활성 여부와 매핑 상태, 매핑 결과는 상태 값이라 생성자에서 빼고 도메인 메서드로만 변경 |
| 기본값 | 깊이와 정렬 순서를 생성자에서 래퍼 타입으로 받아, 지정하지 않으면 기존과 같은 1, 0 유지 |
| enum | `MappingStatus` 주석을 컨벤션 형식으로 변경 |

### 8.2 검증

- 리팩토링 전 새 테스트 9개 통과 확인: `StandardCategoryServiceIntegrationTest` 2개, `StandardCategoryControllerTest` 1개, `PlatformCategoryTest` 5개, `StandardCategoryTest` 1개
- `PlatformCategory`의 매핑 메서드는 이 저장소에서 호출하는 곳이 없지만, 관리자 매핑 기능에서 쓸 예정이라 지우지 않고 엔티티 테스트로 동작을 고정함
- 리팩토링 후 전체 199개 통과(`./gradlew test`, 로컬)

## 9. 즐겨찾기 리팩토링 (2026-10-15)

### 9.1 변경 내용

| 구분 | 변경 |
|---|---|
| 도메인 경계 | `BookRepository`, `LibraryRepository` 직접 사용을 `BookService.getBookOriginReference`, `LibraryService.getLibraryReference`로 전환. 검증 순서(사용자, 대상, 중복)는 그대로 |
| 새 클래스 | 도서 도메인에 `BookService` 추가. 검색 전용인 `BookSearchService`와 분리해 다른 도메인용 조회만 담당 |
| 오류 코드 | 즐겨찾기 오류 코드를 소유 도메인으로 이동. `BOOK_NOT_FOUND`는 같은 값의 `BookErrorCode`를 쓰고, `LIBRARY_NOT_FOUND`는 `LibraryErrorCode`에 같은 값으로 추가. 비게 된 `FavoriteErrorCode`, `FavoriteException` 삭제 |
| Controller | 엔드포인트별 주석 정리, 한 줄 120자를 넘던 메서드 선언 줄바꿈 |
| Entity | `FavoriteBook`, `FavoriteLibrary`의 와일드카드 import 제거, 빌더를 없애고 `create` 정적 팩토리만 둠 |
| DTO | 주석 정리, 플랫폼을 지역 변수로 꺼내 변환 코드 정리 |

### 9.2 검증

- 기존 목 기반 테스트를 같은 시나리오의 새 테스트로 교체: `FavoriteServiceIntegrationTest` 11개를 실제 DB로 검증하는 13개로, 직접 메서드를 호출하던 `FavoriteControllerTest` 8개를 `@WebMvcTest` 6개로
- 새 서비스 테스트는 준비 데이터를 SQL로 넣어 엔티티 생성 방식과 무관하게 함. 오류는 예외 클래스가 아니라 오류 코드와 HTTP 상태로 검증해, 오류 코드의 소유 도메인이 바뀌어도 응답이 같음을 확인
- 보강한 시나리오: 비활성 도서, 숨김 도서관, 다른 사용자 데이터 제외, 탈퇴 사용자의 8개 기능 전체 거부
- 리팩토링 전 새 테스트 19개 통과 확인, 리팩토링 후 전체 199개 통과(`./gradlew test`, 로컬). 교체한 테스트 수가 같아 총 개수는 이전과 같음

### 9.3 다음 단계로 넘긴 결정

- **도서와 사용자 도메인 순환:** 도서 검색 서비스는 로그인 사용자의 즐겨찾기 표시를 위해 즐겨찾기 저장소를 직접 조회한다. 이번 전환으로 사용자 도메인이 도서 도메인을 호출하므로, 도서 검색이 즐겨찾기 Service를 호출하면 순환이 된다. 컨벤션 2.5의 읽기 전용 조회 쿼리로 도서 도메인 안에서 즐겨찾기 ID를 조회하는 방식을 권장한다

### 9.4 알려진 차이

- 없는 도서나 도서관을 즐겨찾기할 때 예외 클래스가 `FavoriteException`에서 `BookException`, `LibraryException`으로 바뀌어 경고 로그의 `exceptionType` 값이 달라짐. HTTP 응답은 같음

## 10. 인증과 사용자 리팩토링 (2026-10-15)

### 10.1 변경 내용

| 구분 | 변경 |
|---|---|
| 도메인 경계 | `AuthService`의 `UserRepository` 직접 사용을 새 `UserService.findActiveUserInfo`로 전환. 비활성 사용자를 어떤 오류로 처리할지는 지금처럼 인증 도메인이 결정(401 `user_not_active`) |
| 새 클래스 | 사용자 도메인에 `UserService`와 다른 도메인용 조회 정보 `UserInfo` 추가. 재발급 응답의 `AuthUserResponse`는 엔티티 대신 `UserInfo`로 생성 |
| AuthService | 단계마다 붙어 있던 인라인 주석 제거, 유예 시간 상수와 세션 전체 삭제 이유를 주석으로 남김. 처리 순서와 해시 계산 순서는 그대로 |
| Controller | 주석 정리, Origin 검증 이유를 클래스 주석으로 이동, 한 줄 120자 초과 선언 줄바꿈 |
| 오류 코드, 예외 | `AuthErrorCode`, `UserErrorCode`를 Lombok으로 줄임. 예외 클래스의 타입 변환 재정의 제거(쓰는 곳 없음) |
| Entity | `User`의 와일드카드 import 제거, 빌더를 없애고 `create` 정적 팩토리만 둠. 탈퇴 시각 필드명을 `withdrawn`에서 `withdrawnAt`으로 변경(컬럼명은 그대로) |
| 다른 단계 파일 | 6단계 대상인 `OAuthService`의 사용자 생성 호출 한 줄만 `User.create`로 변경 |

### 10.2 검증

- 리팩토링 전 19개 통과 확인: 기존 `AuthServiceTest` 9개, `UserWithdrawalServiceTest` 3개와, 메서드를 직접 부르던 Controller 테스트를 교체한 `AuthControllerTest` 6개, `UserControllerTest` 1개
- 새 Controller 테스트는 실제 쿠키 관리자로 재발급 응답 JSON 구조와 `Set-Cookie` 헤더(이름, `Path=/api`, `HttpOnly`, `SameSite`, 만료 시 `Max-Age=0`)를 함께 고정
- `AuthServiceTest`는 목 사용자 저장소를 감싼 실제 `UserService`를 넘기도록 생성 코드만 바꿔, 기존 목 설정과 검증을 그대로 유지
- 사용자 준비 데이터는 빌더 대신 `User.create`와 `EntityIds.withId`, 탈퇴 사용자는 `withdraw` 호출로 만듦
- 리팩토링 후 전체 199개 통과(`./gradlew test`, 로컬). 교체한 테스트 수가 같아 총 개수는 이전과 같음

### 10.3 남은 사항

- **사용자와 인증 도메인 순환:** 회원 탈퇴가 아직 `RefreshTokenStore`를 직접 호출한다. 탈퇴 이벤트로 바꾸면 토큰 삭제 시점이 커밋 이후로 바뀌는 동작 변경이라, 구조 변경과 섞이지 않도록 이번에는 하지 않음(4장 동작 변경 목록)
- **`JwtAuthenticationFilter`의 `UserRepository` 직접 사용:** global 패키지라 10단계에서 `UserService`로 전환
- 테스트용 기록: 재발급 테스트의 Refresh Token 만료 시각을 2099년으로 두면 쿠키 `Max-Age`가 int 범위를 넘어 테스트용 응답 객체가 해석하지 못함. 운영 유효 기간과 같은 14일로 둠

## 11. 소셜 로그인과 연동 해제 리팩토링 (2026-10-15)

### 11.1 변경 내용

| 구분 | 변경 |
|---|---|
| 서비스 | 주석을 컨벤션 형식으로 정리하고 처리 흐름은 그대로 둠. 연동 해제의 선점, 외부 호출, 상태 기록 구조와 재시도 정책은 컨벤션 11.2의 기준 예시라 바꾸지 않음 |
| 로그 | `OAuthService`의 "클래스명.메서드명 ::" 형식 로그를 "한국어 문장 + 키=값" 형식으로 변경. 연동 해제 처리기의 오류 로그도 같은 형식으로 정리 |
| 오류 코드 문자열 | 연동 해제 처리기가 `last_error_code`에 저장하는 문자열 3개를 상수로 모음. 저장되는 값은 그대로 |
| Repository | 이 저장소에서 쓰지 않는 메서드 4개 삭제: `existsByUser_IdAndSocialProvider`, `countByUser_Id`, `findBySocialAccount_SocialProviderAndSocialAccount_ProviderSubject`, `findAllByIdIn` |
| Entity | `SocialAccount`는 생성자 `@Builder`(인자 6개), `SocialOAuthCredential`은 `create` 정적 팩토리, `SocialUnlinkTask`는 상태 값까지 열어 두던 빌더를 없애고 기존 `createPending`만 둠. DB `NOT NULL` 값만 필수 검증 |
| enum | 줄 끝 `//` 주석과 끝 쉼표 정리, 상태 전이는 `SocialUnlinkTask` 클래스 주석의 목록으로 이동 |

### 11.2 검증

- 리팩토링 전 새 `OAuthServiceIntegrationTest` 10건 통과 확인. 기존 목 기반 1건(연동 해제 중 로그인 차단)을 실제 DB를 쓰는 테스트로 교체하고 신규 가입, 프로필 갱신, 끝난 연동 해제 후 재가입, 식별값 누락, 비활성 사용자 시나리오를 추가
- 나머지 연동 해제 테스트 18개(`SocialUnlinkTaskTest` 5, `SocialOAuthCredentialServiceTest` 6, `SocialUnlinkTaskStateServiceTest` 7)와 스케줄러, 작업 생성, 처리기 테스트 5개는 준비 코드만 바꿈. 운영 코드와 같은 생성 경로를 쓰고, 처리 중이면서 재시도 5회인 작업처럼 특정 상태가 필요한 경우만 `SocialUnlinkTaskFixture` 사용
- 리팩토링 후 전체 208개 통과(`./gradlew test`, 로컬)

### 11.3 남은 사항

- ~~`SocialUnlinkInProgressException`은 `BusinessException`이 아닌 `RuntimeException`~~ 16.4에서 `UserException`(`SOCIAL_UNLINK_IN_PROGRESS`)으로 전환 완료
- ~~`User.updateProfile`과 `SocialAccount.updateProfile`의 인자 순서 불일치~~ 16.4에서 (이름, 이메일, 이미지)로 통일 완료

## 12. 테스트 구분과 실행 방식 정리 (2026-10-15)

컨벤션 12장에 테스트 전략을 반영하고, 실제 DB 테스트를 구분해 따로 실행하도록 바꿨다.

| 구분 | 변경 |
|---|---|
| 합성 어노테이션 | `@MySqlDataJpaTest` 추가. `@DataJpaTest`, 내장 DB 교체 금지, 컨테이너 설정, `@Tag("db")`를 한 번에 적용 |
| 테스트 이름 | 실제 DB를 쓰는 Service 테스트 4개를 `XxxServiceIntegrationTest`로 변경: 도서관, 표준 카테고리, 즐겨찾기, 소셜 가입. FULLTEXT 테스트는 트랜잭션 정책이 달라 `BookSearchRepositoryFullTextTest` 유지, 전체 컨텍스트 테스트는 `@Tag("db")`만 추가 |
| Gradle | `test`는 `db` 태그 제외, 새 `dbTest`는 `db` 태그만 실행, `check`가 둘 다 실행. 실제 자원 접근 차단 설정은 모든 테스트 작업에 공통 적용 |
| CI | 테스트 단계를 `./gradlew clean test`에서 `./gradlew clean check`로 변경해 DB 테스트도 실행 |

### 12.1 검증 (로컬)

| 명령 | 클래스 | 테스트 | 실패 | 소요 |
|---|---|---|---|---|
| `./gradlew clean test` | 34 | 169 | 0 | 약 11초 |
| `./gradlew dbTest` | 6 | 39 | 0 | 약 59초 |

- 합계 208개로 이전 전체 실행과 같다. `test` 결과에 DB 테스트 클래스가 없음을 확인했다.
- `./gradlew check --dry-run`으로 `check`가 `test`와 `dbTest`를 모두 실행함을 확인했다.

### 12.2 남은 사항

- CI 러너에서 `check`(Testcontainers 포함)가 동작하는지 미검증. 러너는 이미 Docker로 이미지를 빌드하므로 동작할 가능성이 높지만, 다음 배포 실행에서 확인 필요
- ~~CI 테스트 단계의 `MYSQL_HOST`, `MYSQL_PORT` 환경 변수~~ 삭제 완료(3.4)

## 13. 도서 검색 리팩토링 (2026-10-15)

### 13.1 변경 내용

| 구분 | 변경 |
|---|---|
| 도메인 순환 해소 | 즐겨찾기 ID를 사용자 도메인 Repository 대신 도서 도메인의 읽기 전용 쿼리로 조회(컨벤션 2.5). 이제 사용자(즐겨찾기)가 도서를 부르는 한 방향만 남음 |
| 플랫폼 조회 | `PlatformRepository` 직접 사용을 `PlatformService.getPlatformInfos`, `findPlatformInfo`로 전환. 도서 쪽 "플랫폼 없음" 메시지("존재하지 않는 플랫폼입니다.")가 플랫폼 도메인 메시지와 달라, 없을 때의 예외는 지금처럼 `BookErrorCode`로 던져 응답 유지 |
| 서비스 시그니처 | 인자 9개, 7개였던 두 검색 메서드를 `PlatformBookSearchRequest`, `BookSearchRequest`로 묶음. Controller가 요청 파라미터로 만들어 넘기므로 API는 그대로 |
| BookSearchService | 75줄 메서드를 검증, 플랫폼 선택, 그룹 생성 단계로 분리. 검증 순서와 오류, 미리보기 3건, 정렬은 그대로. 플랫폼별 검색에서 그룹마다 하던 즐겨찾기 조회를 한 번으로 줄임(결과 동일) |
| PlatformType | 서비스 안 private enum과 응답 DTO에 중복되던 플랫폼 종류 판단을 `book/enums/PlatformType`으로 통합 |
| DTO | `favoriteYn`, `availableYn`, `sourceBookId` 내부 이름을 `favorite`, `available`, `platformBookId`로 바꾸고 `@JsonProperty`로 JSON 필드명 유지. 조회 결과 record와 요청 record 주석 정리 |
| Repository | 검색 쿼리 구현의 개인 태그 주석을 이유 주석으로 바꾸고, 비슷한 이름의 두 보유 현황 조회의 차이를 인터페이스 주석에 명시 |
| Entity | `BookOrigin`, `LibraryBook`, `SubscriptionBook`의 와일드카드 import 제거, 생성자 `@Builder`로 변경, DB `NOT NULL` 값만 필수 검증. 콘텐츠 종류와 수집 시점 이용 상태는 미지정 시 기존 기본값(EBOOK, AVAILABLE) |
| 유니크 제약 | `BookOrigin`의 `@UniqueConstraint`를 실제 DB(`uk_book_origin_platform_book`: 플랫폼, 콘텐츠 종류, 플랫폼 도서 ID)에 맞춤. 스키마 검증은 유니크 제약을 비교하지 않아 실행 동작 변화 없음 |
| 오류 코드, enum | `BookErrorCode` Lombok 적용, enum 주석 정리 |

### 13.2 검증

- 리팩토링 전 새 테스트 15개 통과 확인: `BookSearchServiceIntegrationTest` 11개(네 API의 오류 코드와 메시지, 정렬, 필터, 즐겨찾기 표시, 관련 도서), `BookControllerTest` 4개(네 API의 JSON 필드 전체, 기본 페이지 크기, 로그인 사용자 전달, 검색어 기록)
- 기존 `BookContentTypeTest`, `BookDetailAssemblyTest`, `BookforestApplicationTests`, `RankingTest`, `SearchKeywordTest`는 생성자, 호출 형태, 내부 접근자 이름, 엔티티 준비 코드만 바꿈. 검증 내용은 그대로
- 리팩토링 후 `./gradlew clean check` 통과: `test` 173개, `dbTest` 50개, 합계 223개(로컬)

### 13.3 남은 사항

- **잠재 버그:** 플랫폼 도서 상세는 (플랫폼, 플랫폼 도서 ID)로 한 건을 찾지만 DB 유니크 키에는 콘텐츠 종류가 포함된다. 같은 식별값의 전자책과 오디오북이 함께 수집되면 결과가 두 건이라 예외가 난다. 콘텐츠 종류를 요청에 넣거나 우선순위를 정하는 동작 변경이 필요해 4장 목록에 올림
- **검색어 도메인 의존:** 검색 쿼리 구현이 `SearchKeywordRules.normalize`를 직접 부르고, Controller가 `SearchKeywordService`로 검색어를 기록한다. 8단계에서 검색어 도메인을 정리할 때 함께 판단
- **랭킹의 도서 Repository 직접 사용:** 8단계에서 전환

## 14. 랭킹, 검색어 리팩토링 (2026-10-15)

### 14.1 변경 내용

| 구분 | 변경 |
|---|---|
| 패키지 | 평면 구조였던 두 도메인을 계층 구조로 이동. 랭킹은 `controller`, `service`, `client`, `dto`, `enums`, 검색어는 `controller`, `service`, `config`, `dto` |
| 도메인 경계 | `RankingService`의 `BookRepository` 직접 사용을 `BookService.findAvailabilitiesByIsbns`와 조회 정보 `BookAvailabilityInfo`로 전환. 순서와 중복 제거 규칙은 그대로 |
| 검색어 서비스 | 한 줄 압축 메서드를 풀어 쓰고 상수와 주석 정리. Redis 키, Lua 스크립트 인자, 지표 이름과 태그 값은 운영 데이터, 모니터링과 맞물려 그대로 유지 |
| 메서드 이름 | `get`을 `getPopularKeywords`, `retryAfter`를 `getRetryAfterSeconds`, `actorHash`를 `hashActor`, `validEvent`를 `isUuid`, `SearchKeywordRules.valid`, `visible`을 `isValid`, `isVisible`로 변경 |
| DTO | 서비스 안에 있던 `PopularKeywords`, `Item` record를 `dto/PopularSearchKeywordResponse`로 이동. 필드명이 같아 Redis에 저장된 기존 스냅샷을 그대로 읽음 |
| 스케줄러 | 서비스 안의 `@Scheduled` 메서드를 `SearchKeywordScheduler`로 분리. 랭킹 스케줄러와 같은 구조 |
| 웹 설정 | 익명 클래스 인터셉터를 `SearchRequestInterceptor`로 분리. 응답 상태 코드와 메시지 문자열, 쿠키 속성은 그대로 |
| 외부 API 클라이언트 | 파싱 순서와 예외 조건은 그대로 두고 단계별 메서드로 분리, 오류 메시지를 한국어로 변경(로그에는 예외 종류만 남아 외부 영향 없음) |
| 로그 | 영어 로그를 한국어 문장 + 키=값 형식으로 변경. 랭킹 실패 로그는 API 키 노출 방지를 위해 지금처럼 예외 종류와 HTTP 상태만 기록 |
| 개인 태그 주석 | `ponytail:` 주석 두 곳을 이유 주석으로 변경 |

### 14.2 검증

- 리팩토링 전 새 Controller 테스트 3개(`RankingControllerTest` 1, `PopularSearchKeywordControllerTest` 2) 통과 확인
- `SearchKeywordTest`가 로컬에 설치된 `redis-server`를 띄우고 없으면 건너뛰던 방식을 Redis 컨테이너(`RedisTestContainer`)로 바꾸고, 리팩토링 전 13개 통과 확인(건너뜀 0)
- 기존 테스트를 대상 클래스별로 나눔. 검증 내용은 그대로
  - `RankingTest` 11개 → `DataLibraryClientTest` 5개, `RankingServiceTest` 6개
  - `SearchKeywordTest` 13개 → `SearchKeywordRulesTest` 1개, `SearchRequestInterceptorTest` 2개, `SearchKeywordServiceIntegrationTest` 10개(`db` 태그)
- 다른 패키지로 옮긴 급상승 빈 날짜 테스트는 서비스 쪽에서 클라이언트 예외를 직접 지정하고, 빈 날짜에서 예외가 나는지는 `DataLibraryClientTest`에 1개 추가해 검증
- 리팩토링 후 `./gradlew clean check` 통과: `test` 167개, `dbTest` 60개, 합계 227개(로컬)

### 14.3 남은 사항

- 도서 도메인이 검색어 도메인을 한 방향으로 사용한다. 검색 쿼리가 `SearchKeywordRules.normalize`를, 도서 Controller가 `SearchKeywordService.record`를 부른다. 검색어 도메인은 도서를 부르지 않아 순환은 없음
- ~~기존 테스트(`RankingServiceTest`, `SearchKeywordServiceIntegrationTest` 등)는 아직 JUnit `assertEquals`와 Mockito `when`을 쓴다~~ 16.5에서 AssertJ, BDDMockito로 전환 완료

## 15. 공통 설정 리팩토링 (2026-10-15)

### 15.1 변경 내용

| 구분 | 변경 |
|---|---|
| 예외 처리 | `GlobalExceptionHandler`의 반복 응답 생성 코드를 한 메서드로 모으고 클래스 Javadoc 추가. 처리하는 예외 종류, 응답 상태와 본문, 로그 레벨은 그대로 |
| 오류 코드 | `CommonErrorCode`를 컨벤션 10.1의 Lombok 형식으로 변경. 상수, 상태 코드, 코드 문자열, 메시지는 그대로 |
| 예외, 응답 | `BusinessException`의 getter를 Lombok으로 바꾸고 빈 줄 정리. `ErrorCode`, `ErrorResponse`, `ErrorResponseWriter` 주석을 명사형으로 정리 |
| 비동기, 스케줄링 | `SocialUnlinkExecutionConfig`가 `@EnableScheduling`까지 맡아 랭킹, 검색어 스케줄러가 소셜 연동 해제 설정에 기대고 있었음. 활성화는 `global/config/AsyncSchedulingConfig`, 소셜 연동 해제 실행기는 `domain/user/config/SocialUnlinkExecutorConfig`로 분리. 빈 이름 `socialUnlinkExecutor`와 스레드 풀 값은 그대로 |
| Redis 토큰 저장소 | 인증 도메인 전용인 `RedisRefreshTokenStore`를 `global/redis`에서 `domain/auth/repository`로 이동. 교체 스크립트 빈을 둔 `RedisConfig`는 검색어 서비스와 같은 방식(클래스 상수)으로 바꾸고 삭제. 키 형식, Hash 필드 이름, 스크립트 인자 순서는 그대로 |
| 기타 | `BaseTimeEntity`, `JpaAuditingConfig`, `MySqlFullTextFunctionContributor`, `ServletInitializer`에 Javadoc 추가와 import, 빈 줄 정리 |

### 15.2 검증

- 리팩토링 전 잠금 테스트 추가 후 기존 코드에서 통과 확인
  - `RedisRefreshTokenStoreIntegrationTest` 11개(`db` 태그, Redis 컨테이너): 키 형식과 Hash 필드, 만료 세션 무시, 조회, 삭제, 전체 삭제, 교체 결과 3종, 잘못된 교체 요청
  - `AsyncSchedulingConfigTest` 2개: 비동기와 스케줄링 처리기 등록, 실행기 빈 이름과 스레드 풀 값
- 기존 `GlobalExceptionHandlerTest` 5개, `ErrorResponseWriterTest` 2개는 그대로 통과
- 리팩토링 후 `./gradlew clean check` 통과: `test` 169개, `dbTest` 71개, 합계 240개(로컬). 전체 컨텍스트를 띄우는 `BookforestApplicationTests`로 옮긴 설정과 저장소의 빈 등록 확인

### 15.3 남은 사항

- 4장의 동작 변경 3건(필수 파라미터 누락 400 응답, `errors` 배열, 4xx 로그 `info`)은 응답이나 운영 로그가 바뀌어 진행 여부 결정 대기
- ~~기존 `GlobalExceptionHandlerTest`, `ErrorResponseWriterTest`는 JUnit `assertEquals`를 쓴다~~ 16.5에서 AssertJ, BDDMockito로 전환 완료
- ~~`ServletInitializer`와 `war` 플러그인~~ 16.5에서 제거 완료

## 16. 보안 리팩토링 (2026-10-15)

### 16.1 변경 내용

| 구분 | 변경 |
|---|---|
| 도메인 경계 | `JwtAuthenticationFilter`의 `UserRepository` 직접 사용을 `UserService.isActiveUser`로 전환. 요청마다 호출되므로 기존과 같은 존재 여부 쿼리(`existsByIdAndActiveTrue`) 유지 |
| 보안 설정 | `SecurityConfig`의 허용 경로를 용도별 상수로 묶고 줄바꿈 정리. 경로, 메서드, 순서, CORS 값은 그대로 |
| 오류 코드 | `SecurityErrorCode`, `OAuth2LoginErrorCode`를 컨벤션 10.1의 Lombok 형식으로 변경. 코드 문자열과 메시지는 그대로 |
| 토큰 발급 | `TokenProvider`에서 Access Token에만 쓰던 범용 생성 메서드와 도달하지 않는 권한 null 분기를 정리. 클레임 이름과 값은 그대로 |
| 소셜 로그인 | `CustomOAuth2UserService`의 쓰이지 않던 메시지 인자를 제거하고 예외 생성을 한 메서드로 통합. `OAuth2AuthenticationSuccessHandler`의 호출되지 않던 쿠키 작성 메서드와 상수 삭제(쿠키는 `RefreshTokenCookieManager`가 작성) |
| 주석, 형식 | 보안 패키지 36개 파일의 `/* */` 필드 주석, 설명 없는 `@param`, `@return`을 컨벤션 6장 형식으로 정리. 카카오, 네이버 연동 해제 클라이언트에 재시도 여부를 목록으로 정리 |

- 인증 흐름, 쿠키 속성, 토큰 정책, 암호문 형식, 외부 API 요청 형식, 로그 레벨은 바꾸지 않았다.
- 예외 메시지 중 `TokenProvider`의 null 검사 메시지 두 개만 `~없음.`을 `~없습니다.`로 맞췄다. 응답에 나가지 않는 내부 메시지다.

### 16.2 검증

- 리팩토링 전 `SecurityConfigTest` 18개 추가 후 기존 코드에서 통과 확인. 실제 `SecurityConfig`와 JWT 필터를 적용한 `@WebMvcTest`로 아래를 검증
  - 비로그인 허용 GET 경로 8개, 재발급과 로그아웃의 JWT 필터 제외
  - 랭킹 경로는 GET만 허용, 로그인 필요 경로의 401 `authentication_required`
  - 유효한 토큰, 비활성 사용자, 만료 토큰, 잘못된 Authorization 헤더 응답
  - 허용 출처와 허용하지 않은 출처의 CORS 사전 요청
- `JwtAuthenticationFilterTest`는 mock 대상만 `UserRepository`에서 `UserService`로 바꿈. 검증 내용은 그대로
- 리팩토링 후 `./gradlew clean check` 통과: `test` 187개, `dbTest` 71개, 합계 258개(로컬)

### 16.3 남은 사항

- ~~잘못된 형식의 Authorization 헤더는 비로그인 허용 경로에서도 401로 거절~~ 비로그인 요청으로 처리하도록 변경 완료(4장). `SecurityConfigTest` 20개, `./gradlew clean check` 260개 통과
- `JwtAuthenticationFilter`의 제외 경로 비교는 `servletPath`를 쓴다. `DispatcherServlet`이 `/`가 아닌 경로에 매핑되면 제외가 동작하지 않으므로 서블릿 매핑을 바꿀 때 주의
- ~~소셜 연동 해제 클라이언트와 소셜 토큰 암호화의 `global/security` 위치~~ 16.5에서 사용자 도메인으로 이동 완료
- `KakaoResponse`, `NaverResponse`의 `getString`, `getMap` 도우미가 중복된다. 두 클래스뿐이라 그대로 둠
- ~~기존 보안 테스트는 JUnit `assertEquals`와 Mockito `when`을 쓴다~~ 16.5에서 AssertJ, BDDMockito로 전환 완료

### 16.4 미뤄 둔 사용자 도메인 정리 (2026-10-19)

| 구분 | 변경 |
|---|---|
| 예외 계층 | `RuntimeException`을 상속하던 `SocialUnlinkInProgressException`을 지우고 `UserException(UserErrorCode.SOCIAL_UNLINK_IN_PROGRESS)`로 전환(컨벤션 10.1). 코드 문자열은 프론트 콜백 URL로 나가는 기존 값 `social_unlink_in_progress`와 같음. `CustomOAuth2UserService`는 사용자 예외의 코드 문자열을 OAuth2 오류 코드로 넘기고, 실패 처리기가 모르는 코드는 `oauth2_error`로 바꿈 |
| 인자 순서 | `SocialAccount.updateProfile`을 (이메일, 닉네임, 이미지)에서 `User.updateProfile`, `User.create`와 같은 (닉네임, 이메일, 이미지)로 변경하고 호출부 수정 |

- 리팩토링 전 `CustomOAuth2UserServiceTest` 5개 추가 후 기존 코드에서 통과 확인. 로그인 성공과 실패 사유별 OAuth2 오류 코드(`unsupported_provider`, `social_unlink_in_progress`, `inactive_user`, `invalid_oauth_response`) 검증
- 프로필 갱신은 기존 `OAuthServiceIntegrationTest`가 DB 값으로 검증
- 변경 후 `./gradlew clean check` 통과: `test` 194개, `dbTest` 71개, 합계 265개(로컬)

### 16.5 남은 구조 정리 (2026-10-19)

| 구분 | 변경 |
|---|---|
| 패키지 이동 | 사용자 도메인만 쓰던 `global/security/oauth/unlink`와 `global/security/crypto`를 사용자 도메인으로 이동. 연동 해제 클라이언트 6개는 `domain/user/client`, 암호화 3개는 `domain/user/crypto`, 설정값 `SocialUnlinkProperties`, `SocialTokenCryptoProperties`는 `domain/user/config`. 클래스 내용, 빈 이름, 설정 키(`app.security.social-unlink`, `app.security.social-token`)는 그대로. 테스트 3개도 같은 패키지로 이동 |
| 컨벤션 | 1장 패키지 구조에 `crypto`(도메인 데이터 암호화) 추가. 규칙 변경이라 문서를 먼저 고침 |
| 테스트 형식 | JUnit 단언과 Mockito `when`, `verify`를 쓰던 테스트 29개를 AssertJ, BDDMockito로 전환. 예외 객체가 필요한 곳은 `assertThatExceptionOfType(...).isThrownBy(...).actual()`, 호출 순서 검증은 `then(mock).should(inOrder)`로 바꿈. `isEmpty`, `contains`, `hasSize`, `isBetween` 같은 AssertJ 단언으로 의미가 같은 곳만 정리. 와일드카드 import와 쓰지 않는 import 제거 |
| 빌드 | `war` 플러그인, `providedRuntime` Tomcat 의존성, `ServletInitializer` 제거. 배포는 `bootJar`로 만든 `app.jar`를 내장 Tomcat으로 실행하는 방식 그대로 |

- 테스트 전환은 괄호를 해석하는 스크립트로 바꾼 뒤 직접 검토했다. 파일마다 변환 전후의 단언, 검증 호출 수가 같은지 비교해 빠진 단언이 없음을 확인
- `bootJar` 결과물에 내장 Tomcat과 `Start-Class`가 그대로 들어 있음을 확인
- 변경 후 `./gradlew clean check` 통과: `test` 194개, `dbTest` 71개, 합계 265개(로컬)
- 남은 사항: 기존 테스트 20개 파일, 77개 메서드가 아직 한국어 메서드 이름에 `@DisplayName`과 given, when, then 구역이 없다(컨벤션 12.6). 이번 범위는 단언과 목 형식이라 그대로 둠

### 16.6 동작 변경 (2026-10-19)

4장 목록의 동작 변경을 진행했다. 구조 리팩토링 커밋과 섞지 않도록 별도 커밋으로 나누는 것을 권장한다.

| 변경 | 내용 | 영향 |
|---|---|---|
| 필수 파라미터 누락 400 | `MissingServletRequestParameterException`을 `invalid_request`(400)로 처리 | `/api/library`에 `platformId`가 없을 때 500 `internal_server_error`에서 400 `invalid_request`로 바뀜. 프론트는 항상 값을 보내므로 영향 없음 |
| 4xx 로그 레벨 | 비즈니스 예외는 4xx면 `info`, 5xx면 예외 스택과 함께 `error`. 잘못된 요청 로그도 `warn`에서 `info`로 변경(컨벤션 13.2) | 운영 로그 레벨 변경 |
| `errors` 배열 | `ErrorResponse`에 `errors`(필드, 사유) 추가, 비어 있으면 JSON에서 생략. `@Valid` 실패(`MethodArgumentNotValidException`)만 채움 | 다른 오류 응답 JSON은 그대로(`ErrorResponseWriterTest`의 전체 JSON 비교로 확인). 지금은 `@Valid`를 쓰는 API와 검증 의존성이 없어 실제로 채워지는 응답은 없음 |
| 탈퇴 토큰 삭제 이벤트 | 사용자 도메인이 `UserWithdrawnEvent`를 발행하고 인증 도메인 `UserWithdrawnEventListener`가 커밋 후 세션 삭제. 사용자에서 인증으로 향하던 의존이 사라져 두 도메인의 순환 해소 | 세션 삭제가 탈퇴 커밋 이후로 바뀜. 롤백되면 지우지 않음. 삭제 실패는 `error` 로그만 남기고 탈퇴는 성공 처리, 재발급이 비활성 사용자를 막고 남은 세션도 지우므로 다시 로그인 상태가 되지 않음 |
| 콘텐츠 종류 중복 | 플랫폼 도서 상세가 같은 ID의 원본을 모두 조회해 전자책 우선, 같은 종류는 먼저 수집된 도서를 반환. 여러 건이면 `warn` 로그 | API 변경 없음. 기존에는 예외로 500. 2026-10-19 로컬 DB(운영 복제) 기준 중복 0건이라 현재 응답은 그대로 |
| `SearchKeyword` VO | 도입하지 않고 종료 | 도서관과 도서 검색의 정규화 규칙이 의도적으로 다르고 쓰는 곳이 각각 한 곳뿐 |

- 테스트 추가: 필수 파라미터 누락(`LibraryControllerTest`), `errors` 배열과 생략(`GlobalExceptionHandlerTest` 2개), 같은 ID 전자책 우선(`BookSearchServiceIntegrationTest`), 커밋 후 세션 삭제, 롤백 시 미삭제, 삭제 실패 시 탈퇴 성공(`UserWithdrawalServiceIntegrationTest` 3개). 누락 파라미터와 같은 ID 테스트는 변경 전 코드에서 실패하는 것을 먼저 확인
- 변경 후 `./gradlew clean check` 통과: `test` 197개, `dbTest` 75개, 합계 272개(로컬)

## 17. 확인 필요 사항

- [ ] 도서 검색 쿼리에서 다른 도메인 테이블을 조인하는 부분이 컨벤션 2.5 범위 안에 있는지
- [x] 회원 탈퇴 이벤트 전환 시 액세스 토큰 차단 동작이 그대로인지(16.6, Access Token 차단은 인증 필터의 활성 사용자 확인이라 세션 삭제와 무관)
- [x] `BookOrigin` 엔티티의 유니크 제약 선언을 실제 DB 기준으로 맞춤(13.1)
- [ ] 엔티티가 없는 테이블 4개(`book_cover_enrichment`, `collection_run_failure`, `collection_run_log`, `library_list_checkpoint`)는 크롤러 소유로 보임. 이 저장소에서 다룰 필요가 있는지 확인
