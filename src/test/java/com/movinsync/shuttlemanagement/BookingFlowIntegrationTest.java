package com.movinsync.shuttlemanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movinsync.shuttlemanagement.model.*;
import com.movinsync.shuttlemanagement.repository.*;
import com.movinsync.shuttlemanagement.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StopRepository stopRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    private Student student;
    private Wallet wallet;
    private Stop fromStop;
    private Stop toStop;
    private String jwt;

    @BeforeEach
    void setUp() {
        tripRepository.deleteAll();
        studentRepository.deleteAll();
        stopRepository.deleteAll();

        // Let Student cascade-save the Wallet via CascadeType.ALL
        student  = studentRepository.save(
                new Student(null, "Test Student", "test@university.edu", "password", Role.STUDENT,
                        new Wallet(null, 500)));
        wallet   = student.getWallet();
        fromStop = stopRepository.save(new Stop(null, "Campus Gate", 12.9716, 77.5946));
        toStop   = stopRepository.save(new Stop(null, "Library",     12.9352, 77.6245));
        jwt      = jwtUtil.generateToken(student.getEmail(), student.getRole().name());
    }

    @AfterEach
    void tearDown() {
        tripRepository.deleteAll();
        studentRepository.deleteAll();
        stopRepository.deleteAll();
    }

    // ── booking ───────────────────────────────────────────────────────────────

    @Test
    void bookTrip_success_returnsBookingResultAndDebitsWallet() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trips/book")
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId",  student.getId().toString())
                        .param("fromStopId", fromStop.getId().toString())
                        .param("toStopId",   toStop.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip.status").value("BOOKED"))
                .andExpect(jsonPath("$.finalFare").isNumber())
                .andReturn();

        int fare = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("finalFare").asInt();

        Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualTo(500 - fare);
    }

    @Test
    void bookTrip_insufficientBalance_returnsServerError() throws Exception {
        wallet.setBalance(0);
        walletRepository.save(wallet);

        mockMvc.perform(post("/api/trips/book")
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId",  student.getId().toString())
                        .param("fromStopId", fromStop.getId().toString())
                        .param("toStopId",   toStop.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    // ── cancellation ──────────────────────────────────────────────────────────

    @Test
    void bookAndCancel_withinRefundWindow_walletFullyRestored() throws Exception {
        // Step 1: book
        MvcResult bookResult = mockMvc.perform(post("/api/trips/book")
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId",  student.getId().toString())
                        .param("fromStopId", fromStop.getId().toString())
                        .param("toStopId",   toStop.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode bookJson = objectMapper.readTree(bookResult.getResponse().getContentAsString());
        long tripId = bookJson.get("trip").get("id").asLong();

        // Step 2: cancel immediately (well within the 10-minute refund window)
        mockMvc.perform(delete("/api/trips/{tripId}/cancel", tripId)
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId", student.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Step 3: wallet fully restored
        Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualTo(500);
    }

    @Test
    void cancelTrip_wrongStudent_returnsServerError() throws Exception {
        // Book as the real student
        MvcResult bookResult = mockMvc.perform(post("/api/trips/book")
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId",  student.getId().toString())
                        .param("fromStopId", fromStop.getId().toString())
                        .param("toStopId",   toStop.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        long tripId = objectMapper.readTree(bookResult.getResponse().getContentAsString())
                .get("trip").get("id").asLong();

        // Try to cancel with a different studentId
        mockMvc.perform(delete("/api/trips/{tripId}/cancel", tripId)
                        .header("Authorization", "Bearer " + jwt)
                        .param("studentId", "9999"))
                .andExpect(status().isBadRequest());
    }

    // ── security ──────────────────────────────────────────────────────────────

    @Test
    void bookTrip_withoutJwt_returnsUnauthorizedOrForbidden() throws Exception {
        mockMvc.perform(post("/api/trips/book")
                        .param("studentId",  student.getId().toString())
                        .param("fromStopId", fromStop.getId().toString())
                        .param("toStopId",   toStop.getId().toString()))
                .andExpect(status().is4xxClientError());
    }
}
