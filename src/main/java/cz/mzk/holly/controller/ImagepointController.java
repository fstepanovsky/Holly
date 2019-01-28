package cz.mzk.holly.controller;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

    public static final String[] ACCEPTED_FORMATS = {"jpg", "jp2"};
    public static final Set<String> ACCEPTED_FORMATS_SET = new HashSet<>(Arrays.asList(ACCEPTED_FORMATS));


    @GetMapping("/")
    public String greeting(Model model) {
        model.addAttribute("uuid", "uuid:");
        return "index";
    }

    @PostMapping("/")
    public String download(
            @RequestParam(name="uuid") String uuid,
            @RequestParam(name="from", required = false) Integer fromPage,
            @RequestParam(name="to", required = false) Integer toPage,
            @RequestParam(name="format", defaultValue = "jpg") String format,
            Model model) {

        if (!format.isEmpty() && !ACCEPTED_FORMATS_SET.contains(format)) {
            return "400";
        }

        model.addAttribute("uuid", uuid);
        model.addAttribute("from", fromPage);
        model.addAttribute("to", toPage);
        return "index";
    }
}
