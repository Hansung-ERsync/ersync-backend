package com.hansungteam.ersync.system;

/**
 * 실행 중인 애플리케이션의 배포 버전을 나타냅니다.
 *
 * @param commitSha 빌드 시 주입된 Git 커밋 SHA
 */
public record ServerVersionResponse(String commitSha) {
}
