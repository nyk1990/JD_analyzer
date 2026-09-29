package com.nkharade.jdanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JdAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JdAnalyzerApplication.class, args);
    }
}
