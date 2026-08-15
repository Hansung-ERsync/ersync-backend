# ERSync 공통 에이전트 컨텍스트

- Audience: frontend and backend AI agents
- Updated: 2026-08-13

This is the one shared context delivered to both frontend and backend agents. It defines product terms, roles, state, visibility, clinical data, workflow, privacy, and reliability rules that both sides must interpret identically.

Human-readable product policies and scenarios are maintained separately in [`mvp-requirements.md`](../project/mvp-requirements.md). This context must not override them.

## 1. Product Mission

ERSync helps paramedics send a structured emergency-patient summary to nearby emergency departments. Hospitals answer accept or reject. The paramedic chooses one accepted hospital as the current destination. The destination hospital receives current location and in-transit clinical updates until handoff is confirmed.

The product supports communication. It does not replace medical judgment, official dispatch, or legally required records.

## 2. Applications and Stack

- Paramedic client: Flutter mobile app planned
- Hospital client: React web app planned
- Admin client: React web app planned
- Backend: Java and Spring Boot
- Realtime: delivery method is selected per feature; clients must recover by refetching authoritative state
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
12. After destination selection, non-destination accepted and pending offers remain visible with `another hospital en route`; accepted hospitals may withdraw and pending hospitals may still accept or reject.
13. The request owner may cancel before handoff is requested, with a mandatory cancellation reason. Cancellation is terminal.
14. MVP assumes at least one hospital eventually accepts. It has no separate no-response or candidate-exhausted outcome, full resend action, or hospital phone action.
15. A destination hospital keeps the last location when updates stop and sees a stale age after 30 seconds by default.
16. If the current destination issues an urgent inability-to-receive notice, exclude only that hospital and resume searching from the latest paramedic location on the same request.

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

Hospital signup also captures ER address, optional detail address up to 200 characters,
verified latitude/longitude, and contact number. New hospital receiving state defaults to
`OFF`. An offer snapshots the hospital address, detail address, and coordinates when it is
created. Expose these location fields to the owning paramedic only while the offer is
`ACCEPTED`; keep them absent for pending, rejected, withdrawn, and no-response offers.
Hospital detail address is ER location data, not the prohibited patient residential address.

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
4. records the notification intent in a transactionally safe way;
5. notifies authorized hospital clients;
6. retains previous values in the clinical timeline.

Hospital users cannot edit paramedic clinical records.

Before destination selection, hospitals with an open `PENDING` or `ACCEPTED` offer may receive the latest minimum clinical summary needed for a response. After destination selection, only the current destination receives later clinical updates and exact location. Other pending and accepted hospitals keep their existing permitted snapshot and response rights. When an urgent destination withdrawal causes a pending hospital to be re-notified, provide that hospital with the latest minimum clinical summary at that point. Stop delivery after rejection, withdrawal, cancellation, or completion.

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
- not previously contacted in the current dispatch attempt
- not excluded after acceptance withdrawal

Initial search expands immediately by 10km until at least three candidates exist or 100km is reached. Send to every eligible hospital inside the selected radius.

If no hospital accepts for 60 seconds, expand by 10km and send only to newly included hospitals. Stop automatic expansion on the first acceptance.

Within one transport request, one hospital has at most one offer/card. A repeated notification updates the existing pending offer instead of creating a duplicate card.

MVP assumes at least one hospital eventually accepts. Do not expose a separate no-response or candidate-exhausted outcome, full resend action, or hospital phone action. A pending hospital remains pending until it accepts, rejects, the request is cancelled or completed, or an allowed policy transition closes it.

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

After rejection, the hospital may keep only the opaque request identifiers, response status,
rejection reason and processing time in HISTORY. Block offer detail, clinical timeline and
location access with `TRANSPORT_005`. Do not expose patient demographics, clinical data,
requester contact, distance or ETA in the rejected history item.

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

Before destination selection, an accepted hospital may withdraw with a mandatory reason. If another destination already exists, that destination remains unchanged and no new search starts.

The current destination uses an urgent inability-to-receive notice rather than an ordinary withdrawal. On that notice:

- notify the paramedic urgently;
- clear the current destination;
- exclude the withdrawn hospital from this transport request;
- keep the same `TransportRequest` and all response history;
- retain every other accepted hospital so the paramedic can select it immediately;
- re-notify existing pending hospitals by updating their existing card, request time, the clinical summary fixed at that re-request time, and current-origin distance and ETA;
- keep other accepted hospitals selectable but retain their frozen clinical and route information until selected as the new destination;
- do not resend to rejected hospitals;
- search from the latest paramedic location and create offers only for newly eligible hospitals;
- never choose the next destination automatically.

The paramedic UI does not enter a separate `no accepted hospital` state. Until the
paramedic selects a new destination, display `새로운 목적지를 찾고 있습니다`.

## 12. Destination Policy

`TransportRequest.currentDestinationOfferId` is null or references one accepted offer.

The paramedic may select another accepted offer. On change:

- update the current destination atomically;
- notify the new hospital that the ambulance is en route;
- notify the previous destination that it is no longer selected;
- keep both acceptance records;
- keep non-destination accepted offers visible as `accepted, another hospital en route` and withdrawable;
- keep pending offers visible as `another hospital en route, response available`;
- keep non-destination accepted offers in the paramedic's accepted list;
- stop exact location delivery to the previous destination.

## 13. Transport Cancellation

The request owner paramedic may cancel after request creation and before `HANDOFF_REQUESTED`.

Mandatory reason:

```text
PATIENT_REFUSED_TRANSPORT
GUARDIAN_SELF_TRANSPORT
SCENE_RESOLVED
OTHER
```

Cancellation:

- is allowed from `SEARCHING`, `ACCEPTED_AVAILABLE`, or `EN_ROUTE`;
- sets the request to `CANCELLED` and records actor, reason, and server time;
- clears the current destination;
- closes active offers without erasing their response history;
- notifies every pending or accepted hospital, including non-destination accepted hospitals;
- removes the request from every hospital active dashboard and the paramedic active flow;
- rejects resume and later clinical, location, destination, re-search, or handoff commands.

If transport becomes necessary again, create a new request.

## 14. Location and ETA

- mobile sends location around every 10 seconds while en route;
- server keeps latest location only, not route history;
- exact location is delivered only to current destination hospital;
- the destination keeps displaying the latest stored position when updates stop;
- location becomes `STALE` when no new server receipt occurs for 30 seconds by default;
- return `lastReceivedAt` and a server-derived stale state so the client can show `마지막 수신 N초/분 전`;
- a new location automatically returns the state to current;
- stale location is an operational state, not an error or urgent alert;
- map API calculates distance and ETA;
- ETA response includes calculation time and provider status;
- map failure returns `UNAVAILABLE` and never blocks clinical request flow;
- stale successful ETA may be shown only with its calculation time.

## 15. Request State Model

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

Destination is represented separately by `TransportRequest.currentDestinationOfferId` and immutable destination events. `목적지 재선정 중` is a product display state after urgent destination withdrawal; its internal representation must be chosen in the feature spec without reintroducing candidate exhaustion. Offer closure is represented by `closedAt` and the request state. Offer response history should be event/audit based. Do not erase earlier statuses. Legacy enum values may remain in the current code until a dedicated implementation aligns it, but they are not target MVP behavior.

## 16. Handoff Completion

1. paramedic requests completion;
2. request becomes `HANDOFF_REQUESTED`;
3. only the current destination hospital may confirm;
4. confirmation changes request to `COMPLETED`;
5. all active offers close and disappear from active lists;
6. persisted clinical and audit history remains.

Repeated commands must be idempotent.

## 17. Emergency Department Receiving State

- `ON`: eligible for new requests
- `OFF`: excluded from new requests
- changing to OFF does not affect accepted or en-route cases
- changing to OFF does not imply acceptance withdrawal

## 18. Realtime Event Minimum

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
TRANSPORT_REQUEST_CANCELLED
TRANSPORT_REQUEST_CLOSED
```

Realtime events are hints; clients must refetch authoritative state after reconnect.
The backend engineer selects the persistence and delivery mechanism for each feature
and records the choice in its implementation document.

## 19. Security and Audit

- TLS for all traffic
- role and organization authorization on every endpoint
- no patient access for super admin
- no secrets or clinical payloads in application logs
- encrypted mobile offline draft
- hash passwords and invitation codes
- exact location restricted to current destination hospital
- audit actor, organization, action, server time, entity, before/after or event payload, and reason
- audit urgent destination withdrawal, resulting search rounds, transport cancellation, and cancellation reason
- use correlation IDs that do not contain patient data

Retention periods require legal validation. Completion means hidden from active views, not physical deletion.

## 20. Reliability and Performance Targets

- idempotency key for request creation and all critical commands
- optimistic/pessimistic concurrency guard for offer response, destination change, urgent-withdrawal re-search, and cancellation
- normal API p95 target: 1 second
- server-to-client response notification target: 3 seconds
- reconnect and catch-up for realtime clients
- retry external map and push providers with bounded backoff
- monitor event delivery delay, search radius, candidate count, urgent-withdrawal re-search counts, stale-location duration, and map failures

Targets must be validated by load and field tests.

## 21. Conflict Resolution for Agents

If older notes conflict with this document, follow these resolutions:

- hospital account is shared for MVP, not personal;
- initial hospital recipients are automatic, not manually selected;
- multiple hospitals can accept; one acceptance does not cancel others;
- destination is chosen by the paramedic;
- selecting a destination keeps other accepted and pending offers visible with `another hospital en route` while restricting later clinical updates and exact location to the current destination;
- cancellation before handoff request is terminal and requires a reason;
- no-response, candidate exhaustion, full resend, and hospital phone actions are out of MVP;
- urgent destination withdrawal keeps the same request, excludes the withdrawn hospital, re-notifies pending hospitals without duplicate cards, and adds newly eligible hospitals from the latest location;
- stale location remains visible with elapsed-time text and is not an error;
- ETA is map-derived, not manually entered;
- situation-room features are future scope;
- shared hospital responses record account, hospital, and server time, not a responder personal name;
- initial rejection reasons and post-acceptance withdrawal reasons are separate;
- no patient direct identifiers are shared after acceptance in MVP.

## 22. Out of MVP

- realtime hospital bed/OR/staff/equipment integration
- public emergency-data auto-sync
- situation-room coordination
- medical direction
- personal hospital staff accounts
- patient direct identifiers in the app
- GPS route history
- automatic final hospital assignment
- separate no-response and candidate-exhausted outcomes
- full hospital resend after maximum-radius search and hospital phone action
- complex password/account recovery

## 23. Required Tests

At minimum test:

- every required clinical field with value, unavailable, and refused states
- emergency unfinished Pre-KTAS and later completed assessment
- invalid conditional fields and `OTHER` details
- append-only updates and correction history
- measured/client/server timestamp separation
- unauthorized hospital clinical access
- search radius expansion sends offers only to newly eligible hospitals
- one offer/card per transport request and hospital when a pending hospital is re-notified
- simultaneous hospital acceptance
- atomic destination change
- non-destination accepted and pending cards remain visible with response rights but no later clinical or exact-location delivery
- current destination urgent withdrawal, withdrawn-hospital exclusion, pending-card refresh, and latest-location search for new hospitals
- cancellation from each allowed state, mandatory reason, notification, destination release, and terminal behavior
- ETA failure without request failure
- exact location access restrictions
- stale location after 30 seconds and automatic recovery on the next update
- duplicate completion commands
- offline create retry with one server request

## 24. External Validation Required

Before production, verify:

- latest official Pre-KTAS criteria, versioning, and assessor qualification
- relation to official 119 activity records
- legal basis and retention for clinical/location data
- shared hospital account acceptance by operating organizations
- distance policy suitability by region
- fallback process during network failure

## 25. Reference Basis

- [National Fire Agency field emergency treatment guideline](https://www.nfa.go.kr/nfa/publicrelations/legalinformation/archives/?cntId=50&mode=view)
- [Korean prehospital emergency-patient severity classification standard](https://www.law.go.kr/LSW/admRulInfoP.do?admRulSeq=2100000270800&chrClsCd=010201)
- [Enforcement Rule of the 119 Rescue and Emergency Medical Services Act, Article 18](https://www.law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsJoLnkSeq=1000995617)

These references guide requirements. They do not by themselves prove medical or legal compliance.
