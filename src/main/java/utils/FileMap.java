package utils;

public class FileMap {
    private String nom;
    private String extension;
    private byte[] bytes;

    public FileMap(String fileName, byte[] bytes) {
        if (fileName == null) {
            fileName = "";
        }
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }

        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            this.nom = fileName.substring(0, dot);
            this.extension = fileName.substring(dot + 1);
        } else {
            this.nom = fileName;
            this.extension = "";
        }
        this.bytes = bytes;
    }

    public String getNom() { return nom; }
    public String getExtension() { return extension; }
    public byte[] getBytes() { return bytes; }
}