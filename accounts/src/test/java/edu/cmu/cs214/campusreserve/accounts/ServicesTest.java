package edu.cmu.cs214.campusreserve.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.accounts.support.RecordingUserStore;
import edu.cmu.cs214.campusreserve.accounts.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServicesTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T08:00:00Z");
    private static final Duration HOUR = Duration.ofHours(1);

    private TestClock clock;
    private AccountDirectory directory;
    private TokenService tokens;
    private AuthorizationService authorization;

    @BeforeEach
    void setUp() {
        clock = TestClock.at("2026-09-10T09:00:00Z");
        directory = new AccountDirectory();
        directory.register(Account.member("u-2", "Dana Reyes", "Dana@andrew.edu", CREATED));
        directory.register(Account.member("u-1", "Lee Park", "lee@andrew.edu", CREATED));
        directory.register(Account.admin("u-3", "Sam Ortiz", "sam@andrew.edu", CREATED));
        tokens = new TokenService(directory, clock, TokenMinter.sequential("tok"));
        authorization = new AuthorizationService(directory, tokens, clock);
    }

    private static List<String> idsOf(List<Account> accounts) {
        return accounts.stream().map(Account::userId).toList();
    }

    private static List<String> valuesOf(List<Token> issued) {
        return issued.stream().map(Token::value).toList();
    }

    private static DenialReason reasonOf(AuthResult result) {
        return result.denialReason().orElseThrow(() -> new AssertionError("expected a denial: " + result));
    }

    @Nested
    class Directory {

        @Test
        void findsByIdAndAddressAndRefusesDuplicates() {
            assertEquals("Dana Reyes", directory.require("u-2").name());
            assertEquals("u-2", directory.findByEmail("DANA@ANDREW.EDU").orElseThrow().userId());
            assertTrue(directory.findByEmail("nobody@andrew.edu").isEmpty());
            assertTrue(directory.find(null).isEmpty());
            assertFalse(directory.contains("u-99"));
            assertThrows(IllegalArgumentException.class, () -> directory.require("u-99"));
            assertThrows(IllegalArgumentException.class,
                () -> directory.register(Account.member("u-1", "Other", "other@andrew.edu", CREATED)));
            IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> directory.register(Account.member("u-4", "Impostor", "dana@ANDREW.edu", CREATED)));
            assertTrue(taken.getMessage().contains("u-2"), taken.getMessage());
            assertEquals(3, directory.count());
        }

        @Test
        void listsSearchesAndEditsAccounts() {
            assertEquals(List.of("u-1", "u-2", "u-3"), idsOf(directory.listAll()));
            assertEquals(List.of("u-3"), idsOf(directory.listByRole(Role.ADMIN)));
            assertEquals(List.of("u-2"), idsOf(directory.search(" reyes ")));
            assertEquals(List.of("u-1", "u-2", "u-3"), idsOf(directory.search("@ANDREW")));
            assertTrue(directory.search("  ").isEmpty(), "a blank query matches nothing");

            assertFalse(directory.deactivate("u-2").active());
            assertEquals(2, directory.countActive());
            assertFalse(directory.deactivate("u-2").active(), "deactivating twice is harmless");
            assertTrue(directory.reactivate("u-2").active());
            assertEquals(Role.ADMIN, directory.assignRole("u-1", Role.ADMIN).role());
            assertEquals("Dana Okafor", directory.rename("u-2", " Dana Okafor ").name());
            assertTrue(directory.search("reyes").isEmpty(), "the old name is gone");
        }

        @Test
        void syncPushesOnlyActiveAccountsIntoTheUserStore() {
            directory.deactivate("u-3");
            RecordingUserStore store = new RecordingUserStore();
            assertEquals(2, directory.syncInto(store));
            assertEquals(List.of("u-1", "u-2"), store.findAll().stream().map(user -> user.id()).toList());
            assertTrue(store.findById("u-3").isEmpty(), "an inactive account is left out");
            assertEquals("Dana@andrew.edu", store.findById("u-2").orElseThrow().email());
            assertEquals(2, directory.syncInto(store), "a second sync rewrites the same people");
            assertEquals(4, store.saveCalls());
        }
    }

    @Nested
    class Sessions {

        @Test
        void issuesATokenStampedFromTheClockAndRefusesBadCallers() {
            Token token = tokens.issue("u-1", HOUR);
            assertEquals("tok-1", token.value());
            assertEquals(Instant.parse("2026-09-10T10:00:00Z"), token.expiresAt());
            assertEquals("Lee Park", tokens.authenticate("tok-1").orElseThrow().name());

            assertThrows(IllegalArgumentException.class, () -> tokens.issue("u-99", HOUR));
            assertThrows(IllegalArgumentException.class, () -> tokens.issue("u-1", Duration.ZERO));
            directory.deactivate("u-1");
            assertThrows(IllegalStateException.class, () -> tokens.issue("u-1", HOUR));

            TokenService stuck = new TokenService(directory, clock, (userId, issuedAt) -> "same");
            stuck.issue("u-2", HOUR);
            assertThrows(IllegalStateException.class, () -> stuck.issue("u-3", HOUR));
        }

        @Test
        void expiredTokensStopWorkingBeforeAnyPurgeRuns() {
            tokens.issue("u-1", HOUR);
            clock.advanceMinutes(61);
            assertTrue(tokens.authenticate("tok-1").isEmpty());
            assertTrue(tokens.find("tok-1").isPresent(), "the entry is still held until a purge");
            assertEquals(0, tokens.activeTokenCount());
            assertEquals(1, tokens.storedTokenCount());
            assertEquals(List.of(), tokens.tokensFor("u-1"));
            assertEquals(1, tokens.purgeExpired());
            assertEquals(0, tokens.storedTokenCount());
        }

        @Test
        void inactiveAccountsDoNotAuthenticateWithALiveToken() {
            tokens.issue("u-1", HOUR);
            directory.deactivate("u-1");
            assertTrue(tokens.authenticate("tok-1").isEmpty());
            assertEquals(1, tokens.activeTokenCount(), "the token itself is still inside its window");
            assertTrue(directory.reactivate("u-1").active());
            assertTrue(tokens.authenticate("tok-1").isPresent());
        }

        @Test
        void keepsOnlyTheNewestSessionsWhenTheLimitIsReached() {
            TokenService capped = new TokenService(directory, clock, TokenMinter.sequential("cap"), 2);
            capped.issue("u-1", HOUR);
            capped.issue("u-1", HOUR);
            capped.issue("u-2", HOUR);
            capped.issue("u-1", HOUR);
            assertEquals(2, capped.sessionLimit());
            assertEquals(List.of("cap-2", "cap-4"), valuesOf(capped.tokensFor("u-1")));
            assertTrue(capped.authenticate("cap-1").isEmpty(), "the oldest session was dropped");
            assertEquals(1, capped.tokensFor("u-2").size(), "another account keeps its own sessions");
            assertThrows(IllegalArgumentException.class,
                () -> new TokenService(directory, clock, TokenMinter.sequential("z"), 0));
        }

        @Test
        void refreshSwapsALiveTokenForAFreshOne() {
            tokens.issue("u-1", HOUR);
            clock.advanceMinutes(30);
            Token renewed = tokens.refresh("tok-1", HOUR).orElseThrow();
            assertEquals("tok-2", renewed.value());
            assertEquals(Instant.parse("2026-09-10T10:30:00Z"), renewed.expiresAt());
            assertTrue(tokens.find("tok-1").isEmpty(), "the old value is gone");
            assertEquals(1, tokens.storedTokenCount());
            clock.advanceMinutes(61);
            assertTrue(tokens.refresh("tok-2", HOUR).isEmpty(), "an expired token cannot be renewed");
        }

        @Test
        void summarizesLiveSessionsAndRevokesThem() {
            tokens.issue("u-1", HOUR);
            clock.advanceMinutes(15);
            tokens.issue("u-1", HOUR);
            tokens.issue("u-3", HOUR);

            List<SessionSummary> sessions = tokens.sessionsFor("u-1");
            assertEquals(2, sessions.size());
            assertEquals("tok-...", sessions.get(0).maskedToken(), "the value never appears in full");
            assertEquals(2700L, sessions.get(0).remainingSeconds());
            assertTrue(sessions.get(0).live());
            assertEquals(3600L, sessions.get(1).remainingSeconds());

            assertTrue(tokens.revoke("tok-1"));
            assertFalse(tokens.revoke("tok-1"));
            assertEquals(1, tokens.revokeAllFor("u-1"));
            assertEquals(0, tokens.revokeAllFor("u-1"));
            assertEquals(List.of("tok-3"), valuesOf(tokens.tokensFor("u-3")));
        }
    }

    @Nested
    class Access {

        @BeforeEach
        void signIn() {
            tokens.issue("u-1", HOUR);
            tokens.issue("u-3", HOUR);
        }

        private String header(String value) {
            return AccountRules.authorizationHeader(value);
        }

        @Test
        void acceptsAWellFormedHeaderAndSeparatesTheWaysOneCanFail() {
            AuthResult allowed = authorization.authenticateHeader(header("tok-1"));
            assertEquals("u-1", allowed.allowedAccount().orElseThrow().userId());
            assertEquals("u-1", allowed.orElseThrow().userId());

            assertEquals(DenialReason.MISSING_TOKEN, reasonOf(authorization.authenticateHeader(null)));
            assertEquals(DenialReason.MISSING_TOKEN, reasonOf(authorization.authenticate(" ")));
            assertEquals(DenialReason.INVALID_TOKEN, reasonOf(authorization.authenticateHeader("Basic tok-1")));
            assertEquals(DenialReason.INVALID_TOKEN, reasonOf(authorization.authenticate("tok-404")));

            clock.advanceMinutes(61);
            AuthResult stale = authorization.authenticate("tok-1");
            assertEquals(DenialReason.EXPIRED_TOKEN, reasonOf(stale), "a stale token is not an unknown one");
            assertTrue(stale.describe().startsWith("denied expired_token"), stale.describe());
        }

        @Test
        void deniesASwitchedOffAccountAndKeepsTheTokenValueOutOfTheDetail() {
            directory.deactivate("u-1");
            AuthResult result = authorization.authenticateHeader(header("tok-1"));
            assertEquals(DenialReason.ACCOUNT_INACTIVE, reasonOf(result));
            assertFalse(result.describe().contains("tok-1"), result.describe());
        }

        @Test
        void ownersAndAdministratorsMayActAndStaffOperationsNeedAnAdministrator() {
            Account holder = directory.require("u-1");
            assertTrue(authorization.mayActFor(holder, "u-1"));
            assertFalse(authorization.mayActFor(holder, "u-3"));
            assertTrue(authorization.mayActFor(directory.require("u-3"), "u-1"), "staff may act on anyone");
            assertTrue(authorization.authorizeOwner(header("tok-1"), "u-1").isAllowed());
            assertEquals(DenialReason.NOT_OWNER, reasonOf(authorization.authorizeOwner(header("tok-1"), "u-3")));
            assertEquals(DenialReason.MISSING_TOKEN, reasonOf(authorization.authorizeOwner(null, "u-1")));

            assertTrue(authorization.authorizeAdmin(header("tok-2")).isAllowed());
            AuthResult denied = authorization.authorizeAdmin(header("tok-1"));
            assertEquals(DenialReason.ADMIN_REQUIRED, reasonOf(denied));
            AccessDeniedException refused = assertThrows(AccessDeniedException.class, denied::orElseThrow);
            assertEquals(DenialReason.ADMIN_REQUIRED, refused.reason());
            assertTrue(refused.detail().contains("u-1"), refused.detail());
        }
    }
}
