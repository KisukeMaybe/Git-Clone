import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

public class GitUtils {

    // Initialize the .git directory structure
    public static void initGitDirectory() {
        try {
            new File(".git").mkdirs();
            new File(".git/objects").mkdirs();
            new File(".git/refs").mkdirs();
            new File(".git/HEAD").createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(".git/HEAD"))) {
                writer.write("ref: refs/heads/main\n");
            }
        } catch (IOException e) {
            System.out.println("Error initializing git directory: " + e.getMessage());
        }
    }

    // Store objects in the .git/objects directory
    public static void storeObjects(byte[] packfileData, String dir) throws IOException {
        // Write the unpacked objects into the appropriate .git/objects directory
        File objectsDir = new File(".git/objects");
        if (!objectsDir.exists()) {
            objectsDir.mkdirs();
        }

        String objectDir = ".git/objects/" + "abc"; // Generate unique object directory name
        File objectFile = new File(objectDir);

        try (FileOutputStream out = new FileOutputStream(objectFile)) {
            out.write(packfileData);
        }

        System.out.println("Packfile unpacked and stored in .git/objects.");
    }

    // Compress the data using Deflater
    public static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int bytesCompressed = deflater.deflate(buffer);
            outputStream.write(buffer, 0, bytesCompressed);
        }
        return outputStream.toByteArray();
    }

    // Uncompress using InflaterInputStream
    public static byte[] decompress(InputStream inputStream) throws IOException {
        InflaterInputStream inflaterInputStream = new InflaterInputStream(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inflaterInputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }
}
