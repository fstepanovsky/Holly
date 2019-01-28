package cz.mzk.holly.controller;

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

    @GetMapping("/")
    public String greeting(Model model) {
        model.addAttribute("uuid", "uuid:");
        return "index";
    }

    @PostMapping("/")
    public String download(@RequestParam(name="uuid") String uuid, Model model) {
        model.addAttribute("uuid", uuid);
        return "index";
    }
}
