/**
 * Who people are and how a caller proves it is them.
 *
 * <p>Three types carry the work. {@link
 * edu.cmu.cs214.campusreserve.accounts.AccountDirectory} holds the accounts and
 * is the only thing that knows an address belongs to one person. {@link
 * edu.cmu.cs214.campusreserve.accounts.TokenService} mints and retires the
 * opaque strings a caller presents. {@link
 * edu.cmu.cs214.campusreserve.accounts.AuthorizationService} answers the three
 * questions a caller asks before acting: who is this, may they touch this
 * record, and are they staff.
 *
 * <p>Nothing here reaches into the booking domain. The traffic goes one way:
 * {@code AccountDirectory.syncInto} pushes the active accounts out as core user
 * views, which is all a booking ever needs to know about a person.
 *
 * <p>Every judgement about time goes through an injected {@link java.time.Clock},
 * so expiry can be placed anywhere a caller wants it.
 */
package edu.cmu.cs214.campusreserve.accounts;
