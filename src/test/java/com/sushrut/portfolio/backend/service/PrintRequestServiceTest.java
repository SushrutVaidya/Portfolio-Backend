package com.sushrut.portfolio.backend.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.sushrut.portfolio.backend.entities.PrintRequest;
import com.sushrut.portfolio.backend.service.impl.PrintRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for print-request validation + slot-cap semantics.
 *
 * Advisory-lock acquisition is mocked (we can't verify Postgres-side
 * lock behavior from a unit test — see PrintRequestIT for that). What
 * we DO verify here is:
 *   - 25-slot cap returns 410
 *   - City / pincode / phone format checks return 400
 *   - Happy path saves + returns claimed/remaining
 */
@ExtendWith(MockitoExtension.class)
class PrintRequestServiceTest {

    @Mock private PrintRequestRepository repo;
    @Mock private EntityManager em;
    @InjectMocks private PrintRequestService service;

    private Map<String, String> validBody() {
        Map<String, String> b = new HashMap<>();
        b.put("fullName",     "Sushrut Vaidya");
        b.put("addressLine1", "12 Line Street");
        b.put("city",         "hyderabad");
        b.put("pincode",      "500001");
        b.put("phone",        "9876543210");
        return b;
    }

    private void stubLockAcquired() {
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyInt(), anyLong())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(1);
    }

    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }

    @Test
    void happyPath_savesAndReturnsCounts() {
        stubLockAcquired();
        when(repo.count()).thenReturn(3L);
        when(repo.save(any(PrintRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> out = service.submit(validBody());

        assertThat(out).containsEntry("success", true)
                       .containsEntry("claimed", 4L)
                       .containsEntry("remaining", 21L);
        verify(repo, times(1)).save(any(PrintRequest.class));
    }

    @Test
    void when25SlotsClaimed_throws410() {
        stubLockAcquired();
        when(repo.count()).thenReturn(25L);
        assertThatThrownBy(() -> service.submit(validBody()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("410");
    }

    @Test
    void rejects_cityOutsideAllowlist() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        Map<String, String> body = validBody();
        body.put("city", "Mumbai");
        assertThatThrownBy(() -> service.submit(body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Hyderabad");
    }

    @Test
    void rejects_shortName() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        Map<String, String> body = validBody();
        body.put("fullName", "X");
        assertThatThrownBy(() -> service.submit(body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Full name");
    }

    @Test
    void rejects_nonDigitPincode() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        Map<String, String> body = validBody();
        body.put("pincode", "5A0001");
        assertThatThrownBy(() -> service.submit(body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Pincode");
    }

    @Test
    void rejects_shortPhone() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        Map<String, String> body = validBody();
        body.put("phone", "1234");
        assertThatThrownBy(() -> service.submit(body))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Phone");
    }

    @Test
    void acceptsInternationalPhonePrefix() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        when(repo.save(any(PrintRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, String> body = validBody();
        body.put("phone", "+919876543210");

        Map<String, Object> out = service.submit(body);
        assertThat(out).containsEntry("success", true);
    }

    @Test
    void malformedCardId_isSwallowedNotThrown() {
        stubLockAcquired();
        when(repo.count()).thenReturn(0L);
        when(repo.save(any(PrintRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, String> body = validBody();
        body.put("cardId", "not-a-uuid");
        // Should NOT throw — cardId is optional metadata
        Map<String, Object> out = service.submit(body);
        assertThat(out).containsEntry("success", true);
    }
}
