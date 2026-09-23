package com.nibash.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploads under {@code /media/**} from {@code nibash.media-dir} (spec §13.2) — ticket photos,
 * receipts, documents and contracts.
 *
 * <p>Uploads come back from this app's own origin, so two headers make sure a file can only ever be
 * <i>displayed</i>, never executed: {@code X-Content-Type-Options: nosniff} stops the browser
 * reinterpreting a "photo" as HTML, and a {@code sandbox} Content-Security-Policy strips script
 * from anything that is opened directly. The upload type allowlist in {@code StorageService} is
 * the first line of defence; this is the second.
 */
@Configuration
public class MediaConfig implements WebMvcConfigurer {

    private final String mediaLocation;

    public MediaConfig(@Value("${nibash.media-dir}") String mediaDir) {
        String absolute = Paths.get(mediaDir).toAbsolutePath().normalize().toUri().toString();
        this.mediaLocation = absolute.endsWith("/") ? absolute : absolute + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaLocation)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
                return true;
            }
        }).addPathPatterns("/media/**");
    }
}
