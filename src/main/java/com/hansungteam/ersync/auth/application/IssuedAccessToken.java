package com.hansungteam.ersync.auth.application;

import java.time.Instant;

/** 서명된 Access Token 원문과 만료 시각입니다. */
public record IssuedAccessToken(String value, Instant expiresAt) {
}
