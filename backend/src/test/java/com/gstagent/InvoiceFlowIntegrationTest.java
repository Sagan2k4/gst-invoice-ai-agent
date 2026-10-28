package com.gstagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end: register → upload → agent processes (mock model) → duplicate detection → RBAC → reports. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceFlowIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void fullInvoiceLifecycle() throws Exception {
        String token = register("owner-" + UUID.randomUUID() + "@example.com");

        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());

        byte[] image = "fake-bill-image-bytes".getBytes();
        long id = upload(token, image);
        JsonNode bill = awaitProcessed(token, id);

        assertThat(bill.get("status").asText()).isIn("VALIDATED", "NEEDS_REVIEW");
        assertThat(bill.get("supplierGstin").asText()).hasSize(15);
        assertThat(bill.get("lineItems")).isNotEmpty();
        assertThat(bill.get("category").asText()).isNotBlank();

        // Same file again → duplicate
        long dupId = upload(token, image);
        JsonNode dup = awaitProcessed(token, dupId);
        assertThat(dup.get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(dup.get("duplicateOfId").asLong()).isEqualTo(id);

        // RBAC: a viewer can read but not upload or approve
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"View Only","email":"viewer-%s@example.com","password":"password123","role":"VIEWER"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        // Approve and check the monthly report
        mvc.perform(post("/api/invoices/" + id + "/approve").param("acceptIssues", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String month = YearMonth.from(java.time.LocalDate.parse(bill.get("invoiceDate").asText())).toString();
        mvc.perform(get("/api/reports/monthly").param("month", month).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCount").value(1));
        mvc.perform(get("/api/reports/monthly/excel").param("month", month).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/monthly/pdf").param("month", month).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotUpload() throws Exception {
        String adminToken = register("admin-" + UUID.randomUUID() + "@example.com");
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"View Only","email":"%s","password":"password123","role":"VIEWER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        String viewerToken = login(email, "password123");

        mvc.perform(multipart("/api/invoices")
                        .file(new MockMultipartFile("files", "bill.png", "image/png", "x".getBytes()))
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    @Test
    void sameFileTwiceInOneBatchFlagsOnlyTheLaterCopy() throws Exception {
        String token = register("batch-" + UUID.randomUUID() + "@example.com");
        byte[] bytes = ("batch-bill-" + UUID.randomUUID()).getBytes();
        String body = mvc.perform(multipart("/api/invoices")
                        .file(new MockMultipartFile("files", "a.png", "image/png", bytes))
                        .file(new MockMultipartFile("files", "b.png", "image/png", bytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        long first = created.get(0).get("id").asLong();
        long second = created.get(1).get("id").asLong();

        assertThat(awaitProcessed(token, first).get("status").asText()).isNotEqualTo("DUPLICATE");
        JsonNode dup = awaitProcessed(token, second);
        assertThat(dup.get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(dup.get("duplicateOfId").asLong()).isEqualTo(first);
    }

    @Test
    void logoutRevokesAccessToken() throws Exception {
        String token = register("logout-" + UUID.randomUUID() + "@example.com");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Acme Traders","fullName":"Asha Rao","email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private long upload(String token, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/invoices")
                        .file(new MockMultipartFile("files", "bill.png", "image/png", bytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get(0).get("id").asLong();
    }

    private JsonNode awaitProcessed(String token, long id) throws Exception {
        for (int i = 0; i < 50; i++) {
            String body = mvc.perform(get("/api/invoices/" + id).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = json.readTree(body);
            if (!"PROCESSING".equals(node.get("status").asText())) {
                return node;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Invoice " + id + " still processing");
    }
}
