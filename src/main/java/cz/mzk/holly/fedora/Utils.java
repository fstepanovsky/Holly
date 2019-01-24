package cz.mzk.holly.fedora;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Jakub Kremlacek
 */
public class Utils {

    public static final int RETRY_COUNT = 3;
    public static final String[] ALEPH_BASES = {"mzk01", "mzk03"};

    public static synchronized void writeToFile(File f, String str, boolean append) throws IOException {
        FileWriter writer = new FileWriter(f, append);
        writer.write(str + "\n");
        writer.close();
    }

    public static String readFile(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    public static Document getDocumentFromURL(String url) throws IOException, SAXException, ParserConfigurationException {
        if (url == null) return null;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();

        return db.parse(new URL(url).openStream());
    }

    /**
     * request for MARC record from Aleph XServer with retrieval of Sysno and Base
     *
     * @param identifierValue
     * @param parentUUID
     * @param identifier
     * @return
     */
    public static Map.Entry<String, String> getSysnoWithBaseFromAleph(String identifierValue, String parentUUID, Identifier identifier) throws IOException {
        Document doc;
        String sysno;
        String base = null;

        if (identifierValue == null) return null;

        if (identifierValue.contains(" ")) {
            identifierValue  = identifierValue.replaceAll(" ", "%20");
        }

        doc = getResponseFromAleph(identifierValue, RETRY_COUNT, parentUUID, identifier);

        String set_number = doc.getElementsByTagName("set_number").item(0).getTextContent();
        String no_entries = doc.getElementsByTagName("no_entries").item(0).getTextContent();

        int counter = 0;

        do {
            try {
                doc = getDocumentFromURL("http://aleph.mzk.cz/X?op=present&set_no=" + set_number + "&set_entry=" + no_entries + "&format=marc");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            counter++;
        } while (
                counter < RETRY_COUNT &&
                        doc.getElementsByTagName("doc_number").getLength() < 0
                );

        if (counter == RETRY_COUNT) throw new IOException("Could not get sysno from Aleph.");

        sysno = doc.getElementsByTagName("doc_number").item(0).getTextContent();

        NodeList field = doc.getElementsByTagName("subfield");

        for (int i = 0; i < field.getLength(); i++) {
            String label = ((Element) field.item(i)).getAttribute("label");

            if (label.equals("l")) {
                base = field.item(i).getTextContent();
            }
        }

        if (sysno == null || !base.startsWith("MZK0")) return null;

        return new AbstractMap.SimpleEntry<>(sysno, base);
    }

    /**
     * Loads signature from foxml stored in directory param (root is defined by having same uuid in name as directory)
     *
     * @param file to read
     * @return export signature
     */
    public static String getSignatureFromRootObject(File file) {

        Document doc;

        try {
            doc = getDocumentFromFile(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load document from file " + file.getName());
        }

        NodeList msl = doc.getElementsByTagName("mods:shelfLocator");

        if (msl.getLength() < 1) {
            //System.err.println("Signature not found within parent (" + file.getName() + ") record");
            return null;
        }

        return msl.item(0).getTextContent();
    }

    /**
     * Loads signature from foxml stored in directory param (root is defined by having same uuid in name as directory)
     *
     * @param file to read
     * @return export signature
     */
    public static String getISSNFromRootObject(File file) {

        Document doc;

        try {
            doc = getDocumentFromFile(file);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load document from file " + file.getName());
        }

        NodeList msl = doc.getElementsByTagName("mods:identifier");

        if (msl.getLength() < 1) {
            System.err.println("identifiers not found within parent (" + file.getName() + ") record");
            return null;
        }

        for ( int i = 0; i < msl.getLength(); i++) {
            if (((Element) msl.item(i)).getAttribute("type").equals("issn")) {
                return msl.item(i).getTextContent();
            }
        }

        System.err.println("ISSN not found within parent (" + file.getName() + ") record");

        if (doc.getElementsByTagName("mods:title").item(0).getTextContent().equals("Estetika")) {
            return "Estetika";
        }

        return null;
    }

    /**
     * Loads DOM object from File
     *
     * @param file xml file to be loaded
     * @return DOM xml document of file
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public static Document getDocumentFromFile(File file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        return dBuilder.parse(file);
    }

    private static Document getResponseFromAleph(String signature, int retryCount, String parentUUID, Identifier id) throws IOException {
        Document doc;

        for (String alephBase : ALEPH_BASES) {
            doc = getResponseFromAleph(alephBase, signature, retryCount, id);

            if (doc != null) return doc;
        }

        return null;

        //throw new IOException("Could not get record with uuid: " + parentUUID + " and " + id.value + ": " + signature + " from Aleph");
    }

    private static Document getResponseFromAleph(String base, String signature, int retryCount, Identifier id) {
        int counter = 0;
        Document doc;

        do {
            try {
                doc = getDocumentFromURL("http://aleph.mzk.cz/X?base=" + base + "&op=find&request="+id.getValue()+"=" + signature);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            counter++;
        } while (
                counter < retryCount &&
                        doc.getElementsByTagName("set_number").getLength() < 1 &&
                        doc.getElementsByTagName("no_entries").getLength() < 1
                );

        if (counter == retryCount) return null;

        return doc;
    }

    public static String getFullDSLoc(FedoraRESTConnector frc, String uuid) throws IOException {
        String dsLoc;
        String fullInfo = frc.loadFullInfo(uuid);

        String dsLocTag = "<td align=\"right\"><strong>Datastream Location: </strong></td>";
        String leftTag = "<td align=\"left\">";
        int start = fullInfo.indexOf(dsLocTag);

        if (start == -1) {
            throw new IllegalStateException("DS Location element not found.");
        }

        start += dsLocTag.length();

        int end = fullInfo.indexOf("</td>", start);

        dsLoc = fullInfo.substring(start, end); //contains starting td
        dsLoc = dsLoc.substring(dsLoc.indexOf(leftTag) + leftTag.length());
        return dsLoc;
    }
}
