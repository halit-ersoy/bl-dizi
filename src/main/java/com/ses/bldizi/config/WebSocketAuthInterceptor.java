package com.ses.bldizi.config;

import com.ses.bldizi.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final PersonRepository personRepository;

    public WebSocketAuthInterceptor(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            Cookie[] cookies = servletRequest.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("wdiAuth".equals(cookie.getName())) {
                        String cookieVal = cookie.getValue();
                        try {
                            // Validate the cookie and get user info
                            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookieVal);
                            if (userInfo != null && userInfo.containsKey("nickname")) {
                                attributes.put("userId", userInfo.get("ID").toString());
                                attributes.put("nickname", userInfo.get("nickname").toString());
                            }
                        } catch (Exception e) {
                            // Suppress exception to let connection fail gracefully or connect anonymously
                        }
                        break;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        // No action needed
    }
}
