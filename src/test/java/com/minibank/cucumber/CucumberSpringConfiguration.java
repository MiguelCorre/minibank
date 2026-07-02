package com.minibank.cucumber;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.minibank.TestcontainersConfiguration;

import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Boots the full application against a Testcontainers PostgreSQL for every
 * Cucumber run; scenarios drive it through MockMvc.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {
}
