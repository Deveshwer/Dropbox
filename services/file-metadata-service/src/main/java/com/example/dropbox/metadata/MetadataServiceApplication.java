package com.example.dropbox.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.dropbox.metadata.files.S3StorageProperties;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(S3StorageProperties.class)
public class MetadataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataServiceApplication.class, args);
    }
}
