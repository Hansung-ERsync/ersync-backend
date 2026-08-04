package com.hansungteam.ersync.privacy.infrastructure;

import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 연락처 제공 동의 이력 영속성 접근점입니다. */
public interface ContactSharingConsentRepository extends JpaRepository<ContactSharingConsent, Long> {

    boolean existsByAccountPublicIdAndPolicyVersion(String accountId, String policyVersion);

    boolean existsByAccountPublicIdAndConsentTypeAndPolicyVersion(
            String accountId,
            ConsentType consentType,
            String policyVersion
    );

    List<ContactSharingConsent> findByAccountPublicIdOrderByConsentedAtAsc(String accountId);
}
