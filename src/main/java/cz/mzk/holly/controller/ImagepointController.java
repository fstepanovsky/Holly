package cz.mzk.holly.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author Jakub Kremlacek
 */
@Controller
public class ImagepointController {

    private static final Logger logger = Logger.getLogger(ImagepointController.class.getName());

    public static final String[] ACCEPTED_FORMATS = {"jpg", "jp2"};
    public static final Set<String> ACCEPTED_FORMATS_SET = new HashSet<>(Arrays.asList(ACCEPTED_FORMATS));

    @GetMapping("/")
    public String greeting(Model model) {
        model.addAttribute("uuid", "uuid:");
        return "index";
    }

    @PostMapping("/")
    public ResponseEntity<Resource> download(
            @RequestParam(name="uuid") String uuid,
            @RequestParam(name="from", required = false) Integer fromPage,
            @RequestParam(name="to", required = false) Integer toPage,
            @RequestParam(name="format", defaultValue = "jpg") String format,
            HttpServletRequest request) throws IOException {

        if (!format.isEmpty() && !ACCEPTED_FORMATS_SET.contains(format)) {
            return null;
        }

        File out = File.createTempFile("test", ".txt");

        FileWriter fw = new FileWriter(out);

        fw.write("lorem ipsum");
        fw.close();

        Resource resource = new UrlResource(out.toURI());

        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
