package com.hansungteam.ersync.privacy.infrastructure;

import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import org.springframework.data.jpa.repository.JpaRepository;

/** 연락처 제공 동의 이력 영속성 접근점입니다. */
public interface ContactSharingConsentRepository extends JpaRepository<ContactSharingConsent, Long> {

    boolean existsByAccountPublicIdAndPolicyVersion(String accountId, String policyVersion);
}
