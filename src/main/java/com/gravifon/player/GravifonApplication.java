package com.gravifon.player;

import com.gravifon.player.config.GravifonProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(GravifonProperties.class)
@EnableScheduling
public class GravifonApplication {
    public static void main(String[] args) {
        SpringApplication.run(GravifonApplication.class, args);
    }
}
