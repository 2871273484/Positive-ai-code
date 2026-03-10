package com.xr.positiveaicode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xr.positiveaicode.mapper")
public class PositiveAiCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositiveAiCodeApplication.class, args);
    }

}
