package com.ebike.config;

import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class LocaleConfiguration {

    // The bean must be called "localeResolver": that is the name Spring MVC looks the resolver up by.
    @Bean
    public LocaleResolver localeResolver(UserRepository users, UserPreferenceRepository preferences) {
        return new UserLocaleResolver(users, preferences);
    }
}
