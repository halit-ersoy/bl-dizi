package com.ses.bldizi.controller;

import com.ses.bldizi.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class SsoController {

    private final PersonRepository personRepository;

    public SsoController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /**
     * SSO Login endpoint: Consumes ticket generated from another platform (e.g. WhoDatIdols),
     * creates/updates session for bldizi, sets the 'wdiAuth' HTTP-only cookie, and redirects user.
     * GET /sso-login?ticket={ticket}&redirect={redirect}
     */
    @GetMapping("/sso-login")
    public void ssoLogin(
            @RequestParam(name = "ticket", required = false) String ticket,
            @RequestParam(name = "redirect", required = false, defaultValue = "/") String redirect,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String targetRedirect = cleanRedirect(redirect);

        if (ticket != null && !ticket.isBlank()) {
            try {
                Map<String, Object> result = personRepository.consumeSsoTicket(ticket.trim(), "bldizi");
                Object successObj = result.get("success");
                boolean isSuccess = false;
                if (successObj instanceof Boolean) {
                    isSuccess = (Boolean) successObj;
                } else if (successObj instanceof Number) {
                    isSuccess = ((Number) successObj).intValue() == 1;
                }

                if (isSuccess && result.get("cookie") != null) {
                    String cookieVal = result.get("cookie").toString();
                    Cookie authCookie = new Cookie("wdiAuth", cookieVal);
                    authCookie.setHttpOnly(true);
                    authCookie.setPath("/");
                    authCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
                    if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
                        authCookie.setSecure(true);
                    }
                    response.addCookie(authCookie);
                }
            } catch (Exception e) {
                // Log and continue redirect
                System.err.println("SSO Login error: " + e.getMessage());
            }
        }

        response.sendRedirect(targetRedirect);
    }

    /**
     * SSO Transition endpoint: Generates an SSO ticket for WhoDatIdols and redirects
     * to WhoDatIdols' SSO login endpoint.
     * GET /sso-sync?redirect={redirect}
     */
    @GetMapping("/sso-sync")
    public void ssoSync(
            @RequestParam(name = "redirect", required = false, defaultValue = "/") String redirect,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String targetRedirect = cleanRedirect(redirect);

        // Find current wdiAuth cookie
        String currentCookie = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("wdiAuth".equalsIgnoreCase(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    currentCookie = c.getValue().trim();
                    break;
                }
            }
        }

        if (currentCookie != null) {
            String ticket = personRepository.createSsoTicket(currentCookie, "whodatidols");
            if (ticket != null && !ticket.isBlank()) {
                String encodedTicket = URLEncoder.encode(ticket, StandardCharsets.UTF_8);
                String encodedRedirect = URLEncoder.encode(targetRedirect, StandardCharsets.UTF_8);
                String targetUrl = "https://whodatidols.com/sso-login?ticket=" + encodedTicket + "&redirect=" + encodedRedirect;
                response.sendRedirect(targetUrl);
                return;
            }
        }

        // If not authenticated or ticket creation failed, redirect directly to whodatidols
        String targetUrl = "https://whodatidols.com" + (targetRedirect.startsWith("/") ? targetRedirect : "/" + targetRedirect);
        response.sendRedirect(targetUrl);
    }

    private String cleanRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/";
        }
        String cleaned = redirect.trim();
        // Prevent open redirect to absolute domains or protocol relative (//evil.com)
        if (cleaned.startsWith("//") || cleaned.contains("://")) {
            return "/";
        }
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        return cleaned;
    }
}
