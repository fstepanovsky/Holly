package cz.mzk.holly.extractor;

import cz.mzk.holly.DocumentUtils;
import cz.mzk.holly.FileUtils;
import cz.mzk.holly.fedora.FedoraRESTConnector;
import cz.mzk.holly.model.Batch;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ImageExtractor {
    // ToDo: Solve boilerplate code.
    private static final Logger logger = Logger.getLogger(ImageExtractor.class.getName());

    private static final boolean DEBUG = false;

    private final String BASE_PATH_MZK;
    private final String BASE_PATH_NDK;
    private final Path PACK_PATH;

    private final FedoraRESTConnector fedora = new FedoraRESTConnector();

    public ImageExtractor() {
        if (DEBUG) {
            logger.severe("Running in DEBUG!");

            BASE_PATH_MZK = "test";
            BASE_PATH_NDK = "test";

            PACK_PATH = new File("test").toPath();

            return;
        }

        if (System.getenv("BASE_PATH_MZK") == null) {
            throw new IllegalStateException("System not configured properly, please set BASE_PATH_MZK");
        }

        if (System.getenv("BASE_PATH_NDK") == null) {
            throw new IllegalStateException("System not configured properly, please set BASE_PATH_NDK");
        }

        if (System.getenv("BATCH_PATH") == null) {
            throw new IllegalStateException(("System not configured properly, please set PACK_PATH"));
        }

        BASE_PATH_MZK = System.getenv("BASE_PATH_MZK");
        BASE_PATH_NDK = System.getenv("BASE_PATH_NDK");

        PACK_PATH = new File(System.getenv("BATCH_PATH")).toPath();
    }

    public ImageExtractor(String mzk, String ndk, String packPath) {
        BASE_PATH_MZK = mzk;
        BASE_PATH_NDK = ndk;
        PACK_PATH = new File(packPath).toPath();
    }

    /**
     * Lists all batches present in PACK_PATH with their status
     *
     * @return batchlist in PACK_PATH
     */
    public List<Batch> listBatches() {
        var files = PACK_PATH.toFile().listFiles();
        var batches = new LinkedList<Batch>();

        for (File f : files) {
            if (!f.isFile() || !f.getName().contains(".zip")) {
                continue;
            }

            if (f.getName().endsWith(".zip")) {
                batches.add(new Batch(f.getName(), "ok"));
            } else {
                String status;

                try {
                    status = Files.readAllLines(f.toPath()).get(0);
                } catch (IOException e) {
                    logger.severe("Could not read status file. Reason: " + e.getMessage());
                    status = "unknown";
                }

                batches.add(new Batch(f.getName(), status));
            }
        }

        return batches;
    }

    /**
     * Loads path on imageserver for specified uuid
     *
     * @param uuid uuid of object containing image datastreams
     * @return path on imageserver if object contains an imagelink, null otherwise
     */
    public String getImagePath(String uuid) {
        String imageUrl;
        try {
            if (hasUuidPrefix(uuid))
                //cannot check existance
                imageUrl = fedora.getImgAddressFromRels(uuid, false);
            else
                return "";
        } catch (IOException e) {
            logger.severe("Could not load information from RELS-EXT. Reason: " + e.getMessage());
            return null;
        }

        return getPhysicalPath(imageUrl);
    }

    /**
     * Loads list of paths for a given object
     *
     * @param uuid parent object
     * @return list of paths
     */
    public List<String> getImagePaths(String uuid, Integer fromPage, Integer toPage) {
        List<String> pages;
        try {
            pages = getPagesUuids(uuid, fromPage, toPage);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            logger.severe(e.getMessage());
            return null;
        }

        if (pages.isEmpty()) {
            return Collections.emptyList();
        }
        List<String>paths = new ArrayList<>();
        for (String page : pages) {
            String path = getImagePath(page);
            if (page != null && !path.isEmpty()) {
                paths.add(path);
            }
        }

        return paths;
    }

    public List<String> getPagesUuids(String uuid, Integer from, Integer to) throws IOException, ParserConfigurationException, SAXException {
        if (!hasUuidPrefix(uuid)) {
            throw new IllegalArgumentException("Invalid UUID: " + (uuid == null ? "null" : uuid));
        }

        List<String> model = getFedoraRDFResourceFromRels(uuid, "fedora-model:hasModel");

        if (model.size() != 1) {
            model = getFedoraRDFResourceFromRels(uuid, "hasModel");

            if (model.size() != 1) {
                throw new IllegalStateException("Could not load model from RELS-EXT for uuid: " + uuid);
            }
        }

        switch (model.get(0)) {
            //TODO: add all models that can contain relation "hasPage"
            case "model:monograph":
            case "model:map":
            case "model:periodicalitem":
                List<String> pageUuids = getFedoraRDFResourceFromRels(uuid, "kramerius:hasPage");

                //attempt to load resources if prefix is not present
                if (pageUuids.isEmpty()) {
                    pageUuids = getFedoraRDFResourceFromRels(uuid, "hasPage");
                }

                //filter range
                if (from != null || to != null) {
                    pageUuids = pageUuids.subList(
                            from != null ? from : 0,
                            to != null ? to : 0);
                }

                //TODO: process attachments

                return pageUuids;
            case "model:page":
                return Collections.singletonList(uuid);
            default:
                //recursive loading is unsafe - f.e.: export entire periodical
                //JK: recursive search is safe, blocking should be done when pages are countable, e.g. 1000 page limit
                System.err.println("Supplied UUID does not contain pages and recursive search is not allowed");

                return null;
        }
    }

    private String getPhysicalPath(String imgUrl) {
        String path;
        try {
            path =  new URL(imgUrl).getPath();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "";
        }

        String physicalPath = "";

        if (path.contains("/NDK"))
        {
            var pathComponents = path.split("/");
            switch (pathComponents[2]) {
                case "2012": case "2013":
                    physicalPath = path.replace("NDK", "ndk01");
                    break;
                case "2014":
                    physicalPath = path.replace("NDK", "ndk02");
                    break;
                case "2015": case "2016":
                    physicalPath = path.replace("NDK", "ndk03");
                    break;
                case "2017": case "2018":
                    physicalPath = path.replace("NDK", "ndk04");
                    break;
                case "2019":
                    physicalPath = path.replace("NDK", "ndk2019");
                    break;
            }
            physicalPath = Paths.get(BASE_PATH_NDK, physicalPath).toString();
        } else {
            physicalPath = Paths.get(BASE_PATH_MZK, path).toString();
        }

        if (path.contains(".tif"))
            return physicalPath;
        else
            return physicalPath + ".jp2";
    }

    private boolean hasUuidPrefix(String uuid) {
        if (uuid == null)
            return false;

        return uuid.startsWith("uuid:");
    }

    private static String getXMLElementText(String xml, String elementTag) {
        return xml.substring(
                xml.indexOf("<" + elementTag + ">") + elementTag.length() + 2,
                xml.indexOf("</" + elementTag + ">") );
    }

    private List<String> getFedoraRDFResourceFromRels(String uuid, String elementTag) throws IOException, ParserConfigurationException, SAXException {
        String xml = fedora.loadRELS(uuid);

        var doc = DocumentUtils.loadDocumentFromString(xml);

        NodeList elements = doc.getElementsByTagName(elementTag);

        List<String> results = new LinkedList<>();

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String value = element.getAttribute("rdf:resource");

            String result = value.substring("info:fedora/".length());

            results.add(result);
        }

        return results;
    }

    public void batch(String name, String uuidListStr, String format) {
        batch(name, uuidListStr, format, null, null);
    }

    public void batch(String name, String uuidListStr, String format, Integer fromPage, Integer toPage) {
        if (uuidListStr == null || uuidListStr.isEmpty()) {
            return;
        }

        //TODO .txt files with prefix

        File zipFile = PACK_PATH.resolve(name + (name.toLowerCase().endsWith(".zip") ? "" : ".zip")).toFile();

        if (zipFile.exists()) {
            throw new IllegalArgumentException("File: " + name + " already exists");
        }

        new Thread(new Packer(zipFile, uuidListStr, format, fromPage, toPage)).start();
    }

    /**
     * Acquires batch file from storage if exists otherwise returns null.
     *
     * @param name name of the batch file
     * @return file object of the specified file name, otherwise null
     */
    public File getBatchFile(String name) {
        var batchFile = PACK_PATH.resolve(name).toFile();

        if (!batchFile.exists()) {
            return null;
        } else {
            return batchFile;
        }
    }

    /**
     * Removes batch file with specified name
     *
     * @param name name of the batch file to be removed
     */
    public void deleteBatchFile(String name) {
        //safecheck
        name = name.replaceAll("/", "");

        var batchFile = PACK_PATH.resolve(name).toFile();

        if (!batchFile.exists()) {
            logger.info("File: " + name + " does not exist.");
            return;
        }

        batchFile.delete();
    }

    private void createReportFile(String name, String msg) {
        var file = PACK_PATH.resolve(name + ".txt").toFile();
        FileWriter fw = null;
        try {
            try (var writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.append(msg + "\n");
            }
        } catch (IOException e) {
            logger.severe("Could not write error report file.");
        }
    }

    class Packer implements Runnable {
        private File zipFile;
        private String uuidListStr;
        private Integer fromPage;
        private Integer toPage;
        private String format;

        public Packer(File zipFile, String uuidListStr, String format, Integer fromPage, Integer toPage) {
            this.zipFile = zipFile;
            this.uuidListStr = uuidListStr;
            this.format = format;
            this.fromPage = fromPage;
            this.toPage = toPage;
        }

        @Override
        public void run() {
            var es = Executors.newFixedThreadPool(4);
            var map = new ConcurrentHashMap<String, List<String>>();

            try {
                if (uuidListStr == null || uuidListStr.isEmpty()) {
                    logger.info("No uuid set in the list");
                    createReportFile(zipFile.getName(), "No uuid set in the list.");
                    return;
                }

                if (!uuidListStr.contains("\n")) {
                    logger.info("List does not contain single EOL sign");
                }

                String[] uuids = uuidListStr.split("\n");

                for (String uuid : uuids) {
                    //strip whitespaces
                    uuid = uuid.replaceAll("\\s+","");

                    if (!hasUuidPrefix(uuid)) {
                        createReportFile(zipFile.getName(), "Invalid uuid requested.");
                        logger.warning("Invalid uuid: " + uuid);
                        return;
                    }

                    es.submit(new TitleProcessor(uuid, map, fromPage, toPage));
                }
            } finally {
                es.shutdown();
            }

            try {
                es.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.severe(e.getMessage());
                return;
            }

            if (map.isEmpty()) {
                logger.warning("No images found.");
                createReportFile(zipFile.getName(), "No images found.");
                return;
            }

            //map ready
            try {
                FileUtils.createZipArchive(zipFile, map);
            } catch (IOException e) {
                logger.severe(e.getMessage());
                createReportFile(zipFile.getName(), "Could not create zip archive.");
                return;
            }
        }
    }

    class TitleProcessor implements Runnable {
        private String uuid;
        private Map<String, List<String>> map;
        private Integer fromPage;
        private Integer toPage;

        public TitleProcessor(String uuid, Map<String, List<String>> map, Integer fromPage, Integer toPage) {
            this.uuid = uuid;
            this.map = map;
            this.fromPage = fromPage;
            this.toPage = toPage;
        }

        @Override
        public void run() {
            var imagePaths = getImagePaths(uuid, fromPage, toPage);

            map.put(uuid, imagePaths);
        }
    }
}
