# ERSync 백엔드 에이전트 컨텍스트

- Audience: Java and Spring Boot backend agents
- Updated: 2026-07-25

Read this document after `docs/agents/context.md`. Before implementation, read
the team-reviewed feature documents and then `docs/conventions.md`. Domain
boundaries, error contracts, and delivery rules are included in this document.

## 1. Repository Context

Current backend skeleton:

- Java 25 toolchain
- Spring Boot 4.1.0
- Spring MVC and Spring Security
- MySQL connector
- Gradle
- base package: `com.hansungteam.ersync`
- existing global exception types under `global.exception`

JPA, validation, Flyway, MySQL, and the test database foundation are configured. Domain entities and migrations have not been added yet. Realtime delivery and external observability are not configured.

### 1.1 Existing Platform Contract

- every request receives a server-generated trace ID;
- the same value is returned through `X-Trace-Id` and error responses;
- domain failures use registered `CustomException(ErrorCode)` values;
- all 4xx and 5xx responses use the shared error response shape;
- error logs use structured `key=value` fields that LogScope can analyze;
- security is stateless and only health endpoints are public by default;
- authorization requires role checks plus organization or request ownership checks;
- JWT login is planned with a 15-minute access token and 7-day refresh token;
- refresh tokens must be stored as hashes and rotated on refresh;
- local development uses Docker MySQL, tests use H2 plus MySQL Testcontainers, and EC2 dev uses RDS;
- pull requests run Gradle checks and Docker build;
- `main` creates an ECR image and deploys it to EC2 with readiness-based rollback;
- runtime secrets come from AWS Secrets Manager and must not be committed or baked into images.

Before changing code, read:

1. `docs/agents/context.md`
2. this backend context
3. the team-reviewed feature `spec.md`
4. the feature `implementation.md`
5. `docs/conventions.md`

After implementation, update the same feature's `review.md` with requirement checks,
changed API and DB contracts, executed tests, remaining risks, and the next
recommended task. When the feature affects frontend integration, also create or
update `docs/contracts/{number}-{feature}.md` from the implemented code and tests,
then include it in the pull request. The feature templates define the length limits.

## 2. Backend Mission

Provide an auditable emergency transport coordination API that:

- accepts structured patient assessments from an authenticated paramedic;
- automatically sends offers to eligible nearby emergency departments;
- supports hospital accept, reject, and later acceptance withdrawal;
- keeps one paramedic-selected current destination;
- shares latest location and ETA only with the authorized destination;
- appends in-transit clinical reassessments without overwriting history;
- closes a case only after two-party handoff confirmation.

The service supports communication. It does not make medical decisions or replace official records.

## 3. Mandatory Domain Invariants

1. A `SUPER_ADMIN` cannot read patient or location data.
2. A paramedic can create and clinically update only their own active request.
3. A hospital can read only an offer sent to its organization.
4. Patient direct identifiers are not part of the MVP schema or DTOs.
5. Required clinical fields use values or explicit unavailable/refused states, never ambiguous nulls.
6. Clinical records are append-only after submission.
7. Clinical time, client entry time, and trusted server receipt time are separate.
8. Multiple hospital offers may be accepted concurrently.
9. `currentDestinationOfferId` is null or references exactly one accepted offer for the same request.
10. Initial hospital recipients are server-selected, never supplied by the client.
11. The same hospital receives the same request at most once per dispatch attempt. A retry after candidate exhaustion may contact it again in a new attempt.
12. ETA/provider failure never rolls back request creation or clinical updates.
13. Exact current location is accessible only to the current destination hospital and the request owner.
14. Completion requires paramedic request followed by current destination hospital confirmation.
15. Selecting a destination hides non-destination accepted offers from hospital active queries without deleting their response history.
16. The request owner may cancel before handoff is requested. Cancellation is terminal and requires a reason.
17. Completed and cancelled requests disappear from active queries but remain persisted for the retention period.
18. A latest location older than the configured 30-second threshold is `STALE`, not an application error.

## 4. Roles and Organizations

```java
enum AccountRole {
    SUPER_ADMIN,
    PARAMEDIC,
    HOSPITAL_STAFF
}

enum OrganizationType {
    HOSPITAL,
    EMS_UNIT
}
```

Account policy:

- `SUPER_ADMIN`: one bootstrap/operator account
- `PARAMEDIC`: individual account bound to one EMS unit
- `HOSPITAL_STAFF`: one shared account bound to one hospital ER

Do not require a personal responder name for hospital commands in MVP. Audit the shared account ID, hospital ID, and server time.

## 5. Package Boundaries

Suggested feature-first packages:

```text
auth
organization
invitation
hospital
transport
clinical
offer
location
handoff
notification
audit
global
```

Within a feature, separate API DTOs, application services, domain types, and persistence adapters. Do not expose JPA entities directly from controllers.

## 6. Entity Draft

| Entity | Purpose |
|---|---|
| `Organization` | hospital or EMS unit identity |
| `UserAccount` | authenticated account and role |
| `InvitationCode` | one-time organization/role signup credential |
| `HospitalProfile` | ER address, coordinates, contact, receiving state |
| `ClinicalProtocolDefinition` | versioned ERSync form/options/conditional-rule definition |
| `TransportRequest` | patient transport workflow root |
| `PatientAssessmentVersion` | demographics, incident, symptoms, onset; append-only correction versions |
| `PreKtasAssessment` | one severity assessment or initial emergency exception |
| `ConsciousnessAssessment` | one AVPU observation |
| `VitalSignSet` | one complete set of five vital states/values |
| `TreatmentEvent` | one performed or attempted treatment |
| `SupplementalAssessment` | protocol-triggered pupil, glucose, condition-specific, or history data |
| `CurrentPatientSnapshot` | derived latest hospital-facing clinical projection |
| `SearchRound` | one radius search execution |
| `HospitalOffer` | one request sent to one hospital |
| `HospitalOfferResponseEvent` | immutable acceptance/rejection/withdrawal history |
| `DestinationEvent` | destination selection/change/release history |
| `LatestLocation` | latest location only for a request |
| `EtaSnapshot` | latest distance/ETA calculation result |
| `Handoff` | paramedic request and hospital confirmation timestamps |
| `OutboxEvent` | reliable integration/realtime event |
| `AuditEvent` | security and business audit record |

Use UUID/ULID identifiers or another non-sequential public ID. Internal numeric IDs may exist but must not leak if enumeration risk matters.

## 7. Organization and Invitation Data

### 7.1 Organization

Minimum fields:

```text
id
type
name
active
createdAt
updatedAt
```

### 7.2 UserAccount

```text
id
organizationId nullable only for SUPER_ADMIN
role
loginId unique
passwordHash
active
createdAt
lastLoginAt
```

### 7.3 InvitationCode

```text
id
organizationId
targetRole
codeHash
status
expiresAt
issuedBy
issuedAt
usedBy nullable
usedAt nullable
revokedBy nullable
revokedAt nullable
version
```

```java
enum InvitationCodeStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    REVOKED
}
```

Generation rules:

- cryptographically secure random plaintext;
- sufficient entropy, not a short sequential number;
- plaintext returned once;
- only hash persisted;
- expiry 3 days, 7 days, or validated custom value;
- code bound to organization and target role.

Signup must lock or conditionally update the code so two requests cannot consume it. Account creation and code consumption are one transaction.

## 8. Hospital Profile

```text
organizationId primary/foreign key
erAddress
latitude
longitude
erContact
receivingStatus ON | OFF
locationVerifiedAt
updatedAt
version
```

Requirements:

- organization type must be `HOSPITAL`;
- coordinates must be valid WGS84 values;
- location is confirmed during signup;
- receiving status defaults to `OFF`;
- OFF excludes only new offers;
- OFF does not close or withdraw existing accepted/en-route cases.

For MySQL, use a spatial point with SRID 4326 and spatial index if supported by the selected persistence design. A numeric lat/lon representation is acceptable initially, but distance queries must be tested and indexed appropriately.

### 8.1 Clinical Protocol Definition

MVP protocol definitions are medically reviewed, versioned release artifacts. They are not editable by the super admin.

Minimum metadata:

```text
version
status ACTIVE | SUPPORTED | RETIRED
effectiveAt
schemaHash
definitionResource
createdAt
```

Rules:

- pin `assessmentProtocolVersion` when a request draft is created;
- validate initial and supplemental fields against that supported version;
- store the official Pre-KTAS standard version separately on each Pre-KTAS assessment;
- support an explicit deployment overlap window for older drafts;
- reject a retired incompatible version with a stable error that requires client review;
- never silently reinterpret values under a new protocol;
- keep old definitions for persisted-record interpretation and audit;
- do not auto-calculate clinical classification unless an approved algorithm is implemented and validated.

Suggested read API:

```http
GET /api/v1/clinical-protocols/active
GET /api/v1/clinical-protocols/{version}
```

Responses contain option identifiers, labels, conditional field rules, units, and supported version metadata. They must not contain executable scripts supplied by an admin.

## 9. Transport Aggregate

### 9.1 TransportRequest

```text
id
requestNumber
paramedicAccountId
emsOrganizationId
callbackContactSnapshot
paramedicRoleSnapshot
paramedicQualificationSnapshot nullable
status
currentDestinationOfferId nullable
currentSearchRadiusKm
currentDispatchAttemptNumber
firstAcceptedAt nullable
assessmentProtocolVersion
createdAt
handoffRequestedAt nullable
completedAt nullable
cancelReason nullable
cancelledAt nullable
version
```

```java
enum TransportRequestStatus {
    SEARCHING,
    CANDIDATES_EXHAUSTED,
    ACCEPTED_AVAILABLE,
    EN_ROUTE,
    HANDOFF_REQUESTED,
    COMPLETED,
    CANCELLED
}
```

`DRAFT` belongs to the mobile client. The server creates a request only when the initial payload passes validation.

### 9.2 HospitalOffer

```text
id
transportRequestId
hospitalOrganizationId
searchRoundId
dispatchAttemptNumber
distanceAtOfferMeters
etaAtOfferSeconds nullable
etaCalculatedAt nullable
status
sentAt
respondedAt nullable
closedAt nullable
version
```

```java
enum HospitalOfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    NO_RESPONSE,
    ACCEPTANCE_WITHDRAWN
}
```

Destination selection is not an offer status. It is represented by `TransportRequest.currentDestinationOfferId` and immutable `DestinationEvent` rows. Request completion sets `closedAt` without erasing whether the offer was pending, accepted, rejected, or withdrawn.

Database constraint:

```text
unique (transport_request_id, hospital_organization_id, dispatch_attempt_number)
```

An exhaustion retry creates a new offer row in a new dispatch attempt. Never reopen or overwrite the earlier offer response.

### 9.3 SearchRound

```text
id
transportRequestId
trigger INITIAL | TIMER_EXPANSION | EXHAUSTION_RETRY | WITHDRAWAL_RESEARCH
dispatchAttemptNumber
centerLatitude
centerLongitude
radiusKm
candidateCount
newOfferCount
createdAt
```

Store the search center snapshot for audit. Do not treat it as route history.

## 10. Patient Assessment Version

This record stores demographics, incident details, symptoms, and onset. The initial record is created with the request. Corrections append another version.

```text
id
transportRequestId
versionNumber
ageStatus EXACT | ESTIMATED | UNKNOWN
ageYears nullable
sex MALE | FEMALE | UNKNOWN
occurrenceType DISEASE | NON_DISEASE | OTHER | UNKNOWN
occurrenceOtherDetail nullable
mechanism nullable
mechanismOtherDetail nullable
injurySites set/child rows
primarySymptom
primarySymptomOtherDetail nullable
secondarySymptoms set/child rows
onsetTimeStatus EXACT | ESTIMATED | UNKNOWN
onsetAt nullable
lastKnownWellStatus nullable
lastKnownWellAt nullable
accidentTimeStatus nullable
accidentAt nullable
cardiacArrestTimeStatus nullable
cardiacArrestAt nullable
enteredAt
serverReceivedAt
createdBy
supersedesAssessmentId nullable
correctionReason nullable
```

Validation:

- `ageYears` required for exact/estimated and absent for unknown;
- mechanism and injury sites required for non-disease;
- `OTHER` requires bounded detail where supported;
- primary symptom always present, including explicit unknown;
- timestamp required for exact/estimated and absent for unknown;
- correction requires `supersedesAssessmentId` and reason;
- version number is monotonically increasing per request.

Use child tables or validated enum collections for multi-value injury sites and secondary symptoms. Do not store comma-separated strings.

## 11. Pre-KTAS Assessment

```text
id
transportRequestId
classificationStatus COMPLETED | EMERGENCY_UNFINISHED
level nullable, integer 1..5
exceptionReason nullable
exceptionDetail nullable
assessorAccountId
assessedAt
standardVersion
enteredAt
serverReceivedAt
supersedesAssessmentId nullable
correctionReason nullable
```

```java
enum PreKtasExceptionReason {
    CPR_IN_PROGRESS,
    SCENE_DANGER,
    INSUFFICIENT_ASSESSMENT_TIME,
    OTHER
}
```

Rules:

- completed requires level 1 through 5;
- completed forbids exception fields;
- emergency unfinished forbids level and requires exception reason;
- `OTHER` requires detail;
- there is no level 6 or unassessable level;
- initial emergency exception allows creation but marks `classificationUpdateRequired=true` in the snapshot;
- the first later completed assessment clears that derived flag;
- server resolves the authenticated assessor; do not trust a client assessor ID.

The official standard version must come from server configuration, not arbitrary client input. The client may echo the version for stale-form detection.

## 12. Consciousness Assessment

```text
id
transportRequestId
avpu A | V | P | U | UNASSESSABLE
unassessableReason nullable
unassessableDetail nullable
observedAt
enteredAt
serverReceivedAt
createdBy
supersedesAssessmentId nullable
correctionReason nullable
```

```java
enum ConsciousnessUnassessableReason {
    SCENE_DANGER,
    PATIENT_INACCESSIBLE,
    OTHER
}
```

Reason is required only for `UNASSESSABLE`; `OTHER` requires detail.

## 13. Vital Sign Set

A submitted set contains all five vital items. Do not accept a partial set.

Common item state:

```java
enum ClinicalValueState {
    VALUE,
    MEASUREMENT_UNAVAILABLE,
    PATIENT_REFUSED
}

enum MeasurementUnavailableReason {
    PATIENT_CONDITION,
    SCENE_DANGER,
    INJURY_SITE,
    DEVICE_ERROR,
    OTHER
}
```

Suggested fixed columns:

```text
id
transportRequestId

bloodPressureState
systolic nullable
diastolic nullable
bloodPressureUnavailableReason nullable
bloodPressureOtherDetail nullable

pulseState
pulsePerMinute nullable
pulseUnavailableReason nullable
pulseOtherDetail nullable

respiratoryRateState
respirationsPerMinute nullable
respiratoryUnavailableReason nullable
respiratoryOtherDetail nullable

temperatureState
temperatureCelsius nullable
temperatureUnavailableReason nullable
temperatureOtherDetail nullable

spo2State
spo2Percent nullable
spo2UnavailableReason nullable
spo2OtherDetail nullable

measuredAt
enteredAt
serverReceivedAt
createdBy
supersedesVitalSignSetId nullable
correctionReason nullable
```

Per-item validation:

- `VALUE`: numeric field required; unavailable fields absent;
- `MEASUREMENT_UNAVAILABLE`: reason required; numeric field absent;
- `PATIENT_REFUSED`: numeric and unavailable reason absent;
- unavailable `OTHER`: bounded detail required;
- validate plausible technical ranges without turning a clinically unusual value into a silent rejection;
- out-of-range but representable values should usually require explicit confirmation on the client and remain storable;
- impossible protocol values are rejected with field errors.

Units are fixed by contract:

- blood pressure: mmHg
- pulse: per minute
- respiratory rate: per minute
- temperature: Celsius
- SpO2: percent

## 14. Treatment Event

```java
enum TreatmentType {
    NONE,
    OXYGEN,
    AIRWAY,
    CPR,
    DEFIBRILLATION_AED,
    IV_FLUID,
    MEDICATION,
    BLEEDING_WOUND,
    IMMOBILIZATION,
    ECG,
    WARMING_COOLING,
    DELIVERY,
    OTHER
}
```

Common fields:

```text
id
transportRequestId
type
performedAt
enteredAt
serverReceivedAt
createdBy
detailSchemaVersion
detailsJson
supersedesTreatmentEventId nullable
correctionReason nullable
```

For MVP, a JSON column is acceptable for type-specific detail only if:

- request DTOs use typed sealed interfaces/records;
- Bean Validation enforces the subtype contract;
- a schema version is stored;
- arbitrary unknown keys are rejected;
- clinical queries do not depend on ad hoc text parsing;
- treatment summary is generated from typed values.

Prefer subtype tables later if reporting or interoperability requires them.

Required detail examples:

| Type | Details |
|---|---|
| `OXYGEN` | method, flow rate, start time |
| `AIRWAY` | method/device, performed time, success |
| `CPR` | start time, status, ROSC |
| `DEFIBRILLATION_AED` | monitor/defibrillation flags, count, time |
| `IV_FLUID` | success, fluid type, amount, time |
| `MEDICATION` | name, dose, unit, route, time |
| `BLEEDING_WOUND` | site, method, tourniquet, time |
| `IMMOBILIZATION` | site, method |
| `ECG` | lead type, time, findings, transmitted |
| `DELIVERY` | action, birth time |
| `OTHER` | bounded detail, time |

Initial request must contain at least one event. `NONE` must be the only initial treatment when present. All attempts, including failures, remain in history.

### 14.1 Supplemental Assessment

Use typed, protocol-triggered records instead of one generic note field.

Supported groups:

```java
enum SupplementalAssessmentType {
    PUPILS,
    GLUCOSE,
    MEDICAL_HISTORY,
    ALLERGIES,
    CURRENT_MEDICATIONS,
    INFECTION_ISOLATION,
    CARDIAC_ARREST_DETAIL,
    MAJOR_TRAUMA_DETAIL,
    CARDIAC_CHEST_PAIN_DETAIL,
    SUSPECTED_STROKE_DETAIL,
    PREGNANCY_DELIVERY_DETAIL,
    PREFERRED_HOSPITAL_NOTICE
}
```

Common fields:

```text
id
transportRequestId
type
observedAt nullable
enteredAt
serverReceivedAt
createdBy
detailSchemaVersion
detailsJson
supersedesAssessmentId nullable
correctionReason nullable
```

Use typed DTO subtypes and validation, as with treatment details. A protocol-triggered required field must have a value or explicit `UNKNOWN` state. Do not require every supplemental group for every patient.

Preferred hospital is contextual data only. It cannot modify candidate search, create an offer, or set `currentDestinationOfferId`.

Recipient personal name/signature is not stored in MVP. In-app handoff records the shared hospital account and server confirmation time. External legally required signatures remain outside ERSync.

## 15. Clinical Timestamp and Correction Policy

Every clinical record distinguishes:

- clinical occurrence: `assessedAt`, `observedAt`, `measuredAt`, or `performedAt`;
- client entry: `enteredAt` supplied by client;
- server receipt: `serverReceivedAt` generated by server.

Rules:

- use `Instant` in the backend and UTC in storage;
- preserve the client timezone only if needed for diagnostics;
- reject implausibly future client/clinical times with a field error or require correction;
- never use client time to order security/audit events;
- use server time for state transition ordering;
- do not update submitted clinical rows;
- correction appends a replacement referencing the old row and requires a reason;
- only one active correction chain head contributes to the latest snapshot;
- keep every superseded row queryable in the audit timeline.

## 16. Current Patient Snapshot

`CurrentPatientSnapshot` is a projection, not legal source history.

Minimum fields:

```text
transportRequestId
latestPatientAssessmentId
latestPreKtasAssessmentId
latestConsciousnessAssessmentId
latestVitalSignSetId
classificationUpdateRequired
treatmentSummaryJson
supplementalSummaryJson
lastClinicalUpdateAt
projectionVersion
```

Update the snapshot transactionally with each accepted clinical command, or rebuild through an ordered event/projection worker with read-after-write guarantees. The first approach is simpler for MVP.

Hospital card/query DTOs join this projection with request/offer data. They must not return patient direct identifiers because none should exist in the domain.

## 17. Initial Request API

```http
POST /api/v1/transport-requests
Authorization: Bearer <paramedic token>
Idempotency-Key: <stable client key>
```

Request shape:

```json
{
  "clientEnteredAt": "2026-07-17T01:20:00Z",
  "assessmentProtocolVersion": "ersync-patient-assessment-1.0",
  "originLocation": {
    "source": "GPS",
    "latitude": 37.123,
    "longitude": 127.123,
    "accuracyMeters": 12.4,
    "capturedAt": "2026-07-17T01:19:50Z"
  },
  "patientAssessment": {
    "ageStatus": "ESTIMATED",
    "ageYears": 70,
    "sex": "MALE",
    "occurrenceType": "DISEASE",
    "primarySymptom": "DYSPNEA",
    "secondarySymptoms": [],
    "onsetTimeStatus": "ESTIMATED",
    "onsetAt": "2026-07-17T00:50:00Z"
  },
  "preKtasAssessment": {
    "classificationStatus": "COMPLETED",
    "level": 2,
    "assessedAt": "2026-07-17T01:18:00Z",
    "standardVersion": "server-advertised-version"
  },
  "consciousnessAssessment": {
    "avpu": "V",
    "observedAt": "2026-07-17T01:18:30Z"
  },
  "vitalSignSet": {
    "bloodPressure": { "state": "VALUE", "systolic": 90, "diastolic": 60 },
    "pulse": { "state": "VALUE", "value": 118 },
    "respiratoryRate": { "state": "VALUE", "value": 30 },
    "temperature": { "state": "VALUE", "value": 38.1 },
    "spo2": { "state": "VALUE", "value": 88 },
    "measuredAt": "2026-07-17T01:19:00Z"
  },
  "treatments": [
    {
      "type": "OXYGEN",
      "performedAt": "2026-07-17T01:19:30Z",
      "details": { "method": "MASK", "flowLitersPerMinute": 10 }
    }
  ]
}
```

Do not accept:

- hospital IDs;
- manual ETA;
- patient name or registration number;
- arbitrary clinical free text outside bounded `OTHER` fields;
- caller account/organization IDs supplied as authority.

Server transaction:

1. authenticate active paramedic and EMS organization;
2. reserve idempotency key;
3. validate complete structured payload;
4. create `TransportRequest`;
5. validate and store the GPS or paramedic-confirmed manual origin as `LatestLocation`;
6. append initial clinical records with server timestamps;
7. create current snapshot;
8. commit;
9. start candidate search through an application event/job;
10. return request ID and search state.

Do not hold the database transaction open while calling a map provider.

## 18. Clinical Update APIs

```http
POST /api/v1/transport-requests/{requestId}/patient-assessments
POST /api/v1/transport-requests/{requestId}/pre-ktas-assessments
POST /api/v1/transport-requests/{requestId}/consciousness-assessments
POST /api/v1/transport-requests/{requestId}/vital-sign-sets
POST /api/v1/transport-requests/{requestId}/treatments
POST /api/v1/transport-requests/{requestId}/supplemental-assessments
GET  /api/v1/transport-requests/{requestId}/clinical-timeline
```

Every POST requires:

- authenticated request owner;
- active request state;
- idempotency key;
- client entry timestamp;
- type-specific clinical timestamp;
- `supersedesRecordId` and correction reason when correcting.

Do not provide generic `PATCH /clinical-record/{id}` mutation.

Each transaction:

1. lock/check request version and ownership;
2. validate record and correction relation;
3. insert immutable row;
4. update current snapshot;
5. insert audit and outbox rows;
6. commit;
7. dispatch notification asynchronously.

## 19. Clinical Update Recipients

Use least-privilege delivery:

- `PENDING` offer hospitals receive the hospital-facing updated summary while deciding;
- before destination selection, `ACCEPTED` offer hospitals receive the updated summary because they may still become destination;
- after destination selection, only the current destination receives later clinical updates among accepted hospitals;
- current destination always receives authorized clinical updates;
- non-destination `ACCEPTED`, `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`, and offers with `closedAt` receive no later clinical payload after destination selection or request closure;
- exact GPS coordinates are sent only to the current destination hospital;
- super admin receives no clinical event.

If operating policy later restricts updates to accepted hospitals only, implement that as an explicit authorization policy change. Do not silently change recipients in a client.

## 20. Candidate Hospital Search

Configuration:

```text
initialRadiusKm = 10
minimumCandidateCount = 3
radiusStepKm = 10
expansionIntervalSeconds = 60
maximumRadiusKm = 100
```

Eligible hospital conditions:

- active `HOSPITAL` organization;
- active hospital account/profile;
- valid coordinates;
- receiving status `ON`;
- no offer for the request in the current dispatch attempt;
- not excluded after withdrawal.

### 20.1 Initial Search

```text
radius = 10km
repeat:
  candidates = all eligible hospitals inside radius
  if candidates >= 3 or radius == 100km:
      create offers for every candidate in radius
      stop
  radius = min(radius + 10km, 100km)
```

If fewer than three candidates exist at 100km, create offers for all found candidates and expose the shortage in the request state.

### 20.2 Timed Expansion

If no offer has been accepted 60 seconds after the latest search round:

1. confirm `firstAcceptedAt` is still null;
2. expand by 10km up to 100km;
3. query only eligible, not-yet-contacted hospitals;
4. create new offers with a unique constraint guard;
5. schedule the next check if still no acceptance.

The first committed acceptance sets `firstAcceptedAt` and stops future automatic expansion jobs. A running job must recheck under transaction/lock before inserting offers.

### 20.3 Candidate Exhaustion and Retry

Transition to `CANDIDATES_EXHAUSTED` when:

- the 100km search round has been sent and its final 60-second response window ends without an accepted offer; or
- every contacted hospital rejects before the final window ends; or
- no eligible hospital exists inside the maximum radius.

Close remaining pending offers as `NO_RESPONSE` outcomes for the finished dispatch attempt without rewriting earlier response events.

```http
POST /api/v1/transport-requests/{requestId}/search-retries
Idempotency-Key: ...
```

Preconditions:

- request owner paramedic;
- status is `CANDIDATES_EXHAUSTED`;
- no accepted offer exists;
- idempotency key present.

Effects in one transaction:

1. increment `currentDispatchAttemptNumber`;
2. reset request search fields and status to `SEARCHING`;
3. create an `EXHAUSTION_RETRY` search round for the new attempt;
4. allow hospitals rejected or nonresponsive in earlier attempts to receive a new offer;
5. preserve every previous search round, offer, and response event;
6. publish `HOSPITAL_SEARCH_RETRY_STARTED`.

Phone connection uses the registered ER contact returned to the paramedic client. It creates no acceptance and changes no request or offer state.

Use `TRANSPORT_001` for a missing request, `AUTH_003` for the wrong owner or role, and `TRANSPORT_004` when retry is not allowed in the current request state.

### 20.4 Withdrawal Re-search

If an accepted hospital withdraws:

- keep the same request ID and patient records;
- use latest available paramedic location as center;
- fall back to prior search center only when no current location exists;
- exclude every hospital that already has an offer, including the withdrawn hospital;
- trigger a new `WITHDRAWAL_RESEARCH` round immediately;
- follow the same radius/count rules;
- notify the paramedic if no new candidate exists.

## 21. Map and Distance Adapter

Define a provider-neutral port, for example:

```java
interface RouteEstimatePort {
    RouteEstimate estimate(Coordinate origin, Coordinate destination);
}
```

Search radius can use database geospatial distance. Route ETA uses the external map provider.

Provider calls:

- happen outside the main state transaction;
- have short timeouts;
- use bounded retry/circuit breaking;
- return explicit available/unavailable status;
- never block initial request persistence;
- never log raw patient coordinates at info/error level.

## 22. Hospital Response APIs

```http
POST /api/v1/hospital/offers/{offerId}/accept
POST /api/v1/hospital/offers/{offerId}/reject
POST /api/v1/hospital/offers/{offerId}/withdraw-acceptance
```

### 22.1 Acceptance

Preconditions:

- authenticated hospital matches offer hospital;
- offer is `PENDING`;
- request is active;
- command idempotency key is valid.

Transaction:

- change offer to `ACCEPTED`;
- append response event and audit;
- set request `firstAcceptedAt` if null;
- change request to `ACCEPTED_AVAILABLE` unless already en route;
- write outbox event.

Do not choose destination or cancel other offers.

### 22.2 Rejection

```java
enum HospitalRejectionReason {
    ER_GENERAL_BED_SHORTAGE,
    ISOLATION_BED_SHORTAGE,
    OPERATING_ROOM_SHORTAGE,
    ICU_SHORTAGE,
    SPECIALIST_UNAVAILABLE,
    EQUIPMENT_UNAVAILABLE,
    OTHER
}
```

Rules:

- only a `PENDING` offer may be rejected;
- one reason is required;
- `OTHER` requires bounded detail;
- append immutable response event;
- expose reason to request owner;
- stop later clinical delivery to this hospital.

### 22.3 Acceptance Withdrawal

```java
enum AcceptanceWithdrawalReason {
    BED_SHORTAGE,
    OPERATING_ROOM_SHORTAGE,
    SPECIALIST_UNAVAILABLE,
    EQUIPMENT_UNAVAILABLE,
    OTHER
}
```

Rules:

- only an `ACCEPTED` offer may withdraw;
- request status must be before `HANDOFF_REQUESTED`;
- reason required and `OTHER` requires detail;
- rejection and withdrawal enums must remain separate;
- if current destination, atomically clear `currentDestinationOfferId`;
- after clearing, use `ACCEPTED_AVAILABLE` when another accepted offer exists, otherwise `SEARCHING`;
- mark offer `ACCEPTANCE_WITHDRAWN`;
- append destination release event when applicable;
- write urgent paramedic notification;
- trigger re-search after commit;
- never send another offer for this request to the same hospital.

## 23. Destination APIs

```http
PUT /api/v1/transport-requests/{requestId}/destination
{
  "offerId": "..."
}
```

Preconditions:

- request owner paramedic;
- request active and not handoff-requested/completed;
- selected offer belongs to request;
- selected offer is `ACCEPTED`;
- selected hospital organization is active;
- idempotency key present.

Atomic transaction:

1. lock request/version;
2. capture previous destination;
3. set new `currentDestinationOfferId`;
4. set status `EN_ROUTE`;
5. append `DestinationEvent` with previous/new offer IDs;
6. write outbox events for new and previous hospitals;
7. commit.

Changing from A to B does not reject or delete A's acceptance. Exact location authorization changes immediately when the transaction commits.

Repeated selection of the same destination returns the current state without duplicating history.

Active hospital query behavior after destination selection:

- the current destination remains visible as `EN_ROUTE`;
- every other accepted offer is excluded from the hospital active dashboard;
- hidden accepted offers remain in response history and the paramedic's accepted list;
- selecting a hidden accepted offer as the new destination makes it active for that hospital again;
- the previous destination receives the change notification and is removed from its active dashboard.

### 23.1 Transport Cancellation API

```http
POST /api/v1/transport-requests/{requestId}/cancel
Idempotency-Key: ...

{
  "reason": "PATIENT_REFUSED_TRANSPORT"
}
```

Cancellation reasons:

```text
PATIENT_REFUSED_TRANSPORT
GUARDIAN_SELF_TRANSPORT
SCENE_RESOLVED
OTHER
```

Preconditions:

- request owner paramedic;
- status is `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`, or `EN_ROUTE`;
- reason and idempotency key are present.

Atomic effects:

1. lock and recheck request state;
2. set status `CANCELLED`, reason, actor, and server cancellation time;
3. clear `currentDestinationOfferId`;
4. close every active offer while preserving response status and history;
5. append destination release, audit, and outbox events;
6. notify every pending or accepted hospital, including hidden accepted offers;
7. publish `TRANSPORT_REQUEST_CANCELLED` and remove the request from active queries.

Cancellation cannot be resumed. Reject later clinical, location, destination, search-retry, and handoff commands with the existing invalid-state error contract.

Use existing errors:

- missing reason: `COMMON_001`;
- missing request: `TRANSPORT_001`;
- wrong owner or role: `AUTH_003`;
- invalid request state: `TRANSPORT_004`.

## 24. Latest Location and ETA

```http
PUT /api/v1/transport-requests/{requestId}/location
{
  "latitude": 37.123,
  "longitude": 127.123,
  "accuracyMeters": 12.4,
  "capturedAt": "2026-07-17T01:25:00Z"
}
```

Rules:

- only request owner may update;
- accept while request is active, especially `EN_ROUTE`;
- validate coordinate and accuracy bounds;
- reject or ignore clearly older updates using captured/server sequence rules;
- upsert one `LatestLocation` row;
- store trusted `serverReceivedAt` for stale-state calculation;
- do not append route points to a history table;
- audit that an update occurred without copying coordinates into general audit logs;
- publish exact coordinate event only to current destination hospital.

After storing location, calculate route distance/ETA asynchronously for the current destination. Store:

```text
status AVAILABLE | UNAVAILABLE
distanceMeters nullable
durationSeconds nullable
calculatedAt
providerCode
originCapturedAt
```

Provider failure updates availability/metrics but does not fail location storage.

Hospital authorization must be enforced on both REST query and realtime destination topic. A previous destination loses coordinate access immediately after change.

Hospital location responses include:

```text
status CURRENT | STALE
lastReceivedAt
staleAfterSeconds
```

The default stale threshold is 30 seconds and must be configurable. When stale, return the latest stored coordinate and server receipt time. The next accepted location returns the state to `CURRENT`. Staleness must not raise an error response, urgent event, or audible alert.

## 25. Handoff

```http
POST /api/v1/transport-requests/{requestId}/handoff-request
POST /api/v1/hospital/transport-requests/{requestId}/handoff-confirm
```

### 25.1 Paramedic Request

Preconditions:

- request owner;
- current destination exists and is accepted;
- status is `EN_ROUTE`;
- idempotency key present.

Effects:

- set `handoffRequestedAt`;
- status `HANDOFF_REQUESTED`;
- notify only current destination hospital;
- keep active request visible.

### 25.2 Hospital Confirmation

Preconditions:

- hospital matches current destination offer;
- status `HANDOFF_REQUESTED`;
- idempotency key present.

Effects in one transaction:

- set hospital confirmation server time;
- set request `COMPLETED` and `completedAt`;
- clear/retain destination pointer according to persistence design, but history must identify final hospital;
- set `closedAt` on all offers while retaining their response status;
- publish close event to every hospital that still has an active offer view;
- stop location and clinical-update acceptance;
- keep all records for retention.

Do not accept confirmation from another accepted but non-destination hospital.

## 26. Receiving Status API

```http
PUT /api/v1/hospital/receiving-status
{
  "status": "ON"
}
```

Only the hospital's own shared account may change it. Use optimistic locking. OFF affects future eligibility only and must not mutate offers or destinations.

## 27. Query APIs

### 27.1 Paramedic

```http
GET /api/v1/transport-requests/active
GET /api/v1/transport-requests/{requestId}
GET /api/v1/transport-requests/{requestId}/offers
GET /api/v1/transport-requests/{requestId}/clinical-timeline
```

Return accepted, pending, and rejected offers with permitted reasons. Return latest distance/ETA. Do not return internal invitation/admin fields.

### 27.2 Hospital

```http
GET /api/v1/hospital/offers?status=active
GET /api/v1/hospital/offers/{offerId}
GET /api/v1/hospital/offers/{offerId}/clinical-timeline
GET /api/v1/hospital/history
```

The hospital request DTO contains only the allowed hospital-facing snapshot. Exact location fields are null/absent unless this offer is the current destination.

The active query excludes accepted offers that are not the current destination once any destination is selected. This is a query/authorization rule, not an offer-status rewrite. The hospital history retains its acceptance record.

### 27.3 Admin

```http
GET  /api/v1/admin/organizations
POST /api/v1/admin/organizations
POST /api/v1/admin/organizations/{organizationId}/invitation-codes
GET  /api/v1/admin/invitation-codes
POST /api/v1/admin/invitation-codes/{invitationCodeId}/revoke
```

Admin repository/service code must not join or query clinical tables.
Admin APIs must not expose protocol mutation in MVP.

## 28. DTO and Validation Guidance

- use Java records for immutable request/response DTOs where appropriate;
- use Bean Validation plus cross-field validators for tagged unions;
- reject unknown enum values and unknown JSON properties;
- return stable machine error codes and per-field errors;
- cap all `OTHER` details and treatment text lengths;
- normalize whitespace without changing clinical meaning;
- derive actor and organization from authentication;
- return server timestamps and aggregate version;
- publish OpenAPI and generate frontend models when possible.

Example field error:

```json
{
  "code": "CLINICAL_VALIDATION_FAILED",
  "message": "입력한 환자 정보를 확인해 주세요.",
  "fieldErrors": [
    {
      "field": "vitalSignSet.bloodPressure.unavailableReason",
      "code": "REQUIRED_FOR_STATE",
      "message": "측정 불가 사유를 선택해 주세요."
    }
  ],
  "traceId": "non-sensitive-correlation-id"
}
```

Integrate with the existing `global.exception` pattern. Do not leak stack traces or payloads.

## 29. Authorization Matrix

| Action | Super admin | Request paramedic | Other paramedic | Offer hospital | Other hospital |
|---|---:|---:|---:|---:|---:|
| Manage organizations/codes | yes | no | no | no | no |
| Create transport request | no | yes | yes for own new case | no | no |
| Read request clinical data | no | yes | no | yes, if active authorized offer | no |
| Append clinical data | no | yes | no | no | no |
| Accept/reject offer | no | no | no | yes | no |
| Withdraw own acceptance | no | no | no | yes | no |
| Select destination | no | yes | no | no | no |
| Retry exhausted search | no | yes | no | no | no |
| Cancel transport before handoff request | no | yes | no | no | no |
| Read exact current location | no | yes | no | only current destination | no |
| Request handoff | no | yes | no | no | no |
| Confirm handoff | no | no | no | current destination only | no |

Apply authorization in application services as well as controller rules. Realtime subscription authorization must use the same policy.

## 30. Concurrency and Idempotency

Critical races:

- two signups consuming one invitation code;
- many hospitals accepting simultaneously;
- expansion job running while first acceptance commits;
- candidate exhaustion racing with the final hospital acceptance;
- duplicate search retry creating more than one dispatch attempt;
- paramedic changing destination while a hospital withdraws;
- paramedic cancelling while a hospital accepts or destination changes;
- location event targeting a hospital during destination change;
- duplicate clinical update retry;
- duplicate handoff request/confirmation.

Controls:

- unique constraints as final guards;
- aggregate version/optimistic locking;
- row lock or atomic conditional update for high-risk transitions;
- per-user idempotency records with request hash and stored result;
- same key plus different payload returns conflict;
- transactionally persisted outbox events;
- state and authorization rechecked inside transaction.

Never rely only on a frontend-disabled button.

## 31. Realtime and Outbox

Minimum event types:

```text
TRANSPORT_REQUEST_RECEIVED
HOSPITAL_OFFER_ACCEPTED
HOSPITAL_OFFER_REJECTED
HOSPITAL_OFFER_NO_RESPONSE
HOSPITAL_ACCEPTANCE_WITHDRAWN
HOSPITAL_SEARCH_EXHAUSTED
HOSPITAL_SEARCH_RETRY_STARTED
DESTINATION_SELECTED
DESTINATION_CHANGED
VITAL_SIGNS_ADDED
CONSCIOUSNESS_CHANGED
PRE_KTAS_CHANGED
TREATMENT_ADDED
SUPPLEMENTAL_ASSESSMENT_ADDED
PATIENT_SNAPSHOT_UPDATED
AMBULANCE_LOCATION_UPDATED
ETA_UPDATED
HANDOFF_REQUESTED
HANDOFF_CONFIRMED
TRANSPORT_REQUEST_CANCELLED
TRANSPORT_REQUEST_CLOSED
```

Outbox fields:

```text
id
aggregateType
aggregateId
eventType
payloadJson
recipientScope
createdAt
publishedAt nullable
attemptCount
nextAttemptAt nullable
```

Payload minimization:

- avoid full clinical payload if an invalidation event is enough;
- encrypt/protect broker transport;
- never place exact location in a broad hospital topic;
- destination location uses a destination-scoped channel and authorization check;
- clients refetch after event receipt;
- retain sequence/version to detect gaps.

## 32. Audit Policy

Audit:

- authentication and relevant authorization failures;
- organization and account state changes;
- invitation issue/use/revoke;
- request creation and search rounds;
- candidate exhaustion and retry dispatch attempts;
- offer send/accept/reject/withdraw;
- destination select/change/release;
- transport cancellation and cancellation reason;
- clinical record append/correction;
- location update occurrence without general-log coordinates;
- handoff request/confirmation;
- receiving ON/OFF.

Each audit row includes:

```text
actorAccountId
actorOrganizationId
action
targetType
targetId
serverOccurredAt
reasonCode nullable
beforeAfterMetadata or immutable event reference
correlationId
```

Do not copy passwords, invitation plaintext, tokens, direct identifiers, or full clinical payloads into generic logs.

## 33. Security and Privacy

- TLS everywhere;
- strong password hashing such as Argon2id or bcrypt with current parameters;
- secure invitation-code hashing/verification;
- short-lived access token or secure server session plus rotation policy;
- organization/role authorization on every query and command;
- rate limit login, code validation, and sensitive commands;
- encrypt sensitive backups and mobile drafts;
- secrets from environment/secret manager, never source control;
- no real patient data in development, fixtures, or CI;
- redact request/response bodies from APM;
- exact coordinate access audited and destination-scoped;
- retention and deletion jobs based on approved legal policy.

## 34. Reliability and Operations

- database migration tool such as Flyway or Liquibase;
- health/readiness checks that do not expose secrets;
- structured logs with non-sensitive correlation IDs;
- metrics for API latency/error, outbox lag, notification delay, search radius/candidate count, exhaustion/retry count, stale-location duration, and map failures;
- alert on stuck outbox events and abnormal offer delivery failures;
- map/push provider timeout and circuit breaker;
- backup and restore drills;
- database transaction boundaries documented in application services;
- UTC storage and server clock synchronization.

Initial targets:

- normal API p95 <= 1 second under expected load;
- response event delivery <= 3 seconds after commit;
- no loss of committed state when realtime delivery fails.

Validate targets with load and field tests.

## 35. Data Retention

Do not hard-code a guessed legal retention period in domain constants.

- completion and cancellation remove the request from active queries only;
- retain official/audit data for the approved period;
- separate retention by clinical, security, invitation, and operational metrics if required;
- delete or de-identify after expiration;
- legal hold overrides normal deletion when formally required;
- record retention job outcomes without logging clinical content.

## 36. Required Tests

### 36.1 Clinical Validation

- exact, estimated, and unknown age combinations
- disease/non-disease conditional mechanism and injury fields
- exact, estimated, and unknown onset time
- Pre-KTAS 1..5 and rejection of other levels
- emergency unfinished reason and later completed assessment
- AVPU unassessable reason
- each vital as value, unavailable, and refused
- unavailable `OTHER` detail
- treatment `NONE` exclusivity
- typed treatment details and failed attempts
- protocol-triggered supplemental fields and explicit unknown state
- preferred hospital not affecting search or destination
- active/supported/retired protocol version behavior
- separate ERSync protocol and Pre-KTAS standard versions
- impossible field combinations and bounded text

### 36.2 Append-Only History

- initial records created atomically
- in-transit update inserts new row
- old record unchanged
- correction references old row and requires reason
- snapshot uses latest valid chain head
- clinical/client/server timestamps remain distinct
- timeline order deterministic

### 36.3 Search

- 10km already has at least three hospitals
- immediate expansion to 20/30km
- fewer than three at 100km
- all eligible hospitals inside selected radius receive offers
- 60-second expansion includes only new hospitals
- first acceptance stops expansion
- unique constraint prevents duplicate offer within one dispatch attempt
- OFF/inactive/missing-location hospitals excluded
- final wait or all-rejected result transitions to candidate exhaustion
- exhaustion retry keeps the request and creates a new dispatch attempt
- previously rejected and nonresponsive hospitals are eligible in the new attempt
- duplicate retry is idempotent
- phone action does not change server state
- withdrawal re-search uses latest location and excludes contacted hospitals

### 36.4 Hospital Response

- multiple simultaneous acceptances succeed independently
- acceptance does not choose destination
- non-destination accepted offers disappear from hospital active queries
- hidden acceptance history and paramedic accepted list remain available
- hidden accepted offer becomes active when selected as the new destination
- rejection enum and required other detail
- withdrawal enum remains separate
- current destination withdrawal clears destination atomically
- rejected/withdrawn hospital loses later clinical access

### 36.5 Destination and Location

- only request owner selects accepted offer
- destination change preserves previous acceptance
- destination authorization switches atomically
- previous destination receives change notification and leaves active query
- previous hospital cannot query exact location
- out-of-order older location update ignored/rejected
- only latest location retained
- location becomes stale after the configured 30 seconds while the last coordinate remains readable
- new location clears stale state without an urgent event
- ETA failure does not fail location/request flow

### 36.6 Handoff

- paramedic cannot request without destination
- non-destination hospital cannot confirm
- confirmation requires requested state
- duplicate commands are idempotent
- completion closes active offers and rejects later clinical/location updates

### 36.7 Transport Cancellation

- cancellation succeeds from every allowed pre-handoff state
- missing cancellation reason is rejected
- cancellation clears destination and closes all active offers
- pending, selected, and hidden accepted hospitals receive cancellation invalidation
- duplicate cancellation is idempotent
- handoff-requested, completed, and cancelled requests cannot be cancelled
- cancelled request cannot be resumed or retried

### 36.8 Auth and Privacy

- admin cannot access any clinical endpoint
- other paramedic cannot access request
- other hospital cannot access offer
- hospital timeline contains no direct patient identifiers
- logs and errors redact clinical payloads and coordinates
- realtime topic subscription enforces organization and destination scope

### 36.9 Invitation

- plaintext shown only on generation response
- hash comparison works
- expired/used/revoked codes rejected
- concurrent signup consumes once
- role/organization binding cannot be changed by request

## 37. Development Environment Contract

- local development uses Docker MySQL and the `local` Spring profile.
- local run command: `docker compose up -d`, then `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`.
- local DB uses `127.0.0.1:3306`, database `ersync`, and account `ersync_local`.
- local developers must not connect directly to RDS.
- the `test` profile remains for fast H2-based tests.
- MySQL compatibility is validated with Testcontainers MySQL 8.4 tests.
- frontend local dev servers call the shared dev API through configured CORS origins.
- do not create branch-specific AWS environments for MVP development.
- run `./gradlew clean check` locally before creating a pull request.
- when API, database, or runtime configuration changes, also verify the local profile and readiness.
- do not create a pull request while a required local check is failing.
- record the executed commands and results in the feature `review.md`.
- merge to `main` only after PR review and Backend CI success.
- one pull request completes one user-verifiable feature, including its API,
  persistence, authorization, errors, tests, and feature documents.
- do not split one feature into controller-only, service-only, repository-only,
  or migration-only pull requests.
- split a large feature only at a user workflow boundary that can be reviewed,
  tested, and deployed independently.
- when frontend integration changes, include one contract generated from
  `docs/templates/frontend-contract.md`.

## 38. Suggested Implementation Order

1. Add persistence, validation, migration, and test dependencies.
2. Implement account, organization, invitation, and security boundaries.
3. Implement hospital profile and receiving state.
4. Implement clinical DTOs, cross-field validators, and append-only entities.
5. Implement initial request transaction and current snapshot.
6. Implement geospatial candidate search and offer uniqueness.
7. Implement hospital response and destination concurrency.
8. Implement in-transit clinical update endpoints and timeline.
9. Implement latest location and map adapter.
10. Implement handoff two-party confirmation.
11. Implement outbox/realtime delivery and reconnect query support.
12. Add security, race, load, failure, and retention tests.

Do not start with realtime transport before the transactional domain state is reliable.

## 39. Out of MVP

- hospital internal bed/OR/staff/equipment integration
- public emergency-data synchronization
- situation-room coordination
- medical direction
- hospital employee personal accounts
- patient direct identifiers
- manual initial hospital selection
- manual ETA
- GPS route history
- automatic final hospital assignment
- complex account recovery

## 40. External Validation Required

- current official Pre-KTAS algorithm and version
- assessor qualifications and responsibility
- relationship to the official 119 activity log
- clinical/location data legal basis and retention
- approval for shared hospital account operation
- regional suitability of distance search rules
- network outage fallback process
- exact vital plausibility boundaries and protocol wording

## 41. Domain Ownership

Feature documents remain flat under `docs/features/`. Record the primary domains
in the feature `spec.md`; do not create nested domain folders.

| Domain | Owns | Must not own |
|---|---|---|
| `auth` | login, token lifecycle, authenticated actor context | organization registration, invitation issuance |
| `organization` | hospital and EMS organization identity, membership | hospital ER profile, login tokens |
| `invitation` | one-time organization and role-bound signup codes | authentication sessions |
| `hospital` | ER address, verified coordinates, contact, receiving state | offer decisions, clinical data, ETA calculation |
| `transport` | request, search rounds, offers, destination, retry, cancellation | clinical record contents, map provider details |
| `clinical` | protocol version, assessment, treatment, correction history | hospital search and destination |
| `location` | latest location, freshness, distance, ETA adapter | full route history |
| `handoff` | paramedic completion request and hospital confirmation | request creation and hospital search |
| `notification` | realtime state-change delivery, reconnect, outbox dispatch | authoritative business state |
| `audit` | security and business audit events | passwords, tokens, invitation plaintext, full clinical payloads |
| `global` | security, shared errors, trace ID, logs, database and health foundation | feature-specific business policy |

Boundary rules:

- `clinical` records are append-only after submission.
- `transport` allows multiple accepted offers but at most one current destination.
- `location` persists only the latest position; exact coordinates are destination-scoped.
- `handoff` completes only after the paramedic request and destination hospital confirmation.
- `notification` events signal change; current query state remains authoritative.
- shared accounts and security context come from authentication, never client-supplied IDs.

## 42. Error and Log Contract

All 4xx and 5xx responses use:

```json
{
  "code": "HOSPITAL_001",
  "message": "병원을 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "01JABC"
}
```

- `code` is a stable client branch key.
- `message` is a fixed, user-safe message.
- `fieldErrors` contains DTO validation failures.
- `traceId` connects the response to server logs.
- never return a stack trace or internal exception message.

Raise a known failure with:

```java
throw new CustomException(ErrorCode.HOSPITAL_NOT_FOUND);
```

Do not create ad hoc error codes or messages in controllers and services. A new
error requires:

1. a failure condition in the feature `spec.md`;
2. a unique `ErrorCode` enum value;
3. reviewed HTTP status and public message;
4. a failure-path test;
5. an entry in the table below.

Code format is `{AREA}_{3-digit number}`. Enum names describe the condition, for
example `TRANSPORT_STATUS_CANNOT_CHANGE`.

| HTTP | Use |
|---:|---|
| 400 | invalid request format, value, or validation |
| 401 | missing, expired, or forged authentication |
| 403 | authenticated but role, organization, or ownership denied |
| 404 | resource does not exist |
| 405 | unsupported HTTP method |
| 409 | duplicate request or state-transition conflict |
| 429 | rate limit exceeded |
| 500 | unexpected internal failure |
| 502 | invalid external-service response |
| 503 | temporary external or critical dependency failure |

Current error registry:

| Area | Code | HTTP | Condition |
|---|---|---:|---|
| COMMON | `COMMON_001` | 400 | DTO, parameter, or type validation failure |
| COMMON | `COMMON_002` | 405 | unsupported HTTP method |
| COMMON | `COMMON_003` | 500 | unhandled server exception |
| COMMON | `COMMON_004` | 403 | common access denial |
| COMMON | `COMMON_005` | 409 | unique-key or duplicate-request conflict |
| COMMON | `COMMON_006` | 404 | no mapped or available resource |
| AUTH | `AUTH_001` | 401 | missing authentication |
| AUTH | `AUTH_002` | 401 | invalid or expired access token |
| AUTH | `AUTH_003` | 403 | missing role or permission |
| USER | `USER_001` | 404 | user account not found |
| USER | `USER_002` | 403 | disabled account |
| HOSPITAL | `HOSPITAL_001` | 404 | hospital not found |
| HOSPITAL | `HOSPITAL_002` | 409 | hospital cannot receive the request |
| HOSPITAL | `HOSPITAL_003` | 409 | receiving state cannot be confirmed |
| HOSPITAL | `HOSPITAL_004` | 409 | insufficient bed, staff, or equipment capacity |
| TRANSPORT | `TRANSPORT_001` | 404 | transport request not found |
| TRANSPORT | `TRANSPORT_002` | 409 | destination offer was not accepted |
| TRANSPORT | `TRANSPORT_003` | 409 | transport request expired |
| TRANSPORT | `TRANSPORT_004` | 409 | state transition not allowed |
| TRANSPORT | `TRANSPORT_005` | 404 | hospital offer not found |
| TRANSPORT | `TRANSPORT_006` | 409 | hospital already accepted or rejected |
| PROTOCOL | `PROTOCOL_001` | 404 | protocol not found |
| PROTOCOL | `PROTOCOL_002` | 409 | protocol version inactive |
| PROTOCOL | `PROTOCOL_003` | 500 | protocol evaluation failed |

Structured error logs:

```text
WARN event=BUSINESS_ERROR traceId=01JABC code=HOSPITAL_001 status=404 method=GET path=/api/v1/hospitals/{hospitalId} exception=CustomException
ERROR event=SYSTEM_ERROR traceId=01JXYZ code=COMMON_003 status=500 method=POST path=/api/v1/transports exception=IllegalStateException
```

| Event | Level |
|---|---|
| `BUSINESS_ERROR` | WARN |
| `VALIDATION_ERROR` | WARN |
| `AUTH_ERROR` | WARN |
| `SYSTEM_ERROR` | ERROR with stack trace |

- log the Spring route pattern when possible, not a dynamic identifier.
- do not log request or response bodies.
- never log patient data, tokens, passwords, invitation plaintext, secrets, or exact GPS.
- add LogScope parsing and matching for `traceId` when request-level analysis is needed.

## 43. Frontend Integration Contract

Create `docs/contracts/{number}-{feature}.md` when a feature changes an API,
authorization scope, state or enum, error handling, or realtime event consumed by
frontend.

- use one contract per feature;
- create or finalize it after implementation and local verification;
- include method, path, request, response, authorization, enums, errors, events,
  frontend sequence, compatibility, and verification;
- include it only when it matches code and tests and has no unresolved decisions;
- link the contract from the feature `review.md` and pull request;
- record `Frontend Impact: NONE` instead when there is no frontend effect;
- do not maintain another handwritten API contract document.

If OpenAPI is introduced, OpenAPI is the exact schema source and the frontend
contract remains the feature-level change and integration guide.
