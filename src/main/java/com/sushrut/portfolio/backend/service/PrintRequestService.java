package com.sushrut.portfolio.backend.service;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sushrut.portfolio.backend.entities.PrintRequest;
import com.sushrut.portfolio.backend.service.impl.PrintRequestRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class PrintRequestService {

    private static final Logger log = LoggerFactory.getLogger(PrintRequestService.class);

    // Postgres advisory-lock key. Any int64 that no other feature uses.
    // Serializes ALL concurrent print-request submissions across the whole app.
    private static final long PRINT_LOCK_KEY = 8675309L;

    public static final int MAX_CLAIMS = 25;
    private static final java.util.Set<String> VALID_CITIES =
        java.util.Set.of("HYDERABAD", "NAGPUR");

    @Autowired
    private PrintRequestRepository repo;

    @PersistenceContext
    private EntityManager em;

    public long getCount() {
        return repo.count();
    }

    @Transactional
    public Map<String, Object> submit(Map<String, String> body) {
        // Take a Postgres transaction-scoped advisory lock BEFORE counting.
        // Auto-released when the transaction commits/rolls back. Two concurrent
        // submits at slot 24 no longer both see current=24 and both save.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
          .setParameter(1, PRINT_LOCK_KEY)
          .getSingleResult();

        long current = repo.count();
        if (current >= MAX_CLAIMS) {
            throw new ResponseStatusException(HttpStatus.GONE, "All 25 slots have been claimed");
        }

        String city = body.getOrDefault("city", "").toUpperCase().trim();
        if (!VALID_CITIES.contains(city)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "We only ship to Hyderabad and Nagpur right now");
        }

        String name    = body.getOrDefault("fullName",     "").trim();
        String addr1   = body.getOrDefault("addressLine1", "").trim();
        String pincode = body.getOrDefault("pincode",      "").trim();
        String phone   = body.getOrDefault("phone",        "").trim();

        if (name.length() < 2 || addr1.isEmpty() || pincode.length() != 6 || phone.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid fields");
        }

        PrintRequest pr = new PrintRequest();
        pr.setFullName(name);
        pr.setAddressLine1(addr1);
        pr.setAddressLine2(body.getOrDefault("addressLine2", "").trim());
        pr.setCity(city);
        pr.setPincode(pincode);
        pr.setPhone(phone);

        String cardIdStr = body.get("cardId");
        if (cardIdStr != null && !cardIdStr.isBlank()) {
            try {
                pr.setCardId(UUID.fromString(cardIdStr));
            } catch (IllegalArgumentException e) {
                log.warn("Malformed cardId '{}' on print request — ignoring", cardIdStr);
            }
        }

        repo.save(pr);
        // Compute claimed/remaining from the value we just wrote — saves 2 extra
        // SELECT COUNT(*) round-trips vs the old code.
        long claimed = current + 1;
        long remaining = MAX_CLAIMS - claimed;

        return Map.of(
            "success",   true,
            "claimed",   claimed,
            "remaining", remaining,
            "message",   "You're in! We'll reach out to confirm delivery."
        );
    }
}
