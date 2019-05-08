package cz.mzk.holly.extractor;

import cz.mzk.holly.DocumentUtils;
import cz.mzk.holly.FileUtils;
import cz.mzk.holly.fedora.FedoraRESTConnector;
import cz.mzk.holly.model.Batch;
import cz.mzk.holly.model.TreeNode;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ImageExtractor {
    // ToDo: Solve boilerplate code.
    private static final Logger logger = Logger.getLogger(ImageExtractor.class.getName());

    private static final boolean DEBUG = false;

    private static final String STATUS_SUFFIX = "_e";
    public static final String PACKING_SUFFIX = "_p";
    public static final String SEARCH_SUFFIX = "_s";
    public static final String WAITING_SUFFIX = "_w";

    public static final int PAGE_LIMIT = 2000;

    private final String BASE_PATH_MZK;
    private final String BASE_PATH_NDK;
    private final Path PACK_PATH;

    private final FedoraRESTConnector fedora = new FedoraRESTConnector();

    private final AtomicInteger pageCounter = new AtomicInteger(0);

    public ImageExtractor() {
        if (DEBUG) {
            logger.severe("Running in DEBUG!");

            BASE_PATH_MZK = "test";
            BASE_PATH_NDK = "test";

            PACK_PATH = new File("out").toPath();

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

    /**
     * Lists all batches present in PACK_PATH with their status
     *
     * @return batchlist in PACK_PATH
     */
    public List<Batch> listBatches() {
        var files = PACK_PATH.toFile().listFiles();
        var batches = new LinkedList<Batch>();

        if (files == null) {
            throw new IllegalArgumentException("File listing returned null! Check PACK_PATH variable.");
        }

        for (File f : files) {
            if (!f.isFile() || !f.getName().contains(".zip")) {
                continue;
            }

            if (f.getName().toLowerCase().endsWith(".zip")) {
                batches.add(new Batch(f.getName(), "ok", FileUtils.humanReadableByteCount(f.length(), true)));
            } else if (f.getName().toLowerCase().endsWith(".zip" + PACKING_SUFFIX)) {
                batches.add(new Batch(f.getName(), "packing", FileUtils.humanReadableByteCount(f.length(), true)));
            } else if (f.getName().toLowerCase().endsWith(".zip" + SEARCH_SUFFIX)) {
                batches.add(new Batch(f.getName(), "searching", "-"));
            } else if (f.getName().toLowerCase().endsWith(".zip" + WAITING_SUFFIX)) {
                batches.add(new Batch(f.getName(), "waiting", "-"));
            } else {
                String status;

                try {
                    var lines = Files.readAllLines(f.toPath());

                    status = lines.size() != 0 ? Files.readAllLines(f.toPath()).get(0) : "nok";
                } catch (IOException e) {
                    logger.severe("Could not read status file. Reason: " + e.getMessage());
                    status = "unknown";
                }

                batches.add(new Batch(f.getName(), status, "-"));
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
    private String getImagePath(String uuid) {
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
     * Processes supplied tree object, filling its information about structure and pages.
     *
     * Tree is not being processed if pageCounter is over PAGE_LIMIT
     *
     * @param tree tree to be processed
     * @param cfg processing configuration
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public void processTree(TreeNode tree, Packer.Config cfg) throws IOException, ParserConfigurationException, SAXException {

        if (pageCounter.get() > PAGE_LIMIT) {
            logger.info("Skipping tree processing. Passed page limit.");
            return;
        }

        var model = getFedoraRDFResourceFromRels(tree.getName(), "fedora-model:hasModel");

        if (model.size() != 1) {
            model = getFedoraRDFResourceFromRels(tree.getName(), "hasModel");

            if (model.size() != 1) {
                throw new IllegalStateException("Could not load model from RELS-EXT for uuid: " + tree.getName());
            }
        }

        List<String> pageUuids = null;

        switch (model.get(0)) {
            //for hierarchical models process their structure
            case "model:periodical":
                var volumeUuids = getUUIDsFromRelsExt(tree, "kramerius:hasVolume");

                //process years
                rangeProcessTree(tree, cfg, volumeUuids, "date",
                        cfg.getFromYear() == null ? null : cfg.getFromYear().toString(),
                        cfg.getToYear() == null ? null : cfg.getToYear().toString());

                break;
            case "model:periodicalvolume":
                var itemUuids = getUUIDsFromRelsExt(tree, "kramerius:hasItem");

                //process issues
                rangeProcessTree(tree, cfg, itemUuids, "number", cfg.getFromIssue(), cfg.getToIssue());

                break;
            //for page models get list of pages
            //TODO: add all models that can contain relation "hasPage"
            case "model:monograph":
            case "model:map":
            case "model:periodicalitem":
                pageUuids = getUUIDsFromRelsExt(tree, "kramerius:hasPage");

                //filter range
                if (cfg.getFromPage() != null || cfg.getToPage() != null) {
                    pageUuids = pageUuids.subList(
                            cfg.getFromPage() != null ? cfg.getFromPage() - 1 : 0,
                            cfg.getToPage() != null ? cfg.getToPage() : 0);
                }

                //TODO: process attachments

                break;
            case "model:page":
                pageUuids = Collections.singletonList(tree.getName());
                break;
            default:
                //recursive loading is unsafe - f.e.: export entire periodical
                //JK: recursive search is safe, blocking should be done when pages are countable, e.g. 1000 page limit
                System.err.println("Supplied UUID does not contain pages and recursive search is not allowed");

                return;
        }

        //get page paths and store them to tree
        if (pageUuids != null) {
            for (String page : pageUuids) {
                String path = getImagePath(page);
                if (page != null && !path.isEmpty()) {
                    //page found, add to tree increment pageCounter
                    tree.addPagePath(path);
                    pageCounter.addAndGet(1);
                }
            }
        }
    }

    /**
     * Processes list of provided uuids and if provided checks their mods value for specified element tag value within specified range
     *
     * @param tree parent tree node
     * @param cfg packer configuration
     * @param itemUuids list of uuids to be processed
     * @param elementName name of mods element to be checked
     * @param startValue starting value of range
     * @param endValue ending value of range
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    private void rangeProcessTree(
            TreeNode tree,
            Packer.Config cfg,
            List<String> itemUuids,
            String elementName,
            String startValue,
            String endValue
    ) throws IOException, ParserConfigurationException, SAXException {
        boolean reachedStartingVolume = false;

        //process subitems
        for (String itemUuid : itemUuids) {

            String elementValue = null;

            //check requested range start if range is specified
            if ((startValue != null && !startValue.equals("")) || (endValue != null && !startValue.equals(""))) {
                elementValue = fedora.getModsFirstElement(itemUuid, elementName);
            }

            if (startValue != null && !startValue.equals("") && !reachedStartingVolume) {
                if (elementValue.equals(startValue)) {
                    //signal to process every following subtree because we reached requested range
                    reachedStartingVolume = true;
                } else {
                    //skip processing subtree, since it is before requested range
                    continue;
                }
            }

            var subTree = tree.createSubTree(itemUuid);
            processTree(subTree, cfg);

            //check requested range end
            if (endValue != null && !endValue.equals("") && elementValue.equals(endValue)) {
                //reached last issue, stop processing next issue
                break;
            }
        }
    }

    private List<String> getUUIDsFromRelsExt(TreeNode tree, String elementTag) throws IOException, ParserConfigurationException, SAXException {
        var pageUuids = getFedoraRDFResourceFromRels(tree.getName(), elementTag);

        //attempt to load resources if prefix is not present
        if (pageUuids.isEmpty() && elementTag.contains(":")) {
            pageUuids = getFedoraRDFResourceFromRels(tree.getName(), elementTag.substring(elementTag.indexOf(":") + 1));
        }
        return pageUuids;
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

    public boolean hasUuidPrefix(String uuid) {
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
        batch(new Packer.Config(name, uuidListStr, format, fromPage, toPage, null, null, null, null));
    }

    public void batchPeriodical(String batchName, String uuid, Integer fromYear, Integer toYear, String fromIssue, String toIssue, String format) {
        if (!hasUuidPrefix(uuid) || uuid.contains("/n")) {
            throw new IllegalArgumentException("Provide single valid uuid.");
        }

        //TODO: if provided uuid is periodical internal item, automatically search for root uuid and use that instead for batch process

        batch(new Packer.Config(batchName, uuid, format, null, null, fromYear, toYear, fromIssue, toIssue));
    }

    private void batch(Packer.Config cfg) {
        if (cfg.getUuidListStr() == null || cfg.getUuidListStr().isEmpty()) {
            return;
        }

        var zipFile = PACK_PATH.resolve(cfg.getName() + (cfg.getName().toLowerCase().endsWith(".zip") ? "" : ".zip")).toFile();

        if (batchExists(zipFile)) {
            throw new IllegalArgumentException("File: " + cfg.getName() + " already exists");
        }

        Packer.execute(this, zipFile, cfg);
    }

    /**
     * Checks whether batch with specified name exists within any state of processing
     *
     * @param zipFile batch zip file
     * @return true if batch exists in any state
     */
    private boolean batchExists(File zipFile) {

        var parentPath = zipFile.toPath().getParent();
        var fileList = new LinkedList<File>();

        Collections.addAll(fileList,
                parentPath.resolve(zipFile.getName() + STATUS_SUFFIX).toFile(),
                parentPath.resolve(zipFile.getName() + PACKING_SUFFIX).toFile(),
                parentPath.resolve(zipFile.getName() + SEARCH_SUFFIX).toFile(),
                parentPath.resolve(zipFile.getName() + WAITING_SUFFIX).toFile());

        return fileList.stream().anyMatch(File::exists);
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

    public void createReportFile(String name, String msg) {
        var file = PACK_PATH.resolve(name + STATUS_SUFFIX).toFile();
        try {
            try (var writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.append(msg + "\n");
            }
        } catch (IOException e) {
            logger.severe("Could not write error report file.");
        }
    }

    public int getPageCounterValue() {
        return pageCounter.get();
    }
}
