package com.hansungteam.ersync.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** DB 기반 지연 작업과 outbox 발행 scheduler를 활성화합니다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
