/*
 * 
 */

/*
 * Created on Jul 7, 2004
 */
package com.zimbra.cs.db;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.cs.util.Config;

/**
 * @author tim
 */
public class Versions {

    private static Log mLog = LogFactory.getLog(Versions.class);

    /**
     * The DB_VERSION is stored into the config table of the DB when the DB is created.  
     * If the DB_VERSION does not match our server's version, we will not run.
     * 
     * UPDATE THESE TO REQUIRE RESET-WORLD TO BE RUN
     *  
     */
    public static final int DB_VERSION = 65;

    /**
     * The INDEX_VERSION is stored into the config table of the DB when the DB is created.  
     * If the INDEX_VERSION does not match our server's version, we will not run.
     *
     * UPDATE THESE TO REQUIRE RESET-WORLD TO BE RUN
     *  
     */
    public static final int INDEX_VERSION = 2;


    /////////////////////////////////////////////////////////////
    // Called at boot time
    /////////////////////////////////////////////////////////////
    public static boolean checkVersions() {
        return (checkDBVersion() && checkIndexVersion());
    }

    public static boolean checkDBVersion() {
        String val = Config.getString("db.version", "0");
        if (val.equals(Integer.toString(DB_VERSION))) {
            return true;
        } else {
            mLog.error("DB Version Mismatch: ours=\""+DB_VERSION+"\" from DB=\""+val+"\"");
            return false;
        }
    }

    public static boolean checkIndexVersion() {
        String val = Config.getString("index.version", "0");
        if (val.equals(Integer.toString(INDEX_VERSION))) {
            return true;
        } else {
            mLog.error("Index Version Mismatch: ours=\""+INDEX_VERSION+"\" from DB=\""+val+"\"");
            return false;
        }
    }

    public static int getDbVersion() {
        return DB_VERSION;
    }

    public static int getIndexVersion() {
        return INDEX_VERSION;
    }

    /////////////////////////////////////////////////////////////
    // main and command-line parsing
    /////////////////////////////////////////////////////////////

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(Versions.class.getName(), options); 
        System.exit(1);
    }

    static CommandLine parseCmdlineArgs(String args[], Options options) {
        CommandLineParser parser = new GnuParser();

        // Loose convention for naming options:
        //
        // Options applicable for normal, production usage have lowercase
        // letter.  Options for debugging, testing, or diagnostic
        // uses have uppercase letter.

        options.addOption("h", "help", false, "print usage");
        options.addOption("o", "outputdir", true, "output directory for version.sql");

        CommandLine cl = null;
        boolean err = false;
        try {
            cl = parser.parse(options, args);
        } catch (ParseException pe) {
            System.err.println("error: " + pe.getMessage());
            err = true;
        }

        if (err || cl.hasOption("h"))
            usage(options);

        return cl;
    }
}
