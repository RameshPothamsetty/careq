package com.careq.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/users/profile-pictures/**")
                .addResourceLocations("file:uploads/profile-pictures/");
    }

    /**
     * Fix: azure-storage-blob pulls in jackson-dataformat-xml, which makes
     * Spring's default converter order serve XML before JSON — every DTO response
     * (and every error response) would go out as XML, breaking the JSON contract
     * the gateway and frontend expect. Drop the XML converter entirely; the Azure
     * SDK uses its own internal XML handling and is unaffected. Controllers also
     * declare produces=application/json (needed by standalone MockMvc tests, which
     * don't load this config), but this is the production-wide safety net.
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(MappingJackson2XmlHttpMessageConverter.class::isInstance);
        // Explicitly ensure the JSON converter is present (it always is in Boot, but
        // keeping the ordering predictable guards against converter-list surprises).
        boolean hasJson = converters.stream()
                .anyMatch(MappingJackson2HttpMessageConverter.class::isInstance);
        if (!hasJson) {
            converters.add(new MappingJackson2HttpMessageConverter());
        }
    }
}
