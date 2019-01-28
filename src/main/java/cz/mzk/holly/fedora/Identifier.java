package cz.mzk.holly.fedora;

/**
 * @author Jakub Kremlacek
 */
public enum Identifier {
    ISSN("ssn"), SIGNATURE("sig"), BARCODE("bar");

    String value;

    Identifier(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
