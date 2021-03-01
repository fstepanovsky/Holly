package cz.mzk.holly;

import cz.mzk.holly.model.TreeNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author kremlacek
 */
public class FileUtils {

    private static final String JP2_TO_JPG_CONVERT = System.getenv("JP2_TO_JPG_CONVERT");
    private static final Logger logger = Logger.getLogger(FileUtils.class.getName());

    public static File createZipArchive(String[] srcFiles) throws IOException {
        File zipFile = File.createTempFile("download", ".zip");

        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        byte[] buffer = new byte[1024];

        for (String srcFilePath : srcFiles) {
            File srcFile = new File(srcFilePath);
            FileInputStream fis = new FileInputStream(srcFile);
            // begin writing a new ZIP entry, positions the stream to the start of the entry data
            zos.putNextEntry(new ZipEntry(srcFile.getName()));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            // close the InputStream
            fis.close();
        }

        zos.close();

        return zipFile;
    }

    public static void createZipArchive(File zipFile, TreeNode root, String format) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        zipSubTree("", root, zos, format);

        zos.close();
    }

    private static void zipSubTree(String path, TreeNode root, ZipOutputStream zos, String format) throws IOException {
        //process subtrees
        for(var entry : root.getSubTree().entrySet()) {
            zipSubTree(path + (path.isEmpty() ? "" : File.separator) /*+ entry.getKey()*/, entry.getValue(), zos, format);
        }

        //process pages under current node
        byte[] buffer = new byte[1024];

        for(var page : root.getPagePaths()) {
            var formatEquals = page.toLowerCase().endsWith(format);
            var srcFile = new File(page);
            var name = srcFile.getName();
            File convertedFile = null;

            if (!srcFile.exists()) {
                throw new IllegalStateException("Source file does not exist. File: " + page);
            }

            if (!formatEquals) {
                name = name.substring(0, name.lastIndexOf(".")) + "." + format;
                convertedFile = File.createTempFile("holly_","_" + name);

                convertFile(srcFile, convertedFile);
            }

            FileInputStream fis = new FileInputStream(formatEquals ? srcFile : convertedFile);
            // begin writing a new ZIP entry, positions the stream to the start of the entry data
            zos.putNextEntry(new ZipEntry(/* path + (path.isEmpty() ? "" : File.separator) + */ name));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            // close the InputStream
            fis.close();

            if (convertedFile != null) {
                //logger.info("Deleting: " + convertedFile.getAbsolutePath());
                convertedFile.delete();
            }
        }
    }

    private static void convertFile(File srcFile, File convertedFile) throws IOException {
        if (JP2_TO_JPG_CONVERT == null || JP2_TO_JPG_CONVERT.isEmpty()) {
            throw new IllegalStateException("Conversion SW not set.");
        }

        var convertProcess = Runtime.getRuntime().exec(JP2_TO_JPG_CONVERT + " " + srcFile + " " + convertedFile);

        try {
            int i = convertProcess.waitFor();

            if (i != 0) {
                throw new IllegalStateException("Convert process failed with non zero return code.");
            }
        } catch (InterruptedException e) {
            logger.severe("Convert process failed. Reason: " + e.getMessage());
            throw new IllegalStateException("Convert process failed. Reason:" + e);
        }
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
