package cz.mzk.holly.fedora;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Scanner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * @author Jakub Kremlacek
 */
public class FedoraRESTConnector {

    public static final String FULL_SUFFIX = "/big.jpg";
    public static final String THUMB_SUFFIX = "/thumb.jpg";
    public static final String PREVIEW_SUFFIX = "/preview.jpg";

    public enum FedoraDSType {
        FULL("IMG_FULL"), THUMB("IMG_THUMB"), PREVIEW("IMG_PREVIEW"), RELS("RELS-EXT"), MODS("BIBLIO_MODS");

        private final String name;

        FedoraDSType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final String FEDORA_ADDRESS = "http://fedora.dk-back.infra.mzk.cz/fedora";
    public static final String FEDORA_FSTRING = "/objects/%s";
    public static final String FEDORA_DS_FSTRING = "/objects/%s/datastreams/%s";

    //Label             empty
    //Control group     Redirect        R
    //dsState           default         A
    //checksumType      disabled        DISABLED
    //mimeType          image/jpeg      image/jpeg
    public static final String FEDORA_DS_CREATE_FSTRING =
            "?" +
            "controlGroup=R" + "&" +
            "checksumType=DISABLED" + "&" +
            "mimeType=image/jpeg" + "&" +
            "dsLocation=%s";

    public static final String FEDORA_FS_MODIFY_LOCATION_FSTRING =
            "?" +
            "dsLocation=%s";

    public static final String FEDORA_RS_FSTRING =
            "/risearch?type=triples&lang=spo&format=N-Triples&limit=10&query=%s+%s+%%3Cinfo:fedora/%s%%3E";

    private static final String DELETE_PASSWORD = "fedoraAdmin";

    private boolean objectDeleteLocked = true;

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
     * Receives path on imageserver from given tiles-url in RELS-EXT XML stored in string.
     *
     * @param rels rels-ext xml containing tiles-url
     * @return imageserver path or null if not found + message in System.err
     */
    public static String getImageserverAdressFromRelsExt(String rels) {
        return getImageserverAdressFromRelsExt(rels, false);
    }

    /**
     * Receives path on imageserver from given tiles-url in RELS-EXT XML stored in string. System.err message can be disabled with suppressErrorReport
     *
     * @param rels rels-ext xml containing tiles-url
     * @param suppressErrorReport set to true if System.err should be used
     * @return imageserver path or null if not found
     */
    public static String getImageserverAdressFromRelsExt(String rels, boolean suppressErrorReport) {
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
     * Loads FULL datastream info from Fedora
     *
     * @param uuid page uuid
     * @return FULL DS info XML in String
     * @throws IOException
     */
    public String loadFullInfo(String uuid) throws IOException {
        return loadDS(FedoraDSType.FULL, uuid, false);
    }

    public String[] getSysnoWBase(String uuid) throws IOException {
        return getSysnoWBase(uuid, false, null);
    }

    public String[] getSysnoWBase(String uuid, boolean wildcardRelation, String childrenYearDir) throws IOException {
        String responseLine = wildcardRelation ? getRiSearchResponseStr("*", "*", uuid) : getRiSearchHasPageResponseStr(uuid);

        if (responseLine.isEmpty()) {
            //System.err.println("Object with uuid: " + uuid + " is orphan and therefore does not have parent with SYSNO");
            return null;
        }

        String[] response = responseLine.split(" ");
        String parentUUID = response[0].substring(response[0].indexOf("/") + 1, response[0].length() - 1);
        File tempContent = File.createTempFile(parentUUID + "_", ".xml");

        String mods = loadDS(FedoraDSType.MODS ,parentUUID, true);

        String yearDir = null;

        if (wildcardRelation && mods.contains("<mods:date>")) {
            int yrBegin = mods.indexOf("<mods:date>") + "<mods:date>".length();
            int yrEnd = mods.indexOf("</mods:date>", yrBegin);
            yearDir = mods.substring(yrBegin, yrEnd);
            yearDir = yearDir.replaceAll(" ", "");
        }

        FileUtils.writeStringToFile(tempContent, mods, Charset.defaultCharset());

        String signature = Utils.getSignatureFromRootObject(tempContent);

        if (signature == null) {
            tempContent.delete();
            return getSysnoWBase(parentUUID, true, yearDir);
        }

        Map.Entry<String, String> sb;

        if (signature.equals("nezjištěna") || signature.startsWith("PE")) {
            String issn = Utils.getISSNFromRootObject(tempContent);

            if (issn == null) {
                throw new IllegalStateException("Could not load ISSN");
            }

            if (issn.equals("Estetika")) {
                return new String[1];
            }

            if (issn.equals("n0001") && parentUUID.equals("uuid:ae81bb20-435d-11dd-b505-00145e5790ea")) {
                sb = new AbstractMap.SimpleEntry<>("000210291", "mzk01");
            } else {
                sb = Utils.getSysnoWithBaseFromAleph(issn, parentUUID, Identifier.ISSN);
            }

        } else {
            sb = Utils.getSysnoWithBaseFromAleph(signature, parentUUID, Identifier.SIGNATURE);
        }

        tempContent.delete();

        if (sb == null) {
            throw new IllegalArgumentException("Could not receive SYSNO for " + uuid);
        }

        String sysno = sb.getKey();

        String[] result = new String[childrenYearDir == null ? 4 : 5];

        result[0] = sb.getValue().toLowerCase();
        result[1] = sysno.substring(0, 3);
        result[2] = sysno.substring(3, 6);
        result[3] = sysno.substring(6, 9);

        if (childrenYearDir != null) {
            result[4] = childrenYearDir;
        }

        return result;
    }

    public String getImgAddressFromRels(String uuid) throws IOException {
        return getImgAddressFromRels(uuid, true);
    }

    public String getImgAddressFromRels(String uuid, boolean existCheck) throws IOException {
        return getImgAddressFromRels(uuid, existCheck, false);
    }

    private String getImgAddressFromRels(String uuid, boolean existCheck, boolean suppressErrorIfMissing) throws IOException {
        String rels = loadRELS(uuid);

        String address = getImageserverAdressFromRelsExt(rels, suppressErrorIfMissing);

        //check if exists
        if (existCheck && getResponseCodeDirect(address) != 200) {
            throw new IllegalArgumentException("Image at address " + address + " does not exist");
        }

        return address;
    }

    private int getResponseCode(String uuid, String requestMethod) throws IOException {
        if (requestMethod.equals("DELETE") && objectDeleteLocked) {
            System.out.println("You are attempting to delete entire Fedora object, this action is ireversible and therefore is validated.");
            System.out.println("Please provide a delete password:");
            System.out.print("> ");

            if (new Scanner(System.in).nextLine().equals(DELETE_PASSWORD)) {
                objectDeleteLocked = false;
            } else {
                System.out.println("Bad password!");
                throw new IllegalStateException("Bad password provided for deletion of object.");
            }
        }

        return getResponseCodeDirect(FEDORA_ADDRESS + getFedoraString(uuid), requestMethod);
    }

    private int getResponseCodeDS(String uuid, FedoraDSType dsId, String params, String requestMethod) throws IOException {
        return getResponseCodeDS( uuid, dsId, params, requestMethod, null);
    }

    private int getResponseCodeDS(String uuid, FedoraDSType dsId, String params, String requestMethod, String requestBody) throws IOException {
        URL url = new URL(FEDORA_ADDRESS + getFedoraDsString(uuid, dsId.getName()) + (params != null ? params : ""));

        //System.out.println(url);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod(requestMethod);

        if (requestBody != null && !requestBody.isEmpty()) {
            connection.setDoOutput(true);

            OutputStream os = connection.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write(requestBody);
            osw.flush();
            osw.close();
        }

        connection.connect();

        int i = connection.getResponseCode();

        connection.disconnect();

        return i;
    }

    private static int getResponseCodeDirect(String urlStr) throws IOException {
        return getResponseCodeDirect(urlStr, "GET");
    }

    private static int getResponseCodeDirect(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);

        System.out.println(urlStr);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod(method);
        connection.connect();

        int i = connection.getResponseCode();

        connection.disconnect();

        return i;
    }

    public String loadRELS(String uuid) throws IOException {
        return loadDS(FedoraDSType.RELS, uuid, true);
    }

    private String loadDS(FedoraDSType type, String uuid, boolean content) throws IOException {
        URL url = new URL(FEDORA_ADDRESS + getFedoraDsString(uuid, type.getName()) + (content ? "/content" : ""));

        //System.out.println(url);

        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        String encoding = con.getContentEncoding();
        encoding = encoding == null ? "UTF-8" : encoding;

        return IOUtils.toString(in, encoding);
    }

    public InputStream loadDSIS(FedoraDSType type, String uuid, boolean content) throws IOException {
        URL url = new URL(FEDORA_ADDRESS + getFedoraDsString(uuid, type.getName()) + (content ? "/content" : ""));

        //System.out.println(url);

        URLConnection con = url.openConnection();
        return con.getInputStream();
    }

    private String getFedoraString(String uuid) {
        return String.format(FEDORA_FSTRING, uuid);
    }

    private String getFedoraDsString(String uuid, String dsId) {
        return String.format(FEDORA_DS_FSTRING, uuid, dsId);
    }

    private String getFedoraDsCreateString(String imageserverPath) {
        return String.format(FEDORA_DS_CREATE_FSTRING, imageserverPath);
    }

    private String getFedoraDsModifyLocationFstring(String location) {
        return String.format(FEDORA_FS_MODIFY_LOCATION_FSTRING, location);
    }

    private String getRisearchFstring(String a, String relation, String b) {
        return String.format(FEDORA_RS_FSTRING, a, relation, b);
    }

    public String getRiSearchHasPageResponseStr(String uuid) throws IOException {
        return getRiSearchResponseStr("*", "%3Chttp%3A%2F%2Fwww.nsdl.org%2Fontologies%2Frelationships%23hasPage%3E", uuid);
    }

    public String getRiSearchResponseStr(String a, String relation, String uuid) throws IOException {
        URL url = new URL(FEDORA_ADDRESS + getRisearchFstring(a, relation, uuid));

        //System.out.println(url);

        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        String encoding = con.getContentEncoding();
        encoding = encoding == null ? "UTF-8" : encoding;

        return IOUtils.toString(in, encoding);
    }

    public String getMods(String uuid) throws IOException {
        return loadDS(FedoraDSType.MODS, uuid, true);
    }

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
