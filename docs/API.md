# API 명세 — points-subject

[← README 로 돌아가기](../README.md)

이 문서는 points-subject 의 모든 REST 엔드포인트 상세 명세입니다. 도메인 설계 의도와 흐름은 README 본문(§5 ERD, §6 금액 흐름)에서 다루고, 이 문서는 **각 엔드포인트의 의도 + Request/Response + 에러 매핑** 에 집중합니다.

## 목차

- [인프라 / 기술 선택](#인프라--기술-선택)
- [공통 응답 포맷](#공통-응답-포맷)
- [에러 코드](#에러-코드)
- 일반 사용자 API
  - [POST /api/points/earn — 시스템 자동 적립](#post-apipointsearn--시스템-자동-적립)
  - [POST /api/points/earn/{earnId}/cancel — 적립 취소](#post-apipointsearnearnidcancel--적립-취소)
  - [POST /api/points/use — 포인트 사용](#post-apipointsuse--포인트-사용)
  - [POST /api/points/cancel — 사용 취소](#post-apipointscancel--사용-취소-전체--부분)
  - [GET /api/points/users/{userId}/balance — 잔액 조회](#get-apipointsusersuseridbalance--잔액-조회)
  - [GET /api/points/users/{userId}/history — 거래 이력](#get-apipointsusersuseridhistory--거래-이력)
- 운영자 API
  - [POST /api/admin/points/earn — 운영자 수기 적립](#post-apiadminpointsearn--운영자-수기-적립)
  - [PUT /api/admin/users/{userId}/max-balance — 회원별 보유 한도 개별 설정](#put-apiadminusersuseridmax-balance--회원별-보유-한도-개별-설정)

---

## 인프라 / 기술 선택

본 API 가 운영 환경에서 사용한다고 가정하는 핵심 인프라와 사유. 상세 토폴로지는 [`docs/AWS.md`](AWS.md) 참조.

| 영역 | 선택 | 사유 |
| --- | --- | --- |
| **영속성** | MySQL (Writer + Reader) | 본 코드의 H2 MySQL mode 와 dialect 호환 → 별도 schema 변환 불필요. Writer 가 트랜잭션 전담, Reader 로 read 부하 분산. |
| **캐시 + 분산락** | Redis | 멀티 인스턴스 환경에서 회원 단위 직렬화 (DB 까지 락 경쟁이 전파되는 것을 1차에서 흡수) + 잔액 미리보기 read 부하 캐싱. **결제 트랜잭션 자체는 캐시 우회** (정확성=DB, 미리보기=Redis 분리) |
| **비동기 메시징** | Kafka | 적립/적립취소 spike (프로모션 시점 등) 흡수 — Consumer 가 자기 페이스로 처리. at-least-once + 파티션 키 (`external_event_id`) 멱등으로 중복 도착에서도 안전 |

---

## 공통 응답 포맷

**성공 응답** — 엔드포인트별 본문(아래 각 섹션 참조). HTTP status 는 생성형은 `201 Created`, 조회/변경은 `200 OK`.

**에러 응답** — `ErrorResponse` 단일 포맷.

```json
{
  "code": "POINT-103",
  "message": "회원 보유 한도를 초과합니다"
}
```

검증 실패 시 `errors` 배열이 추가됩니다.

```json
{
  "code": "POINT-001",
  "message": "요청 값 검증에 실패했습니다",
  "errors": [
    { "field": "amount", "reason": "must be greater than 0" }
  ]
}
```

## 에러 코드

`POINT-{도메인}{일련}` 체계. 0=공통 / 1=적립 / 2=적립취소 / 3=사용 / 4=사용취소.

> **공통 에러 (모든 엔드포인트 공통 발생 가능)**: `POINT-001`(요청 검증 실패), `POINT-099`(catch-all 500). 각 엔드포인트의 *가능 에러* 목록에는 도메인 특화 에러만 나열하고, 이 두 코드는 별도로 명시하지 않습니다.

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| `POINT-001` | 400 | 요청 값 검증 실패 (`@Valid` 위반, 필수 파라미터 누락, 타입 불일치 등) |
| `POINT-099` | 500 | 서버 내부 오류 (스택은 로그에만, 응답엔 미노출) |
| `POINT-101` | 400 | 1회 적립 금액 범위 초과 (FF4j `points.earn.min/max-per-transaction`) |
| `POINT-102` | 400 | 만료일 범위 초과 (FF4j `points.expiry.min/max-days`) |
| `POINT-103` | 409 | 회원 보유 한도 초과 |
| `POINT-201` | 404 | 적립 건 없음 (또는 ownership 위반) |
| `POINT-202` | 409 | 사용된 적립 / 이미 취소된 적립은 취소 불가 |
| `POINT-301` | 409 | 보유 잔액 부족 |
| `POINT-302` | 409 | `orderNumber` 중복 (이미 처리된 주문) |
| `POINT-401` | 404 | 사용 건 없음 (또는 ownership 위반) |
| `POINT-402` | 409 | 취소 가능 금액 초과 |
| `POINT-403` | 409 | `orderRefundId` 중복 (이미 처리된 환불) |

---

## `POST /api/points/earn` — 시스템 자동 적립

**왜 필요한가**
회원의 행위(이벤트 참여, 외부 시스템 연동 등) 결과로 발생하는 일반 적립을 처리합니다. `MANUAL`(운영자 수기) 과 식별을 분리하기 위해 별도 엔드포인트로 두었으며, 이 경로로 들어온 적립은 `type=SYSTEM` 으로 기록되고 사용 우선순위에서 후순위가 됩니다.

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | Long | ✅ | 양수 |
| `amount` | Long | ✅ | 양수, FF4j 정책 `points.earn.min ~ max` 범위 (기본 1~100,000) |
| `expiryDays` | Integer | ⬜ | 미지정 시 FF4j `points.expiry.default-days` (기본 365) 적용. 1 이상, 1825 미만 |

**Response — `201 Created`**

```json
{
  "earnId": 10,
  "userId": 1,
  "amount": 1000,
  "type": "SYSTEM",
  "expiresAt": "2027-05-03T14:00:00"
}
```

**예시**

```bash
curl -X POST http://localhost:8080/api/points/earn \
  -H 'Content-Type: application/json' \
  -d '{ "userId": 1, "amount": 1000, "expiryDays": 365 }'
```

**가능 에러**: `POINT-101`, `POINT-102`, `POINT-103`

---

## `POST /api/points/earn/{earnId}/cancel` — 적립 취소

**왜 필요한가**
적립 발생 자체를 무효화해야 하는 경우(잘못 지급, 정책 위반 발견 등) 사용합니다. 핵심 invariant 는 **"한 번이라도 사용된 적립은 취소 불가"** — 이미 사용된 포인트를 사후에 회수하면 사용처(주문)와의 정합성이 깨지기 때문입니다 (`PointEarn.isCancellable() = ACTIVE && remaining == initial`).

**Path / Body**

| 위치 | 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| path | `earnId` | Long | ✅ | 양수, `point_earn.id` |
| body | `userId` | Long | ✅ | 양수, ownership 검증용 (다른 회원 적립 취소 차단) |

**Response — `200 OK`**

```json
{
  "earnId": 10,
  "userId": 1,
  "status": "CANCELLED",
  "cancelledAt": "2026-05-03T14:25:00"
}
```

**예시**

```bash
curl -X POST http://localhost:8080/api/points/earn/10/cancel \
  -H 'Content-Type: application/json' \
  -d '{ "userId": 1 }'
```

**가능 에러**: `POINT-201` (없음/ownership), `POINT-202` (사용/취소 이력)

---

## `POST /api/points/use` — 포인트 사용

**왜 필요한가**
주문 결제 시 포인트를 차감합니다. **`orderNumber` 가 자연 멱등 키** — 같은 주문번호로 두 번 사용 자체가 비정상이라 보고 두 번째 호출은 `409 POINT-302` 로 거부합니다 (네트워크 재시도가 아닌 클라이언트 버그로 판단). 차감은 **수기 → 만료 임박 → 적립일 빠른 순** 으로 우선순위 정렬된 적립 후보들에서 FIFO 분배되며, 응답의 `allocations` 가 1원 단위 분배 결과를 그대로 노출합니다 (README §6 참조).

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | Long | ✅ | 양수 |
| `orderNumber` | String | ✅ | 1~64자, 멱등 키 |
| `amount` | Long | ✅ | 양수 |

**Response — `201 Created`**

```json
{
  "useId": 20,
  "userId": 1,
  "orderNumber": "ORD-A",
  "amount": 1500,
  "usedAt": "2026-05-03T14:30:00",
  "allocations": [
    { "earnId": 10, "amount": 500 },
    { "earnId": 11, "amount": 800 },
    { "earnId": 12, "amount": 200 }
  ]
}
```

**예시**

```bash
curl -X POST http://localhost:8080/api/points/use \
  -H 'Content-Type: application/json' \
  -d '{ "userId": 1, "orderNumber": "ORD-A", "amount": 1500 }'
```

**가능 에러**: `POINT-301` (잔액 부족), `POINT-302` (orderNumber 중복)

---

## `POST /api/points/cancel` — 사용 취소 (전체 / 부분)

**왜 필요한가**
주문 환불 시 포인트를 복원합니다. 환불은 **네트워크 재시도가 정상 시나리오**라, 호출자(주문 시스템)가 발급한 `orderRefundId` 를 멱등 키로 받아 같은 환불을 두 번 처리하는 사고를 차단합니다. 부분취소를 N회 누적할 수 있고, 원본 적립이 만료된 경우엔 **신규 `USE_CANCEL_REISSUE` 적립을 발급해 환불금을 적재**합니다 — "정상 사용·취소했던 포인트가 만료 때문에 사라지는" 모순을 방지하기 위함입니다 (README §6 참조).

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | Long | ✅ | 양수 |
| `orderNumber` | String | ✅ | 1~64자, 원본 USE 의 `order_number` |
| `orderRefundId` | String | ✅ | 1~64자, 환불 멱등 키 (호출자 발급) |
| `amount` | Long | ✅ | 양수, 잔여 취소 가능액 이하 |

**Response — `200 OK`**

```json
{
  "cancelId": 21,
  "amount": 700,
  "remainingCancellable": 800,
  "cancelledAt": "2026-05-03T14:45:00"
}
```

`remainingCancellable` 는 **이번 취소 후 남은 취소 가능 금액** — 추가 부분취소 시 클라이언트가 사전 검증할 수 있도록 응답에 포함합니다.

**예시**

```bash
curl -X POST http://localhost:8080/api/points/cancel \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "orderNumber": "ORD-A",
    "orderRefundId": "RF-1",
    "amount": 700
  }'
```

**가능 에러**: `POINT-401` (원본 USE 없음/ownership), `POINT-402` (취소 가능액 초과), `POINT-403` (orderRefundId 중복)

---

## `GET /api/points/users/{userId}/balance` — 잔액 조회

**왜 필요한가**
회원의 현재 사용 가능한 포인트 합계를 반환합니다. 별도 캐시 없이 매 호출 시 ACTIVE 상태 + 미만료 적립의 `remaining_amount` 합계를 즉석 계산 — write 경로의 정합성을 단순하게 유지하려는 의도(잔액 캐시 → invalidation 복잡도 폭증).

**Path**

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `userId` | Long | ✅ | 양수 |

**Response — `200 OK`**

```json
{
  "userId": 1,
  "availableBalance": 12500
}
```

**예시**

```bash
curl http://localhost:8080/api/points/users/1/balance
```

**가능 에러**: 도메인 특화 에러 없음 (공통 에러만 가능).

---

## `GET /api/points/users/{userId}/history` — 거래 이력

**왜 필요한가**
회원의 4행위(EARN / EARN_CANCEL / USE / USE_CANCEL) 를 단일 시계열로 반환합니다. `point_action_log` 라는 read-side projection 위에서 동작하므로 4 테이블 union 없이 정렬·페이징이 한 번에 끝납니다. UI 의 "포인트 사용 내역" 화면이 주 소비처입니다.

**Path / Query**

| 위치 | 필드 | 타입 | 필수 | 기본 | 설명 |
| --- | --- | --- | --- | --- | --- |
| path | `userId` | Long | ✅ | — | 양수 |
| query | `page` | int | ⬜ | `0` | 0-base, `>=0` |
| query | `size` | int | ⬜ | `10` | 양수 |

**Response — `200 OK`**

```json
{
  "userId": 1,
  "page": 0,
  "size": 10,
  "totalElements": 4,
  "totalPages": 1,
  "hasNext": false,
  "items": [
    {
      "type": "USE_CANCEL",
      "id": 21,
      "amount": 700,
      "orderNumber": "ORD-A",
      "occurredAt": "2026-05-03T14:45:00"
    },
    {
      "type": "USE",
      "id": 20,
      "amount": 1500,
      "orderNumber": "ORD-A",
      "occurredAt": "2026-05-03T14:30:00"
    },
    {
      "type": "EARN",
      "id": 10,
      "amount": 1000,
      "orderNumber": null,
      "occurredAt": "2026-05-03T14:00:00"
    }
  ]
}
```

`items[].id` 는 행위 종류에 따라 다릅니다 — EARN/EARN_CANCEL 은 `point_earn.id`, USE/USE_CANCEL 은 `point_use.id`.

**예시**

```bash
curl 'http://localhost:8080/api/points/users/1/history?page=0&size=20'
```

**가능 에러**: 도메인 특화 에러 없음 (공통 에러만 가능).

---

## `POST /api/admin/points/earn` — 운영자 수기 적립

**왜 필요한가**
운영자가 보상·보전 등의 사유로 직접 포인트를 지급해야 할 때 사용합니다. 일반 적립과 같은 `EarnPointRequest` 를 받지만 **`type=MANUAL` 로 기록되어 사용 우선순위 1순위** 가 됩니다 — 운영자가 의도해서 지급한 포인트가 회원의 다른 적립보다 먼저 소진되도록 보장하기 위함.

**왜 시스템 적립과 별도 엔드포인트로 분리했나**

요청 페이로드는 같지만 path 를 분리해 다음 효과를 얻습니다.

- **인증/인가 일괄 보호**: `/api/admin/**` 패턴 한 줄로 Spring Security · 게이트웨이가 admin 경로를 통째로 보호. body 컬럼 분기였다면 매 요청 파싱 후 권한 검증 필요.
- **트레이스·디버깅**: access log / APM transaction name 이 path 별로 분리되어 어느 경로로 들어온 적립인지 즉시 식별 가능. metric · rate-limit · 알림 임계치도 path 별로 독립 설정.


**Request / Response**: 시스템 적립과 동일 (단 응답의 `type` 이 `MANUAL`).

**예시**

```bash
curl -X POST http://localhost:8080/api/admin/points/earn \
  -H 'Content-Type: application/json' \
  -d '{ "userId": 1, "amount": 5000, "expiryDays": 90 }'
```

```json
{
  "earnId": 14,
  "userId": 1,
  "amount": 5000,
  "type": "MANUAL",
  "expiresAt": "2026-08-01T14:00:00"
}
```

**가능 에러**: `POINT-101`, `POINT-102`, `POINT-103`

---

## `PUT /api/admin/users/{userId}/max-balance` — 회원별 보유 한도 개별 설정

**왜 필요한가**

회원의 최대 보유 한도(가질 수 있는 포인트 합계의 상한)는 두 단계로 결정됩니다.

```
1단계 — 글로벌 default
        FF4j 의 points.balance.max-per-user
        (모든 회원에게 일괄 적용, 시드 1,000,000)

2단계 — 회원별 개별 값 (있을 때만)
        point_user.max_balance 컬럼

적용:   effectiveLimit = COALESCE(point_user.max_balance, FF4j 글로벌 default)
```

이 엔드포인트는 **2단계의 회원별 값을 운영자가 채우거나 비우는 도구**입니다. 코드 변경 없이 다음 시나리오를 처리합니다.

| 운영 시나리오 | 요청 body | 효과 |
| --- | --- | --- |
| VIP 회원에게 한도 상향 (예: 500만) | `{ "maxBalance": 5000000 }` | 이 회원만 500만, 다른 회원은 글로벌 default 그대로 |
| 약관 위반 회원의 적립 차단 | `{ "maxBalance": 0 }` | 이 회원에게 적립 시 무조건 POINT-103 거부 (잔액 + 적립금 > 0 이므로) |
| VIP 해제 → 일반 회원으로 회귀 | `{ "maxBalance": null }` | 회원별 값 비움 → 다시 글로벌 default 적용 |

> 모든 회원의 한도를 일괄 변경하려면 이 엔드포인트가 아니라 **FF4j Web Console** 에서 `points.balance.max-per-user` 값을 수정합니다 (회원별 값이 설정된 회원은 그 값이 우선).

**Path / Body**

| 위치 | 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| path | `userId` | Long | ✅ | 양수 |
| body | `maxBalance` | Long | ⬜ | 0 이상. `null` 이면 회원별 값을 지워 글로벌 default 로 회귀, `0` 은 적립 차단 의도로 명시적 허용 |

**Response — `200 OK`**

```json
{
  "userId": 1,
  "maxBalance": 5000000
}
```

**예시 — 회원에게 500만 한도 적용**

```bash
curl -X PUT http://localhost:8080/api/admin/users/1/max-balance \
  -H 'Content-Type: application/json' \
  -d '{ "maxBalance": 5000000 }'
```

**예시 — 회원별 한도 해제 (글로벌 default 로 회귀)**

```bash
curl -X PUT http://localhost:8080/api/admin/users/1/max-balance \
  -H 'Content-Type: application/json' \
  -d '{ "maxBalance": null }'
```

**가능 에러**: 도메인 특화 에러 없음 (공통 에러만 가능).
