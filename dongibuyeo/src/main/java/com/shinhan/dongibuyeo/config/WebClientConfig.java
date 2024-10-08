package com.shinhan.dongibuyeo.config;

import com.shinhan.dongibuyeo.global.client.GlobalErrorFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private final String BASE_URL = "https://finopenapi.ssafy.io/ssafy/api/v1";

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .filter(new GlobalErrorFilter())
                .baseUrl(BASE_URL);
    }
}
