package br.edu.ucsal.mteste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MsTesteApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsTesteApplication.class, args);
    }
}
