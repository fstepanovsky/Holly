package cz.mzk.holly.extractor;

import cz.mzk.holly.model.TreeNode;
import java.util.logging.Logger;

/**
 * @author kremlacek
 */
class TitleProcessor implements Runnable {
    private static final Logger logger = Logger.getLogger(TitleProcessor.class.getName());

    private final ImageExtractor imageExtractor;
    private final String uuid;
    private final TreeNode root;
    private final Packer.Config cfg;

    public TitleProcessor(ImageExtractor imageExtractor, String uuid, TreeNode root, Packer.Config cfg) {
        this.imageExtractor = imageExtractor;
        this.uuid = uuid;
        this.root = root;
        this.cfg = cfg;
    }

    @Override
    public void run() {
        if (!imageExtractor.hasUuidPrefix(uuid)) {
            throw new IllegalArgumentException("Invalid UUID: " + (uuid == null ? "null" : uuid));
        }

        var subTree = root.createSubTree(uuid);

        try {
            imageExtractor.processTree(subTree, cfg);
        } catch (Exception e) {
            logger.severe("Processing tree: " + subTree.getName() + " failed. Reason: " + e.getMessage());
        }
    }
}
