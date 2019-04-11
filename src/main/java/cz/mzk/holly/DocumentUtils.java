package cz.mzk.holly;

import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author kremlacek
 */
public class DocumentUtils {

    /**
     * Loads DOM document from String
     *
     * @param docStr document string
     * @return DOM document
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    public static Document loadDocumentFromString(String docStr) throws ParserConfigurationException, IOException, SAXException {
        var dbf = DocumentBuilderFactory.newInstance();
        var builder = dbf.newDocumentBuilder();
        return builder.parse(docStr);
    }
}
