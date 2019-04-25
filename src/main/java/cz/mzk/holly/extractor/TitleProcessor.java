package cz.mzk.holly.extractor;

import cz.mzk.holly.model.TreeNode;
import java.io.IOException;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * @author kremlacek
 */
class TitleProcessor implements Runnable {
    private static final Logger logger = Logger.getLogger(TitleProcessor.class.getName());

    private ImageExtractor imageExtractor;
    private String uuid;
    private TreeNode root;
    private Integer fromPage;
    private Integer toPage;

    public TitleProcessor(ImageExtractor imageExtractor, String uuid, TreeNode root, Integer fromPage, Integer toPage) {
        this.imageExtractor = imageExtractor;
        this.uuid = uuid;
        this.root = root;
        this.fromPage = fromPage;
        this.toPage = toPage;
    }

    @Override
    public void run() {
        if (!imageExtractor.hasUuidPrefix(uuid)) {
            throw new IllegalArgumentException("Invalid UUID: " + (uuid == null ? "null" : uuid));
        }

        var subTree = root.createSubTree(uuid);

        try {
            imageExtractor.processTree(subTree, fromPage, toPage);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            logger.severe("Processing tree: " + subTree.getName() + " failed. Reason: " + e.getMessage());
        }
    }
}
