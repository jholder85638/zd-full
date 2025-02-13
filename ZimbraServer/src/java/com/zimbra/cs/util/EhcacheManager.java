/*
 * 
 */
package com.zimbra.cs.util;

import java.io.File;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.util.Constants;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;

/**
 * Ehcache configurator.
 *
 * As of Ehcache 2.0, disk cache only configuration is no longer supported. But, {@code maxElementsInMemory = 1} is
 * virtually disk cache only. {@code maxElementsInMemory = 0} gives an infinite capacity.
 *
 * TODO Byte size based limit is only available from Ehcache 2.5.
 *
 * @author ysasaki
 */
public final class EhcacheManager {
    private static final EhcacheManager SINGLETON = new EhcacheManager();

    public static final String IMAP_ACTIVE_SESSION_CACHE = "imap-active-session-cache";
    public static final String IMAP_INACTIVE_SESSION_CACHE = "imap-inactive-session-cache";

    private EhcacheManager() {
        Configuration conf = new Configuration();
        DiskStoreConfiguration disk = new DiskStoreConfiguration();
        disk.setPath(LC.zimbra_home.value() + File.separator + "data" + File.separator + "mailboxd");
        conf.addDiskStore(disk);
        conf.addCache(createImapActiveSessionCache());
        conf.addCache(createImapInactiveSessionCache());
        conf.setUpdateCheck(false);
        CacheManager.create(conf);
    }

    public static EhcacheManager getInstance() {
        return SINGLETON;
    }

    public void startup() {
    }

    private CacheConfiguration createImapActiveSessionCache() {
        CacheConfiguration conf = new CacheConfiguration();
        conf.setName(IMAP_ACTIVE_SESSION_CACHE);
        conf.setOverflowToDisk(true);
        conf.setDiskPersistent(false);
        conf.setMaxElementsInMemory(1); // virtually disk cache only
        conf.setMaxElementsOnDisk(0); // infinite, but essentially limited by max concurrent IMAP connections
        return conf;
    }

    private CacheConfiguration createImapInactiveSessionCache() {
        CacheConfiguration conf = new CacheConfiguration();
        conf.setName(IMAP_INACTIVE_SESSION_CACHE);
        conf.setOverflowToDisk(true);
        conf.setDiskPersistent(true);
        conf.setMaxElementsInMemory(1); // virtually disk cache only
        conf.setMaxElementsOnDisk(LC.imap_inactive_session_cache_size.intValue());
        return conf;
    }

    public void shutdown() {
        CacheManager.getInstance().shutdown();
    }
}
