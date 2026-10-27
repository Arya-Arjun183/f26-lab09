package edu.cmu.cs214.campusreserve.http;

import edu.cmu.cs214.campusreserve.accounts.Account;
import edu.cmu.cs214.campusreserve.accounts.AuthResult;
import edu.cmu.cs214.campusreserve.accounts.AuthorizationService;
import edu.cmu.cs214.campusreserve.accounts.DenialReason;
import java.util.Objects;

/**
 * Turns the {@code Authorization} header into an account, using the accounts
 * module's checks.
 *
 * <p>The API does not decide who anybody is. It reads the header, hands it to
 * {@link AuthorizationService}, and turns a denial into the status that denial
 * says it deserves: 401 when the credentials are the problem, 403 when the
 * caller is known but may not act.
 */
public final class RequestAuth {

    private final AuthorizationService authorization;

    public RequestAuth(AuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    /** The account behind the request, or a 401. */
    public Account require(RequestContext request) {
        String header = request.authorizationHeader()
            .orElseThrow(() -> ApiException.unauthorized("This endpoint needs a bearer token."));
        return unwrap(authorization.authenticateHeader(header));
    }

    /**
     * The account behind the request, which must be able to act for
     * {@code ownerUserId}. Owners may act for themselves and administrators may
     * act for anyone.
     */
    public Account requireActingFor(RequestContext request, String ownerUserId) {
        Account account = require(request);
        return unwrap(authorization.checkOwner(account, ownerUserId));
    }

    /** The account behind the request, which must be an administrator. */
    public Account requireAdministrator(RequestContext request) {
        return unwrap(authorization.checkAdmin(require(request)));
    }

    private static Account unwrap(AuthResult result) {
        if (result instanceof AuthResult.Allowed allowed) {
            return allowed.account();
        }
        AuthResult.Denied denied = (AuthResult.Denied) result;
        DenialReason reason = denied.reason();
        throw new ApiException(reason.suggestedHttpStatus(), reason.code(), denied.detail());
    }
}
