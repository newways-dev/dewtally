package com.kate.dewtally.web;

import com.kate.dewtally.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static com.kate.dewtally.security.TestUsers.mockMvc;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API description the web UI generates its types from.
 */
@SpringBootTest(properties = "dewtally.validation.kosit.enabled=false")
@Import(TestcontainersConfiguration.class)
class OpenApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = mockMvc(context);
    }

    @Test
    void describesTheApiWithoutLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("dewtally API"))
                .andExpect(jsonPath("$.paths['/api/invoices/counts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/documents/{id}/content'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test
    void doesNotOfferTheUserAsARequestParameter() throws Exception {
        // The user comes from the token: only the real query parameters are described
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/invoices'].get.parameters[*].name")
                        .value(containsInAnyOrder("status", "q", "page", "size")));
    }
}