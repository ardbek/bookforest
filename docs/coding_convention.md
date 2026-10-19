# BookForest 코딩 컨벤션

> 작성일: 2026-10-15
>
> 이 문서는 사람과 AI가 BookForest 코드를 작성하거나 수정할 때 따르는 공통 규칙이다.
> 리팩토링 진행 상황, 도입할 의존성, 미검증 사항은 [리팩토링 계획](./refactoring_plan.md)에서 관리한다.

---

## 0. 적용 원칙

### 0.1 문서의 범위와 우선순위

- 이 문서는 코드 구조와 작성 스타일의 기준이다.
- 기존 비즈니스 동작, API 계약, 데이터 정합성을 임의로 변경하는 근거로 사용하지 않는다.
- 규칙 적용과 동작 유지가 충돌하면 변경을 중단하고 별도 의사결정 사항으로 보고한다.
- 규칙과 기존 코드 스타일이 다르면 이 문서를 따른다. 기존 코드는 리팩토링 대상이며 기준이 아니다.
- 규칙을 바꾸려면 이 문서를 먼저 고치고 코드를 고친다.

### 0.2 엄격한 규칙과 판단이 필요한 규칙

규칙은 코드의 품질과 유지보수성을 높이기 위한 수단이다. 규칙을 기계적으로 적용해 불필요한 추상화, 메서드 분리, 클래스 생성, 주석 추가가 생겨서는 안 된다.

| 구분 | 해당 장 | 적용 방식 |
|---|---|---|
| 엄격 준수 | 2장 계층과 도메인 경계, 5장 API 계약, 10장 예외 분류와 처리 위치, 11장 트랜잭션, 12장 검증 원칙, 15장 AI 작업 규칙 | 예외 없이 지킨다. 지킬 수 없으면 작업을 멈추고 보고한다 |
| 판단 적용 | 1장 패키지 세부, 3장 메서드, 4장 변수명, 6장 주석, 7~9장 형태, 13장 로그 형식, 14장 포매팅 | 가독성과 유지보수성을 우선해 적용한다. 규칙을 지키려고 코드가 더 읽기 어려워지면 규칙을 따르지 않고 이유를 남긴다 |

---

## 1. 패키지 구조

모든 도메인은 같은 하위 패키지 구조를 따른다. 하위 패키지는 **실제로 클래스가 있을 때만** 만든다.

```
com.ebook.bookforest
├── domain
│   └── {도메인}
│       ├── controller   // HTTP 진입점
│       ├── service      // 비즈니스 흐름, 트랜잭션, 스케줄러
│       ├── repository   // DB 접근, 조회 전용 projection
│       ├── client       // 외부 API 호출
│       ├── config       // 도메인 전용 설정, XxxProperties
│       ├── crypto       // 도메인 데이터 암호화(소셜 토큰 등)
│       ├── dto          // XxxRequest, XxxResponse, XxxInfo
│       ├── vo           // 값 객체
│       ├── entity       // JPA 엔티티
│       ├── enums
│       ├── event        // 도메인 이벤트와 리스너
│       └── exception    // XxxErrorCode, XxxException
└── global               // 여러 도메인이 공유하는 설정, 보안, 예외 처리
```

- 현재 평면 구조인 `ranking`, `searchkeyword` 도메인도 이 구조로 옮긴다.
- 스케줄러는 `service` 패키지에 두고 클래스 이름을 `XxxScheduler`로 끝낸다.
- 여러 도메인이 공유하는 설정은 `global/config`에 둔다.

---

## 2. 계층 책임과 도메인 경계

| 계층 | 해야 하는 일 | 하면 안 되는 일 |
|---|---|---|
| Controller | 요청 매핑, 입력 바인딩과 검증(`@Valid`), 인증 사용자 추출, Service 호출, 응답 반환 | 비즈니스 분기, Repository 직접 호출, 엔티티 반환 |
| Service | 비즈니스 흐름, 트랜잭션 경계, 엔티티와 DTO 변환 호출, 도메인 예외 발생 | HTTP 객체(`HttpServletRequest` 등) 의존, 다른 도메인 Repository 직접 호출 |
| Repository | 쿼리 정의와 실행 | 비즈니스 판단, 예외 메시지 결정 |

### 2.1 데이터 소유

- 각 테이블과 엔티티는 하나의 도메인이 소유한다. 상태 변경은 소유 도메인의 Service에 요청한다.
- 다른 도메인의 Repository를 직접 주입하지 않는다. 필요한 데이터는 소유 도메인의 Service에서 받는다.
- 오류 코드는 그 개념을 소유한 도메인이 정의한다. "플랫폼 없음"은 `PlatformErrorCode`에 둔다.

### 2.2 도메인 간 조회 결과

- 다른 도메인에 제공하는 조회 결과는 필요한 값만 담은 **조회용 record를 우선** 사용한다.
- 조회용 record는 데이터를 제공하는 도메인의 `dto` 패키지에 두고 `XxxInfo`로 이름 짓는다.
- 조회용 record에는 엔티티, JPA 프록시, 변경 가능한 엔티티 컬렉션을 넣지 않는다.
- 단일 값이나 존재 여부만 필요하면 값 타입이나 `boolean` 반환을 허용한다. 식별자 컬렉션(`Set<Long>`)도 값 타입으로 본다.
- 엔티티의 전체 필드를 기계적으로 복제하지 않는다. 조회 계약에 필요한 값만 넣는다.

```java
/** 다른 도메인에 제공하는 플랫폼 조회 정보 */
public record PlatformInfo(
    Long id,
    String platformCode,
    String platformName,
    boolean supportsLibrary,
    boolean supportsSubscription
) {

    public static PlatformInfo from(Platform platform) {
        return new PlatformInfo(
            platform.getId(),
            platform.getPlatformCode(),
            platform.getPlatformName(),
            platform.isSupportsLibrary(),
            platform.isSupportsSubscription()
        );
    }
}

// 도서관 서비스는 플랫폼 엔티티 대신 조회 정보만 받는다
PlatformInfo platform = platformService.getPlatformInfo(platformId);
```

### 2.3 연관관계 설정용 엔티티 참조

JPA 연관관계를 설정하려면 다른 도메인의 엔티티가 필요할 수 있다. 예를 들어 즐겨찾기를 만들려면 도서 엔티티를 연결해야 한다.

- 소유 도메인의 Service가 **참조 전용 메서드** `getXxxReference(id)`를 제공한다.
- 이 메서드는 기존과 같은 존재 여부, 노출 여부 검증을 하고 같은 예외를 던진다.
- 반환된 엔티티는 연관관계 설정에만 쓴다. 받는 쪽은 그 엔티티의 상태를 바꾸거나 필드를 읽어 비즈니스 판단을 하지 않는다. 판단이 필요하면 `XxxInfo`를 따로 받는다.

```java
/**
 * 원본 도서 엔티티 참조(즐겨찾기 연관관계 설정용)
 *
 * @throws BookException 활성 도서가 없는 경우 BOOK_NOT_FOUND
 */
@Transactional(readOnly = true)
public BookOrigin getBookOriginReference(Long bookOriginId) { ... }
```

### 2.4 호출 방향과 순환 금지

- 도메인 Service 간 순환 의존과 순환 호출을 만들지 않는다. 순환은 **도메인 단위**로 판단한다. 클래스가 달라도 도메인 A가 B를 부르고 B가 A를 부르면 순환이다.
- 역방향 호출이 필요하면 도메인 이벤트로 분리한다. 이벤트를 발행하는 도메인은 듣는 도메인을 모른다.
- 예: 인증 도메인은 사용자 도메인을 호출한다. 회원 탈퇴 시 토큰 삭제는 사용자 도메인이 탈퇴 이벤트를 발행하고 인증 도메인이 받아서 처리한다.

### 2.5 읽기 전용 조회 쿼리의 조인

- 성능이 필요한 **읽기 전용 조회 쿼리**는 다른 도메인의 테이블을 조인할 수 있다. 도서 검색처럼 여러 도메인을 가로지르는 화면 조회가 해당한다.
- 이런 쿼리의 결과는 엔티티가 아니라 projection record로 받는다.
- 쓰기는 반드시 소유 도메인의 Service를 거친다.

### 2.6 기존 코드 전환

- 단순 조회 결과 전달부터 조회용 record로 전환한다.
- JPA 연관관계 설정을 위해 엔티티 참조가 필요한 호출은 별도로 분류하고 2.3을 적용한다.
- 이 작업을 이유로 기존 연관관계 매핑이나 DB 스키마를 임의로 변경하지 않는다.
- 전환 과정에서도 기존 조회 조건, 예외, API 응답 계약을 유지한다.

---

## 3. 메서드 작성 규칙

### 3.1 이름

Service 메서드는 아래 동사로 시작한다. Controller 메서드는 호출하는 Service 메서드와 같은 이름을 쓴다.

| 동사 | 의미 | 반환 |
|---|---|---|
| `get` (단건) | 반드시 있어야 하는 대상 조회, 없으면 예외 | 단건 |
| `get` (목록) | 조건 없는 목록 조회, 결과가 없어도 예외 아님 | 빈 목록 허용 |
| `getXxxInfo` | 다른 도메인에 제공하는 조회 정보(2.2) | `XxxInfo` |
| `getXxxReference` | 연관관계 설정용 엔티티 참조(2.3) | 엔티티 |
| `find` | 없을 수 있는 단건 조회 | `Optional` |
| `search` | 검색 조건이나 키워드로 조회 | 목록 또는 페이지, 빈 결과 허용 |
| `count`, `exists` | 개수, 존재 여부 | `long`, `boolean` |
| `create` / `add` | 새 데이터 생성 / 연관 추가(즐겨찾기 등) | 생성된 식별자나 응답 |
| `update` / `change` | 여러 필드 수정 / 단일 상태 변경 | `void` 또는 응답 |
| `delete` / `remove` | 삭제 / 연관 해제 | `void` |
| `validate` | 검증, 실패 시 예외 | `void` |
| `to`, `from`, `of` | 변환, 정적 팩토리 | 변환 결과 |

업무 의미가 분명한 동사는 아래 표에 등록한 뒤 쓴다. 새 동사가 필요하면 이 표에 의미와 함께 먼저 추가한다.

| 업무 동사 | 의미 |
|---|---|
| `refresh` | 캐시나 스냅샷 재계산 |
| `issue` / `reissue` / `revoke` | 토큰 발급 / 재발급 / 폐기 |
| `withdraw` | 회원 탈퇴 |
| `record` | 집계용 이벤트 기록 |

- 동사 뒤에는 대상 명사를 붙인다. `getPlatforms`, `searchLibraries`, `addFavoriteBook`.
- `process`, `handle`, `execute`처럼 의미가 넓은 동사는 이벤트 리스너와 스케줄러에서만 쓴다.

### 3.2 구조

- public 메서드는 **흐름이 보이게** 쓴다. 검증, 조회, 처리, 변환 단계가 한눈에 읽혀야 한다.
- 본문이 20줄을 넘으면 분리를 **검토**한다. 줄 수를 줄이는 것만이 목적인 분리는 하지 않는다. 기준은 실제로 읽기 쉬운가이다.
- 한 메서드는 하나의 원자적 작업을 한다. 이름에 `And`가 들어가도 하나의 원자적 작업이면 나누지 않는다.
- 파라미터가 4개를 넘으면 요청 객체(`XxxRequest` 또는 `XxxCondition`)로 묶는 것을 검토한다.
- 한 줄에 여러 문장을 쓰는 압축 코드는 쓰지 않는다. `if`와 `for`는 본문이 한 줄이어도 중괄호를 쓴다.
- public 메서드를 먼저, 그 메서드가 호출하는 private 메서드를 그 아래에 둔다.
- `null` 반환 대신 `Optional`, 빈 컬렉션, 예외 중 하나를 쓴다. 응답 DTO의 선택 필드는 `null`을 허용한다.

```java
@Transactional(readOnly = true)
public LibraryPageResponse searchLibraries(Long platformId, String rawKeyword, Pageable pageable) {
    PlatformInfo platform = platformService.getPlatformInfo(platformId);
    validateLibrarySupported(platform);

    Page<Library> libraries = findVisibleLibraries(platformId, SearchKeyword.from(rawKeyword), pageable);

    return LibraryPageResponse.from(libraries.map(LibraryResponse::from));
}
```

### 3.3 Repository 메서드

- 파생 쿼리 조건(`And`, `Or`, `True`, `Containing` 등)이 **3개를 넘으면** `@Query`로 바꾸고, 의도를 드러내는 짧은 이름을 쓴다. 짧은 이름도 3.1의 동사 규칙을 따른다.
- 파생 쿼리를 직접 작성한 쿼리로 바꿀 때는 검색 조건뿐 아니라 **특수문자 처리, 대소문자 처리, 정렬, 빈 값과 `null` 처리까지 기존 의미를 유지**한다. 파생 쿼리의 `Containing`은 `%`와 `_`를 이스케이프하므로, 직접 쓴 `LIKE`도 같은 처리를 해야 한다.
- 바꾼 쿼리는 Repository 테스트로 기존과 같은 결과를 내는지 확인한다.

```java
// 나쁜 예
Page<Library> findAllByPlatformIdAndVisibleTrueAndLibraryNameContainingIgnoreCaseOrderByLibraryNameAsc(...);

// 좋은 예: 단건 연관관계는 @EntityGraph로 가져오고, LIKE 특수문자는 이스케이프
@EntityGraph(attributePaths = "platform")
@Query("""
    select l from Library l
    where l.platform.id = :platformId
      and l.visible = true
      and upper(l.libraryName) like upper(concat('%', :#{escape(#keyword)}, '%')) escape :#{escapeCharacter()}
    order by l.libraryName asc
    """)
Page<Library> findVisibleByPlatformAndName(
    @Param("platformId") Long platformId,
    @Param("keyword") String keyword,
    Pageable pageable
);
```

### 3.4 연관 데이터 조회와 페이징

- **컬렉션 fetch join과 페이징을 함께 쓰지 않는다.** Hibernate가 전체 결과를 가져와 메모리에서 페이지를 나눌 수 있다. 필요하면 식별자 페이지를 먼저 조회한 뒤 연관 데이터를 따로 조회한다.
- 단건 연관관계(`@ManyToOne`)는 `@EntityGraph`로 가져오는 것을 우선한다. `@Query`에 `join fetch`를 쓰고 페이징하면 `countQuery`를 따로 지정한다.
- 단건 연관관계의 fetch도 실제 SQL과 페이지 결과를 테스트로 확인한다.

---

## 4. 변수명 규칙

### 4.1 일반

- 클래스는 `PascalCase`, 변수와 메서드는 `camelCase`, 상수는 `UPPER_SNAKE_CASE`를 쓴다.
- 약어는 단어처럼 쓴다. `isbnCode`, `oauthToken`, `HttpClient`. 전부 대문자(`OAUTHToken`)로 쓰지 않는다.
- 업계 표준 약어(`id`, `url`, `isbn`, `jwt`, `dto`)만 허용한다. 자체 약어(`cnt`, `lib`, `pf`, `bk`)는 쓰지 않는다.
- 한 글자 변수는 람다의 짧은 범위에서만 허용한다. 그 외에는 대상 이름을 쓴다(`library`, `platform`).
- 같은 개념은 프로젝트 전체에서 같은 이름을 쓴다. 아래 용어집을 따른다.

### 4.2 boolean

- **접두어 없는 형용사나 과거분사**를 쓴다. `active`, `visible`, `favorite`, `withdrawn`.
- 능력이나 보유를 나타낼 때는 3인칭 동사를 쓴다. `supportsLibrary`, `hasNext`, `containsAudio`.
- 필드에 `is`를 붙이지 않는다. Lombok이 기본형 `boolean`에 대해 `isActive()` 게터를 만든다.
- boolean 필드는 기본형 `boolean`을 쓴다. 래퍼 `Boolean`은 "값 없음"이 의미가 있을 때(선택 요청 파라미터 등)만 쓴다.
- `Yn` 접미어는 내부 코드에서 쓰지 않는다. 이미 API에 나간 이름은 5장에 따라 `@JsonProperty`로 유지한다.

### 4.3 컬렉션, 시간, 단위

- 컬렉션은 복수형을 쓴다. `libraries`, `platformIds`. `libraryList` 같은 타입 접미어는 쓰지 않는다.
- `Map`은 `{값}By{키}` 형식을 쓴다. `platformsByBookId`.
- 시각은 `~At`(`createdAt`, `removedAt`), 날짜는 `~Date`(`publishedDate`), 기간은 `~Ttl`이나 `~Duration`을 쓴다.
- 숫자 단위가 타입으로 드러나지 않으면 이름에 붙인다. `timeoutMillis`, `retryDelaySeconds`.

### 4.4 용어집

| 개념 | 코드에서 쓰는 이름 | 쓰지 않는 이름 |
|---|---|---|
| 플랫폼 원본 도서 | `bookOrigin` | `book`, `originBook` |
| 플랫폼 내 도서 식별값 | `platformBookId` | `sourceBookId`, `bookCode` |
| 무료 전자도서관 | `library` | `lib`, `eLibrary` |
| 구독 플랫폼 지원 | `supportsSubscription` | `subscribe`, `isSubscribe` |
| 무료 도서관 지원 | `supportsLibrary` | `free`, `isFree` |
| 즐겨찾기 여부 | `favorite` | `favoriteYn`, `isFavorite` |
| 정규화된 값 | `xxxNorm` (엔티티), `normalizedXxx` (지역 변수) | `cleanXxx` |

> 용어집에 없는 새 개념을 추가하면 이 표에 먼저 등록한다. API에 이미 나간 이름(`sourceBookId` 등)은 5장에 따라 외부 이름을 유지한다.

---

## 5. API 계약 유지

API 계약은 프론트엔드가 의존하는 모든 외부 동작이다. 아래 항목은 리팩토링으로 바꾸지 않는다.

- URL, HTTP 메서드, 응답 상태 코드
- JSON 필드명, 요청 파라미터명
- 요청 파라미터의 기본값과 필수 여부
- 응답 필드의 자료형, `null`일 때 필드를 내보내는지 생략하는지
- 목록의 정렬 기준, 페이지 번호와 크기의 기준
- 오류 응답의 `code` 문자열과 형식
- 인증·인가 동작(어떤 API가 로그인 없이 열려 있는지 등)

내부 이름이 규칙과 다르면 내부 이름만 규칙에 맞추고, 외부 이름은 어노테이션으로 고정한다. 이름을 유지해도 자료형이나 `null` 처리가 바뀌면 계약이 바뀐 것이다.

```java
public record LibraryResponse(
    Long libraryId,
    String libraryName,
    String platform,
    String siteUrl,
    @JsonProperty("favoriteYn") boolean favorite
) { }

@GetMapping
public PlatformListResponse getPlatforms(
    @RequestParam(name = "isSubscribe", required = false) Boolean supportsSubscription
) { ... }
```

- 계약을 바꿔야 할 때는 프론트엔드와 별도로 합의하고 별도 커밋으로 진행한다.

---

## 6. 주석 규칙

### 6.1 문장 형식

**코드에 이미 드러난 내용은 주석으로 반복하지 않는다.** 주석에는 코드만 봐서는 알 수 없는 계약, 이유, 주의점만 쓴다.

| 쓰지 않는 것 | 이미 드러나는 곳 |
|---|---|
| 기본값, 페이지 크기 | `@PageableDefault(size = 8)` 같은 어노테이션 값, 필드 초기값(`= true`) |
| 응답 상태 | `ResponseEntity.noContent()` 같은 반환 코드 |
| 중복이나 없는 대상에 대한 처리 | `exists` 확인 후 `return`, `deleteBy` 한 줄 같은 메서드 본문 |
| 함께 조회하는 연관 데이터 | `@EntityGraph`, `join fetch` |
| private 메서드의 동작 | 몇 줄짜리 본문 그 자체 |

| 쓰는 것 | 예 |
|---|---|
| 호출하는 쪽이 지켜야 할 계약 | `받는 쪽은 엔티티 상태 변경이나 필드 기반 판단 금지` |
| 코드가 그렇게 된 이유 | `기존 파생 쿼리와 같은 의미를 위해 대소문자 무시, LIKE 특수문자 이스케이프` |
| 일부러 하지 않은 일 | `자동 매핑 신뢰도는 유지` |
| 코드 밖에서 정해진 사실 | `교보 무료도서관은 부모 코드의 이름이 도서관마다 달라 null로 운용` |
| 호출하는 쪽에서 안 보이는 동작 | Service가 하는 검색어 정규화를 Controller `@param`에 적는 경우 |

```java
// 나쁜 예: 본문과 어노테이션에 이미 있는 내용
/** 즐겨찾기 도서 추가, 이미 추가한 도서는 오류 없이 유지 */
/** 즐겨찾기 도서 목록 조회(최근 추가순), 기본 페이지 크기 8 */
/** 서비스 노출 여부, 생성 시 노출 상태 */

// 좋은 예
/** 즐겨찾기 도서 추가 */
/** 즐겨찾기 도서 목록 조회(최근 추가순) */
/** 서비스 노출 여부 */
```

모든 코드 주석(Javadoc, 필드 주석, 인라인 주석, `@param`, `@return`, `@throws`)은 **명사형으로 끝낸다**.

- `~다`, `~함`, `~임` 같은 서술형 어미를 쓰지 않는다. 명사나 명사구로 끝낸다.
- 끝에 마침표를 찍지 않는다.
- 한 주석에 정보가 둘 이상이면 쉼표로 잇는다. 한 줄에 다 들어가지 않으면 여러 줄 `/** */`로 쓰고, 각 줄을 명사형으로 끝낸다.
- 이 규칙은 주석에만 적용한다. 예외 메시지(10장)와 로그 메시지(13장)는 각 장의 형식을 따른다.

| 나쁜 예 | 좋은 예 |
|---|---|
| `플랫폼 코드. 외부 연동과 응답에서 플랫폼을 구분하는 키다.` | `외부 연동과 응답에서 플랫폼을 구분하는 키` |
| `플랫폼 목록을 이름순으로 조회한다.` | `플랫폼 목록 조회(이름순)` |
| `빈 문자열은 만들어지지 않는다.` | `빈 문자열 생성 불가` |
| `무료 전자도서관 서비스를 제공하는지 여부.` | `무료 전자도서관 서비스 제공 여부` |

메서드 설명의 첫 줄은 **"대상 + 동작"을 먼저 쓰고, 대상을 좁히거나 꾸미는 말은 뒤에 괄호로 붙인다**.

- 괄호에 넣는 것: 정렬 기준(이름순, 최근 추가순), 범위나 조건(플랫폼별, 사용자별, 활성, 노출), 용도(연관관계 설정용, 다른 도메인 제공용)
- 괄호 안에 여러 개면 쉼표로 잇는다. 괄호 앞에는 공백을 두지 않는다.
- 대상을 꾸미지 않는 부가 설명(호출 시 주의점 등)은 괄호 뒤에 쉼표로 잇는다. 단, 코드에 이미 드러난 내용이면 쓰지 않는다.
- 필드와 클래스 주석은 동작이 아니라 대상을 설명하므로 이 규칙을 적용하지 않는다.

| 나쁜 예 | 좋은 예 |
|---|---|
| `최근 추가순 즐겨찾기 도서 목록 조회` | `즐겨찾기 도서 목록 조회(최근 추가순)` |
| `플랫폼별 노출 도서관 이름순 검색` | `도서관 검색(플랫폼별, 노출 도서관, 이름순)` |
| `연관관계 설정용 활성 원본 도서 엔티티 참조` | `원본 도서 엔티티 참조(연관관계 설정용, 활성 도서)` |
| `연관관계 설정용 노출 도서관 엔티티 참조, 받는 쪽 상태 변경 금지` | `도서관 엔티티 참조(연관관계 설정용, 노출 도서관), 받는 쪽 상태 변경 금지` |

### 6.2 Javadoc

- 직접 작성하는 업무 클래스와 그 public 메서드에는 Javadoc을 쓴다.
- 다음에는 Javadoc을 강제하지 않는다. 테스트 클래스, 단순 `@Override` 구현, record 접근자, 이름으로 충분한 DTO와 Info record, private 메서드.
- 첫 줄은 "무엇을 하는가"를 명사형 한 줄로 쓴다. 이어지는 줄에 **왜** 그렇게 하는지, 예외 상황, 호출 시 주의점을 쓴다.
- 설명이 없는 `@param`, `@return`은 쓰지 않는다. 쓸 거면 의미, 허용 범위, `null` 가능 여부를 적는다.
- 발생하는 도메인 예외는 `@throws`로 적는다.
- 값이나 경우에 따라 동작이 나뉘면 6.3의 목록 형식으로 쓴다.

```java
/**
 * 플랫폼 목록 조회(이름순)
 *
 * <p>구독 여부별 조회 범위
 * <ul>
 *   <li>true: 구독 플랫폼</li>
 *   <li>false: 무료 도서관 플랫폼</li>
 *   <li>null: 전체 플랫폼</li>
 * </ul>
 */
public PlatformListResponse getPlatforms(Boolean supportsSubscription) { ... }

/**
 * 플랫폼 정보 단건 조회(다른 도메인 제공용)
 *
 * @throws PlatformException 플랫폼이 없는 경우 PLATFORM_NOT_FOUND
 */
public PlatformInfo getPlatformInfo(Long platformId) { ... }
```

```java
// 나쁜 예: 설명 없는 @param, @return
/**
 * 플랫폼 정보 조회
 * @param isSubscribe
 * @return
 */
```

### 6.3 값별 설명은 목록으로

파라미터 값, 상태, 조건에 따라 동작이 나뉘는 설명은 한 줄에 쉼표로 잇지 않는다. 경우마다 한 줄씩 목록으로 쓴다.

- 목록은 `@param` 안이 아니라 Javadoc 본문에 쓴다. `@param` 정렬 공백 때문에 줄이 흐트러지는 것을 막기 위해서다.
- 목록 앞에 `<p>`로 시작하는 명사형 제목 한 줄을 둔다. 무엇을 기준으로 나누는지 적는다.
- 목록은 `<ul>`과 `<li>`로 쓰고, 각 항목은 `값: 설명` 형식이다. 설명도 명사형으로 끝낸다.
- `<li>`는 `<ul>`보다 공백 2칸 더 들여쓴다.
- 두 가지 경우뿐이고 한 줄로 충분히 읽히면 목록을 쓰지 않아도 된다.

```java
// 나쁜 예: 경우를 쉼표로 이어 한 줄에 몰아 씀
/**
 * 플랫폼 목록 조회(이름순)
 *
 * @param supportsSubscription true면 구독 플랫폼만, false면 무료 도서관 플랫폼만,
 *                             null이면 전체 플랫폼 조회
 */

// 좋은 예
/**
 * 플랫폼 목록 조회(이름순)
 *
 * <p>구독 여부별 조회 범위
 * <ul>
 *   <li>true: 구독 플랫폼</li>
 *   <li>false: 무료 도서관 플랫폼</li>
 *   <li>null: 전체 플랫폼</li>
 * </ul>
 */
```

### 6.4 필드 주석

- 엔티티, DTO, VO의 필드 주석은 `/** */` 형식으로 통일한다. 한 줄을 기본으로 하고, 길어지면 6.1에 따라 여러 줄로 쓴다. `/* */`와 줄 끝 `//`는 쓰지 않는다.
- 이름만으로 충분한 필드(`id`, `createdAt`)에는 주석을 달지 않는다.
- 값의 형식, 출처, 정규화 규칙처럼 이름에 드러나지 않는 정보를 적는다.

```java
/** 플랫폼이 부여한 도서 식별값, 플랫폼 안에서만 유일 */
private String platformBookId;

/**
 * 검색용 정규화 제목
 * 공백과 특수문자를 제거하고 소문자로 바꾼 값
 */
private String titleNorm;
```

### 6.5 인라인 주석

- 코드가 **무엇을** 하는지가 아니라 **왜** 그렇게 하는지를 적는다.
- 개인 태그(`ponytail:` 등), 작업 이력, 주석 처리된 코드는 남기지 않는다. 이력은 git이 관리한다.
- 임시 조치에는 `// TODO(작성자): 이유` 형식을 쓴다. 이유도 명사형으로 끝낸다.

```java
// 같은 ISBN이 여러 플랫폼에 있으므로 플랫폼 ID까지 비교
// TODO(hedgehog): 국제 전화번호 형식 지원
```

---

## 7. DTO 규칙

- 요청은 `XxxRequest`, 응답은 `XxxResponse`, 다른 도메인에 주는 조회 정보는 `XxxInfo`로 끝나는 **record**로 만든다. 검색 조건 묶음은 `XxxCondition`을 쓴다.
- 엔티티에서 응답으로의 변환은 **응답 record의 정적 팩토리** `from(entity)`가 담당한다. 여러 값을 조합하면 `of(...)`를 쓴다.
- 엔티티는 DTO를 알지 못한다. 엔티티에 `toResponse()` 같은 메서드를 두지 않는다.
- Controller는 엔티티를 반환하지 않는다.
- 요청 검증은 `XxxRequest`에 Bean Validation(`@NotNull`, `@Size` 등)을 붙이고 Controller에서 `@Valid`로 실행한다.
- 새 목록 응답은 감싸는 record(`XxxListResponse`, `XxxPageResponse`)를 둔다. 기존 API 형식은 5장에 따라 유지한다.
- 쓰지 않는 필드를 `null`로 채워 두지 않는다. 필요할 때 추가한다. 단, 이미 API에 나간 필드는 5장에 따라 유지한다.

---

## 8. VO 규칙

- **정규화나 검증 규칙이 따라다니는 값만** VO로 만든다. 단순 문자열과 숫자는 그대로 쓴다.
- VO는 `vo` 패키지의 record로 만든다.
- **VO의 검증과 정규화는 생성자에서 보장한다.** 정적 팩토리는 생성자로 위임하며, 생성 경로에 따라 유효성이나 정규화 결과가 달라지지 않는다.
- 필수 값은 `of(raw)`, 비어 있을 수 있는 값은 `Optional<Vo>`를 반환하는 `from(raw)`로 만든다.
- 기존에 흩어진 정규화 로직을 VO로 모을 때, 기존 결과와 달라지는 부분이 있으면 동작 변경으로 보고 별도 커밋으로 분리한다.

도입 후보:

| VO | 이유 |
|---|---|
| `SearchKeyword` | 도서관 검색(공백 제거)과 도서 검색(유니코드 정규화, 소문자 변환)의 정규화 규칙이 다름. 규칙을 하나로 정한 뒤 도입 |
| `Isbn13` | 하이픈 제거와 자릿수 검증 |

```java
/** 앞뒤 공백을 제거한 검색 키워드, 빈 문자열 생성 불가 */
public record SearchKeyword(String value) {

    /** 검색 키워드 정규화와 유효성 검증 */
    public SearchKeyword {
        value = value == null ? null : value.trim();

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("검색 키워드는 비어 있을 수 없습니다.");
        }
    }

    /** 필수 검색 키워드 생성 */
    public static SearchKeyword of(String raw) {
        return new SearchKeyword(raw);
    }

    /** 입력이 비어 있는 경우 빈 Optional 반환 */
    public static Optional<SearchKeyword> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(of(raw));
    }
}
```

---

## 9. Entity 규칙

### 9.1 생성

- 클래스 레벨 `@Builder`와 `@AllArgsConstructor`를 쓰지 않는다.
- **생성 시점에 받을 필드만 가진 생성자**에 `@Builder`를 붙인다. `id`, 감사 필드, 상태 필드는 생성자 파라미터에서 뺀다.
- 생성자 안에서 필수값을 검증한다(`Objects.requireNonNull` 또는 도메인 검증). 필수값 기준은 DB의 `NOT NULL` 제약이다. DB가 null을 허용하는 컬럼을 엔티티에서 더 엄격하게 막지 않는다.
- 기본값이 있는 생성 필드(깊이, 정렬 순서 등)는 생성자에서 래퍼 타입으로 받고, `null`이면 상수로 정의한 기본값을 쓴다. 기본형으로 받으면 빌더에서 지정하지 않았을 때 0이 되어 기존 기본값이 조용히 바뀐다. 기준 예시는 `StandardCategory`다.
- 생명주기가 있는 엔티티(사용자, 소셜 계정, 즐겨찾기, 연동 해제 작업 등)는 의도를 드러내는 정적 팩토리(`create`, `createPending`)를 추가로 두고, 운영 코드는 팩토리만 쓴다.
- 생성 인자가 두세 개뿐이라 팩토리 인자로 충분하면 `@Builder` 없이 정적 팩토리만 둔다. 기준 예시는 `FavoriteBook`이다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 막는다.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "platform")
public class Platform extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_id")
    private Long id;

    /** 외부 연동과 응답에서 플랫폼을 구분하는 키 */
    @Column(name = "platform_code")
    private String platformCode;

    @Column(name = "platform_name")
    private String platformName;

    /** 무료 전자도서관 서비스 제공 여부 */
    @Column(name = "supports_library", nullable = false)
    private boolean supportsLibrary;

    /** 구독 서비스 제공 여부 */
    @Column(name = "supports_subscription", nullable = false)
    private boolean supportsSubscription;

    /** DB의 null 허용에 맞춘 플랫폼 코드와 이름의 필수 검증 생략 */
    @Builder
    private Platform(
        String platformCode,
        String platformName,
        boolean supportsLibrary,
        boolean supportsSubscription
    ) {
        this.platformCode = platformCode;
        this.platformName = platformName;
        this.supportsLibrary = supportsLibrary;
        this.supportsSubscription = supportsSubscription;
    }
}
```

### 9.2 상태 변경

- setter를 만들지 않는다(`@Setter`, `@Data` 금지).
- 상태 변경은 의도를 드러내는 메서드로만 한다. `withdraw()`, `markRemoved()`, `startProcessing()`.
- 엔티티는 허용되지 않은 상태 전이를 `IllegalStateException`으로 막는다. 이것은 내부 불변식 보호다.
- 사용자 요청으로 생길 수 있는 예상된 거절은 Service가 먼저 확인하고 도메인 예외로 표현한다(10.2).

```java
// Service: 예상 가능한 거절은 도메인 예외
if (!user.isActive()) {
    throw new UserException(UserErrorCode.USER_ALREADY_WITHDRAWN);
}
user.withdraw(now);

// Entity: 내부 불변식 보호
public void withdraw(LocalDateTime withdrawnAt) {
    if (!active) {
        throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
    }
    ...
}
```

### 9.3 매핑

- 연관관계는 기본 `FetchType.LAZY`로 둔다.
- 컬럼명, 테이블명은 `@Column`, `@Table`로 명시한다(`lower_snake_case`). 스키마는 `ddl-auto: validate`라 DB와 반드시 일치해야 한다.
- 매핑 속성(`nullable`, `length` 등)을 바꾸는 것은 스키마 계약 변경이다. 리팩토링에서 임의로 바꾸지 않는다.
- enum은 `@Enumerated(EnumType.STRING)`만 쓴다.

---

## 10. 예외 규칙

### 10.1 예외 클래스와 오류 코드

- 실패 상황이 있는 도메인은 `XxxErrorCode`(enum, `ErrorCode` 구현)와 `XxxException`(`BusinessException` 상속)을 한 쌍으로 둔다. 실패 상황이 생길 때 추가한다.
- 도메인 예외의 생성자는 **자기 도메인의 ErrorCode 타입만** 받는다. 다른 도메인 오류를 잘못 던지면 컴파일 단계에서 막힌다.
- 에러 코드 상수는 `대상_상태` 형식의 `UPPER_SNAKE_CASE`, 응답 코드 문자열은 같은 값의 `lower_snake_case`를 쓴다. `PLATFORM_NOT_FOUND` / `platform_not_found`.
- 이미 프론트에 나간 코드 문자열은 5장에 따라 바꾸지 않는다.
- 메시지는 사용자에게 보여도 되는 문장으로 쓰고 `~습니다.`로 끝낸다. 내부 식별값, SQL, 스택 정보를 넣지 않는다.
- ErrorCode enum의 반복 코드는 Lombok으로 줄인다.

```java
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PlatformErrorCode implements ErrorCode {

    PLATFORM_NOT_FOUND(HttpStatus.NOT_FOUND, "platform_not_found", "플랫폼 정보가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

public class PlatformException extends BusinessException {

    public PlatformException(PlatformErrorCode errorCode) {
        super(errorCode);
    }

    public PlatformException(PlatformErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
```

### 10.2 예외 종류 선택

| 상황 | 예외 |
|---|---|
| 비즈니스 규칙 위반, 사용자가 고칠 수 있는 오류 | 도메인 예외(`XxxException`) |
| 사용자 요청으로 생길 수 있는 예상된 상태 전이 거절 | 도메인 예외(`XxxException`) |
| 내부 구현의 불변식 위반, 일어나면 안 되는 상태 전이 | `IllegalStateException` |
| 생성자·팩토리의 필수값 누락 | `NullPointerException`(`Objects.requireNonNull`), `IllegalArgumentException` |
| 외부 API 실패 | 외부 클라이언트 전용 예외로 감싸고 원인(`cause`)을 반드시 넘김 |

- 상태 전이라고 해서 무조건 `IllegalStateException`으로 통일하지 않는다. 같은 상황이 사용자 요청으로 생길 수 있는지 기능 정책에 맞춰 판단한다.

### 10.3 처리 위치

- MVC 요청 처리 중 생긴 예외는 `GlobalExceptionHandler`에서 처리한다. Controller에서 `try-catch`로 응답을 만들지 않는다.
- Spring Security 필터 영역의 인증·인가 오류는 해당 보안 처리기(`JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `JwtExceptionHandlerFilter`, OAuth2 실패 처리기)에서 처리하되, `ErrorResponseWriter`로 공통 오류 응답 형식을 쓴다.
- Service는 예외를 잡아서 무시하지 않는다. 잡는다면 복구하거나, 더 의미 있는 예외로 감싸서 다시 던진다.
- 예외를 감쌀 때는 원래 예외를 `cause`로 넘겨 스택을 잃지 않는다.

### 10.4 오류 응답 형식

- 기본 형식은 `code`, `message` 두 필드다.
- **입력 검증(`@Valid`) 실패일 때만** `errors` 배열을 추가해 필드별 사유를 알려준다. 다른 오류에서는 `errors` 필드가 응답에 나타나지 않는다(`@JsonInclude(NON_EMPTY)`).

```json
{
  "code": "invalid_request",
  "message": "요청 값이 올바르지 않습니다.",
  "errors": [
    { "field": "keyword", "reason": "2자 이상 입력해야 합니다." }
  ]
}
```

---

## 11. 트랜잭션 규칙

### 11.1 선언 위치

- **트랜잭션이 필요한** Service의 public 메서드에 메서드 단위로 `@Transactional`을 선언한다. 클래스 레벨에는 선언하지 않는다.
- 조회 메서드는 `@Transactional(readOnly = true)`를 쓴다.
- 트랜잭션이 필요 없는 메서드(외부 API만 호출, 순수 계산, 캐시만 읽기)에는 선언하지 않는다.
- Controller, Repository, Entity에는 선언하지 않는다. 이벤트 리스너는 허용한다.

### 11.2 외부 작업과 트랜잭션 경계

- **외부 API 호출은 트랜잭션 밖에서 한다.** DB 커넥션을 잡은 채 네트워크를 기다리지 않기 위해서다.
- 기본 방식은 짧은 트랜잭션으로 상태를 바꾸고, 트랜잭션 밖에서 외부 호출을 한 뒤, 다시 짧은 트랜잭션으로 결과를 기록하는 것이다.
- 실패하면 안 되는 외부 작업은 **작업 상태를 DB에 기록하고 재시도하는 구조**로 만든다. 소셜 연동 해제(`SocialUnlinkTaskProcessor`, `SocialUnlinkTaskStateService`, `SocialUnlinkRetryScheduler`)가 기준 예시다.

### 11.3 커밋 후 이벤트

- `@TransactionalEventListener(phase = AFTER_COMMIT)`은 커밋 이후의 **실행 시점**만 뜻한다. 트랜잭션 자원이 반납됐다거나 외부 작업이 성공한다는 보장은 없다.
- 리스너에서 DB에 쓰려면 별도 트랜잭션(`REQUIRES_NEW`)을 명시한다. 그렇지 않으면 쓰기가 커밋되지 않을 수 있다.
- 리스너의 외부 작업이 실패했을 때의 정책(무시, 로그, 재시도 작업 기록)을 정하고 코드에 드러낸다.
- 소셜 연동 해제 리스너는 커밋 후 비동기로 실행되고, 상태 기록은 `REQUIRES_NEW`로 처리한다. 이 구조를 따른다.

### 11.4 자기 호출과 예외

- 기본 프록시 방식에서 자기 호출은 호출 대상 메서드의 `@Transactional` 설정을 새로 적용하지 않는다. 이미 시작된 트랜잭션은 유지될 수 있다. 별도의 트랜잭션 경계가 필요하면 다른 빈을 통해 호출한다.
- 트랜잭션 안에서 예외를 잡고 계속 진행하지 않는다. 롤백 표시가 남아 커밋 시점에 실패할 수 있다.

---

## 12. 테스트 규칙

### 12.1 테스트 전략

- JUnit 5, AssertJ, BDDMockito를 기본 테스트 도구로 사용한다.
- 비즈니스 로직 검증은 Mock 기반 단위 테스트를 우선한다.
- DB 동작이 테스트의 핵심인 경우 실제 MySQL 통합 테스트를 사용한다.
- 실제 DB 테스트는 Testcontainers MySQL을 사용한다.
- 모든 Service 테스트를 실제 DB 기반으로 작성하지 않는다.
- 테스트 종류는 검증하려는 동작과 외부 의존성에 따라 결정한다.

| 대상 | 방식 | 확인할 것 |
|---|---|---|
| Entity, VO | 순수 단위 테스트 | 생성 검증, 상태 전이, 정규화 |
| Service의 분기, 계산, 검증 | Mock 기반 단위 테스트(`XxxServiceTest`) | 반환 결과, 도메인 예외, 필요한 상태 변화와 외부 효과 |
| 영속성, 연관관계, 쿼리 결과가 핵심인 Service | 통합 테스트(`XxxServiceIntegrationTest`) | 저장된 데이터, 조회 결과, 오류 코드 |
| Controller | `@WebMvcTest` + MockMvc | URL, 파라미터 바인딩, 응답 JSON, 상태 코드, 오류 응답 |
| Repository | `@MySqlDataJpaTest`(`XxxRepositoryTest`) | 직접 작성한 `@Query`, FULLTEXT 검색, fetch 결과와 페이지 |
| 전체 흐름 | `@SpringBootTest` + Testcontainers MySQL | 트랜잭션 롤백·전파, 커밋 후 이벤트, 핵심 시나리오 |
| 외부 API 클라이언트 | Mock 서버(`MockRestServiceServer` 등) | 요청 형식, 응답과 오류 변환 |

### 12.2 단위 테스트

- Service의 분기, 계산, 검증, 예외 처리는 단위 테스트를 우선한다.
- Mockito로 외부 의존성을 대체한다.
- Spring Context가 필요하지 않으면 로딩하지 않는다.
- 구현 세부사항보다 비즈니스 동작을 검증한다.
- Mock 호출 횟수와 순서는 비즈니스 요구사항인 경우에만 검증한다. 예를 들어 "검증에 실패하면 외부 요청을 보내지 않는다"는 미호출 검증이 의미 있다.
- Mockito 단위 테스트는 트랜잭션을 검증하지 못한다. 롤백, 전파, 커밋 후 이벤트처럼 Spring 실행 환경에 의존하는 동작은 통합 테스트로 검증한다.

### 12.3 통합 테스트

- Repository의 직접 작성한 쿼리와 FULLTEXT 검색은 실제 DB로 검증한다.
- 영속성, 연관관계, 트랜잭션이 핵심인 Service는 통합 테스트를 작성한다.
- 리팩토링으로 내부 의존성 구조가 크게 변경되는 경우, 외부에서 관찰 가능한 동작을 고정하기 위한 통합 테스트를 고려한다.
- 통합 테스트에서는 필요한 계층만 실제 구현을 사용한다. Service 통합 테스트는 `@MySqlDataJpaTest`에 대상 Service와 그 Service가 부르는 Service만 `@Import`한다.
- 외부 API는 검증 대상이 아닌 경우 Mock으로 대체할 수 있다.
- 가능한 한 최소한의 Spring Context를 로딩한다.
- 준비 데이터는 엔티티 생성 방식과 무관하도록 SQL로 직접 넣을 수 있다. 기준 예시는 `FavoriteServiceIntegrationTest`다.
- 도서 검색이 MySQL FULLTEXT에 의존하므로 DB 테스트는 H2를 쓰지 않는다.
- Spring Data 파생 쿼리만 있는 Repository는 별도 테스트를 만들지 않아도 된다.

### 12.4 테스트 구분

- 테스트 클래스는 대상 클래스와 같은 패키지에 둔다.
- 단위 테스트는 `XxxServiceTest` 형식을 사용한다.
- Service 통합 테스트는 `XxxServiceIntegrationTest` 형식을 사용한다.
- Repository 테스트는 `XxxRepositoryTest` 형식을 사용한다. 트랜잭션 정책이 다른 FULLTEXT 테스트는 `XxxRepositoryFullTextTest`로 분리한다(12.12).
- 실제 DB가 필요한 테스트에는 `@Tag("db")`를 적용한다.
- DB 테스트는 공통 합성 어노테이션 `@MySqlDataJpaTest`를 우선 사용한다. `@DataJpaTest`, 내장 DB 교체 금지, 컨테이너 설정, `@Tag("db")`를 한 번에 적용한다.
- 테스트 실행 대상은 클래스 이름이 아닌 JUnit Tag로 구분한다.

```java
@MySqlDataJpaTest
@Import({LibraryService.class, PlatformService.class})
class LibraryServiceIntegrationTest {
    ...
}
```

### 12.5 테스트 실행

| 명령 | 실행 대상 |
|---|---|
| `./gradlew test` | DB가 필요 없는 테스트(`db` 태그 제외) |
| `./gradlew dbTest` | 실제 DB 통합 테스트(`db` 태그) |
| `./gradlew check` | 전체 테스트 및 검증(`test`와 `dbTest` 모두) |

- DB 테스트는 Docker 실행 환경이 필요하다.
- 전체 테스트는 CI에서 실행 가능해야 한다. CI의 테스트 단계는 `./gradlew check`를 실행한다.
- 작업 완료 보고(15.2)에는 실행한 명령을 적는다. `test`만 실행했다면 DB 테스트는 미검증으로 보고한다.

### 12.6 작성 형식

- 메서드 이름은 영어 `camelCase`로 상황과 결과를 쓰고, `@DisplayName`에 한국어 문장을 쓴다.
- 본문은 `// given`, `// when`, `// then` 세 구역으로 나눈다. 준비할 것이 없으면 `// given`을 생략한다.
- 단언은 **AssertJ**, 목 설정은 **BDDMockito**를 쓴다. `given(...).willReturn(...)` 외에 `willThrow(...).given(mock).method()` 같은 BDD API도 필요하면 쓴다. JUnit `assertEquals`와 Mockito `when`은 새로 쓰지 않는다. 기존 테스트는 그 기능을 리팩토링할 때 함께 바꾼다.
- MockMvc 테스트는 `status()`, `jsonPath()` 같은 MockMvc 전용 검증을 쓴다.
- 테스트 하나는 하나의 시나리오를 검증한다. 단언 하나를 뜻하지 않는다. 같은 시나리오의 관련 속성은 함께 검증한다.
- 현재 시각, 실행 순서, 공유 데이터에 의존하지 않는다. 시각이 필요하면 고정된 값을 주입한다.

### 12.7 예외 검증

- 도메인 예외는 예외 타입과 ErrorCode를 검증한다. 문자열 필드 이름 대신 공개 접근자를 쓴다.
- ErrorCode가 없는 일반 예외는 그 예외의 계약에 필요한 내용(타입, 필요하면 메시지)을 검증한다.

```java
@Test
@DisplayName("플랫폼이 없으면 PLATFORM_NOT_FOUND 예외 발생")
void getPlatformInfoThrowsWhenPlatformNotFound() {
    // given
    given(platformRepository.findById(1L)).willReturn(Optional.empty());

    // when, then
    assertThatThrownBy(() -> platformService.getPlatformInfo(1L))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(PlatformErrorCode.PLATFORM_NOT_FOUND)
        );
}
```

### 12.8 Controller 테스트

- Controller 테스트는 API 계약(5장)을 지키는 안전망이다. 응답 필드명, 자료형, 상태 코드를 반드시 검증한다.
- `@WebMvcTest`에는 대상과 무관한 웹 구성(보안 필터, 검색어 집계 설정 등)이 함께 올라온다. 의존성을 목으로 채우지 말고 `excludeFilters`로 제외한다. 기준 예시는 `PlatformControllerTest`다.
- 로그인 사용자가 필요한 Controller 테스트는 보안 필터를 끈 상태에서 `TestSecurityContextHolder`로 사용자를 지정하고 테스트가 끝나면 비운다. 기준 예시는 `FavoriteControllerTest`다.
- Refresh Token 쿠키를 다루는 Controller 테스트는 `RefreshTokenCookieTestConfig`를 `@Import`해 실제 쿠키 관리자와 고정된 쿠키 설정으로 `Set-Cookie` 헤더까지 검증한다.

### 12.9 테스트 DB 스키마

- 테스트 DB는 운영과 호환되는 MySQL 버전, collation, FULLTEXT 인덱스와 파서 설정을 쓴다.
- 스키마는 운영 DB에서 데이터 없이 추출한 DDL 파일을 테스트 리소스에 두고 컨테이너 시작 시 적용한다.
- 테스트에서도 `ddl-auto`는 `validate`로 두어, 엔티티와 스키마가 어긋나면 테스트가 실패하게 한다.
- 운영 스키마를 바꾸면 같은 변경에서 이 DDL 파일도 갱신한다.
- Redis가 필요한 테스트는 `RedisTestContainer`로 컨테이너 Redis에 연결하고 `@Tag("db")`를 붙인다. 로컬에 설치된 `redis-server`에 의존하지 않는다.
- JPA 기반 DB 테스트는 `@MySqlDataJpaTest`로 컨테이너 DB를 쓴다. 전체 컨텍스트가 필요한 `@SpringBootTest`는 `MySqlTestContainerConfig`를 `@Import`하고 `@Tag("db")`를 붙인다.
- 테스트는 실제 DB, Redis, 외부 API에 접속하지 않는다. `build.gradle`의 테스트 태스크가 접속 정보를 차단하며, 이 차단 설정을 지우거나 우회하지 않는다.

### 12.10 FULLTEXT 검색 테스트

InnoDB FULLTEXT 검색은 커밋된 데이터만 볼 수 있다. 같은 미커밋 트랜잭션 안에서 저장 후 검색하면 결과가 나오지 않는다. `saveAndFlush()`만으로는 부족하다.

- FULLTEXT 검색 테스트는 Testcontainers MySQL을 쓴다.
- FULLTEXT 테스트에서는 테스트 메서드의 자동 롤백 트랜잭션을 비활성화한다(`@Transactional(propagation = NOT_SUPPORTED)`).
- 테스트 데이터는 `TransactionTemplate`으로 명시적으로 커밋한다.
- 검색은 데이터 커밋 이후에 수행한다.
- 각 테스트는 독립적인 데이터 준비와 정리 과정을 갖는다.

```java
@MySqlDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookSearchRepositoryFullTextTest {

    @Test
    @DisplayName("커밋된 도서만 제목 FULLTEXT 검색 결과에 포함")
    void searchBooksFindsCommittedBookByTitle() {
        // given
        transactionTemplate.executeWithoutResult(status -> saveBook("자바의 정석"));

        // when
        Page<BookOrigin> result = bookRepository.searchBooks(titleCondition("자바"), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent())
            .extracting(BookOrigin::getTitleRaw)
            .containsExactly("자바의 정석");
    }
}
```

### 12.11 테스트 데이터 정리

- 테스트 데이터 정리는 `@BeforeEach`와 `@AfterEach`에서 한다. `@AfterEach`는 테스트가 실패해도 실행된다.
- FK 제약조건이 없는 독립 테이블에는 `TRUNCATE`를 쓸 수 있다. 다른 테이블이 참조하는 테이블은 MySQL에서 `TRUNCATE`할 수 없다.
- FK 연관관계가 있는 테이블은 자식 테이블부터 `DELETE`한다.
- 데이터 정리 중 FK 검증을 임의로 끄지 않는다.
- 테스트 클래스마다 정리하는 테이블 목록을 명시해 다른 테스트의 데이터 범위를 건드리지 않는다.
- 데이터 정리 실패를 무시하지 않는다. 정리에 실패하면 테스트도 실패해야 한다.

### 12.12 테스트 격리

- FULLTEXT 테스트와 일반 Repository 테스트의 트랜잭션 정책을 구분한다.
- 일반 Repository 테스트는 `@DataJpaTest`의 기본 롤백 정책을 유지한다.
- 같은 DB를 공유하면서 데이터를 커밋하거나 삭제하는 테스트는 병렬로 실행하지 않는다.
- 테스트 실행 순서에 의존하지 않는다.

### 12.13 테스트 데이터 생성

- 일반 시나리오의 테스트 데이터는 운영 코드와 같은 **공개 생성 경로를 우선** 쓴다. 정적 팩토리가 있는 엔티티는 팩토리로, 없는 엔티티는 생성자 `@Builder`로 만든다.
- 특정 상태(탈퇴한 사용자, 재시도 대기 작업 등)를 직접 구성해야 할 때만 별도 Fixture 도우미를 쓴다. 공개 메서드로 만들 수 있는 상태(탈퇴한 사용자는 `withdraw` 호출)는 공개 경로를 우선한다. 기준 예시는 `SocialUnlinkTaskFixture`다.
- 같은 데이터를 여러 테스트 클래스에서 만들면 테스트 패키지에 `XxxFixture` 클래스로 모은다.
- `id`가 필요한 단위 테스트는 테스트 도우미 `EntityIds.withId`로 넣는다. 운영 코드에 테스트용 setter나 빌더 `id` 항목을 만들지 않는다.
- 팩토리 자체를 검증하는 테스트와, 팩토리로 만든 데이터를 쓰는 일반 서비스 테스트를 구분한다.

### 12.14 리팩토링과 테스트

- 리팩토링 착수 전에 필요한 테스트 의존성, 테스트 DB 초기화, 로컬·CI 실행 환경을 먼저 확보한다.
- 리팩토링 전 기존 동작을 검증하는 테스트를 확보한다. 없으면 먼저 추가한다.
- 내부 구현이 변경되더라도 테스트의 기대 결과는 유지한다. 기대값이 바뀐다면 동작이 바뀐 것이므로 별도 커밋으로 분리한다.
- 테스트를 구현 방식에 과도하게 결합하지 않는다.
- 필요한 경우 Mock 단위 테스트와 통합 테스트를 함께 작성한다.
- 단위 테스트를 통합 테스트로 전환할 때는 검증 목적과 이유를 테스트 클래스 주석에 명시한다.
- 리팩토링을 이유로 모든 Mock 테스트를 통합 테스트로 전환하지 않는다.
- 환경 문제로 필요한 검증을 실행하지 못하면 그 항목을 **미검증**으로 표시하고 완료 처리하지 않는다.

---

## 13. 로깅 규칙

### 13.1 형식

- 로거는 Lombok `@Slf4j`를 쓴다.
- 메시지는 **한국어 문장 + 키=값** 형식으로 쓴다. 앞에는 무슨 일이 일어났는지, 뒤에는 추적에 필요한 식별값을 둔다.
- 문자열 더하기 대신 `{}` 자리표시자를 쓴다.
- 클래스명이나 메서드명 접두어(`OAuthService.validateResponse ::`)는 쓰지 않는다. 로거가 이미 클래스를 기록한다.

```java
log.warn("소셜 연동 해제 재시도 예약: taskId={}, provider={}, retryCount={}", taskId, provider, retryCount);
log.error("소셜 연동 해제 처리 중 예상하지 못한 오류: taskId={}", taskId, exception);
```

### 13.2 레벨

| 레벨 | 언제 | 예시 |
|---|---|---|
| `error` | 즉시 대응이 필요한 일, 5xx, 데이터 정합성 위험, 재시도 소진 | 처리되지 않은 서버 오류, 연동 해제 최종 실패 |
| `warn` | 지켜봐야 하지만 서비스는 계속되는 일 | 외부 API 실패 후 재시도 예정, 설정 누락으로 기능 비활성 |
| `info` | 예상된 사용자 오류(4xx 비즈니스 예외), 주요 상태 변화 | 플랫폼 없음, 스케줄러 갱신 완료, 회원 탈퇴 |
| `debug` | 개발 중 흐름 추적 | 쿼리 조건, 분기 결과 |

- `GlobalExceptionHandler`는 비즈니스 예외의 HTTP 상태가 4xx면 `info`, 5xx면 `error`로 남긴다.
- `error` 로그에는 예외 객체를 마지막 인자로 넘겨 스택을 남긴다. 예상된 외부 실패의 `warn` 로그는 예외 타입만 남겨도 된다.

### 13.3 금지 사항

- 토큰, 비밀번호, API 키, 암호화 키를 남기지 않는다.
- 이메일, 이름, 프로필 같은 개인정보와 사용자 검색어 원문을 남기지 않는다. 필요하면 내부 식별값(`userId`)을 쓴다.
- 같은 예외를 여러 계층에서 중복으로 남기지 않는다. 예외를 던지는 곳이 아니라 **처리하는 곳**에서 한 번만 남긴다.

---

## 14. 포매팅

- 들여쓰기는 공백 4칸, 줄 이어쓰기도 4칸이다.
- 한 줄은 120자를 넘기지 않는다.
- 파라미터나 인자가 한 줄에 들어가지 않으면 하나씩 줄을 바꾼다.
- 와일드카드 import(`*`)는 쓰지 않는다.
- 클래스 안의 순서는 상수, 필드, 생성자, public 메서드, private 메서드, 내부 타입이다.

---

## 15. AI 작업 규칙

AI(Claude, Codex 등)로 코드를 작성하거나 수정할 때 아래를 지킨다.

1. 작업 전에 이 문서와 [리팩토링 계획](./refactoring_plan.md)을 읽는다.
2. 요청받은 범위만 수정한다. 범위 밖에서 규칙 위반을 발견하면 수정하지 말고 보고한다.
3. 0.1에 따라 비즈니스 동작, API 계약, DB 스키마, 외부 데이터 형식을 바꾸지 않는다. 바꿔야 하면 작업을 멈추고 묻는다.
4. 새 개념이나 이름을 만들면 4.4 용어집에 맞추고, 없으면 용어집 추가를 제안한다.
5. 0.2에 따라 규칙을 기계적으로 적용하지 않는다. 불필요한 추상화, 메서드 분리, 클래스, 주석을 만들지 않는다. 코드에 이미 드러난 내용을 옮겨 적은 주석도 만들지 않는다(6.1).
6. 테스트를 실행하지 않았거나 실행 환경이 준비되지 않은 경우, 테스트 통과나 검증 완료로 보고하지 않는다.
7. 검증을 통과시키려고 테스트를 삭제하거나 비활성화하거나 기대 동작을 약화하지 않는다.
8. 작업을 마치면 아래 체크리스트를 확인하고, 15.2 형식으로 결과를 보고한다.

### 15.1 작업 완료 체크리스트

- [ ] 비즈니스 동작, API 계약(5장), DB 스키마가 바뀌지 않았는가
- [ ] 패키지 위치가 1장 구조와 맞고, 빈 패키지를 만들지 않았는가
- [ ] Controller가 Repository를 직접 부르거나 비즈니스 분기를 하지 않는가
- [ ] 다른 도메인의 Repository나 ErrorCode를 직접 쓰지 않는가, 도메인 간 순환이 없는가
- [ ] 다른 도메인에 엔티티 대신 `XxxInfo`를 넘기는가, 엔티티 참조는 연관관계 설정에만 쓰는가
- [ ] 메서드 이름이 3.1 동사 규칙을 따르는가
- [ ] 직접 쓴 쿼리가 기존 파생 쿼리와 같은 의미인가, 컬렉션 fetch join과 페이징을 함께 쓰지 않는가
- [ ] 변수명이 4장 규칙과 용어집을 따르는가
- [ ] 업무 메서드에 의미 있는 Javadoc이 있고, 모든 주석이 명사형으로 끝나며 마침표가 없는가
- [ ] 어노테이션 값, 필드 초기값, 반환 코드, 메서드 본문에 이미 드러난 내용을 주석으로 반복하지 않았는가
- [ ] 메서드 설명 첫 줄이 "대상 + 동작" 뒤에 정렬 기준, 조건, 용도를 괄호로 붙인 형식인가
- [ ] 값별로 나뉘는 설명을 쉼표로 잇지 않고 6.3 목록 형식으로 썼는가
- [ ] 엔티티에 setter나 클래스 레벨 `@Builder`가 없는가
- [ ] 예외를 10.2 기준으로 골랐는가, 예외를 잡아서 무시하지 않는가
- [ ] 트랜잭션이 필요한 메서드에만 메서드 단위로 선언했고, 트랜잭션 안에서 외부 API를 호출하지 않는가
- [ ] 로그가 한국어 문장 + 키=값 형식이고, 레벨이 13.2 기준에 맞으며, 민감 정보가 없는가
- [ ] 새로 쓴 테스트가 12장 형식을 따르는가, 실제 DB 테스트에 `@MySqlDataJpaTest`나 `@Tag("db")`가 있는가
- [ ] `./gradlew check`로 DB 테스트까지 실행했는가
- [ ] 관련 테스트를 실제로 실행했는가

### 15.2 작업 결과 보고 형식

```
작업 결과

변경 범위:
- 변경한 기능과 파일

동작 유지:
- 비즈니스 동작, API 계약, DB 스키마, 외부 데이터 형식 변경 여부

검증:
- 실제 실행한 명령과 결과
- 실행하지 못한 검증과 이유
- 작업 전부터 있던 실패와 이번 변경으로 생긴 실패의 구분

남은 사항:
- 범위 밖에서 발견한 문제
- 별도 합의가 필요한 사항

문서:
- 갱신한 문서와 변경 이유
```
