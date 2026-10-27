package br.com.shema.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShemacrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShemacrmApplication.class, args);
    }
}
