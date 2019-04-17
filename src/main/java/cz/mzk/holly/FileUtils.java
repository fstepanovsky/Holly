package cz.mzk.holly;

import cz.mzk.holly.model.TreeNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author kremlacek
 */
public class FileUtils {
    public static File createZipArchive(String[] srcFiles) throws IOException {
        File zipFile = File.createTempFile("download", ".zip");

        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        byte[] buffer = new byte[1024];

        for (int i=0; i < srcFiles.length; i++) {
            File srcFile = new File(srcFiles[i]);
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

    public static File createZipArchive(File zipFile, TreeNode root) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        zipSubTree("", root, zos);

        zos.close();

        return zipFile;
    }

    public static void zipSubTree(String path, TreeNode root, ZipOutputStream zos) throws IOException {

        //process subtrees
        for(var entry : root.getSubTree().entrySet()) {
            zipSubTree(path + (path.isEmpty() ? "" : File.separator) + entry.getKey(), entry.getValue(), zos);
        }

        //process pages under current node
        byte[] buffer = new byte[1024];

        for(var page : root.getPagePaths()) {
            File srcFile = new File(page);
            FileInputStream fis = new FileInputStream(srcFile);
            // begin writing a new ZIP entry, positions the stream to the start of the entry data
            zos.putNextEntry(new ZipEntry(path + (path.isEmpty() ? "" : File.separator) + srcFile.getName()));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            // close the InputStream
            fis.close();
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
