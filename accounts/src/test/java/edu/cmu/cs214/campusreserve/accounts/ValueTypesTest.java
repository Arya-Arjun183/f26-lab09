package edu.cmu.cs214.campusreserve.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValueTypesTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant ISSUED = Instant.parse("2026-09-10T09:00:00Z");
    private static final Token TOKEN = new Token("tok-abcdef", "u-1", ISSUED, ISSUED.plus(Duration.ofHours(1)));

    private static Account member(String userId, String name, String email) {
        return Account.member(userId, name, email, CREATED);
    }

    @Nested
    class Accounts {

        @Test
        void trimsFieldsKeepsEmailCaseAndRejectsBadValues() {
            Account account = member(" u-1 ", "  Dana Reyes ", " Dana.Reyes@Andrew.Edu ");
            assertEquals("u-1", account.userId());
            assertEquals("Dana Reyes", account.name());
            assertEquals("Dana.Reyes@Andrew.Edu", account.email());
            assertEquals("dana.reyes@andrew.edu", account.emailKey());
            assertTrue(account.active());
            assertThrows(IllegalArgumentException.class, () -> member("u 1", "Dana", "d@a.edu"));
            assertThrows(IllegalArgumentException.class, () -> member("u-1", "Dana", "at-less"));
            assertThrows(NullPointerException.class,
                () -> new Account("u-1", "Dana", "d@a.edu", Role.MEMBER, true, null));
        }

        @Test
        void copiesChangeOneFieldAndNarrowToTheCoreUser() {
            Account holder = member("u-1", "Dana", "dana@andrew.edu");
            Account promoted = holder.withRole(Role.ADMIN);
            assertEquals(Role.ADMIN, promoted.role());
            assertEquals(holder.createdAt(), promoted.createdAt());
            assertTrue(promoted.canAdminister());
            assertFalse(holder.canAdminister());
            assertTrue(holder.owns("u-1"));
            assertFalse(promoted.withActive(false).active());
            assertEquals("Dana Park", holder.withName(" Dana Park ").name());
            assertSame(holder, holder.withRole(Role.MEMBER), "no change means no new object");

            User user = Account.admin("u-9", "Sam Ortiz", "sam@andrew.edu", CREATED).toUser();
            assertEquals("u-9", user.id());
            assertEquals("Sam Ortiz", user.name());
            assertEquals("sam", user.localPart());
        }

        @Test
        void enumsCarryStableWireCodes() {
            assertEquals("member", Role.MEMBER.code());
            assertEquals(Role.ADMIN, Role.fromCode(" ADMIN "));
            assertThrows(IllegalArgumentException.class, () -> Role.fromCode("owner"));
            assertEquals("expired_token", DenialReason.EXPIRED_TOKEN.code());
            assertEquals(DenialReason.NOT_OWNER, DenialReason.fromCode("not_owner"));
            assertTrue(DenialReason.MISSING_TOKEN.isAuthenticationFailure());
            assertFalse(DenialReason.ADMIN_REQUIRED.isAuthenticationFailure());
            assertEquals(401, DenialReason.EXPIRED_TOKEN.suggestedHttpStatus());
            assertEquals(403, DenialReason.NOT_OWNER.suggestedHttpStatus());
        }
    }

    @Nested
    class Tokens {

        @Test
        void expiresAtItsExpiryInstantAndNotBefore() {
            assertFalse(TOKEN.isExpiredAt(ISSUED.plusSeconds(3599)));
            assertTrue(TOKEN.isExpiredAt(ISSUED.plusSeconds(3600)));
            assertEquals(3600L, TOKEN.remainingSeconds(ISSUED));
            assertEquals(60L, TOKEN.remainingSeconds(ISSUED.plusSeconds(3540)));
            assertEquals(0L, TOKEN.remainingSeconds(ISSUED.plusMillis(3599_500L)), "partial seconds are dropped");
            assertEquals(Duration.ofHours(1), TOKEN.lifetime());
        }

        @Test
        void rejectsAWindowThatDoesNotOpenMasksValuesAndMintsThem() {
            assertThrows(IllegalArgumentException.class, () -> new Token("tok", "u-1", ISSUED, ISSUED));
            assertThrows(IllegalArgumentException.class, () -> new Token(" ", "u-1", ISSUED, ISSUED.plusSeconds(1)));
            assertEquals("tok-...", TOKEN.maskedValue());
            assertEquals("...", Token.mask("abcd"));
            assertEquals("...", Token.mask(null));

            TokenMinter counting = TokenMinter.sequential("tok");
            assertEquals("tok-1", counting.mint("u-1", ISSUED));
            assertEquals("tok-2", counting.mint("u-2", ISSUED));
            assertThrows(IllegalArgumentException.class, () -> TokenMinter.sequential(" "));

            TokenMinter drawing = TokenMinter.random();
            String first = drawing.mint("u-1", ISSUED);
            assertNotEquals(first, drawing.mint("u-1", ISSUED));
            assertTrue(first.matches("[A-Za-z0-9_-]+"), first);
            assertThrows(IllegalArgumentException.class, () -> TokenMinter.random(8));
        }
    }

    @Nested
    class Rules {

        @Test
        void normalizesAddressesAndChecksLifetimes() {
            assertEquals("dana@andrew.edu", AccountRules.normalizeEmail("  Dana@Andrew.EDU "));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireEmail("dana@@andrew.edu"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireEmail("@andrew.edu"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireEmail("dana@andrew"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireEmail("da na@andrew.edu"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireEmail(null));

            assertEquals(Duration.ofHours(1), AccountRules.requireLifetime(Duration.ofHours(1)));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireLifetime(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireLifetime(Duration.ofDays(31)));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.requireLifetime(null));
        }

        @Test
        void readsTheTokenOutOfAnAuthorizationHeader() {
            assertEquals(Optional.of("abc123"), AccountRules.bearerToken("Bearer abc123"));
            assertEquals(Optional.of("abc123"), AccountRules.bearerToken("   bearer    abc123  "));
            assertTrue(AccountRules.bearerToken(null).isEmpty(), "a missing header is not a failure");
            assertTrue(AccountRules.bearerToken("   ").isEmpty());
            assertThrows(IllegalArgumentException.class, () -> AccountRules.bearerToken("Basic abc123"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.bearerToken("Bearer"));
            assertThrows(IllegalArgumentException.class, () -> AccountRules.bearerToken("Bearer abc 123"));
            assertEquals("Bearer abc123", AccountRules.authorizationHeader("abc123"));
        }
    }
}
