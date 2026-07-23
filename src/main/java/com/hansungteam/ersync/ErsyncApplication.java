package com.hansungteam.ersync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ERSync 백엔드 애플리케이션의 실행 진입점입니다.
 */
@SpringBootApplication
public class ErsyncApplication {

    /**
     * Spring Boot 애플리케이션을 시작합니다.
     *
     * @param args 실행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(ErsyncApplication.class, args);
    }

}
