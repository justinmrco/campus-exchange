package com.campusexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class CampusExchangeApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(CampusExchangeApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(CampusExchangeApplication.class, args);
    }
}
