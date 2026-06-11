package com.chaosLab.orders;

import com.chaosLab.ChaosScenarios;
import com.chaosLab.ChaosDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ChaosDemoApplication.class, properties = {
        "chaos.scenarios.orders-create.delay.probability=0.0",
        "chaos.scenarios.orders-create.exception.probability=0.0",
        "chaos.scenarios.orders-read.delay.probability=0.0",
        "chaos.scenarios.orders-read.exception.probability=0.0",
        "chaos.scenarios.orders-payment.delay.probability=0.0",
        "chaos.scenarios.orders-payment.exception.probability=0.0"
})
@AutoConfigureMockMvc
class OrdersDemoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsPaysAndExposesYamlConfiguredChaosScenarios() throws Exception {
        assertScenarioRegistered("orders-create");
        assertScenarioRegistered("orders-read");
        assertScenarioRegistered("orders-payment");

        String created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "customer-1",
                                  "sku": "book",
                                  "quantity": 2,
                                  "unitPrice": 19.99
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.total").value(39.98))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(post("/orders/{id}/pay", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/chaos/control/scenarios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("orders-create")))
                .andExpect(content().string(containsString("orders-read")))
                .andExpect(content().string(containsString("orders-payment")));
    }

    private static void assertScenarioRegistered(String name) {
        if (ChaosScenarios.get(name) == null) {
            throw new AssertionError("Scenario was not registered from YAML: " + name);
        }
    }
}
