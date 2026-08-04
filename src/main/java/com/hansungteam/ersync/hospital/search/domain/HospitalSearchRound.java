package com.hansungteam.ersync.hospital.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 검색 회차 안에서 평가한 반경과 실제 신규 전달 수를 기록합니다. */
@Entity
@Table(name = "hospital_search_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalSearchRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_attempt_id", nullable = false)
    private HospitalDispatchAttempt dispatchAttempt;

    @Column(name = "radius_km", nullable = false)
    private int radiusKm;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "new_offer_count", nullable = false)
    private int newOfferCount;

    @Column(name = "evaluated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant evaluatedAt;

    @Column(name = "response_deadline_at", columnDefinition = "datetime(6)")
    private Instant responseDeadlineAt;

    private HospitalSearchRound(
            HospitalDispatchAttempt dispatchAttempt,
            int radiusKm,
            int candidateCount,
            int newOfferCount,
            Instant evaluatedAt,
            Instant responseDeadlineAt
    ) {
        this.dispatchAttempt = dispatchAttempt;
        this.radiusKm = radiusKm;
        this.candidateCount = candidateCount;
        this.newOfferCount = newOfferCount;
        this.evaluatedAt = evaluatedAt;
        this.responseDeadlineAt = responseDeadlineAt;
    }

    public static HospitalSearchRound record(
            HospitalDispatchAttempt dispatchAttempt,
            int radiusKm,
            int candidateCount,
            int newOfferCount,
            Instant evaluatedAt,
            Instant responseDeadlineAt
    ) {
        return new HospitalSearchRound(
                dispatchAttempt,
                radiusKm,
                candidateCount,
                newOfferCount,
                evaluatedAt,
                responseDeadlineAt
        );
    }
}
