package com.sushrut.portfolio.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.sushrut.portfolio.backend.entities.GameUser;
import com.sushrut.portfolio.backend.model.PlayerCardRequest;
import com.sushrut.portfolio.backend.model.PlayerCardResponse;
import com.sushrut.portfolio.backend.service.impl.GameUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GameUserService.
 *
 * Focused on the properties that would silently break at runtime if they
 * regressed: name canonicalization (so "sushrut vaidya" and "Sushrut
 * VAIDYA" resolve to the same row), leaderboard shape/limit, submit-score
 * only-improve semantics.
 */
@ExtendWith(MockitoExtension.class)
class GameUserServiceTest {

    @Mock private GameUserRepository repo;
    @InjectMocks private GameUserService service;

    // ── registerOrGet canonicalization ────────────────────────────────

    @Test
    void registerOrGet_canonicalizesWhitespaceAndCase() {
        when(repo.findByFirstNameAndLastName("Sushrut", "Vaidya")).thenReturn(Optional.empty());
        when(repo.save(any(GameUser.class))).thenAnswer(inv -> inv.getArgument(0));

        GameUser u = service.registerOrGet("  sushrut   ", "VAIDYA");

        assertThat(u.getFirstName()).isEqualTo("Sushrut");
        assertThat(u.getLastName()).isEqualTo("Vaidya");
    }

    @Test
    void registerOrGet_returnsExistingUserWhenFound() {
        GameUser existing = new GameUser();
        existing.setId(UUID.randomUUID());
        existing.setFirstName("Alice");
        existing.setLastName("Wonder");
        when(repo.findByFirstNameAndLastName("Alice", "Wonder")).thenReturn(Optional.of(existing));

        GameUser out = service.registerOrGet("alice", "wonder");

        assertThat(out).isSameAs(existing);
        verify(repo, times(0)).save(any());
    }

    @Test
    void registerOrGet_multiWordNamesTitleCased() {
        when(repo.findByFirstNameAndLastName("Mary Jane", "Van Der Berg")).thenReturn(Optional.empty());
        when(repo.save(any(GameUser.class))).thenAnswer(inv -> inv.getArgument(0));

        GameUser u = service.registerOrGet("mary jane", "van der berg");

        assertThat(u.getFirstName()).isEqualTo("Mary Jane");
        assertThat(u.getLastName()).isEqualTo("Van Der Berg");
    }

    // ── getCard / not-found ───────────────────────────────────────────

    @Test
    void getCard_throws404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCard(id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // ── updateCard: only non-null fields apply ────────────────────────

    @Test
    void updateCard_onlyMutatesProvidedFields() {
        UUID id = UUID.randomUUID();
        GameUser existing = new GameUser();
        existing.setId(id);
        existing.setFirstName("A");
        existing.setLastName("B");
        existing.setBio("old-bio");
        existing.setMotto("old-motto");
        existing.setStatDev(50);
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(GameUser.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerCardRequest req = new PlayerCardRequest();
        req.setBio("new-bio");
        // motto intentionally left null — should NOT overwrite old value

        PlayerCardResponse resp = service.updateCard(id, req);

        assertThat(resp.getBio()).isEqualTo("new-bio");
        assertThat(resp.getMotto()).isEqualTo("old-motto");
        assertThat(existing.getStatDev()).isEqualTo(50);
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    // ── submitScore: monotonic-only updates ───────────────────────────

    @Test
    void submitScore_onlyImprovesBestWpm() {
        UUID id = UUID.randomUUID();
        GameUser u = new GameUser();
        u.setId(id); u.setBestWpm(80); u.setBestAccuracy(90); u.setLevel(4);
        when(repo.findById(id)).thenReturn(Optional.of(u));

        // Lower score should NOT replace personal best
        service.submitScore(id, 50, 70);
        assertThat(u.getBestWpm()).isEqualTo(80);
        assertThat(u.getBestAccuracy()).isEqualTo(90);
        assertThat(u.getLevel()).isEqualTo(4);

        // Higher score DOES replace
        service.submitScore(id, 120, 95);
        assertThat(u.getBestWpm()).isEqualTo(120);
        assertThat(u.getBestAccuracy()).isEqualTo(95);
        assertThat(u.getLevel()).isEqualTo(120 / 20);
    }

    @Test
    void submitScore_nullFieldsTreatedAsZero() {
        UUID id = UUID.randomUUID();
        GameUser fresh = new GameUser();
        fresh.setId(id);
        // bestWpm / bestAccuracy / level default to non-null from field
        // initializers, but old rows may have nulls — verify we don't NPE.
        fresh.setBestWpm(null); fresh.setBestAccuracy(null); fresh.setLevel(null);
        when(repo.findById(id)).thenReturn(Optional.of(fresh));

        Map<String, Object> result = service.submitScore(id, 60, 88);

        assertThat(result).containsEntry("bestWpm", 60)
                          .containsEntry("bestAccuracy", 88);
        assertThat(fresh.getLevel()).isEqualTo(3);
    }

    // ── leaderboard shape ─────────────────────────────────────────────

    @Test
    void leaderboard_returnsRankedList() {
        GameUser a = user("Alice", "First");
        GameUser b = user("Bob",   "Second");
        when(repo.findTop100ByOrderByCreatedAtAsc()).thenReturn(List.of(a, b));

        List<Map<String, Object>> rows = service.getLeaderboard();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("rank", 1)
                               .containsEntry("firstName", "Alice");
        assertThat(rows.get(1)).containsEntry("rank", 2)
                               .containsEntry("firstName", "Bob");
    }

    private static GameUser user(String first, String last) {
        GameUser u = new GameUser();
        u.setId(UUID.randomUUID());
        u.setFirstName(first);
        u.setLastName(last);
        u.setCreatedAt(LocalDateTime.now());
        u.setLevel(1);
        return u;
    }
}
