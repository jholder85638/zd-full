/*
 * 
 */
package org.jivesoftware.wildfire.auth;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.user.UserNotFoundException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Authentication service.
 *
 * Users of Jive that wish to change the AuthProvider implementation used to authenticate users
 * can set the <code>AuthProvider.className</code> Jive property. For example, if
 * you have altered Jive to use LDAP for user information, you'd want to send a custom
 * implementation of AuthFactory to make LDAP authToken queries. After changing the
 * <code>AuthProvider.className</code> Jive property, you must restart your application
 * server.<p>
 * <p/>
 * The getAuthToken method that takes servlet request and response objects as arguments can be
 * used to implement single sign-on. Additionally, two helper methods are provided for securely
 * encrypting and decrypting login information so that it can be stored as a cookie value to
 * implement auto-login.<p>
 *
 * @author Matt Tucker
 */
public class AuthFactory {

    private static AuthProvider authProvider = null;
    private static MessageDigest digest;
    private static final Object DIGEST_LOCK = new Object();
    private static Blowfish cipher = null;

    static {
        // Load an auth provider.
        String className = IMConfig.AUTH_PROVIDER_CLASSNAME.getString(); 
        try {
            Class c = ClassUtils.forName(className);
            authProvider = (AuthProvider)c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading auth provider: " + className, e);
        }
        // Create a message digest instance.
        try {
            digest = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }
    
    public static String getServerDialbackHmac(String data) throws Exception {
        return authProvider.getServerDialbackHmac(data);
    }

    /**
     * Returns the currently-installed AuthProvider. <b>Warning:</b> in virtually all
     * cases the auth provider should not be used directly. Instead, the appropriate
     * methods in AuthFactory should be called. Direct access to the auth provider is
     * only provided for special-case logic.
     *
     * @return the current UserProvider.
     */
    public static AuthProvider getAuthProvider() {
        return authProvider;
    }

    /**
     * Returns true if the currently installed {@link AuthProvider} supports authentication
     * using plain-text passwords according to JEP-0078. Plain-text authentication is
     * not secure and should generally only be used over a TLS/SSL connection.
     *
     * @return true if plain text password authentication is supported.
     */
    public static boolean isPlainSupported() {
        return authProvider.isPlainSupported();
    }

    /**
     * Returns true if the currently installed {@link AuthProvider} supports
     * digest authentication according to JEP-0078.
     *
     * @return true if digest authentication is supported.
     */
    public static boolean isDigestSupported() {
        return authProvider.isDigestSupported();
    }

    /**
     * Returns the user's password. This method will throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @return the user's password.
     * @throws UserNotFoundException if the given user could not be found.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public static String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException {
        assert(username.indexOf('@') > 0);
        return authProvider.getPassword(username.toLowerCase());
    }

    /**
     * Authenticates a user with a username and plain text password and returns and
     * AuthToken. If the username and password do not match the record of
     * any user in the system, this method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param password the password.
     * @return an AuthToken token if the username and password are correct.
     * @throws UnauthorizedException if the username and password do not match any existing user.
     */
    public static AuthToken authenticate(String username, String password)
            throws UnauthorizedException
    {
        assert(username.indexOf('@') > 0);
        authProvider.authenticate(username, password);
        return new AuthToken(username);
    }

    /**
     * Authenticates a user with a username, token, and digest and returns an AuthToken.
     * The digest should be generated using the {@link #createDigest(String, String)} method.
     * If the username and digest do not match the record of any user in the system, the
     * method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param token the token that was used with plain-text password to generate the digest.
     * @param digest the digest generated from plain-text password and unique token.
     * @return an AuthToken token if the username and digest are correct for the user's
     *      password and given token.
     * @throws UnauthorizedException if the username and password do not match any
     *      existing user.
     */
    public static AuthToken authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        assert(username.indexOf('@') > 0);
        authProvider.authenticate(username, token, digest);
        return new AuthToken(username);
    }

    /**
     * Returns a digest given a token and password, according to JEP-0078.
     *
     * @param token the token used in the digest.
     * @param password the plain-text password to be digested.
     * @return the digested result as a hex string.
     */
    public static String createDigest(String token, String password) {
        synchronized (DIGEST_LOCK) {
            digest.update(token.getBytes());
            return StringUtils.encodeHex(digest.digest(password.getBytes()));
        }
    }

    /**
     * Returns an encrypted version of the plain-text password. Encryption is performed
     * using the Blowfish algorithm. The encryption key is stored as the Jive property
     * "passwordKey". If the key is not present, it will be automatically generated.
     *
     * @param password the plain-text password.
     * @return the encrypted password.
     * @throws UnsupportedOperationException if encryption/decryption is not possible;
     *      for example, during setup mode.
     */
    public static String encryptPassword(String password) {
        if (password == null) {
            return null;
        }
        Blowfish cipher = getCipher();
        if (cipher == null) {
            throw new UnsupportedOperationException();
        }
        return cipher.encryptString(password);
    }

    /**
     * Returns a decrypted version of the encrypted password. Encryption is performed
     * using the Blowfish algorithm. The encryption key is stored as the Jive property
     * "passwordKey". If the key is not present, it will be automatically generated.
     *
     * @param encryptedPassword the encrypted password.
     * @return the encrypted password.
     * @throws UnsupportedOperationException if encryption/decryption is not possible;
     *      for example, during setup mode.
     */
    public static String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null) {
            return null;
        }
        Blowfish cipher = getCipher();
        if (cipher == null) {
            throw new UnsupportedOperationException();
        }
        return cipher.decryptString(encryptedPassword);
    }

    /**
     * Returns a Blowfish cipher that can be used for encrypting and decrypting passwords.
     * The encryption key is stored as the Jive property "passwordKey". If it's not present,
     * it will be automatically generated.
     *
     * @return the Blowfish cipher, or <tt>null</tt> if Wildfire is not able to create a Cipher;
     *      for example, during setup mode.
     */
    private static synchronized Blowfish getCipher() {
        if (cipher != null) {
            return cipher;
        }
        // Get the password key, stored as a database property. Obviously,
        // protecting your database is critical for making the
        // encryption fully secure.
        String keyString;
        try {
            Log.error("Blowfish cipher unsupported");
            return null;
//            keyString = JiveGlobals.getProperty("passwordKey");
//            if (keyString == null) {
//                keyString = StringUtils.randomString(15);
//                JiveGlobals.setProperty("passwordKey", keyString);
//                // Check to make sure that setting the property worked. It won't work,
//                // for example, when in setup mode.
//                if (!keyString.equals(JiveGlobals.getProperty("passwordKey"))) {
//                    return null;
//                }
//            }
//            cipher = new Blowfish(keyString);
        }
        catch (Exception e) {
            Log.error(e);
        }
        return cipher;
    }
}