package utils;

public class FileMap {
    private String nom;        // "photo"
    private String extension;  // "jpeg"
    private byte[] bytes;      // contenu du fichier

    public FileMap(String fileName, byte[] bytes) {
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