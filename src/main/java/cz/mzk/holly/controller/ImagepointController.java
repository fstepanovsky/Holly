package cz.mzk.holly.controller;

import cz.mzk.holly.FileUtils;
import cz.mzk.holly.extractor.ImageExtractor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

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
    public RedirectView downloadSingle(
            @RequestParam(name = "uuid") String uuid,
            @RequestParam(name = "from", required = false) Integer fromPage,
            @RequestParam(name = "to", required = false) Integer toPage,
            @RequestParam(name = "batchName") String batchName,
            @RequestParam(name = "format", defaultValue = "jp2") String format,
            HttpServletRequest request) {

        if (!format.isEmpty() && !ACCEPTED_FORMATS_SET.contains(format)) {
            return null;
        }

        var ie = new ImageExtractor();

        ie.batch(batchName, uuid, format, fromPage, toPage);

        return new RedirectView("batchList");
    }

    @GetMapping("/batch")
    public String batchGet(Model model) {
        return "batch/form";
    }

    @PostMapping("/batch")
    public RedirectView pack(
            @RequestParam(name = "uuidList") String uuidList,
            @RequestParam(name = "batchName") String batchName,
            @RequestParam(name = "format", defaultValue = "jp2") String format,
            Model model) {
        if (!ACCEPTED_FORMATS_SET.contains(format)) {
            return null;
        }

        var ie = new ImageExtractor();

        ie.batch(batchName, uuidList, format);

        return new RedirectView("batchList");
    }

    @GetMapping("/batchList")
    public String batchList(Model model) {
        model.addAttribute("batchList", new ImageExtractor().listBatches());
        return "batch/list";
    }

    @PostMapping("/batchList")
    public ResponseEntity<Resource> downloadBatch(
            @RequestParam(name = "name") String name,
            HttpServletRequest request
    ) throws MalformedURLException {
        if (name == null || name.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        var zipFile = new ImageExtractor().getBatchFile(name);

        if (zipFile == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return prepareFileResponse(request, zipFile);
    }

    @PostMapping("/delete")
    public RedirectView deleteBatch(
            @RequestParam(name = "name") String name
    ) {
        new ImageExtractor().deleteBatchFile(name);

        return new RedirectView("batchList");
    }

    @GetMapping("/demo")
    public ResponseEntity<Resource> demo(HttpServletRequest request) throws IOException {

        File test = File.createTempFile("text", ".txt");
        FileWriter fw = new FileWriter(test);
        fw.write("lorem ipsum");
        fw.close();

        File out = FileUtils.createZipArchive(new String[] {test.getAbsolutePath()});

        return prepareFileResponse(request, out);
    }

    private ResponseEntity<Resource> prepareFileResponse(HttpServletRequest request, File responseFile) throws MalformedURLException {
        Resource resource = new UrlResource(responseFile.toURI());

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
