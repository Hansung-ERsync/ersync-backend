package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationCodeGeneratorTest {

    private final SecretDigester secretDigester = new SecretDigester();
    private final InvitationCodeRepository invitationCodeRepository = mock(InvitationCodeRepository.class);

    @Test
    void sixRandomBytesProduceEightBase64UrlCharactersAndMatchingDigest() {
        when(invitationCodeRepository.existsByCodeDigest(any(byte[].class))).thenReturn(false);
        var generator = generatorWith(new byte[] {0, 0, 0, 0, 0, 0});

        var generated = generator.generateUnique();

        assertThat(generated.plainText()).isEqualTo("AAAAAAAA");
        assertThat(generated.plainText()).matches("[A-Za-z0-9_-]{8}");
        assertThat(generated.digest()).isEqualTo(secretDigester.digest(generated.plainText()));
    }

    @Test
    void existingDigestIsSkippedAndNextCandidateIsReturned() {
        when(invitationCodeRepository.existsByCodeDigest(any(byte[].class))).thenReturn(true, false);
        var generator = generatorWith(
                new byte[] {0, 0, 0, 0, 0, 0},
                new byte[] {1, 1, 1, 1, 1, 1}
        );

        var generated = generator.generateUnique();

        assertThat(generated.plainText()).isEqualTo("AQEBAQEB");
        verify(invitationCodeRepository, times(2)).existsByCodeDigest(any(byte[].class));
    }

    @Test
    void repeatedCollisionsFailAfterBoundedAttempts() {
        when(invitationCodeRepository.existsByCodeDigest(any(byte[].class))).thenReturn(true);
        byte[][] collisions = new byte[InvitationCodeGenerator.MAX_GENERATION_ATTEMPTS][];
        Arrays.setAll(collisions, ignored -> new byte[] {0, 0, 0, 0, 0, 0});
        var generator = generatorWith(collisions);

        assertThatThrownBy(generator::generateUnique)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to generate a unique invitation code");
        verify(invitationCodeRepository, times(InvitationCodeGenerator.MAX_GENERATION_ATTEMPTS))
                .existsByCodeDigest(any(byte[].class));
    }

    private InvitationCodeGenerator generatorWith(byte[]... values) {
        return new InvitationCodeGenerator(
                secretDigester,
                invitationCodeRepository,
                new SequenceSecureRandom(values)
        );
    }

    private static final class SequenceSecureRandom extends SecureRandom {

        private final ArrayDeque<byte[]> values;

        private SequenceSecureRandom(byte[]... values) {
            this.values = new ArrayDeque<>(Arrays.asList(values));
        }

        @Override
        public void nextBytes(byte[] bytes) {
            byte[] next = values.removeFirst();
            if (next.length != bytes.length) {
                throw new IllegalArgumentException("Unexpected deterministic random byte length");
            }
            System.arraycopy(next, 0, bytes, 0, bytes.length);
        }
    }
}
