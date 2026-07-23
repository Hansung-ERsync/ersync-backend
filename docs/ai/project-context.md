# ERSync AI Agent Context

- Audience: any AI agent working on this repository
- Status: MVP source of truth
- Updated: 2026-07-17

## 1. Product Mission

ERSync helps paramedics send a structured emergency-patient summary to nearby emergency departments. Hospitals answer accept or reject. The paramedic chooses one accepted hospital as the current destination. The destination hospital receives current location and in-transit clinical updates until handoff is confirmed.

The product supports communication. It does not replace medical judgment, official dispatch, or legally required records.

## 2. Applications and Stack

- Paramedic client: Flutter mobile app planned
- Hospital client: React web app planned
- Admin client: React web app planned
- Backend: Java and Spring Boot
- Realtime: WebSocket or SSE plus push notification fallback
- Map: external geocoding, distance, and ETA API

## 3. MVP Actors

| Role | Account policy | Patient access |
|---|---|---|
| `SUPER_ADMIN` | one operator/bootstrap account | none |
| `PARAMEDIC` | individual account | assigned request only |
| `HOSPITAL_STAFF` | one shared ER account per hospital | offers sent to own hospital |

Organization types are `HOSPITAL` and `EMS_UNIT`.

## 4. Non-Negotiable Product Rules

1. Keep existing account, search, destination, withdrawal, and completion policies.
2. Apply the attached patient-data specification only to clinical input and updates.
3. Never represent missing required clinical data as blank or null without a reason state.
4. Preserve measured/observed/performed time separately from client entry time and server receipt time.
5. Clinical reassessments are append-only. Never overwrite history.
6. Multiple hospitals may accept. Exactly zero or one hospital is the current destination.
7. Hospital target selection is automatic. The paramedic does not manually select initial recipients.
8. ETA is automatic. ETA failure never blocks request creation.
9. Exact live location is visible only to the current destination hospital.
10. Patient name, resident registration number, contact, exact birth date, and detailed home address are not collected or shared in MVP.
11. Completion requires a paramedic request and current destination hospital confirmation.

## 5. Account and Invitation Policy

The super admin registers an organization name and type. It issues an organization- and role-bound one-time invitation code.

Invitation code rules:

- expiry: 3 days, 7 days, or custom
- plaintext shown once
- only a cryptographic hash stored
- states: `AVAILABLE`, `USED`, `EXPIRED`, `REVOKED`
- one successful signup consumes the code
- admin may revoke before expiry
- issue, use, expiry, and revoke events are audited

Hospital signup also captures ER address, verified latitude/longitude, and contact number. New hospital receiving state defaults to `OFF`.

### 5.1 Clinical Protocol Versioning

- every request pins an ERSync patient-assessment protocol version as `assessmentProtocolVersion`;
- every Pre-KTAS record separately stores the official classification standard version;
- protocol definitions are medically reviewed, versioned release artifacts in MVP;
- super admin cannot edit clinical rules;
- clients obtain the active/supported protocol definition from the backend and keep a bundled/cacheable offline copy;
- a draft remains pinned to its creation version;
- the server validates against the submitted supported version;
- never silently migrate an in-progress draft across incompatible versions;
- the app guides structured entry but does not invent an unvalidated automatic clinical decision.

## 6. Clinical Data Model

Do not implement the patient payload as one mutable memo or one unversioned JSON blob. Use structured records and a derived current snapshot.

### 6.1 Patient Demographics

```text
ageStatus: EXACT | ESTIMATED | UNKNOWN
ageYears: integer required for EXACT or ESTIMATED
sex: MALE | FEMALE | UNKNOWN
```

No patient identifier is required.

### 6.2 Incident Assessment

```text
occurrenceType: DISEASE | NON_DISEASE | OTHER | UNKNOWN
mechanism: TRAFFIC | FALL | FALL_FROM_HEIGHT | BLUNT | PENETRATING |
           BURN | POISONING | DROWNING_ASPHYXIA | ASSAULT_SELF_HARM |
           MACHINERY_AGRICULTURAL | OTHER | UNKNOWN
injurySites: HEAD_FACE | NECK | CHEST | ABDOMEN_PELVIS | SPINE |
             UPPER_LIMB | LOWER_LIMB | MULTIPLE | UNKNOWN
primarySymptom: required enum
secondarySymptoms: optional enum set
onsetTimeStatus: EXACT | ESTIMATED | UNKNOWN
onsetAt: required when status is EXACT or ESTIMATED
```

Primary/secondary symptom enum:

```text
ALTERED_CONSCIOUSNESS | DYSPNEA | RESPIRATORY_ARREST | CHEST_PAIN |
CARDIAC_ARREST | SUSPECTED_STROKE | SEIZURE_SYNCOPE | TRAUMA |
BLEEDING | GASTROINTESTINAL | POISONING | BURN | PREGNANCY_DELIVERY |
BEHAVIORAL_SELF_HARM | FEVER_INFECTION | OTHER | UNKNOWN
```

Conditional timestamps may include last-known-well, accident time, and witnessed/estimated cardiac arrest time. Each uses exact, estimated, or unknown semantics.

### 6.3 Pre-KTAS Assessment

```text
level: 1 | 2 | 3 | 4 | 5
assessorAccountId
assessedAt
standardVersion
enteredAt
serverReceivedAt
```

There is no sixth `unassessable` level.

Emergency exception:

```text
classificationStatus: COMPLETED | EMERGENCY_UNFINISHED
exceptionReason: CPR_IN_PROGRESS | SCENE_DANGER |
                 INSUFFICIENT_ASSESSMENT_TIME | OTHER
exceptionDetail: required for OTHER
```

An emergency-unfinished request may be sent without a level. The paramedic must append a completed Pre-KTAS assessment as soon as possible.

### 6.4 Consciousness Assessment

```text
avpu: A | V | P | U | UNASSESSABLE
unassessableReason: SCENE_DANGER | PATIENT_INACCESSIBLE | OTHER
observedAt
enteredAt
serverReceivedAt
```

Reason is required only for `UNASSESSABLE`.

### 6.5 Vital Sign Set

A set contains blood pressure, pulse, respiratory rate, temperature, and SpO2. Every item has an explicit state.

```text
state: VALUE | MEASUREMENT_UNAVAILABLE | PATIENT_REFUSED
value: numeric value when state is VALUE
unavailableReason: PATIENT_CONDITION | SCENE_DANGER | INJURY_SITE |
                   DEVICE_ERROR | OTHER
```

`unavailableReason` is required for `MEASUREMENT_UNAVAILABLE`. A reason is not required for `PATIENT_REFUSED`.

Each vital set stores:

```text
measuredAt
enteredAt
serverReceivedAt
createdBy
```

Blood pressure stores systolic and diastolic values. Units are fixed by the API contract, not free text.

### 6.6 Treatment Event

Treatment types:

```text
NONE | OXYGEN | AIRWAY | CPR | DEFIBRILLATION_AED | IV_FLUID |
MEDICATION | BLEEDING_WOUND | IMMOBILIZATION | ECG |
WARMING_COOLING | DELIVERY | OTHER
```

`NONE` is mutually exclusive with all other treatment types. Store all attempts, including failures.

Each treatment event stores type-specific structured details and:

```text
performedAt
enteredAt
serverReceivedAt
createdBy
attemptResult when applicable
```

Examples:

- oxygen: method, flow rate, start time
- airway: method/device, performed time, success
- CPR: start time, current status, ROSC
- defibrillation: monitor/defibrillation flag, count, time
- IV/fluid: success, fluid type, amount, time
- medication: name, dose, route, time
- bleeding/wound: site, method, tourniquet, time
- immobilization: site, method
- ECG: 3/12 lead, time, findings, transmitted
- delivery: action, birth time
- other: short detail, time

### 6.7 Conditional Supplemental Assessment

Do not force every supplemental field on every patient. Create typed, protocol-triggered records for:

- pupils;
- glucose;
- relevant history, allergies, and medications;
- infection/isolation concern;
- cardiac arrest detail;
- major trauma detail;
- cardiac/chest-pain detail;
- suspected stroke detail;
- pregnancy/delivery detail;
- patient/guardian preferred hospital and prior notice.

When a protocol condition opens a required field, it must have a value or explicit unknown state. Preferred hospital is context only. It does not bypass automatic search or select the destination.

Recipient personal name/signature collection is out of MVP. The shared hospital account and confirmation time represent in-app handoff confirmation. Any legally required external signature remains in the official process.

### 6.8 Clinical Record Time and Correction

Use three distinct time concepts:

- clinical time: `measuredAt`, `observedAt`, `assessedAt`, or `performedAt`
- client entry time: `enteredAt`
- trusted server time: `serverReceivedAt`

Never mutate a submitted clinical record. A correction appends a new record with `supersedesRecordId`, correction reason, and actor. The original remains auditable.

### 6.9 Current Patient Snapshot

`CurrentPatientSnapshot` is a read model derived from latest valid records. It is not the source of truth.

It includes:

- demographics and incident summary
- primary and secondary symptoms
- current Pre-KTAS or emergency exception
- latest AVPU
- latest complete vital set
- clinically relevant treatment summary
- clinically relevant supplemental assessment summary
- patient assessment protocol version
- last clinical update time

## 7. Initial Request Validation

The server accepts an initial request only when:

- age has exact/estimated value or unknown state
- sex has value or unknown state
- occurrence and applicable trauma fields are explicit
- primary symptom is explicit
- onset time has exact/estimated value or unknown state
- Pre-KTAS is complete, or emergency exception reason is present
- AVPU is explicit, including unassessable reason when needed
- all five vital items have value, measurement-unavailable reason, or patient-refused state
- at least one treatment is present, including `NONE`
- every protocol-triggered supplemental field has a value or explicit unknown state
- caller organization and callback contact are resolvable from authenticated context
- the ERSync assessment protocol version is supported by the server
- an origin coordinate exists from GPS or a paramedic-confirmed manual map pin
- a client idempotency key is present

ETA is not a validation requirement. A coordinate is required because automatic hospital search cannot run without it. Initial hospital recipients are not client input.

## 8. Hospital-Facing Payload

Initial and updated hospital views may contain:

- estimated/exact age and sex
- occurrence type, mechanism, injury sites
- primary/secondary symptoms and onset status/time
- Pre-KTAS level or emergency-unfinished reason
- latest AVPU
- all five latest vital values/states
- current treatment summary
- clinically relevant supplemental assessment summary
- EMS organization and callback contact
- authenticated requester account audit reference and role/approved qualification label
- patient assessment protocol version
- distance and automatic ETA when available
- request arrival, elapsed, and last clinical update times

Exclude:

- name
- resident registration number
- exact birth date
- patient contact
- detailed residential address
- unrelated narrative

Do not expose full treatment attempt history on the request card. Provide a current summary and a separate authorized timeline.

## 9. In-Transit Clinical Updates

The paramedic may append:

- vital sign set
- consciousness assessment
- Pre-KTAS assessment
- treatment event
- supplemental assessment

Automatic ETA is updated by location/map processing. Situation-room assistance is out of MVP.

Each accepted update:

1. validates ownership and request state;
2. appends a new immutable record;
3. rebuilds the current snapshot;
4. writes an outbox event in the same transaction;
5. notifies authorized hospital clients;
6. retains previous values in the clinical timeline.

Hospital users cannot edit paramedic clinical records.

Clinical summary recipients are hospitals with an open `PENDING` or `ACCEPTED` offer. Stop delivery after rejection, withdrawal, or request closure. Exact location remains restricted to the current destination hospital.

## 10. Hospital Search Policy

Configuration:

```text
initialRadiusKm = 10
minimumCandidateCount = 3
radiusStepKm = 10
expansionIntervalSeconds = 60
maximumRadiusKm = 100
stopExpansionOnFirstAcceptance = true
```

Eligible hospital:

- registered and active hospital organization
- valid ER coordinates
- receiving state `ON`
- not previously contacted for this request
- not excluded after acceptance withdrawal

Initial search expands immediately by 10km until at least three candidates exist or 100km is reached. Send to every eligible hospital inside the selected radius.

If no hospital accepts for 60 seconds, expand by 10km and send only to newly included hospitals. Stop automatic expansion on the first acceptance.

No hospital receives the same request twice.

## 11. Hospital Response Policy

### 11.1 Rejection

Initial rejection reasons:

```text
ER_GENERAL_BED_SHORTAGE
ISOLATION_BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
ICU_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

`OTHER` requires detail.

### 11.2 Acceptance

Acceptance means the hospital currently states it can receive the patient. Multiple offers can be `ACCEPTED`. Acceptance does not set the destination automatically.

### 11.3 Acceptance Withdrawal

Withdrawal reasons are a separate enum:

```text
BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

`OTHER` requires detail.

On withdrawal:

- notify the paramedic urgently;
- clear destination if the withdrawn hospital was current;
- keep the same `TransportRequest`;
- search from the latest paramedic location;
- exclude the withdrawn hospital and all previously contacted hospitals;
- retain all response history.

## 12. Destination Policy

`TransportRequest.currentDestinationOfferId` is null or references one accepted offer.

The paramedic may select another accepted offer. On change:

- update the current destination atomically;
- notify the new hospital that the ambulance is en route;
- notify the previous destination that it is no longer selected;
- keep both acceptance records;
- stop exact location delivery to the previous destination.

## 13. Location and ETA

- mobile sends location around every 10 seconds while en route;
- server keeps latest location only, not route history;
- exact location is delivered only to current destination hospital;
- map API calculates distance and ETA;
- ETA response includes calculation time and provider status;
- map failure returns `UNAVAILABLE` and never blocks clinical request flow;
- stale successful ETA may be shown only with its calculation time.

## 14. Request State Model

Suggested `TransportRequestStatus`:

```text
SEARCHING
ACCEPTED_AVAILABLE
EN_ROUTE
HANDOFF_REQUESTED
COMPLETED
CANCELLED
```

Suggested `HospitalOfferStatus`:

```text
PENDING
ACCEPTED
REJECTED
ACCEPTANCE_WITHDRAWN
```

Destination is represented separately by `TransportRequest.currentDestinationOfferId` and immutable destination events. Offer closure is represented by `closedAt` and the request state. Offer response history should be event/audit based. Do not erase earlier statuses.

## 15. Handoff Completion

1. paramedic requests completion;
2. request becomes `HANDOFF_REQUESTED`;
3. only the current destination hospital may confirm;
4. confirmation changes request to `COMPLETED`;
5. all active offers close and disappear from active lists;
6. persisted clinical and audit history remains.

Repeated commands must be idempotent.

## 16. Emergency Department Receiving State

- `ON`: eligible for new requests
- `OFF`: excluded from new requests
- changing to OFF does not affect accepted or en-route cases
- changing to OFF does not imply acceptance withdrawal

## 17. Realtime Event Minimum

```text
TRANSPORT_REQUEST_RECEIVED
HOSPITAL_OFFER_ACCEPTED
HOSPITAL_OFFER_REJECTED
HOSPITAL_ACCEPTANCE_WITHDRAWN
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
TRANSPORT_REQUEST_CLOSED
```

Use transactional outbox delivery. Realtime events are hints; clients must refetch authoritative state after reconnect.

## 18. Security and Audit

- TLS for all traffic
- role and organization authorization on every endpoint
- no patient access for super admin
- no secrets or clinical payloads in application logs
- encrypted mobile offline draft
- hash passwords and invitation codes
- exact location restricted to current destination hospital
- audit actor, organization, action, server time, entity, before/after or event payload, and reason
- use correlation IDs that do not contain patient data

Retention periods require legal validation. Completion means hidden from active views, not physical deletion.

## 19. Reliability and Performance Targets

- idempotency key for request creation and all critical commands
- optimistic/pessimistic concurrency guard for offer response and destination change
- normal API p95 target: 1 second
- server-to-client response notification target: 3 seconds
- reconnect and catch-up for realtime clients
- retry external map and push providers with bounded backoff
- monitor outbox lag, notification delay, search radius, candidate count, and map failures

Targets must be validated by load and field tests.

## 20. Conflict Resolution for Agents

If older notes conflict with this document, follow these resolutions:

- hospital account is shared for MVP, not personal;
- initial hospital recipients are automatic, not manually selected;
- multiple hospitals can accept; one acceptance does not cancel others;
- destination is chosen by the paramedic;
- ETA is map-derived, not manually entered;
- situation-room features are future scope;
- shared hospital responses record account, hospital, and server time, not a responder personal name;
- initial rejection reasons and post-acceptance withdrawal reasons are separate;
- no patient direct identifiers are shared after acceptance in MVP.

## 21. Out of MVP

- realtime hospital bed/OR/staff/equipment integration
- public emergency-data auto-sync
- situation-room coordination
- medical direction
- personal hospital staff accounts
- patient direct identifiers in the app
- GPS route history
- automatic final hospital assignment
- complex password/account recovery

## 22. Required Tests

At minimum test:

- every required clinical field with value, unavailable, and refused states
- emergency unfinished Pre-KTAS and later completed assessment
- invalid conditional fields and `OTHER` details
- append-only updates and correction history
- measured/client/server timestamp separation
- unauthorized hospital clinical access
- search radius expansion and no duplicate offer
- simultaneous hospital acceptance
- atomic destination change
- current destination withdrawal and re-search
- ETA failure without request failure
- exact location access restrictions
- duplicate completion commands
- offline create retry with one server request

## 23. External Validation Required

Before production, verify:

- latest official Pre-KTAS criteria, versioning, and assessor qualification
- relation to official 119 activity records
- legal basis and retention for clinical/location data
- shared hospital account acceptance by operating organizations
- distance policy suitability by region
- fallback process during network failure

## 24. Reference Basis

- [National Fire Agency field emergency treatment guideline](https://www.nfa.go.kr/nfa/publicrelations/legalinformation/archives/?cntId=50&mode=view)
- [Korean prehospital emergency-patient severity classification standard](https://www.law.go.kr/LSW/admRulInfoP.do?admRulSeq=2100000270800&chrClsCd=010201)
- [Enforcement Rule of the 119 Rescue and Emergency Medical Services Act, Article 18](https://www.law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000995617)

These references guide requirements. They do not by themselves prove medical or legal compliance.
