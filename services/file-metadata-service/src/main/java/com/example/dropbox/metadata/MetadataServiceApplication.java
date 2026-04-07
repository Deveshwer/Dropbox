package com.example.dropbox.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCaching
public class MetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataServiceApplication.class, args);
    }
}
