package com.ses.bldizi.config;

import com.ses.bldizi.service.CustomPageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class CustomPageFilter extends OncePerRequestFilter {

    private final CustomPageService customPageService;

    private static final Set<String> SYSTEM_PREFIXES = Set.of(
            "admin", "panel", "api", "elements", "homepage", "assets", "static",
            "error", "watch", "login", "logout", "profile", "settings", "history",
            "favorites", "sss", "privacy_policy", "terms_of_use", "requests", "search",
            "streamlive", "uploads", "favicon.ico", "robots.txt", "sitemap.xml",
            "manifest.json", "sw.js", "series", "movies", "episodes", "actors", "actuator"
    );

    public CustomPageFilter(CustomPageService customPageService) {
        this.customPageService = customPageService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path == null || path.isEmpty() || path.equals("/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        int slashIdx = cleanPath.indexOf('/');
        String firstSegment = (slashIdx == -1) ? cleanPath : cleanPath.substring(0, slashIdx);

        if (firstSegment.isBlank() || SYSTEM_PREFIXES.contains(firstSegment.toLowerCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (customPageService.findActiveBySlug(firstSegment).isPresent()) {
            String subPath = (slashIdx == -1) ? "" : cleanPath.substring(slashIdx);
            boolean served = customPageService.servePageDirectly(firstSegment, subPath, request, response);
            if (served) {
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
