package cz.mzk.holly.model;

import java.io.Serializable;

/**
 * @author kremlacek
 */
public class Batch implements Serializable {
    private final String name;
    private final String status;
    private final String fileSize;

    public Batch(String name, String status, String fileSize) {
        this.name = name;
        this.status = status;
        this.fileSize = fileSize;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getFileSize() {
        return fileSize;
    }
}
