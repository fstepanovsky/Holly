package cz.mzk.holly;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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

    public static File createZipArchive(File zipFile, Map<String, List<String>> srcFiles) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);

        byte[] buffer = new byte[1024];

        for (Map.Entry<String, List<String>> entry : srcFiles.entrySet()) {
            for (int i=0; i < entry.getValue().size(); i++) {
                File srcFile = new File(entry.getValue().get(i));
                FileInputStream fis = new FileInputStream(srcFile);
                // begin writing a new ZIP entry, positions the stream to the start of the entry data
                zos.putNextEntry(new ZipEntry(entry.getKey() + File.separator + srcFile.getName()));
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
                // close the InputStream
                fis.close();
            }
        }

        zos.close();

        return zipFile;
    }
}
