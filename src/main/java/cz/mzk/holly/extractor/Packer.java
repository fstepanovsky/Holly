package cz.mzk.holly.extractor;

import cz.mzk.holly.FileUtils;
import cz.mzk.holly.model.TreeNode;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author kremlacek
 */
public class Packer implements Runnable {

    public static final int PACKER_THREAD_LIMIT = 2;
    public static final int HIERARCHY_CRAWLER_TIME_LIMIT = 60;

    private static final Logger logger = Logger.getLogger(Packer.class.getName());
    private static final Semaphore packerSemaphore = new Semaphore(PACKER_THREAD_LIMIT);

    private ImageExtractor imageExtractor;
    private File zipFile;
    private String uuidListStr;
    private Integer fromPage;
    private Integer toPage;
    private String format;

    private Packer(ImageExtractor imageExtractor, File zipFile, String uuidListStr, String format, Integer fromPage, Integer toPage) {
        this.imageExtractor = imageExtractor;
        this.zipFile = zipFile;
        this.uuidListStr = uuidListStr;
        this.format = format;
        this.fromPage = fromPage;
        this.toPage = toPage;
    }

    @Override
    public void run() {
        var es = Executors.newFixedThreadPool(4);
        var root = new TreeNode(true, "");

        //create loading status file
        var loadingFile = createStatusFile(zipFile, ImageExtractor.SEARCH_SUFFIX);

        try {
            loadingFile.createNewFile();

            try {
                if (uuidListStr == null || uuidListStr.isEmpty()) {
                    logger.info("No uuid set in the list");
                    imageExtractor.createReportFile(zipFile.getName(), "No uuid set in the list.");
                    return;
                }

                if (!uuidListStr.contains("\n")) {
                    logger.info("List does not contain single EOL sign");
                }

                String[] uuids = uuidListStr.split("\n");

                for (String uuid : uuids) {
                    //strip whitespaces
                    uuid = uuid.replaceAll("\\s+", "");

                    if (!imageExtractor.hasUuidPrefix(uuid)) {
                        imageExtractor.createReportFile(zipFile.getName(), "Invalid uuid requested.");
                        logger.warning("Invalid uuid: " + uuid);
                        return;
                    }

                    es.submit(new TitleProcessor(imageExtractor, uuid, root, fromPage, toPage));
                }
            } finally {
                //close executor service so that it can't accept more requests
                es.shutdown();
            }

            //wait for all threads to finish
            try {
                es.awaitTermination(HIERARCHY_CRAWLER_TIME_LIMIT, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.severe(e.getMessage());
                return;
            }

            //check page count and exit if limit was exceeded
            if (imageExtractor.getPageCounterValue() > ImageExtractor.PAGE_LIMIT) {
                logger.warning("Page count over limit.");
                imageExtractor.createReportFile(zipFile.getName(), "Page count (" + imageExtractor.getPageCounterValue() + ") over limit.");
                return;
            }

            //check if any images have been found
            if (root.getSubTree().isEmpty() && root.getPagePaths().isEmpty()) {
                logger.warning("No images found.");
                imageExtractor.createReportFile(zipFile.getName(), "No images found.");
                return;
            }
        } catch (IOException e) {
            logger.warning("Could not create status file.");
            imageExtractor.createReportFile(zipFile.getName(), "Could not create status file.");
        } finally {
            //remove loading file
            if (loadingFile.exists()) {
                loadingFile.delete();
            }
        }

        try {
            //try getting lock to enter section with limited thread count execution (see packerSemaphore)
            boolean locked = packerSemaphore.tryAcquire();

            //unable to enter the section, notify user and wait
            if (!locked) {
                //create status file
                var waitFile = createStatusFile(zipFile, ImageExtractor.WAITING_SUFFIX);

                try {
                    waitFile.createNewFile();
                } catch (IOException e) {
                    //status file is not critical therefore we do not rethrow the exception
                    logger.warning("Could not create waiting notification file.");
                }

                //wait until permit is available
                packerSemaphore.acquire();

                //permit acquired, delete wait file
                if (waitFile.exists()) {
                    waitFile.delete();
                }
            }

            //permit acquired, continue with creating zip archive

            //change zipFile name with appropriate suffix
            var tempZipFile = createStatusFile(zipFile, ImageExtractor.PACKING_SUFFIX);

            try {
                FileUtils.createZipArchive(tempZipFile, root, format);
            } catch (IOException | IllegalStateException e) {
                logger.severe(e.getMessage());
                imageExtractor.createReportFile(zipFile.getName(), "Could not create zip archive.");
                tempZipFile.delete();
                return;
            }

            tempZipFile.renameTo(zipFile);
        } catch (InterruptedException e) { //interruption while waiting for permit
            logger.severe(e.getMessage());
        } finally {
            //release permit
            packerSemaphore.release();
        }
    }

    private static File createStatusFile(File archiveFile, String suffix) {
        return archiveFile.toPath().getParent().resolve(archiveFile.getName() + suffix).toFile();
    }

    public static void execute(ImageExtractor imageExtractor, File zipFile, String uuidListStr, String format, Integer fromPage, Integer toPage) {
        new Thread(new Packer(imageExtractor, zipFile, uuidListStr, format, fromPage, toPage)).start();
    }
}
