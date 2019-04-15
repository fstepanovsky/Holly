package cz.mzk.holly.extractor;

import cz.mzk.holly.DocumentUtils;
import cz.mzk.holly.FileUtils;
import cz.mzk.holly.fedora.FedoraRESTConnector;
import cz.mzk.holly.model.Batch;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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

    public static List<Batch> listBatches() {
        Batch b = new Batch("testName", "testStatus");

        return Collections.singletonList(b);
    }

    public String getImagePath(String uuid) {
        String imageUrl;
        try {
            if (hasUuidPrefix(uuid))
                //cannot check existance
                imageUrl = fedora.getImgAddressFromRels(uuid, false);
            else
                return "";

        } catch (IOException e) {
            e.printStackTrace();
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
        List<String> pages = new ArrayList<>();
        try {
            pages = getPagesUuids(uuid, fromPage, toPage);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            logger.severe(e.getMessage());
            return null;
        }

        if (pages.isEmpty()) {
            return Collections.<String>emptyList();
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
            throw new IllegalStateException("Could not load model from RELS-EXT for uuid: " + uuid);
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
        if (uuidListStr == null || uuidListStr.isEmpty()) {
            return;
        }

        File zipFile = PACK_PATH.resolve(name + (name.toLowerCase().endsWith(".zip") ? "" : ".zip")).toFile();

        if (zipFile.exists()) {
            throw new IllegalArgumentException("File: " + name + " already exists");
        }

        new Thread(new Packer(zipFile, uuidListStr, format)).run();
    }

    class Packer implements Runnable {
        private File zipFile;
        private String uuidListStr;
        private String format;

        public Packer(File zipFile, String uuidListStr, String format) {
            this.zipFile = zipFile;
            this.uuidListStr = uuidListStr;
            this.format = format;
        }

        @Override
        public void run() {
            var es = Executors.newFixedThreadPool(4);
            var map = new ConcurrentHashMap<String, List<String>>();

            try {
                if (uuidListStr == null || uuidListStr.isEmpty()) {
                    logger.info("No uuid set in the list");
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
                        throw new IllegalArgumentException("Invalid uuid: " + uuid);
                    }

                    es.submit(new TitleProcessor(uuid, map));
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
            }

            //map ready
            try {
                FileUtils.createZipArchive(zipFile, map);
            } catch (IOException e) {
                logger.severe(e.getMessage());
                return;
            }
        }
    }

    class TitleProcessor implements Runnable {
        private String uuid;
        private Map<String, List<String>> map;

        public TitleProcessor(String uuid, Map<String, List<String>> map) {
            this.uuid = uuid;
            this.map = map;
        }

        @Override
        public void run() {
            var imagePaths = getImagePaths(uuid, null, null);

            map.put(uuid, imagePaths);
        }
    }
}
