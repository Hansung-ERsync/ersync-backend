package com.hansungteam.ersync.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 정보를 체크하기 위한 컨트롤러입니다.
 */
@RequestMapping("/api/system")
@RestController
public class ServerStatusController {

    private final String commitSha;

    /**
     * 서버 상태 컨트롤러를 생성합니다.
     *
     * @param commitSha 빌드 시 주입된 Git 커밋 SHA
     */
    public ServerStatusController(
            @Value("${ersync.build.commit-sha}") String commitSha
    ) {
        this.commitSha = commitSha;
    }

    /**
     * 서버 상태를 확인합니다.
     *
     * @return "Alive"
     */
    @GetMapping("/health")
    public String getServerStatus() {
        return "Alive";
    }

    /**
     * 현재 실행 중인 애플리케이션의 Git 커밋 SHA를 확인합니다.
     *
     * @return 배포 버전 응답
     */
    @GetMapping("/version")
    public ServerVersionResponse getServerVersion() {
        return new ServerVersionResponse(commitSha);
    }
}
