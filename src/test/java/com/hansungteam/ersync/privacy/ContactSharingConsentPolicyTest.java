package com.hansungteam.ersync.privacy;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactSharingConsentPolicyTest {

    private final ContactSharingConsentPolicy policy = new ContactSharingConsentPolicy(
            "CONTACT_SHARING_DEV_1.0",
            "COLLECTION_USE_DEV_1.0",
            "HOSPITAL_PROVISION_DEV_1.0"
    );

    @Test
    void acceptsBothCurrentParamedicConsentVersions() {
        var result = policy.requireParamedicAccepted(
                true,
                "COLLECTION_USE_DEV_1.0",
                true,
                "HOSPITAL_PROVISION_DEV_1.0"
        );

        assertThat(result.collectionUse()).isEqualTo("COLLECTION_USE_DEV_1.0");
        assertThat(result.hospitalProvision()).isEqualTo("HOSPITAL_PROVISION_DEV_1.0");
    }

    @Test
    void rejectsAnyMissingOrMismatchedParamedicConsent() {
        assertThatThrownBy(() -> policy.requireParamedicAccepted(
                false,
                "COLLECTION_USE_DEV_1.0",
                true,
                "HOSPITAL_PROVISION_DEV_1.0"
        )).isInstanceOf(CustomException.class);

        assertThatThrownBy(() -> policy.requireParamedicAccepted(
                true,
                "COLLECTION_USE_DEV_0.9",
                true,
                "HOSPITAL_PROVISION_DEV_1.0"
        )).isInstanceOf(CustomException.class);

        assertThatThrownBy(() -> policy.requireParamedicAccepted(
                true,
                "COLLECTION_USE_DEV_1.0",
                true,
                "HOSPITAL_PROVISION_DEV_0.9"
        )).isInstanceOf(CustomException.class);
    }
}
