package com.ses.bldizi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BlDiziApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlDiziApplication.class, args);
    }
}
