package org.broadinstitute.hellbender.tools.copynumber.legacy;

import htsjdk.samtools.util.Log;
import org.broadinstitute.hellbender.CommandLineProgramTest;
import org.broadinstitute.hellbender.cmdline.ExomeStandardArgumentDefinitions;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.tools.exome.TargetArgumentCollection;
import org.broadinstitute.hellbender.utils.LoggingUtils;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Integration test for {@link CreateReadCountPanelOfNormals}.
 *
 * @author Samuel Lee &lt;slee@broadinstitute.org&gt;
 */
public final class CreateReadCountPanelOfNormalsIntegrationTest extends CommandLineProgramTest {
    private static final String TEST_SUB_DIR = publicTestDir + "org/broadinstitute/hellbender/tools/copynumber/allelic";
    private static final File NORMAL_READ_COUNT_FILE = new File("/home/slee/working/ipython/wes.tsv");

    @Test
    public void testWES() {
//        final File outputPanelOfNormalsFile = createTempFile("create-read-count-panel-of-normals", ".pon");
        final File outputPanelOfNormalsFile = new File("/home/slee/working/ipython/wes.no-gc.pon");
        final String[] arguments = {
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_0.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_1.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_2.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_3.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_4.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_5.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_6.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_7.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_8.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_9.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_10.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_11.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_12.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_13.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_14.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_15.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_16.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_17.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_18.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_19.tsv",
                "-" + CreateReadCountPanelOfNormals.NUMBER_OF_EIGENSAMPLES_SHORT_NAME, "20",
                "-" + StandardArgumentDefinitions.OUTPUT_SHORT_NAME, outputPanelOfNormalsFile.getAbsolutePath(),
                "--" + StandardArgumentDefinitions.VERBOSITY_NAME, "INFO"
        };
        runCommandLine(arguments);
    }

    @Test
    public void testWESWithGC() {
//        final File outputPanelOfNormalsFile = createTempFile("create-read-count-panel-of-normals", ".pon");
        final File outputPanelOfNormalsFile = new File("/home/slee/working/ipython/wes.gc.pon");
        final String[] arguments = {
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_0.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_1.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_2.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_3.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_4.tsv",
                "-" + TargetArgumentCollection.TARGET_FILE_SHORT_NAME, "/home/slee/working/ipython/wes.intervals.annot.tsv",
//                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_5.tsv",
//                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_6.tsv",
//                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_7.tsv",
//                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_8.tsv",
//                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wes_9.tsv",
                "-" + StandardArgumentDefinitions.OUTPUT_SHORT_NAME, outputPanelOfNormalsFile.getAbsolutePath(),
                "--" + StandardArgumentDefinitions.VERBOSITY_NAME, "INFO"
        };
        runCommandLine(arguments);
    }

    @Test
    public void testWGS() {
        final File outputPanelOfNormalsFile = createTempFile("create-read-count-panel-of-normals", ".pon");
        final String[] arguments = {
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wgs_0.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wgs_1.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wgs_2.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wgs_3.tsv",
                "-" + StandardArgumentDefinitions.INPUT_SHORT_NAME, "/home/slee/working/ipython/wgs_4.tsv",
                "-" + StandardArgumentDefinitions.OUTPUT_SHORT_NAME, outputPanelOfNormalsFile.getAbsolutePath(),
                "--" + StandardArgumentDefinitions.VERBOSITY_NAME, "INFO"
        };
        runCommandLine(arguments);
    }
}