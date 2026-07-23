package com.hansungteam.ersync.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 정보를 체크하기 위한 컨트롤러입니다.
 */
@RequestMapping("/api/system")
@RestController
public class ServerStatusController {

    /**
     * 서버 상태를 확인합니다.
     *
     * @return "Alive"
     */
    @GetMapping("/health")
    public String getServerStatus() {
        return "Alive";
    }
}
