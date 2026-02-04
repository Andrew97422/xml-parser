package ru.andrew.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("XML Parser API")
                        .version("1.0")
                        .description("API для парсинга XML и работы с PostgreSQL базой данных")
                        .contact(new Contact()
                                .name("XML Parser")
                                .email("nosoff.4ndr@yandex.ru")));
    }
}
