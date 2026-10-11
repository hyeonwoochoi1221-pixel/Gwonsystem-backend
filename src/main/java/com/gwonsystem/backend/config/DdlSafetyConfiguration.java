package com.gwonsystem.backend.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class DdlSafetyConfiguration {
    @Bean HibernatePropertiesCustomizer ddlSafetyCustomizer() { return new ValidateOnly(); }

    /** Enforce the final Boot Hibernate map, including properties supplied by other customizers. */
    static final class ValidateOnly implements HibernatePropertiesCustomizer, Ordered {
        @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
        @Override public void customize(java.util.Map<String,Object> properties) {
            for (String prefix : new String[]{"jakarta", "javax"}) {
                for (String target : new String[]{"database", "scripts"}) {
                    String key = prefix + ".persistence.schema-generation." + target + ".action";
                    Object value = properties.get(key);
                    if (value != null && !value.toString().isBlank())
                        throw new IllegalStateException("Schema generation action is prohibited: " + key);
                }
            }
            Object action = properties.get("hibernate.hbm2ddl.auto");
            if (action != null && !"validate".equalsIgnoreCase(action.toString()))
                throw new IllegalStateException("Hibernate must use validate");
            properties.put("hibernate.hbm2ddl.auto", "validate");
        }
    }
}
