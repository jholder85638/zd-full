/*
 * 
 */

package com.zimbra.qa.unittest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.zimbra.common.util.StringUtil;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMountpoint;

import junit.framework.TestCase;

public class TestMountpoint
extends TestCase {
    
    private static final String NAME_PREFIX = TestMountpoint.class.getName();
    private static final String USER_NAME = "user1";
    private static final String REMOTE_USER_NAME = "user2";
    
    public void setUp()
    throws Exception {
        cleanUp();
    }
    
    public void tearDown()
    throws Exception {
        cleanUp();
    }
    
    /**
     * Tests {@link ZMailbox#getValidFolderIds(String)}.
     */
    public void testInvalidMountpoint()
    throws Exception {
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        ZMailbox remoteMbox = TestUtil.getZMailbox(REMOTE_USER_NAME);
        String remoteFolderPath = "/" + NAME_PREFIX + "-testInvalidMountpoint-remote";
        ZFolder remoteFolder = TestUtil.createFolder(remoteMbox, remoteFolderPath);
        ZMountpoint mountpoint = TestUtil.createMountpoint(remoteMbox, remoteFolderPath, mbox, NAME_PREFIX + "-mountpoint");
        
        // Test valid mountpoint.
        Set<String> folderIds = new HashSet<String>();
        folderIds.add(mountpoint.getId());
        String inboxId = Integer.toString(Mailbox.ID_FOLDER_INBOX); 
        folderIds.add(inboxId);
        String idString = mbox.getValidFolderIds(StringUtil.join(",", folderIds));
        List<String> returnedIds = Arrays.asList(idString.split(","));
        assertEquals(2, returnedIds.size());
        assertTrue(returnedIds.contains(inboxId));
        assertTrue(returnedIds.contains(mountpoint.getId()));
        assertEquals(1, getNumCommas(idString));
        
        // Delete remote folder and confirm that the id is no longer returned.
        remoteMbox.deleteFolder(remoteFolder.getId());
        idString = mbox.getValidFolderIds(StringUtil.join(",", folderIds));
        returnedIds = Arrays.asList(idString.split(","));
        assertEquals(1, returnedIds.size());
        assertTrue(returnedIds.contains(inboxId));
        assertEquals(0, getNumCommas(idString));
    }
    
    private int getNumCommas(String s) {
        int numCommas = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ',') {
                numCommas++;
            }
        }
        return numCommas;
    }
    
    private void cleanUp()
    throws Exception {
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
        TestUtil.deleteTestData(REMOTE_USER_NAME, NAME_PREFIX);
    }

    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(TestMountpoint.class);
    }
}
