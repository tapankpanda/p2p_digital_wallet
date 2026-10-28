package com.tapan.p2pdigitalwallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI walletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Wallet & P2P Payment API")
                        .version("1.0.0")
                        .description("""
                                RESTful API for digital wallet management
                                and peer-to-peer payments.

                                Features include wallet creation, balance retrieval,
                                paginated transaction history, and wallet lifecycle
                                management. Transfers use transactional processing,
                                idempotency handling, and pessimistic locking to help
                                maintain consistency during concurrent operations.

                                Request validation and centralized exception handling
                                provide consistent API error responses.
                                """));
    }
}
