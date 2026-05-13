package com.interview.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.interview.merchant", "com.interview.common"})
@EnableJpaRepositories(basePackages = {"com.interview.merchant", "com.interview.common"})
@EntityScan(basePackages = {"com.interview.merchant", "com.interview.common"})
@EnableScheduling
public class MerchantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
