package com.portfolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 정적 리소스와 CORS 설정.
 *
 * 업로드된 파일은 JAR 안이 아니라 서버 디스크(file.upload-dir)에 쌓이므로,
 * 해당 경로를 /uploads/** 로 따로 연결해 줍니다.
 * 허용 도메인은 프로파일별 설정(cors.allowed-origins)에서 읽습니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 허용 도메인을 지정하지 않았으면 CORS 를 아예 열지 않습니다.
        // 화면과 API 가 같은 도메인에서 서비스되므로 이것이 기본값입니다.
        //
        // 매핑만 등록하고 도메인을 비워 두면 Spring 이 allowedOrigins 를 "*" 로 보고,
        // allowCredentials=true 와 충돌해 모든 요청이 400 으로 떨어집니다.
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With", "X-XSRF-TOKEN")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadPathUri = uploadPath.toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPathUri)
                .addResourceLocations("file:" + uploadPath.toString().replace("\\", "/") + "/")
                .setCachePeriod(31536000);

        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        registry.addResourceHandler("/styles/**")
                .addResourceLocations("classpath:/static/styles/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/scripts/**")
                .addResourceLocations("classpath:/static/scripts/")
                .setCachePeriod(3600);
    }
}
