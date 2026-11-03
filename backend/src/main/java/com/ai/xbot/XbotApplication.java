package com.ai.xbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class XbotApplication {
    public static void main(String[] args) {
        SpringApplication.run(XbotApplication.class, args);
    }
}
