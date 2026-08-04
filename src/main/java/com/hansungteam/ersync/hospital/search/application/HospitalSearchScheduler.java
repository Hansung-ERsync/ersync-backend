package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** DB에 저장된 실행 시각을 읽어 서버 재시작 뒤에도 병원 탐색을 계속합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ersync.hospital-search.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HospitalSearchScheduler {

    private static final int BATCH_SIZE = 50;

    private final HospitalDispatchAttemptRepository attemptRepository;
    private final HospitalSearchService hospitalSearchService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ersync.hospital-search.scheduler-fixed-delay:PT1S}")
    public void processDueAttempts() {
        List<Long> dueAttemptIds = attemptRepository.findDueIds(
                HospitalDispatchAttemptStatus.SEARCHING,
                clock.instant(),
                PageRequest.of(0, BATCH_SIZE)
        );
        for (Long attemptId : dueAttemptIds) {
            hospitalSearchService.processDueAttempt(attemptId);
        }
    }
}
