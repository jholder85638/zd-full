/*
 * 
 */
package org.jivesoftware.wildfire.auth;

import org.jivesoftware.wildfire.user.UserManager;

/**
 * A token that proves that a user has successfully authenticated.
 *
 * @author Matt Tucker
 * @see AuthFactory
 */
public class AuthToken {

    private static final long serialVersionUID = 01L;
    private String username;

    /**
     * Constucts a new AuthToken with the specified username.
     *
     * @param username the username to create an authToken token with.
     */
    public AuthToken(String username) {
        assert(username.indexOf('@') > 0);
        this.username = username;
    }

    /**
     * Returns the username associated with this AuthToken.
     *
     * @return the username associated with this AuthToken.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns true if this AuthToken is the Anonymous auth token.
     *
     * @return true if this token is the anonymous AuthToken.
     */
    public boolean isAnonymous() {
        return username == null || !UserManager.getInstance().isRegisteredUser(username);
    }
}