/*
 * Contributions to FindBugs
 * Copyright (C) 2009, Tom\u00e1s Pollak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package edu.umd.cs.findbugs;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.findbugs.annotations.ExpectWarning;
import edu.umd.cs.findbugs.annotations.NoWarning;
import edu.umd.cs.findbugs.config.UserPreferences;
import edu.umd.cs.findbugs.test.matcher.BugInstanceMatcher;

/**
 * This test runs a FindBugs analysis on the spotbugsTestCases project and
 * checks if there are any unexpected bugs.
 *
 * The results are checked for the unexpected bugs of type
 * FB_MISSING_EXPECTED_WARNING or FB_UNEXPECTED_WARNING.
 *
 * @see ExpectWarning
 * @see NoWarning
 * @author Tom\u00e1s Pollak
 * @deprecated The annotation based approach is useless for lambdas. Write expectations using {@link BugInstanceMatcher} matchers
 */

// TODO : Rewrite tests using matchers
@Deprecated
public class DetectorsTest {

    private static final String FB_UNEXPECTED_WARNING = "FB_UNEXPECTED_WARNING";

    private static final String FB_MISSING_EXPECTED_WARNING = "FB_MISSING_EXPECTED_WARNING";

    private BugCollectionBugReporter bugReporter;

    private IFindBugsEngine engine;

    private  File spotbugsTestCases;

    /** detectors which are disabled by default but which must be used in test */
    private final String[] enabledDetectors = {"CheckExpectedWarnings","InefficientMemberAccess","EmptyZipFileEntry"};

    public  File getFindbugsTestCases()  {
        if (spotbugsTestCases != null) {
            return spotbugsTestCases;
        }
        File f = new File(SystemProperties.getProperty("spotbugsTestCases.home", "../spotbugsTestCases"));
        Assume.assumeTrue(f.exists());
        Assume.assumeTrue(f.isDirectory());
        Assume.assumeTrue(f.canRead());

        spotbugsTestCases = f;
        return f;
    }

    public File getFindbugsTestCasesFile(String path) {
        File f = new File(getFindbugsTestCases(), path);
        Assume.assumeTrue(f.exists());
        Assume.assumeTrue(f.canRead());

        return f;
    }

    @Before
    public void setUp() throws Exception {
        loadFindbugsPlugin();
    }

    /**
     * Test for expected warnings on javac compiler generated classes
     */
    @Test
    public void testAllRegressionFilesJavac() throws IOException, InterruptedException {
        setUpEngine("build/classes/java/main/");

        engine.execute();

        // If there are zero bugs, then something's wrong
        assertFalse("No bugs were reported. Something is wrong with the configuration", bugReporter.getBugCollection()
                .getCollection().isEmpty());
    }

    /**
     * Test for expected warnings on ecj compiler (Eclipse) generated classes
     * Right now this test does nothing if Eclipse UI were not used to compile project.
     * The main purpose is for developers hacking in Eclipse to see if there are any unexpected
     * failures with Eclipse generated bytecode. It would be nice to make the test explicit by using
     * ecj command line compiler and *always* validate both bytecode kinds.
     */
    @Test
    public void testAllRegressionFilesEcj() throws IOException, InterruptedException {
        setUpEngine("classesEclipse/");

        engine.execute();

        // If there are zero bugs, then something's wrong
        if (bugReporter.getBugCollection().getCollection().isEmpty()) {
            // TODO better to add ecj compiler and to compile classes with it on build, not with UI.
            System.err.println("No bugs were reported. Probably Eclipse was not used to compile the project");
        }
    }

    @After
    public void checkForUnexpectedBugs() {
        List<BugInstance> unexpectedBugs = new ArrayList<BugInstance>();
        for (BugInstance bug : bugReporter.getBugCollection()) {
            if (isUnexpectedBug(bug) && bug.getPriority() == Priorities.HIGH_PRIORITY) {
                unexpectedBugs.add(bug);
                System.out.println(bug.getMessageWithPriorityTypeAbbreviation());
                System.out.println("  " + bug.getPrimarySourceLineAnnotation());
            }
        }

        if (!unexpectedBugs.isEmpty()) {
            Assert.fail("Unexpected bugs (" + unexpectedBugs.size() + "):" + getBugsLocations(unexpectedBugs));
        }
    }

    /**
     * Returns a printable String concatenating bug locations.
     */
    private static String getBugsLocations(List<BugInstance> unexpectedBugs) {
        StringBuilder message = new StringBuilder();
        for (BugInstance bugInstance : unexpectedBugs) {
            message.append("\n");
            if (bugInstance.getBugPattern().getType().equals(FB_MISSING_EXPECTED_WARNING)) {
                message.append("missing ");
            } else {
                message.append("unexpected ");
            }
            StringAnnotation pattern = (StringAnnotation) bugInstance.getAnnotations().get(2);
            message.append(pattern.getValue());
            message.append(" ");
            message.append(bugInstance.getPrimarySourceLineAnnotation());
        }
        return message.toString();
    }

    /**
     * Returns if a bug instance is unexpected for this test.
     */
    private static boolean isUnexpectedBug(BugInstance bug) {
        return FB_MISSING_EXPECTED_WARNING.equals(bug.getType()) || FB_UNEXPECTED_WARNING.equals(bug.getType());
    }

    /**
     * Loads the default detectors from findbugs.jar, to isolate the test from
     * others that use fake plugins.
     */
    private static void loadFindbugsPlugin() {
        DetectorFactoryCollection dfc = new DetectorFactoryCollection();
        DetectorFactoryCollection.resetInstance(dfc);
    }

    /**
     * Sets up a FB engine to run on the 'spotbugsTestCases' project. It enables
     * all the available detectors and reports all the bug categories. Uses a
     * low priority threshold.
     */
    private void setUpEngine(String... analyzeMe) throws IOException {
        this.engine = new FindBugs2();
        Project project = new Project();
        project.setProjectName("spotbugsTestCases");
        this.engine.setProject(project);

        DetectorFactoryCollection detectorFactoryCollection = DetectorFactoryCollection.instance();
        engine.setDetectorFactoryCollection(detectorFactoryCollection);

        bugReporter = new BugCollectionBugReporter(project);
        bugReporter.setPriorityThreshold(Priorities.LOW_PRIORITY);
        bugReporter.setRankThreshold(BugRanker.VISIBLE_RANK_MAX);

        engine.setBugReporter(this.bugReporter);
        UserPreferences preferences = UserPreferences.createDefaultUserPreferences();
        for (String factory : enabledDetectors) {
            DetectorFactory detFactory = detectorFactoryCollection.getFactory(factory);
            preferences.enableDetector(detFactory, true);
        }
        preferences.getFilterSettings().clearAllCategories();
        this.engine.setUserPreferences(preferences);

        for (String s : analyzeMe) {
            project.addFile(getFindbugsTestCasesFile(s).getPath());
            if(s.indexOf("Eclipse") >= 0){
                // TODO see testAllRegressionFilesEcj() comments
                engine.setNoClassOk(true);
            }
        }

        File lib = getFindbugsTestCasesFile("lib");
        for(File f : lib.listFiles()) {
            String path = f.getPath();
            if (f.canRead() && path.endsWith(".jar")) {
                project.addAuxClasspathEntry(path);
            }
        }

        String classpath = System.getProperty("java.class.path");
        if (classpath == null) {
            String rootDirectory;
            try {
                rootDirectory = new File(getClass().getResource("/").toURI()).getCanonicalPath();
            } catch (final URISyntaxException e) {
                throw new RuntimeException(e);
            }
            project.addAuxClasspathEntry(rootDirectory);
        } else {
            String[] cpParts = classpath.split(File.pathSeparator);
            for (String cpStr : cpParts) {
                File file = new File(cpStr);
                if (file.exists()) {
                    project.addAuxClasspathEntry(file.getCanonicalPath());
                }
            }
        }
    }
}
