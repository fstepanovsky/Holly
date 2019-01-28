package cz.mzk.holly.extractor;

import cz.mzk.holly.fedora.FedoraRESTConnector;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public class ImageExtractor {
    // ToDo: Solve boilerplate code.

    private static final String mzkBasePath = System.getenv("BASE_PATH_MZK");
    private static final String ndkBasePath = System.getenv("BASE_PATH_NDK");

    public ImageExtractor(String uuid) {

        // ToDo: validate uuid
        // ToDo: mark as valid if valid
    }

    public ImageExtractor(String[] uuid) {
        // ToDo: validate uuids
        // ToDo: mark as valid if valid
    }

    public String getImagePath(String uuid) {
        FedoraRESTConnector fedora = new FedoraRESTConnector();
        String imageUrl;
        try {
            if (hasUuidPrefix(uuid))
                imageUrl = fedora.getImgAddressFromRels(uuid);
            else
                return "";

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return getPhysicalPath(imageUrl);
    }

    private String getPhysicalPath(String imgUrl) {
        String path;
        try {
            path =  new URL(imgUrl).getPath();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "";
        }

        String physicalPath = "";

        if (path.contains("/NDK"))
        {
            var pathComponents = path.split("/");
            switch (pathComponents[2]) {
                case "2012": case "2013":
                    physicalPath = path.replace("NDK", "ndk01");
                    break;
                case "2014":
                    physicalPath = path.replace("NDK", "ndk02");
                    break;
                case "2015": case "2016":
                    physicalPath = path.replace("NDK", "ndk03");
                    break;
                case "2017": case "2018":
                    physicalPath = path.replace("NDK", "ndk04");
                    break;
                case "2019":
                    physicalPath = path.replace("NDK", "ndk2019");
                    break;
            }
            physicalPath = Paths.get(ndkBasePath, physicalPath).toString();
        } else {
            physicalPath = Paths.get(mzkBasePath, path).toString();
        }

        if (path.contains(".tif"))
            return physicalPath;
        else
            return physicalPath + ".jp2";
    }

    private boolean hasUuidPrefix(String uuid) {
        if (uuid == null)
            return false;

        return uuid.startsWith("uuid:");
    }
}
