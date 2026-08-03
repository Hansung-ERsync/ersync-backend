package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Comparator;

/** 민감한 요청 원문을 보관하지 않고 순서에 안정적인 SHA-256 지문을 계산합니다. */
@Component
public class TransportRequestFingerprint {

    public byte[] digest(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(canonical(request).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available", ex);
        }
    }

    private String canonical(Object value) {
        if (value == null) {
            return "N";
        }
        if (value instanceof String string) {
            String normalized = string.trim();
            return "S" + normalized.length() + ":" + normalized;
        }
        if (value instanceof BigDecimal decimal) {
            return "D" + decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Enum<?> enumeration) {
            return "E" + enumeration.getDeclaringClass().getName() + ":" + enumeration.name();
        }
        if (value instanceof TemporalAccessor temporal) {
            return "T" + temporal;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return "P" + value;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(this::canonical)
                    .sorted(Comparator.naturalOrder())
                    .reduce("C[", (left, right) -> left + right + ";") + "]";
        }
        if (value.getClass().isRecord()) {
            StringBuilder result = new StringBuilder("R{");
            RecordComponent[] components = value.getClass().getRecordComponents();
            java.util.Arrays.sort(components, Comparator.comparing(RecordComponent::getName));
            for (RecordComponent component : components) {
                result.append(component.getName()).append('=').append(canonical(invoke(component, value))).append(';');
            }
            return result.append('}').toString();
        }
        throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
    }

    private Object invoke(RecordComponent component, Object target) {
        try {
            return component.getAccessor().invoke(target);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Unable to canonicalize request", ex);
        }
    }
}
