package cz.mzk.holly.fedora;

import cz.mzk.holly.HTTPUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import org.apache.commons.io.IOUtils;

/**
 * @author Jakub Kremlacek
 */
public class FedoraRESTConnector {

    public enum FedoraDSType {
        FULL("IMG_FULL"), RELS("RELS-EXT"), MODS("BIBLIO_MODS");

        private final String name;

        FedoraDSType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final String FEDORA_ADDRESS = "http://fedora.dk-back.infra.mzk.cz/fedora";
    private static final String FEDORA_DS_FSTRING = "/objects/%s/datastreams/%s";

    private static final String USER  = System.getenv("FEDORA_USER");
    private static final String PW = System.getenv("FEDORA_PASSWORD");

    /**
     * Creates instance with password cookie
     */
    public FedoraRESTConnector() {

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {

                return new PasswordAuthentication(USER, PW.toCharArray());
            }
        });

        CookieHandler.setDefault(new CookieManager());
    }

    /**
     * Receives path on imageserver from given tiles-url in RELS-EXT XML stored in string. System.err message can be disabled with suppressErrorReport
     *
     * @param rels rels-ext xml containing tiles-url
     * @param suppressErrorReport set to true if System.err should be used
     * @return imageserver path or null if not found
     */
    private static String getImageserverAdressFromRelsExt(String rels, boolean suppressErrorReport) {
        int a = rels.indexOf("tiles-url") + 9;
        int i = rels.indexOf(">", a) + 1;
        //no namespace
        int j = rels.indexOf("</tiles-url>");

        //k4 namespace
        if (j == -1) {
            j = rels.indexOf("</kramerius4:tiles-url>");
        }

        //k namespace
        if (j == -1) {
            j = rels.indexOf("</kramerius:tiles-url>");
        }

        if (i > j || i == j || j - i < 27) {
            if (!suppressErrorReport) {
                System.err.println("Tiles url is invalid: " + rels);
            }
            return null;
        }

        return rels.substring(i, j);
    }

    /**
     * Loads image address for specified uuid from its Rels-ext
     *
     * @param uuid document uuid
     * @param existCheck true if exception is wanted to be thrown when image address is within rels-ext does not lead to existing image, e.g. link does not work
     * @return imageserver link to image of specified uuid
     * @throws IOException
     */
    public String getImgAddressFromRels(String uuid, boolean existCheck) throws IOException {
        String rels = loadRELS(uuid);

        String address = getImageserverAdressFromRelsExt(rels, false);

        //check if exists
        if (existCheck && HTTPUtils.getResponseCodeDirect(address) != 200) {
            throw new IllegalArgumentException("Image at address " + address + " does not exist");
        }

        return address;
    }

    /**
     * Loads Rels-ext from Fedora
     *
     * @param uuid uuid of document
     * @return rels-ext xml as string
     * @throws IOException
     */
    public String loadRELS(String uuid) throws IOException {
        return loadDS(FedoraDSType.RELS, uuid);
    }

    /**
     * Loads specified datastream from fedora
     *
     * @param type datastream type
     * @param uuid uuid of document
     * @return xml string value
     * @throws IOException
     */
    private String loadDS(FedoraDSType type, String uuid) throws IOException {
        URL url = new URL(FEDORA_ADDRESS + getFedoraDsString(uuid, type.getName()) + "/content");

        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        String encoding = con.getContentEncoding();
        encoding = encoding == null ? "UTF-8" : encoding;

        return IOUtils.toString(in, encoding);
    }

    private String getFedoraDsString(String uuid, String dsId) {
        return String.format(FEDORA_DS_FSTRING, uuid, dsId);
    }

    /**
     * Retrives mods XML from Fedora
     *
     * @param uuid document uuid
     * @return mods XML in string format
     * @throws IOException
     */
    private String getMods(String uuid) throws IOException {
        return loadDS(FedoraDSType.MODS, uuid);
    }

    /**
     * Retreives first occurence of mods element for specified uuid within mods datastream
     *
     * @param uuid uuid to be scanned
     * @param elementName mods element name without namespace
     * @return value of specified mods lement
     * @throws IOException
     */
    public String getModsFirstElement(String uuid, String elementName) throws IOException {
        var mods = getMods(uuid);

        var elementTagStart = "<" + elementName + ">";
        var elementTagEnd = "</" + elementName + ">";

        var elementTagStartNS = "<mods:" + elementName + ">";
        var elementTagEndNS = "</mods:" + elementName + ">";

        if (!mods.contains(elementTagStart) || !mods.contains(elementTagEnd)) {
            //mods can contain elements with or without NS therefore we have to check both
            elementTagStart = elementTagStartNS;
            elementTagEnd = elementTagEndNS;

            if (!mods.contains(elementTagStart) || !mods.contains(elementTagEnd)) {
                throw new IllegalArgumentException("mods element " + elementName + " is not present in mods of object " + uuid);
            }
        }

        return mods.substring(mods.indexOf(elementTagStart) + elementTagStart.length(), mods.indexOf(elementTagEnd));
    }
}
