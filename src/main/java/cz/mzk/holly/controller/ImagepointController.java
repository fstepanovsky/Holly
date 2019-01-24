package cz.mzk.holly.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Jakub Kremlacek
 */
@RestController
public class ImagepointController {

    @RequestMapping (method = RequestMethod.GET)
    @ResponseBody
    public String getHome() {
        return "HelloWorld";
    }
}
