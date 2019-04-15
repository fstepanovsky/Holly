package cz.mzk.holly.model;

import java.io.Serializable;

/**
 * @author kremlacek
 */
public class Batch implements Serializable {
    private String name;
    private String status;

    public Batch(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
