package com.routealarm.ReachClock.alarm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AlarmControllerTest {

    @Autowired MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    final UUID user = UUID.randomUUID();
    final UUID otherUser = UUID.randomUUID();

    private String json(Object o) throws Exception { return mapper.writeValueAsString(o); }

    private Map<String, Object> validBody() {
        return Map.of("type", "ETA_BEFORE", "destinationName", "Home",
                "latitude", 30.31, "longitude", 78.03, "thresholdMinutes", 10);
    }

    private String createAndGetId() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/alarms")
                        .requestAttr("userId", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createAndGet() throws Exception {
        String id = createAndGetId();
        mvc.perform(get("/api/v1/alarms/" + id).requestAttr("userId", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationName").value("Home"));
    }

    @Test
    void listReturnsOnlyOwnAlarms() throws Exception {
        createAndGetId();
        mvc.perform(get("/api/v1/alarms").requestAttr("userId", otherUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void otherUserCannotAccessAlarm() throws Exception {
        String id = createAndGetId();
        mvc.perform(get("/api/v1/alarms/" + id).requestAttr("userId", otherUser))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/alarms/" + id).requestAttr("userId", otherUser))
                .andExpect(status().isNotFound());
    }

    @Test
    void update() throws Exception {
        String id = createAndGetId();
        Map<String, Object> body = Map.of("type", "ETA_BEFORE", "status", "CANCELLED",
                "destinationName", "Office", "latitude", 30.0, "longitude", 78.0,
                "thresholdMinutes", 15);
        mvc.perform(put("/api/v1/alarms/" + id)
                        .requestAttr("userId", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.destinationName").value("Office"));
    }

    @Test
    void delete_thenNotFound() throws Exception {
        String id = createAndGetId();
        mvc.perform(delete("/api/v1/alarms/" + id).requestAttr("userId", user))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/alarms/" + id).requestAttr("userId", user))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidLatitudeReturns400() throws Exception {
        Map<String, Object> bad = Map.of("type", "ETA_BEFORE", "latitude", 200,
                "longitude", 78.0, "thresholdMinutes", 10);
        mvc.perform(post("/api/v1/alarms").requestAttr("userId", user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCoordinatesReturns400() throws Exception {
        mvc.perform(post("/api/v1/alarms").requestAttr("userId", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "ARRIVAL"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingThresholdForEtaBeforeReturns400() throws Exception {
        Map<String, Object> bad = Map.of("type", "ETA_BEFORE", "latitude", 30.0, "longitude", 78.0);
        mvc.perform(post("/api/v1/alarms").requestAttr("userId", user)
                        .contentType(MediaType.APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest());
    }
}