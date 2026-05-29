package br.edu.ucsal.mteste.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teste")
public class TesteController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "ms-teste",
            "status", "UP",
            "message", "Microservico de teste funcionando com Eureka e Gateway",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
