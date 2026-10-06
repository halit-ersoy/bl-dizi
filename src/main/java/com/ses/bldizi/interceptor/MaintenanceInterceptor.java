package com.ses.bldizi.interceptor;

import com.ses.bldizi.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class MaintenanceInterceptor implements HandlerInterceptor {

    private final SystemSettingService systemSettingService;

    public MaintenanceInterceptor(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        if (systemSettingService.isMaintenanceMode()) {
            String uri = request.getRequestURI();

            // Bakim sayfasina izin ver
            if (uri.equals("/maintenance")) {
                return true;
            }

            // Admin ve API/Stats isteklerine izin ver
            if (uri.startsWith("/admin") || uri.startsWith("/api/")) {
                return true;
            }

            // Statik dosyalara izin ver (sayfa duzgun gorunsun)
            if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/elements/")
                    || uri.startsWith("/homepage/") || uri.startsWith("/favicon")) {
                return true;
            }

            // Diger rotalari /maintenance sayfasina yonlendir
            response.sendRedirect("/maintenance");
            return false;
        }

        return true;
    }
}
