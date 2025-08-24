package com.imposter.mockimposter.config;

import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CookieConfig {

    @Bean
    public CookieSameSiteSupplier cookieSameSiteSupplier() {
        // En dev : SameSite=None + Secure=false (autoriser HTTP localhost)
        return CookieSameSiteSupplier.ofNone();
    }
}
