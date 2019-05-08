import cz.mzk.holly.extractor.ImageExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kremlacek
 */
public class ImageExtractorTest {

    @Test
    public void runSimpleImageSearch() {
        var ie = new ImageExtractor("./tmp/testmzk", "./tmp/testndk", "./tmp/testpack");
        var imagePaths = ie.getImagePaths("uuid:4f359870-e163-4a0f-8e9d-9a5830f7d851", null, null);

        assertEquals(1, imagePaths.size());
    }
}
