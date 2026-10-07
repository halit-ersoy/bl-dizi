package com.ses.bldizi.config;

import com.ses.bldizi.repository.PersonRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PersonRepository personRepository;

    public WebSocketConfig(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // Register endpoint with SockJS fallback options
        registry.addEndpoint("/ws")
                .addInterceptors(new WebSocketAuthInterceptor(personRepository))
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Register direct endpoint for standard WebSocket clients
        registry.addEndpoint("/ws")
                .addInterceptors(new WebSocketAuthInterceptor(personRepository))
                .setAllowedOriginPatterns("*");
    }
}
