/*
 * 
 */
package com.zimbra.common.net;

import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

/**
 * Factory class for various TrustManager types used when creating
 * SSLSocketFactory instances.
 */
public final class TrustManagers {
    private static X509TrustManager defaultTrustManager;
    private static CustomTrustManager customTrustManager;

    /**
     * Sets the default TrustManager for use when creating SSLSocketFactory
     * instances.
     * @param tm the TrustManager to use as the default
     */
    public static synchronized void setDefaultTrustManager(X509TrustManager tm) {
        defaultTrustManager = tm;
    }

    /**
     * Returns the default TrustManager.
     * @return the default TrustManager
     */
    public static synchronized X509TrustManager defaultTrustManager() {
        return defaultTrustManager;
    }

    /**
     * Returns the CustomTrustManager. This is used by default when creating
     * SSLSocketFactory instances on ZCS or ZDesktop (see SocketFactories).
     * @return the custom TrustManager 
     */
    public static synchronized CustomTrustManager customTrustManager() {
        if (customTrustManager == null) {
            try {
                customTrustManager = new CustomTrustManager();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Unable to create CustomTrustManager", e);
            }
        }
        return customTrustManager;
    }

    /**
     * Returns a "dummy" TrustManager that unconditionally trusts all certificates.
     * @return the "dummy" TrustManager
     */
    public static X509TrustManager dummyTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // Trust all certs from client
            }
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // Trust all certs from server
            }
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
