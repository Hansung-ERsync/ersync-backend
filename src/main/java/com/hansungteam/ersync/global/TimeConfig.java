package com.hansungteam.ersync.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 서버 기준 시각을 주입 가능하게 제공해 상태 전이와 테스트의 시각 기준을 고정합니다.
 */
@Configuration
public class TimeConfig {

    /**
     * 운영 코드에서 사용할 UTC 시스템 시계입니다.
     *
     * @return UTC 기준 Clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
