package com.ebike.config;

import com.ebike.config.order.VnPayProperties;
import com.ebike.config.chatbot.GeminiChatProperties;
import com.ebike.config.chatbot.PdfKnowledgeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties({S3StorageProperties.class, VnPayProperties.class, GeminiChatProperties.class, PdfKnowledgeProperties.class})
public class ApplicationConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Keeps open-in-view for normal pages, but declaring it here replaces Spring Boot's own registration so the
     * notification stream can be left out. That stream stays open for up to 30 minutes; with open-in-view it held a
     * database connection the whole time, and about ten open customer pages used up the pool and hung the app.
     */
    @Bean
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer(OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(openEntityManagerInViewInterceptor)
                    .excludePathPatterns("/api/v1/notifications/stream");
            }
        };
    }
}
