# ERSync Frontend Agent Context

- Audience: Flutter mobile and React web agents
- Status: MVP implementation contract
- Updated: 2026-07-17

## 1. Frontend Mission

Build three work-focused clients:

- a one-hand-friendly Flutter app for paramedics;
- a wide, high-signal React dashboard for an emergency department;
- a restrained React admin web for organizations and invitation codes.

Clinical input must be fast without hiding missing data. Use explicit states such as `확인 불가`, `측정 불가`, and `환자 거부` instead of empty fields.

## 2. Source of Truth

Read this file together with:

- `docs/ai/project-context.md`
- `docs/ai/backend-context.md`

If older notes conflict, apply these rules:

- hospital uses one shared ER account for MVP;
- paramedics use personal accounts;
- hospital recipients are selected automatically by the backend;
- multiple hospitals may accept;
- the paramedic selects one current destination;
- ETA is automatic and never a required input;
- situation-room functions are out of MVP;
- patient direct identifiers are never displayed.

## 3. Applications

### 3.1 Paramedic Mobile

- target: Flutter
- primary environment: outdoor, movement, gloves, poor network
- portrait-first layout
- one main action per screen
- large controls and numeric keypad
- encrypted offline draft and retry

### 3.2 Hospital Web

- target: React
- primary environment: wide desktop display
- dense but readable request queue
- keyboard and mouse support
- realtime updates without manual refresh
- prominent alert for withdrawal and handoff actions

### 3.3 Admin Web

- target: React
- organization and invitation-code operations only
- no route, component, or API call for patient data

## 4. Shared UI Rules

1. Never use color as the only status signal.
2. Every async command shows idle, submitting, success, and failure states.
3. Disable repeated submission while a command is in flight.
4. Retrying a command reuses the same idempotency key.
5. Realtime events trigger query invalidation or authoritative refetch.
6. Show all timestamps in the user's local timezone, with source time retained in state.
7. Label ETA as estimated and show `계산 불가` when unavailable.
8. Never render patient name, resident number, contact, exact birth date, or home address.
9. Do not silently discard an entered clinical value when the user navigates between steps.
10. Do not let stale local state override newer server state.

## 5. Visual Priority

This is an operational tool, not a marketing interface.

- use an unframed page layout;
- use cards only for individual requests and accepted hospitals;
- card radius is 8px or less;
- avoid decorative gradients and oversized headings;
- reserve red for immediate danger or destructive confirmation;
- keep labels, units, and values aligned for scanning;
- use the existing icon library or Lucide on React;
- provide tooltips for unfamiliar icon-only buttons;
- keep button dimensions stable during loading.

## 6. Admin Web

### 6.1 Routes

```text
/admin/login
/admin/organizations
/admin/organizations/new
/admin/organizations/:organizationId
/admin/invitation-codes
```

### 6.2 Organization Form

Fields:

- organization type: hospital or EMS unit
- organization name
- active state

The admin does not enter patient data or hospital operational capacity.

### 6.3 Invitation Code Flow

1. Open an organization.
2. Select target role.
3. Select 3 days, 7 days, or custom expiry.
4. Generate a one-time code.
5. Show plaintext exactly once in a copyable modal.
6. Warn that it cannot be displayed again.
7. Return to history after closing.

History columns:

- organization
- target role
- issue time
- expiry time
- status
- used time
- revoked time
- revoke action

Never show a code after the one-time modal closes.

## 7. Signup and Login

### 7.1 Common Signup Flow

1. Enter invitation code.
2. Validate the code.
3. Display the bound organization and role.
4. Enter account credentials.
5. Submit signup once.
6. Route to login or active session.

Errors must distinguish invalid, expired, used, and revoked codes without exposing code hashes.

### 7.2 Hospital Signup

Additional fields:

- ER address
- map search and marker confirmation
- latitude/longitude preview
- ER contact number

The address text alone is insufficient. The user must confirm the map position. Receiving state starts as `OFF`.

### 7.3 Paramedic Signup

The account is personal. Show the bound EMS organization. Do not allow changing organization during signup.

## 8. Paramedic Navigation

Suggested main routes:

```text
/login
/requests/active
/requests/new/patient
/requests/new/assessment
/requests/new/treatment
/requests/new/review
/requests/:requestId/waiting
/requests/:requestId/hospitals
/requests/:requestId/transport
/requests/:requestId/handoff
```

The client may implement steps as one stateful flow instead of literal routes. Back navigation must preserve input.

## 9. New Request Flow

The five user-visible phases are:

1. 환자·발생
2. 환자 평가
3. 현장 처치
4. 전송 전 확인
5. 이송 중 갱신

Keep a visible step indicator. Do not display usage tutorials inside the main content.

### 9.1 Protocol Loading

- load the active ERSync assessment protocol and supported versions from the backend;
- retain a bundled or securely cached version for poor-network use;
- pin the protocol version as `assessmentProtocolVersion` when a draft is created;
- keep that version through the request and in-transit updates;
- show the version on review/detail screens without making it a user-editable medical setting;
- if the server no longer supports a draft version, require review of changed fields instead of silently converting them;
- keep the official Pre-KTAS standard version separate from the ERSync form protocol version;
- do not calculate an automatic Pre-KTAS level unless an officially validated algorithm is later approved.

## 10. Step 1: Patient and Incident

### 10.1 Age

Use a segmented status control:

- 정확
- 추정
- 확인 불가

Show a numeric age field for exact or estimated. Hide and clear only the inactive value when the user explicitly confirms a status change.

Validation examples:

- exact + no number: `나이를 입력해 주세요.`
- estimated + no number: `추정 나이를 입력해 주세요.`
- unknown: valid without a number

### 10.2 Sex

Use large options:

- 남성
- 여성
- 확인 불가

### 10.3 Occurrence Type

Options:

- 질병
- 비질병
- 기타
- 확인 불가

When `비질병` is selected, require mechanism and injury site input. Injury sites allow multiple selection. `기타` requires a short detail only where the API contract provides it.

### 10.4 Symptoms

Require one primary symptom. Secondary symptoms are optional and multi-select.

Primary choices:

```text
의식 저하, 호흡곤란, 호흡정지, 흉통, 심정지, 뇌졸중 의심,
경련·실신, 외상, 출혈, 소화기 증상, 중독, 화상, 임신·분만,
행동 이상·자해, 발열·감염, 기타, 확인 불가
```

Do not permit the same value as both primary and secondary.

### 10.5 Onset Time

Use:

- 정확한 시각
- 추정 시각
- 확인 불가

Show date/time input for exact or estimated. Add conditionally relevant fields such as last-known-well, accident time, or arrest time. Clearly label an estimated timestamp.

## 11. Step 2: Patient Assessment

### 11.1 Pre-KTAS

Show levels 1 through 5 as large stable rows or buttons. Every level includes number and text; color is secondary.

Suggested visual mapping, pending official validation:

| Level | Visual label |
|---|---|
| 1 | 최우선 |
| 2 | 매우 긴급 |
| 3 | 긴급 |
| 4 | 준긴급 |
| 5 | 비긴급 |

Do not invent a sixth `평가 불가` level.

Store/show:

- selected level
- assessment time, editable from current time
- standard version supplied by server/config
- current authenticated assessor

### 11.2 Emergency Unfinished Send

Provide a secondary, visually serious action: `분류 미완료 상태로 긴급 전송`.

On activation:

1. open a confirmation sheet;
2. require one reason;
3. require detail for `기타`;
4. explain that Pre-KTAS must be added after transmission;
5. mark the active request with a persistent `분류 갱신 필요` banner.

Reasons:

- CPR 진행 중
- 현장 위험
- 평가 시간 부족
- 기타

This exception replaces only the initial Pre-KTAS requirement. It does not bypass other required patient fields.

### 11.3 AVPU

Use five large options:

- A 명료
- V 음성 반응
- P 통증 반응
- U 무반응
- 평가 불가

`평가 불가` requires `현장 위험`, `환자 접근 불가`, or `기타`. Show observation time.

### 11.4 Vital Signs

Display five rows with fixed dimensions:

- blood pressure
- pulse
- respiratory rate
- temperature
- SpO2

Each row has a mode control:

- 측정값
- 측정 불가
- 환자 거부

For `측정값`, show numeric input and fixed unit. Blood pressure has systolic/diastolic inputs.

For `측정 불가`, require:

- 환자 상태
- 현장 위험
- 손상 부위 영향
- 장비 오류
- 기타

The measured time applies to the vital set and defaults to now. Make it editable.

Never display a plain empty dash for a required vital. Render `측정 불가 · 장비 오류` or `환자 거부`.

### 11.5 Conditional Assessment Panels

Show these panels only when the selected symptom, incident, or protocol rule requires them:

- pupils
- glucose
- relevant history, allergies, and medications
- infection/isolation concern
- cardiac arrest details
- major trauma details
- cardiac/chest-pain details
- suspected stroke details
- pregnancy/delivery details
- preferred hospital and prior-notice status

An opened required field must have a value or `확인 불가`. Do not make every conditional panel visible at once.

Preferred hospital is informational. It does not replace backend hospital search and does not preselect the destination.

## 12. Step 3: Treatment

Treatment choices:

```text
처치 없음, 산소 투여, 기도 확보, CPR, 제세동·AED, 정맥로·수액,
약물 투여, 출혈·상처 처치, 고정, 심전도, 보온·냉각,
분만 처치, 기타
```

Rules:

- require at least one choice;
- `처치 없음` is mutually exclusive;
- selecting a treatment opens its structured detail panel;
- keep each treatment attempt as a separate draft event;
- a failed attempt is valid and must not be removed automatically;
- `기타` requires a short detail;
- performed time defaults to now and is editable.

Examples:

- oxygen panel: method, flow rate, start time
- medication panel: name, dose, route, administration time
- airway panel: device/method, time, success
- bleeding panel: site, method, tourniquet, time

Use repeatable rows for multiple medication or procedure events. Do not place a card inside another card.

## 13. Step 4: Review and Send

The review screen is a compact clinical summary, not a raw form dump.

Sections:

- patient and incident
- symptoms and onset
- Pre-KTAS or emergency exception
- AVPU
- all five vital signs
- treatment summary
- relevant conditional assessment summary
- assessment protocol version
- current GPS status
- manual current-location confirmation when GPS is unavailable
- map/ETA provider status if available

Every validation error links to the relevant step and field.

Enable `병원 요청 전송` only when all server-required clinical states and an origin coordinate are present. Prefer GPS. If GPS is unavailable, require the paramedic to confirm a map pin. ETA is never required. Hospitals are not selected on this screen.

On send:

1. persist the latest encrypted local draft;
2. submit with an idempotency key;
3. keep the button disabled while pending;
4. on success, remove or mark the draft submitted;
5. route to the waiting screen;
6. on timeout, show `전송 여부 확인 중` and query by idempotency key before retry.

## 14. Offline Draft and Retry

- autosave after meaningful input changes;
- encrypt drafts at rest;
- never write clinical values to analytics or crash breadcrumbs;
- show `기기에 저장됨`, `전송 중`, `전송 실패`, `전송 완료` distinctly;
- preserve the idempotency key across retries;
- remove local clinical data after confirmed submission according to retention policy;
- provide explicit discard with confirmation.

Example:

1. Network fails after the user presses send.
2. The app keeps the draft and idempotency key.
3. It asks the server whether that key already created a request.
4. If found, it opens that request.
5. Otherwise, it retries the same payload once connectivity returns.

## 15. Waiting and Search Screen

Show:

- search policy: 10km start, at least 3 candidates, +10km expansion, 60-second interval, 100km maximum
- current search status
- current radius
- contacted hospital count
- pending, accepted, and rejected counts
- time to next expansion when still eligible
- first acceptance state
- retry/reconnect state

Do not imply that the paramedic must remain idle. The patient update actions become available after request creation.

When 100km is reached with fewer than three candidates, show a clear warning and existing hospital responses. Do not fabricate candidates.

## 16. Hospital Response List

Separate sections:

- accepted hospitals
- pending hospitals
- rejected hospitals

Accepted hospital card:

- hospital name
- distance from latest location
- automatic ETA or `계산 불가`
- acceptance time
- ER contact
- map action
- `이 병원으로 이동` command

Rejected hospital row:

- hospital name
- rejection reason
- other detail when present
- response time

Current destination card has a persistent `이동 중` status. Do not remove other accepted cards.

## 17. Destination Change

Changing destination requires confirmation that names both hospitals.

Example copy:

```text
현재 목적지를 A병원에서 B병원으로 변경합니다.
A병원에는 목적지 변경 알림이 전달됩니다.
```

After success:

- mark B as current destination;
- keep A in accepted/history state;
- stop rendering exact location for A's hospital session through server authorization;
- refetch request and offer state.

## 18. In-Transit Screen

The transport screen prioritizes current destination and quick clinical updates.

Top area:

- current destination hospital
- map/location status
- distance and ETA
- ER call action
- destination-change action

Persistent quick actions:

- 활력징후 추가
- 의식 상태 변경
- Pre-KTAS 재평가
- 처치 추가
- 상황별 추가 평가

Below the actions, show a reverse chronological clinical timeline. Each item includes clinical time and server sync state. Never edit a submitted item in place.

### 18.1 Vital Update

Reuse the same five-row control as initial input. Prefill modes and values from the latest set only as a convenience. Submission creates a complete new set.

### 18.2 Consciousness Update

Show latest AVPU and time. A new selection creates another assessment.

### 18.3 Pre-KTAS Update

Show previous level and time. Submit a new assessment. If the request began with emergency exception, keep the `분류 갱신 필요` banner until a completed level is accepted by the server.

### 18.4 Treatment Update

Create a new treatment event. Do not modify an earlier attempt.

### 18.5 Supplemental Assessment Update

Open only relevant typed fields. Submission appends a new record and refreshes the hospital-facing summary when clinically relevant.

### 18.6 Correction

If correction is supported, open a dedicated action from the timeline. Require a reason. Send `supersedesRecordId`; render both records with a `정정됨` relation.

## 19. Location and ETA UX

- request location permission in context;
- explain only the immediate operational need;
- allow a paramedic-confirmed current-location map pin when GPS acquisition fails;
- while en route, send around every 10 seconds;
- indicate GPS disabled, permission denied, stale, or current;
- never draw or persist a full route history;
- show the exact current ambulance marker only in the current destination hospital UI;
- label ETA with calculated time;
- use `계산 불가` when the provider fails;
- do not block request, acceptance, destination, or completion actions due to ETA failure.

## 20. Acceptance Withdrawal Alert

If a hospital withdraws acceptance:

- interrupt the current mobile view with a high-priority sheet/dialog;
- show hospital name and mandatory reason;
- require acknowledgement;
- clear current destination display if applicable;
- show re-search progress;
- preserve the patient's active request and clinical timeline.

Do not tell the user to create a new patient request.

## 21. Handoff Completion UX

Paramedic flow:

1. press `인계 완료 요청`;
2. confirm the destination hospital;
3. show `병원 확인 대기 중`;
4. prevent duplicate active commands;
5. allow state refresh after reconnect.

The mobile app does not mark completion before hospital confirmation.

## 22. Hospital Dashboard

Suggested routes:

```text
/hospital/login
/hospital/requests
/hospital/requests/:requestId
/hospital/history
/hospital/settings
```

Primary layout:

- top bar: hospital name, connection state, receiving ON/OFF
- main queue: wide request cards, one card per row where practical
- secondary filters: new, accepted, en route, handoff requested
- detail drawer or page: full allowed clinical summary and timeline

Sort active cards by:

1. Pre-KTAS urgency;
2. emergency-unfinished warning;
3. request elapsed time;
4. server arrival time.

Do not let animation reorder a card while a user is interacting with its controls. Defer visual movement until the action completes or provide a stable selected detail view.

## 23. Hospital Request Card

Minimum card content:

- Pre-KTAS number, label, and color
- emergency-unfinished badge when applicable
- exact/estimated/unknown age and sex
- primary symptom
- AVPU
- BP, pulse, RR, temperature, SpO2
- current treatment summary
- distance and ETA if authorized/available
- request arrival and elapsed time
- last clinical update time
- accept and reject commands

Example:

```text
Pre-KTAS 2 | 추정 70세 남성 | 호흡곤란
AVPU V | BP 90/60 | HR 118 | RR 30 | SpO2 88% | T 38.1°C
산소 마스크 10 L/min 투여 중
ETA 12분 · 요청 2분 경과 · 30초 전 갱신
```

Render explicit states, for example `BP 측정 불가 · 환자 상태`.

## 24. Hospital Detail and Timeline

The detail view may show:

- all hospital-facing initial fields;
- current destination status;
- latest exact location only when this hospital is current destination;
- chronological Pre-KTAS, AVPU, vital, and treatment updates;
- response and destination events;
- handoff state.

It must not show direct patient identifiers or unrelated free text.

New clinical events should:

- update the summary;
- add a timestamped timeline item;
- show a concise visual and optional sound alert;
- not erase the earlier value.

Pending and accepted hospitals may receive the current hospital-facing clinical summary. Rejected, withdrawn, and closed offers receive no later clinical update. Only the current destination receives exact location.

## 25. Hospital Decision Modals

### 25.1 Reject Modal

Require exactly one reason:

- 응급실 일반 병상 부족
- 격리 병상 부족
- 수술실 부족
- 중환자실 부족
- 전문의 부재
- 장비 사용 불가
- 기타

`기타` requires detail. Keep rejection and acceptance buttons unavailable while a response command is pending.

### 25.2 Acceptance Withdrawal Modal

Only available after acceptance. Require:

- 병상 부족
- 수술실 부족
- 전문의 부재
- 장비 사용 불가
- 기타

`기타` requires detail. The confirmation text must explain that the paramedic will receive an urgent alert and the hospital cannot receive another offer for this request.

The two reason lists are intentionally different. Do not merge their frontend enums.

## 26. Hospital Location View

Only the current destination hospital sees:

- ambulance current marker;
- latest update time;
- distance;
- automatic ETA;
- stale or disconnected state.

Use a map API component. A non-destination accepted hospital sees clinical updates needed for its acceptance decision but not exact current location.

Do not render route breadcrumbs or historical polylines.

## 27. Hospital Handoff

When the paramedic requests handoff completion:

- show a persistent alert on the current destination request;
- enable `환자 인계 확인` only for that request;
- require a simple confirmation dialog;
- after success, remove it from active queues and route to history or the next request;
- retain final server timestamp in history.

The shared account is the recorded actor. Do not ask for responder personal name in MVP.

## 28. Receiving ON/OFF

Use a labeled switch with confirmation when turning OFF.

- ON: eligible for new requests
- OFF: excluded from new requests
- existing accepted/en-route requests remain visible
- OFF does not withdraw acceptance

Display a persistent page-level OFF state. Do not hide active work.

## 29. Client State Draft

```ts
type ExplicitValueState =
  | { state: 'VALUE'; value: number }
  | { state: 'MEASUREMENT_UNAVAILABLE'; reason: MeasurementUnavailableReason; detail?: string }
  | { state: 'PATIENT_REFUSED' };

type TimedClinicalRecord = {
  id: string;
  clinicalAt: string;
  enteredAt: string;
  serverReceivedAt: string;
  supersedesRecordId?: string;
};

type EtaView = {
  status: 'AVAILABLE' | 'UNAVAILABLE' | 'STALE';
  durationSeconds?: number;
  distanceMeters?: number;
  calculatedAt?: string;
};
```

Generate concrete types from the backend OpenAPI contract when available. Do not maintain divergent handwritten enums across Flutter, React, and backend.

## 30. Realtime Events

Handle at least:

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

Event payloads are not the sole source of truth. Use sequence/version information and refetch after reconnect, gaps, or authorization changes.

## 31. Error Copy

Use specific, actionable Korean messages.

Examples:

```text
혈압 값을 입력하거나 측정 상태를 선택해 주세요.
측정 불가 사유를 선택해 주세요.
기타 사유를 입력해 주세요.
요청 전송 여부를 확인하고 있습니다.
ETA를 계산할 수 없습니다. 병원 요청은 정상적으로 진행됩니다.
A병원이 수락을 철회했습니다. 최신 위치에서 병원을 다시 찾고 있습니다.
병원의 인계 확인을 기다리고 있습니다.
```

Never claim success based only on a local timeout or realtime event.

## 32. Accessibility and Field Usability

- support system text scaling without clipping;
- minimum practical touch target around 48x48 logical pixels;
- high contrast for critical values;
- visible keyboard focus on web;
- semantic labels for icons and controls;
- vibration/sound only as supplements;
- do not rely on hover for required information;
- keep numeric units visible;
- prevent the mobile keyboard from covering the active field or submit action;
- test in bright light and narrow mobile width;
- provide clear destructive confirmation for draft discard and acceptance withdrawal.

## 33. Privacy Rules

- no patient data in URLs, analytics, console logs, or crash metadata;
- redact API error bodies before telemetry;
- clear hospital patient state at logout and authorization loss;
- encrypt mobile drafts;
- hide app content in OS recent-app snapshots where supported;
- do not cache hospital clinical pages in a shared browser cache;
- current location is rendered only after server authorization as destination hospital.

## 34. Frontend Test Requirements

### Paramedic

- all required fields with explicit unavailable/refused states
- conditional mechanism, injury, time, and treatment fields
- emergency-unfinished Pre-KTAS flow and banner
- one-hand layout at target mobile sizes
- draft restore and idempotent retry
- append-only update timeline
- destination selection and change
- withdrawal alert and re-search state
- ETA unavailable without blocking
- handoff request waiting state

### Hospital

- Pre-KTAS sorting and stable interaction
- wide card rendering for longest Korean labels
- detailed rejection versus withdrawal reasons
- realtime clinical update and reconnect catch-up
- exact location visible only as current destination
- shared-account handoff confirmation
- OFF state while active cases remain

### Admin

- one-time code reveal
- revoke and history states
- no patient routes or API calls

### Cross-Cutting

- color-independent statuses
- keyboard navigation
- text scaling and overflow
- duplicate command prevention
- unauthorized/expired session handling
- no sensitive telemetry

## 35. Done Criteria

- all three role flows follow backend authorization;
- the full five-phase patient workflow is usable;
- no required clinical value can remain silently blank;
- initial and in-transit records preserve clinical and server times;
- hospital cards show the agreed minimum summary;
- accepted hospital cards remain visible while one destination is selected;
- live location is destination-only;
- ETA failure is non-blocking;
- both handoff actions are represented correctly;
- offline, reconnect, concurrency, accessibility, and privacy tests pass.

## 36. Out of MVP

- situation-room request UI
- hospital staff personal profiles
- hospital resource database dashboard
- manual ETA input
- manual initial hospital selection
- patient identifiers
- GPS route playback
- automatic final hospital choice
