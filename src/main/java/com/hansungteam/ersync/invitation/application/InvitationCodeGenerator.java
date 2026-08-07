package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.global.crypto.GeneratedSecret;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** 사람이 전달하기 쉬운 8자리 가입 코드와 저장용 다이제스트를 생성합니다. */
@Component
public class InvitationCodeGenerator {

    static final int RANDOM_BYTES = 6;
    static final int CODE_LENGTH = 8;
    static final int MAX_GENERATION_ATTEMPTS = 10;

    private final SecretDigester secretDigester;
    private final InvitationCodeRepository invitationCodeRepository;
    private final SecureRandom secureRandom;

    @Autowired
    public InvitationCodeGenerator(
            SecretDigester secretDigester,
            InvitationCodeRepository invitationCodeRepository
    ) {
        this(secretDigester, invitationCodeRepository, new SecureRandom());
    }

    InvitationCodeGenerator(
            SecretDigester secretDigester,
            InvitationCodeRepository invitationCodeRepository,
            SecureRandom secureRandom
    ) {
        this.secretDigester = secretDigester;
        this.invitationCodeRepository = invitationCodeRepository;
        this.secureRandom = secureRandom;
    }

    /** 과거 발급분을 포함해 저장된 원문과 겹치지 않는 새 가입 코드를 만듭니다. */
    public GeneratedSecret generateUnique() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            byte[] randomBytes = new byte[RANDOM_BYTES];
            secureRandom.nextBytes(randomBytes);
            String plainText = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            if (plainText.length() != CODE_LENGTH) {
                throw new IllegalStateException("Unexpected invitation code length");
            }

            byte[] digest = secretDigester.digest(plainText);
            if (!invitationCodeRepository.existsByCodeDigest(digest)) {
                return new GeneratedSecret(plainText, digest);
            }
        }
        throw new IllegalStateException("Unable to generate a unique invitation code");
    }
}
