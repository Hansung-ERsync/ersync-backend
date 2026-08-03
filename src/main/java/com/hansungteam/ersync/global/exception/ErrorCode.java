package com.hansungteam.ersync.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 실패 응답에서 프론트가 분기할 수 있는 표준 오류 코드입니다.
 *
 * 예: 수락하지 않은 병원을 목적지로 선택한 경우
 * {@code TRANSPORT_DESTINATION_NOT_ACCEPTED}를 던지면 응답에는 {@code TRANSPORT_002}가 내려갑니다.
 */
@Getter
public enum ErrorCode {

    /*
     * 공통 오류
     */
    COMMON_REQUEST_VALIDATION_FAILED("COMMON_001", "요청값 검증에 실패했습니다.", HttpStatus.BAD_REQUEST),
    COMMON_HTTP_METHOD_NOT_ALLOWED("COMMON_002", "허용되지 않은 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    COMMON_INTERNAL_SERVER_ERROR("COMMON_003", "서버 내부 오류입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    COMMON_ACCESS_DENIED("COMMON_004", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    COMMON_DUPLICATE_CONFLICT("COMMON_005", "중복된 데이터가 존재합니다.", HttpStatus.CONFLICT),
    COMMON_RESOURCE_NOT_FOUND("COMMON_006", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    /*
     * 인증/인가 오류
     */
    AUTH_AUTHENTICATION_REQUIRED("AUTH_001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_TOKEN_INVALID("AUTH_002", "유효하지 않은 Access 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_ROLE_REQUIRED("AUTH_003", "해당 역할만 사용할 수 있는 기능입니다.", HttpStatus.FORBIDDEN),
    AUTH_CREDENTIALS_INVALID("AUTH_004", "로그인 정보를 확인할 수 없습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_005", "유효하지 않은 Refresh 토큰입니다.", HttpStatus.UNAUTHORIZED),

    /*
     * 사용자 오류
     */
    USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_INACTIVE("USER_002", "비활성화된 사용자입니다.", HttpStatus.FORBIDDEN),
    USER_LOGIN_ID_DUPLICATE("USER_003", "이미 사용 중인 로그인 ID입니다.", HttpStatus.CONFLICT),
    USER_HOSPITAL_ACCOUNT_ALREADY_EXISTS("USER_004", "이미 병원 공용 계정이 존재합니다.", HttpStatus.CONFLICT),
    USER_CONTACT_OR_CONSENT_REQUIRED("USER_005", "회신 연락처와 연락처 제공 동의가 필요합니다.", HttpStatus.CONFLICT),

    /*
     * 조직 오류
     */
    ORGANIZATION_NOT_FOUND("ORGANIZATION_001", "조직을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    /*
     * 가입 코드 오류
     */
    INVITATION_CODE_INVALID("INVITATION_001", "가입 코드를 확인할 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVITATION_CODE_EXPIRED("INVITATION_002", "만료된 가입 코드입니다.", HttpStatus.CONFLICT),
    INVITATION_CODE_USED("INVITATION_003", "이미 사용된 가입 코드입니다.", HttpStatus.CONFLICT),
    INVITATION_CODE_REVOKED("INVITATION_004", "폐기된 가입 코드입니다.", HttpStatus.CONFLICT),
    INVITATION_STATUS_CANNOT_CHANGE("INVITATION_005", "변경할 수 없는 가입 코드 상태입니다.", HttpStatus.CONFLICT),

    /*
     * 병원 오류
     */
    HOSPITAL_NOT_FOUND("HOSPITAL_001", "병원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    HOSPITAL_UNAVAILABLE("HOSPITAL_002", "현재 요청을 받을 수 없는 병원입니다.", HttpStatus.CONFLICT),
    EMERGENCY_DEPARTMENT_UNAVAILABLE("HOSPITAL_003", "응급실 수용 상태를 확인할 수 없습니다.", HttpStatus.CONFLICT),
    HOSPITAL_CAPACITY_UNAVAILABLE("HOSPITAL_004", "현재 수용 가능한 병상 또는 장비가 없습니다.", HttpStatus.CONFLICT),

    /*
     * 응급 이송 요청 오류
     */
    TRANSPORT_REQUEST_NOT_FOUND("TRANSPORT_001", "이송 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    TRANSPORT_DESTINATION_NOT_ACCEPTED(
            "TRANSPORT_002",
            "수락하지 않은 병원은 목적지로 선택할 수 없습니다.",
            HttpStatus.CONFLICT
    ),
    TRANSPORT_REQUEST_EXPIRED("TRANSPORT_003", "만료된 이송 요청입니다.", HttpStatus.CONFLICT),
    TRANSPORT_STATUS_CANNOT_CHANGE("TRANSPORT_004", "변경할 수 없는 이송 요청 상태입니다.", HttpStatus.CONFLICT),
    HOSPITAL_OFFER_NOT_FOUND("TRANSPORT_005", "병원 수신 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    HOSPITAL_OFFER_ALREADY_DECIDED("TRANSPORT_006", "이미 처리된 병원 수신 요청입니다.", HttpStatus.CONFLICT),

    /*
     * 프로토콜 오류
     */
    PROTOCOL_NOT_FOUND("PROTOCOL_001", "프로토콜을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PROTOCOL_VERSION_INACTIVE("PROTOCOL_002", "비활성화된 프로토콜 버전입니다.", HttpStatus.CONFLICT),
    PROTOCOL_ASSESSMENT_FAILED("PROTOCOL_003", "프로토콜 평가에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

}
