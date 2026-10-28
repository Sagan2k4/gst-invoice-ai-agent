package com.gstagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
@ConfigurationPropertiesScan
public class GstInvoiceAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(GstInvoiceAgentApplication.class, args);
    }
}
