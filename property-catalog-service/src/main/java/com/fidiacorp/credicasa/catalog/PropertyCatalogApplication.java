package com.fidiacorp.credicasa.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MS-2 property-catalog-service (:8082, property_db): US07–US09, US13–US15. */
@SpringBootApplication
public class PropertyCatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropertyCatalogApplication.class, args);
    }
}
