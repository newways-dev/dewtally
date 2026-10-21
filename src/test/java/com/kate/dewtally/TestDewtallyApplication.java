package com.kate.dewtally;

import org.springframework.boot.SpringApplication;

public class TestDewtallyApplication {

    public static void main(String[] args) {
        SpringApplication.from(DewtallyApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
