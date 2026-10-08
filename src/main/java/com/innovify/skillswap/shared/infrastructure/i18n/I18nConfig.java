package com.innovify.skillswap.shared.infrastructure.i18n;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class I18nConfig {

    // The bean must be named "localeResolver" for the DispatcherServlet to pick it up.
    @Bean
    public LocaleResolver localeResolver() {
        return new LatinAmericanSpanishLocaleResolver();
    }
}
