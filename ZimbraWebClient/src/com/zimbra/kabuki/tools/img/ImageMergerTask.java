/*
 * 
 */

package com.zimbra.kabuki.tools.img;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DirSet;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ImageMergerTask
		extends Task {

	//
	// Data
	//

	// required

	private List<DirSet> inputDirs = new LinkedList<DirSet>();
	private File outputDir;
	private String cssFilename;
	private String cssPath;

	// optional

    private String jsFilename;
    private String cacheFilename;
	private boolean copyFiles = false;
    private boolean appendOutput = true;
    private boolean verboseOutput = false;

	//
	// Public methods
	//

	// required

    public DirSet createDirSet() {
        DirSet dirset = new DirSet();
        inputDirs.add(dirset);
        return dirset;
    }

	public void setDestDir(File dir) {
		outputDir = dir;
	}

	public void setCssFile(String filename) {
		cssFilename = filename;
	}

	public void setCssPath(String path) {
		cssPath = path;
	}

    // optional

	public void setJsFile(String filename) {
		jsFilename = filename;
	}

	public void setCacheFile(String filename) {
		cacheFilename = filename;
	}

	public void setCopy(boolean copy) {
		copyFiles = copy;
	}

    public void setVerbose(boolean verbose) {
        verboseOutput = verbose;
    }

    /** @deprecated */
	public void setLayout(String layout) {}

    /** @deprecated */
	public void setDisable(boolean disable) {}

	//
	// Task methods
	//

	public void execute() throws BuildException {

		// check arguments
		assertAndThrow(outputDir != null, "missing destdir attribute");
		assertAndThrow(outputDir.exists(), "destination directory doesn't exist");
		assertAndThrow(outputDir.isDirectory(), "destination must be a directory");
		assertAndThrow(inputDirs.size() > 0, "missing <dirset> element(s)");
		assertAndThrow(cssFilename != null && cssFilename.length() > 0, "missing cssfile attribute");
		assertAndThrow(cssPath != null, "missing csspath attribute");

        // create merger
        ImageMerger merger = new ImageMerger();
        merger.setOutputDirectory(outputDir);
        merger.setCssFilename(cssFilename);
        merger.setCssPath(cssPath);
        merger.setCacheFilename(cacheFilename);
        merger.setJsFilename(jsFilename);
        merger.setCopyFiles(copyFiles);
        merger.setVerbose(verboseOutput);

        // process dirs
		try {
            List<File> dirs = new LinkedList<File>();
            for (DirSet dirset : inputDirs) {
                DirectoryScanner scanner = dirset.getDirectoryScanner(getProject());
                File baseDir = scanner.getBasedir();
                String[] dirnames = scanner.getIncludedDirectories();
                for (String dirname : dirnames) {
                    dirs.add(new File(baseDir, dirname));
                }
            }
            merger.process(dirs);
		}
		catch (Exception e) {
			throw new BuildException(e);
		}

	} // execute()

    //
    // Private methods
    //

    private static void assertAndThrow(boolean condition, String message)
    throws BuildException {
        if (!condition) throw new BuildException(message);
    }

} // class ImageMergerTask