package com.slotline;

import com.slotline.config.SlotlineProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SlotlineProperties.class)
public class SlotlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlotlineApplication.class, args);
    }
}
