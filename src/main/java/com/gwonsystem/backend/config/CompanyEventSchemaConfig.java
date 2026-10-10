package com.gwonsystem.backend.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
@Configuration
@Profile("!dev")
public class CompanyEventSchemaConfig {
 @Bean ApplicationRunner allowOpenEndedCompanyEvents(JdbcTemplate jdbc) {
  return args -> jdbc.execute("ALTER TABLE IF EXISTS company_events ALTER COLUMN end_date DROP NOT NULL");
 }
}
