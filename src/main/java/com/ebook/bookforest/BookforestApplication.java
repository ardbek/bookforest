package com.ebook.bookforest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookforestApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookforestApplication.class, args);
    }

}
