package com.hansungteam.ersync.privacy.application;

/** 검증을 통과한 구급대원 연락처 동의 문구 버전입니다. */
public record ParamedicConsentVersions(
        String collectionUse,
        String hospitalProvision
) {
}
