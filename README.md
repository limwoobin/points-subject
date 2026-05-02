# 무료 포인트 시스템 (points-subject)

회원별 무료 포인트의 **적립 / 적립취소 / 사용 / 사용취소** 4가지 상태 변경 행위와 잔액·이력 조회를 처리하는 Spring Boot REST API 입니다.

---

## 1. 기술 스택

| 분류 | 선택 | 비고 |
| --- | --- | --- |
| Language | Java 21 | Gradle toolchain 으로 강제합니다 |
| Framework | Spring Boot 3.5.14 | 3.x 마지막 minor (LTS 2032-06-30) |
| Persistence | Spring Data JPA + H2 (in-memory, MySQL mode) | 단일 인스턴스 전제입니다 |
| Validation | Bean Validation (Jakarta) | |
| 외부화 정책 | FF4j 2.1 (JDBC store + Web Console) | 무중단 정책 변경을 위해 도입했습니다 |
| Build | Gradle (Groovy DSL) | wrapper 가 함께 커밋되어 있습니다 |
| Test | JUnit 5 | |

> Gradle 자체가 JVM 17+ 를 요구합니다. `JAVA_HOME` 이 JDK 11 을 가리키면 `./gradlew` 가 실행 자체에서 실패하니, JDK 17 또는 21 로 설정해 주시기 바랍니다.

### 1.1 PRD 충족 현황

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| 기능 — 적립 / 적립취소 | ✅ | 구현 완료 |
| 기능 — 사용 | ✅ | 구현 완료 (분배 알고리즘 + orderNumber 멱등 차단) |
| 기능 — 사용취소 | ⏳ | 미구현 (재발급 로직 + cancel-use row + 카운터 갱신) |
| ERD 이미지 (필수) | ❌ | **본 저장소에는 아직 포함되어 있지 않습니다.** 추후 별도로 첨부할 예정입니다 |
| AWS 아키텍처 (옵션) | — | 본 과제 범위에 포함하지 않았습니다 |
| README | ✅ | 본 문서 |

> `src/main/resources/img.png` 는 FF4j Web Console 화면 캡처입니다. ERD 가 아닙니다 — README §3.1 에서만 참조합니다.

## 2. 빌드 및 실행

```bash
./gradlew bootRun                     # 애플리케이션 실행 (기본 포트 8080)
./gradlew build                       # 전체 빌드 + 테스트
./gradlew test                        # 테스트만
./gradlew test --tests "*EarnTest"    # 특정 테스트
./gradlew clean
```

기동 후 접근 경로는 다음과 같습니다.

| 경로 | 용도 |
| --- | --- |
| `http://localhost:8080/h2-console` | H2 콘솔 (JDBC URL: `jdbc:h2:mem:points`) |
| `http://localhost:8080/ff4j-console` | FF4j Web Console — 정책 즉시 변경 |
| `http://localhost:8080/api/ff4j/store/properties/{key}` | FF4j REST API |

## 3. 핵심 설계 결정

### 3.1 외부화 설정 — FF4j

요구사항의 "**하드코딩이 아닌 방법으로 제어 가능**" 두 항목(1회 적립 한도, 회원별 보유 한도)의 본질은 **운영 중 무중단 변경**이라고 판단했습니다. `application.yml` + `@ConfigurationProperties` 만으로는 재배포가 필요해 그 정신을 충족하지 못합니다.

따라서 정책을 DB(H2 재사용)에 저장하고 Web Console / REST API 로 즉시 변경 가능한 **FF4j** 를 채택했습니다.

| 비교 대안 | 채택하지 않은 이유 |
| --- | --- |
| `application.yml` + `@ConfigurationProperties` | 재시작이 필요해 "하드코딩 금지"의 정신을 충족하지 못합니다 |
| DB 정책 테이블 자체 구현 + Admin API | Web Console / Audit / REST 를 모두 직접 구축해야 합니다 (FF4j 비호환 시 fallback 으로만 사용) |
| Spring Cloud Config / Consul KV | 다중 인스턴스 분산 환경용으로, 본 과제 범위를 초과합니다 |
| Togglz / Unleash / LaunchDarkly | Property 기능이 약하거나 SaaS 의존성이 큽니다 |

운영 모델은 다음과 같습니다.

```
[부팅 시]   PointPolicyBootstrapper  →  application.yml 의 시드 값을 FF4J_PROPERTIES 에 INSERT (미존재 키만)
[런타임]    PointPolicyService(facade)  →  ff4j.getProperty(key) 만 호출. 도메인 코드는 facade 만 의존합니다.
[변경]      Web Console / REST  →  즉시 반영 (캐시 자동 무효화), FF4J_AUDIT 에 변경 이력 자동 기록
```

FF4j Web Console 화면은 다음과 같습니다 — 운영자가 정책 값을 즉시 변경할 수 있고, 변경 이력은 `FF4J_AUDIT` 테이블에 자동으로 남습니다.

![FF4j Web Console](src/main/resources/img.png)

관리 대상 키는 7종입니다.

| 키 | 의미 | 시드 |
| --- | --- | --- |
| `points.earn.min-per-transaction` | 1회 적립 하한 | 1 |
| `points.earn.max-per-transaction` | 1회 적립 한도 | 100,000 |
| `points.balance.max-per-user` | 회원별 보유 한도 (글로벌 default) | 1,000,000 |
| `points.expiry.default-days` | 만료일 기본값 | 365 |
| `points.expiry.min-days` | 만료일 하한 | 1 |
| `points.expiry.max-days` | 만료일 상한 (5년 미만) | 1825 |
| `points.use-cancel.reissue-days` | 만료된 원본 → 신규 적립 만료일 | 365 |

회원별 보유 한도 override 는 `point_user.max_balance` 컬럼이 별도로 담당합니다 (FF4j 는 글로벌 default 만 보관합니다).

```
effectiveLimit = COALESCE(point_user.max_balance, FF4j[points.balance.max-per-user])
```

### 3.2 테이블 설계 — 4 + 1

요구사항의 4가지 행위(적립/적립취소/사용/사용취소)에 4개 테이블을 만들지 않습니다. 카디널리티 + 도메인 응집도를 기준으로 **4 + 1** 로 정리했습니다.

| 테이블 | 역할 |
| --- | --- |
| `point_earn` | 적립 원장. 적립취소(1:0..1)는 같은 row 의 `status` / `cancelled_at` 으로 통합합니다 |
| `point_use` | **사용 + 사용취소 통합** (single-table inheritance, `type` 컬럼으로 row 구분) |
| `point_use_detail` | **사용 ↔ 적립 매핑 (1원 단위 추적의 핵심)** |
| `point_user` | **회원별 한도 정책 + 동시성 락 row (dual purpose)** |

도식은 다음과 같습니다.

```
       ┌──────────────────────┐
       │     point_user       │  ← @Lock(PESSIMISTIC_WRITE) 대상
       │ (max_balance, 락 row)│
       └──────────────────────┘

  ┌─────────────┐    1                       N    ┌──────────────┐
  │ point_earn  │────< point_use_detail >────────│  point_use   │
  │ (+ cancel   │  N    use_id, earn_id         1│  (type=USE)  │
  │   통합)     │                                  └──────────────┘
  └─────────────┘                                         ▲ 1
       ▲                                                  │
       │ origin_use_cancel_id                             │ target_use_id
       │ (재적립 시)                                      │
       │                                            N ┌──────────────────┐
       └───────────────────────────────────────────── │   point_use      │
                                                      │ (type=USE_CANCEL)│
                                                      └──────────────────┘
```

핵심 결정은 다음과 같습니다.

- **각 적립건이 잔여액(`remaining_amount`)과 만료일(`expires_at`)을 가지는 독립 단위**입니다. 회원 잔액은 이 합계로 도출하며 별도 캐시는 도입하지 않았습니다.
- **PK = `Long id` IDENTITY** 입니다. `id` 자체가 PRD 의 "pointKey" 역할을 합니다. 적립취소는 별도 pointKey 없이 원본 `id` 를 그대로 사용합니다.
- **사용 우선순위**: 수기 → 만료 임박 → 적립일 빠른 순 (PRD 요구사항).
- **사용취소 복원**: 차감 역순(`point_use_detail.created_at DESC`)으로 복원합니다. 만료된 원본은 신규 `point_earn` 으로 재발급합니다 (`origin = USE_CANCEL_REISSUE`).
- **잔액 한도 / 1회 적립 한도 검증은 `USE_CANCEL_REISSUE` 에는 적용하지 않습니다.** 사용자가 정상 사용·취소했던 포인트가 한도 때문에 사라지는 모순을 방지하기 위함입니다.

### 3.3 동시성 — 회원 단위 비관적 락

같은 회원의 사용/사용취소가 동시에 들어오면 잔액 음수 또는 이중 차감 위험이 있습니다. 이를 막기 위해 **`point_user` row 를 회원 단위 락 대상**으로 사용합니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select u from PointUser u where u.userId = :userId")
Optional<PointUser> findForUpdate(@Param("userId") Long userId);
```

- 단일 row 락이라 데드락 위험이 매우 낮습니다.
- 회원 첫 거래 시 upsert 합니다 (없으면 INSERT, 있으면 SELECT FOR UPDATE).
- 안전망으로 `point_earn` / `point_user` 에 `@Version`(낙관적 락)을 둡니다. 비관적 락이 정상 동작하면 충돌은 0 이며, 충돌 시에는 즉시 실패시키고 재시도 로직은 두지 않습니다.

**분산락 / 잔액 캐시 / 메시지 큐는 도입하지 않았습니다.** 단일 인스턴스 + H2 환경에서는 명백한 오버엔지니어링이라 의도적으로 제외했습니다.

### 3.4 사용 / 사용취소 API 멱등성

**사용/사용취소 API 는 자연 멱등 키로 중복 요청을 차단합니다.** 비관적 락은 동시 요청 사이의 정합성만 보장하고, **시간차를 둔 클라이언트 재시도는 막지 못하기** 때문입니다.

| 행위 | 멱등 키 | 저장 위치 |
| --- | --- | --- |
| 사용 | `order_number` (PRD 요구사항으로 이미 존재) | `point_use.order_number` (DB UK 없이 application 레벨 중복 차단 — `point_use` 가 향후 `USE_CANCEL` row 도 담는 단일 테이블로 확장될 예정이라 컬럼 단위 UK 가 의미상 깔끔하지 않음) |
| 사용취소 | `cancellation_key` (클라이언트 발급) | `point_use(type=USE_CANCEL).cancellation_key` (§3.4 작업 시 컬럼 추가) |

회원 락 안에서 키로 기존 row 조회 → 있으면 **`409 Conflict` 로 명시 실패**시키고 부수효과 없이 거부합니다 (replay 모드 미채택 — 같은 주문에 두 번 사용되는 시나리오 자체가 비정상이라는 판단).

적립 / 적립취소는 멱등 키를 강제하지 않습니다. 운영자 수기 적립은 같은 회원에게 의도적으로 N회 누르는 시나리오가 정상이며, 적립취소는 `earnId` 1:0..1 관계로 자연 중복이 방지되기 때문입니다.

### 3.5 글로벌 예외 처리

도메인 예외 메타데이터(`code`, `message`, `httpStatus`) 3종을 **`PointErrorCode` enum 한 곳**에 응집합니다. 도메인 코드는 `throw new PointException(PointErrorCode.X)` 하나만 사용하고, `@RestControllerAdvice` 인 `GlobalExceptionHandler` 가 enum 정보로 응답 포맷을 조립합니다.

표준 응답:
```json
{ "code": "POINT-103", "message": "회원 보유 한도를 초과합니다" }
```

검증 실패 시 `errors` 가 추가됩니다.
```json
{ "code": "POINT-001", "message": "요청 값 검증에 실패했습니다",
  "errors": [{ "field": "amount", "reason": "must be greater than 0" }] }
```

`GlobalExceptionHandler` 매핑은 다음과 같습니다.

| 예외 | HTTP | code |
| --- | --- | --- |
| `PointException` | `errorCode.httpStatus` | `errorCode.code` |
| `MethodArgumentNotValidException` (`@RequestBody @Valid`) | 400 | `POINT-001` + `errors[]` |
| `ConstraintViolationException` (`@Validated` 파라미터) | 400 | `POINT-001` + `errors[]` |
| `MissingServletRequestParameterException` | 400 | `POINT-001` + 누락 파라미터명 |
| `MethodArgumentTypeMismatchException` | 400 | `POINT-001` + 필요 타입 |
| `HttpMessageNotReadableException` | 400 | `POINT-001` |
| `HttpRequestMethodNotSupportedException` | 400 | `POINT-001` |
| `NoHandlerFoundException` | 400 | `POINT-001` |
| `Exception` (catch-all) | 500 | `POINT-099` (스택은 로그에만 남기고 응답에는 노출하지 않습니다) |

에러 코드 prefix 규칙은 `POINT-{도메인}{일련}` 입니다 (0=공통 / 1=적립 / 2=적립취소 / 3=사용 / 4=사용취소). 새 예외는 enum 한 줄 추가만으로 정의됩니다.

### 3.6 Layer 구조 (CQRS-light)

3-layer 위에 Service 내부에서 Command/Query 를 분리합니다.

```
controller/                  Presentation: @RestController + Bean Validation
  dto/                       Request/Response (record)

service/command/             상태 변경 (적립/적립취소/사용/사용취소). @Transactional, 회원 락
  dto/                       Command/Result
service/query/               조회 (잔액/이력). @Transactional(readOnly=true), 락 미획득
  dto/                       Query/View

domain/entity/               JPA Entity (BaseEntity 상속)
domain/enums/                도메인 enum (PointSource, PointOrigin, EarnStatus …)
repository/                  Spring Data Repository

policy/                      PointPolicyService (FF4j facade), Bootstrapper, PolicyKey
exception/                   PointErrorCode enum, PointException
config/                      FF4j 빈, JPA Auditing
```

레이어 객체는 자기 레이어 바깥으로 새지 않습니다. Entity 는 Controller 로 가지 않고, Request 는 Service 안으로 들어가지 않으며, Service 가 직접 Entity ↔ Result 변환을 담당합니다.

### 3.7 모든 엔티티 공통: audit 5종 + soft-delete (`BaseEntity`)

| 컬럼 | 설명 |
| --- | --- |
| `created_at`, `created_by` | `@CreatedDate`, `@CreatedBy` (`AuditorAware<String>` 가 공급합니다) |
| `updated_at`, `updated_by` | `@LastModifiedDate`, `@LastModifiedBy` |
| `deleted_at` | NULL = 활성 상태입니다. `softDelete()` 또는 `repository.delete(...)` 모두 채웁니다 |

- 클래스 단위 `@SQLDelete` 가 `repository.delete(...)` 호출도 UPDATE 로 변환해 hard-delete 를 차단합니다.
- 클래스 단위 `@SQLRestriction("deleted_at IS NULL")` 가 모든 SELECT/JPQL 에 활성 row 필터를 자동으로 적용합니다.
- 도메인 코드는 deleted 여부를 의식할 필요가 없습니다.

## 4. API 엔드포인트

| Method | Path | 종류 | 설명 | 상태 |
| --- | --- | --- | --- | --- |
| POST | `/api/points/earn` | Command | 포인트 적립 | ✅ |
| POST | `/api/points/earn/{earnId}/cancel` | Command | 적립 취소 | ✅ |
| POST | `/api/points/use` | Command | 포인트 사용 (`order_number` 중복 시 409 — DB UK 없이 application 레벨 차단) | ✅ |
| POST | `/api/points/use/{useId}/cancel` | Command | 사용 취소 — 전체/부분 (멱등 키: `cancellation_key`) | ⏳ 미구현 |
| GET | `/api/points/users/{userId}/balance` | Query | 잔액 조회 | ⏳ 미구현 |
| GET | `/api/points/users/{userId}/history` | Query | 거래 이력 조회 | ⏳ 미구현 |
| PUT | `/api/admin/users/{userId}/max-balance` | Admin | 회원별 보유 한도 override (NULL 전송 시 글로벌 default 회귀) | ✅ |

> ⏳ 표시는 README 의 다른 섹션(§3.2, §3.4 등) 에 설계 의도가 기술되어 있지만 본 저장소 시점에서 코드 구현이 아직 완료되지 않은 항목입니다.

> 본 과제에는 인증/인가가 도입되어 있지 않습니다. `/api/admin/*` 와 `/ff4j-console`, `/h2-console` 은 운영 배포 시 Spring Security 또는 reverse proxy 인증 게이트로 반드시 보호해 주셔야 합니다.

## 5. 운영 한계

본 과제 범위에 의도적으로 포함하지 **않은** 항목입니다. 운영 환경 마이그레이션 시 검토 대상입니다.

| 항목 | 본 과제 | 운영 환경 |
| --- | --- | --- |
| 인증/인가 | 없음 | Spring Security + admin 경로 분리 |
| H2 in-memory | 재기동 시 데이터 휘발 | RDS (MySQL/PostgreSQL) + 운영 마이그레이션 (Flyway/Liquibase) |
| H2 Console / FF4j Console | 항상 활성 | dev profile 한정 또는 인증 게이트 뒤로 |
| 분산락 | DB 비관적 락 (`point_user`) | + Redisson / pg_advisory_lock (다중 인스턴스 시) |
| 잔액 캐시 | 없음 (매 조회 시 SUM) | 비권장 — read-heavy 압력이 입증되면 `point_user.balance` 컬럼 형태로 추가 |
| 메시지 큐 | 없음 | Kafka/SQS — 트랜잭션 커밋 후 발행 (락 보유 시간 폭증 방지) |
| 만료 처리 | 조회 시점 동적 판정 | 데이터 폭증 시 야간 batch 로 마킹 검토 |

## 6. 디렉터리

```
.
├── build.gradle                        Java 21 toolchain, Spring Boot 3.5.14, FF4j 2.1
├── src/main/
│   ├── java/com/example/pointssubject/
│   │   ├── PointsSubjectApplication.java
│   │   ├── config/                     FF4j, JPA Auditing
│   │   ├── controller/                 + GlobalExceptionHandler, dto/
│   │   ├── service/command/            + dto/  (state-changing)
│   │   ├── service/query/              + dto/  (read-only, 미구현 — 잔액/이력 조회 향후 추가 예정)
│   │   ├── domain/entity/              BaseEntity + 엔티티
│   │   ├── domain/enums/
│   │   ├── repository/                 Spring Data
│   │   ├── policy/                     PointPolicyService (FF4j facade), Bootstrapper
│   │   └── exception/                  PointErrorCode, PointException
│   └── resources/
│       └── application.yml             H2 + FF4j 시드 + Hibernate 설정
└── src/test/                           JUnit 5
```
