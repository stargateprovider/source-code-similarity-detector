package ee.ut.similaritydetector.backend;

import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class SolutionParser {

    public static final String outputDirectoryPath = "resources/";
    private final File contentDirectory;
    private final File outputDirectory;
    private final boolean preprocessSourceCode;
    private final boolean anonymousResults;

    private final Analyser analyser;

    public SolutionParser(File contentDirectory, boolean preprocessSourceCode, boolean anonymousResults, Analyser analyser) {
        this.contentDirectory = contentDirectory;
        this.outputDirectory = new File(outputDirectoryPath);
        System.out.println(outputDirectory.getAbsolutePath());
        this.preprocessSourceCode = preprocessSourceCode;
        this.anonymousResults = anonymousResults;
        this.analyser = analyser;
    }

    /**
     * Parses solutions from the given zip folder of solutions as a {@link List<Exercise>} of {@link Exercise}s
     * where an {@link Exercise} contains all the {@link Solution}s for that exercise.
     *
     * @return parsed solutions as a {@link List<Exercise>} of {@link Exercise}s
     */
    public List<Exercise> parseSolutions() {
        List<Exercise> exercises = new ArrayList<>();
        int numSolutions = 0;
        int parsedSolutions = 0;

        // Counts the total number of solutions for progress tracking
        try {
            ZipFile zipFile = new ZipFile(contentDirectory);
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                if (!entry.isDirectory()) {
                    numSolutions++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // File unzipping adapted from: https://www.baeldung.com/java-compress-and-uncompress [11.03.2021]
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(contentDirectory), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(outputDirectory, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    // write file content
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    // parse the solution from unzipped file
                    try {
                        Solution solution = parseSolution(newFile);
                        Exercise exercise = exercises.stream().filter(e -> e.getName().equals(solution.getExerciseName())).findAny().orElse(null);
                        if (exercise != null) {
                            exercise.addSolution(solution);
                        } else {
                            exercises.add(new Exercise(solution.getExerciseName(), solution));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    parsedSolutions ++;
                    analyser.updateProcessingProgress(parsedSolutions, numSolutions);
                }
                zipEntry = zis.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return exercises;
    }

    /**
     * <p>Taken from: https://www.baeldung.com/java-compress-and-uncompress [11.03.2021]</p>
     *
     * <p>Quote from the source:
     * "This method guards against writing files to the file system outside of the target folder."</p>
     */
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * Parses a {@link Solution} from the given {@link ZipEntry}.
     *
     * @param sourceCodeFile the source code file of the solution
     * @return a {@link Solution} parsed from the given {@link ZipEntry}
     */
    private Solution parseSolution(File sourceCodeFile) throws Exception {
        Solution solution;
        Pattern solutionFolderPattern = Pattern.compile("(.+)_(.+)");
        Matcher matcher = solutionFolderPattern.matcher(sourceCodeFile.getParentFile().getName());
        if (matcher.find()) {
            String author;
            if (anonymousResults)
                author = matcher.group(1);
            else
                author = matcher.group(2);
            solution = new Solution(author, sourceCodeFile.getName(), sourceCodeFile);
        } else {
            throw new InvalidPathException(sourceCodeFile.getParentFile().getName(), " is an invalid file path.");
        }
        if (preprocessSourceCode) {
            preprocessSourceCode2(sourceCodeFile.getAbsolutePath());
            String sourceCodePath = sourceCodeFile.getAbsolutePath();
            File preProcessedCodeFile = new File(sourceCodePath.substring(0, sourceCodePath.length() - 3) + "_preprocessed.py");
            solution.setPreprocessedCodeFile(preProcessedCodeFile);
        }
        return solution;
    }

    /**
     * Runs a python script to preprocess the source code,
     * removing comments, docstrings, empty lines and trailing whitespace.
     *
     * @param filePath the source code file's path
     * @throws Exception if the preprocessing fails
     */
    public void preprocessSourceCode2(String filePath) throws Exception {
        final String preprocessorScript = "/ee/ut/similaritydetector/python/Preprocessor.py";

        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.set("source_code_filepath", filePath);
        interpreter.execfile(getClass().getResourceAsStream(preprocessorScript));
    }

}
