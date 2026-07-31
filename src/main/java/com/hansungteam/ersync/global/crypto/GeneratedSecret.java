package com.hansungteam.ersync.global.crypto;

/** 응답에 한 번 반환할 원문과 저장용 다이제스트 묶음입니다. */
public record GeneratedSecret(String plainText, byte[] digest) {

    public GeneratedSecret {
        digest = digest.clone();
    }

    @Override
    public byte[] digest() {
        return digest.clone();
    }
}
