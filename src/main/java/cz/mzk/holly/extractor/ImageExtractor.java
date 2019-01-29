package cz.mzk.holly.extractor;

import cz.mzk.holly.fedora.FedoraRESTConnector;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ImageExtractor {
    // ToDo: Solve boilerplate code.

    private static final String mzkBasePath = System.getenv("BASE_PATH_MZK");
    private static final String ndkBasePath = System.getenv("BASE_PATH_NDK");

    private final FedoraRESTConnector fedora = new FedoraRESTConnector();

    public ImageExtractor() {

    }

    public String getImagePath(String uuid) {
        String imageUrl;
        try {
            if (hasUuidPrefix(uuid))
                imageUrl = fedora.getImgAddressFromRels(uuid);
            else
                return "";

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return getPhysicalPath(imageUrl);
    }

    public List<String> getPagesUuids(String uuid, Integer from, Integer to) throws IOException, ParserConfigurationException, SAXException {
        if (!hasUuidPrefix(uuid)) {
            throw new IllegalArgumentException("Invalid UUID: " + uuid == null ? "null" : uuid);
        }

        List<String> model = getFedoraRDFResourceFromRels(uuid, "fedora-model:hasModel");

        if (model.size() != 1) {
            throw new IllegalStateException("Could not load model from RELS-EXT for uuid: " + uuid);
        }

        switch (model.get(0)) {
            //TODO: add all models that can contain relation "hasPage"
            case "model:monograph":
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

                return pageUuids;
            default:
                if (from != null || to != null) {
                    System.err.println("Defined range for document without pages, ignoring range");
                }

                //TODO: process children recursively
                
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
            physicalPath = Paths.get(ndkBasePath, physicalPath).toString();
        } else {
            physicalPath = Paths.get(mzkBasePath, path).toString();
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

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xml);

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
}
