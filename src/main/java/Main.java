import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

public class Main {
    public static void main(String[] args)
        throws IOException, NoSuchAlgorithmException {
        // Read the command from the arguments
        final String command = args[0];
        
        // Switch based on the command
        switch (command) {
            // Initialize git directory
            case "init" -> {
                final File root = new File(".git");
                new File(root, "objects").mkdirs(); // Create objects directory
                new File(root, "refs").mkdirs(); // Create refs directory
                final File head = new File(root, "HEAD");
                try {
                    head.createNewFile(); // Create HEAD file
                    Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes()); // Point HEAD to main branch
                    System.out.println("Initialized git directory");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Read an object file (e.g., blob, tree, commit) from the .git/objects directory
            case "cat-file" -> {
                var path = args[2];
                var filePath = "./.git/objects/" + path.substring(0, 2) + "/"  +path.substring(2);
                InputStream in = new InflaterInputStream(new FileInputStream(new File(filePath)));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                shovelInToOut(in, out); // Decompress the object file
                var data = out.toByteArray();
                ByteArrayOutputStream dataClean = new ByteArrayOutputStream();
                // Remove trailing 0x00 byte and print the object data
                for (int i = 0; i < data.length; i++) {
                    if(data[i] != 0x00) {
                        dataClean.write(data[i]);
                    } else {
                        break;
                    }
                }
                System.out.print(new String(dataClean.toByteArray(), StandardCharsets.UTF_8));
            }

            // Hash an object (blob) and write it to the .git/objects directory
            case "hash-object" -> {
                var inputPath = args[2];
                var data = new FileInputStream(new File(inputPath)).readAllBytes();
                writeToFile(data); // Write the object to .git/objects and return the hash
            }

            // List files in a tree object by its SHA hash
            case "ls-tree" -> {
                if (args.length < 3 || !args[1].equals("--name-only")) {
                    System.out.println("Usage: ls-tree --name-only <tree_sha>");
                    return;
                }

                String treeSha = args[2]; // Tree SHA
                String dirHash = treeSha.substring(0, 2);
                String fileHash = treeSha.substring(2);
                File treeFile = new File("./.git/objects/" + dirHash + "/" + fileHash);

                if (!treeFile.exists()) {
                    System.out.println("Error: Tree object not found.");
                    return;
                }

                try (InflaterInputStream in = new InflaterInputStream(new FileInputStream(treeFile))) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len); // Decompress the tree object
                    }

                    byte[] decompressedData = out.toByteArray();
                    parseTreeObject(decompressedData); // Parse and print file names in the tree
                } catch (IOException e) {
                    System.out.println("I/O Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Write a tree object based on the current directory structure
            case "write-tree" -> {
                String rootDir = ".";
                try {
                    String treeSha = writeTree(rootDir); // Write the tree object
                    System.out.println(treeSha);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Commit a tree object and create a commit object
            case "commit-tree" -> {
                if (args.length != 6 || !args[2].equals("-p") || !args[4].equals("-m")) {
                    System.out.println("Usage: commit-tree <tree_sha> -p <commit_sha> -m <message>");
                    return;
                }

                String treeSha = args[1]; // Tree SHA
                String parentCommitSha = args[3]; // Parent commit SHA
                String commitMessage = args[5]; // Commit message

                // Create commit object
                try {
                    // Author and committer information (hardcoded)
                    String authorName = "Author Name <author@example.com>";
                    String committerName = "Committer Name <committer@example.com>";

                    // Current timestamp
                    long timestamp = System.currentTimeMillis() / 1000;
                    String authorDate = timestamp + " +0000"; // UTC timestamp
                    String committerDate = authorDate; // Same for simplicity

                    // Build the commit content
                    StringBuilder commitContent = new StringBuilder();
                    commitContent.append("tree ").append(treeSha).append("\n");
                    commitContent.append("parent ").append(parentCommitSha).append("\n");
                    commitContent.append("author ").append(authorName).append(" ").append(authorDate).append("\n");
                    commitContent.append("committer ").append(committerName).append(" ").append(committerDate).append("\n\n");
                    commitContent.append(commitMessage).append("\n");

                    byte[] commitData = commitContent.toString().getBytes(StandardCharsets.UTF_8);

                    // Write the commit object and get its SHA
                    String commitSha = writeObject("commit", commitData);
                    System.out.println(commitSha); // Print the 40-char commit SHA
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Clone a repository (not fully implemented here)
            case "clone" -> cloneRepo(args[1], args[2]);

            // Handle unknown command
            default -> System.out.println("Unknown command: " + command);
        }
    }

    // Helper method to read and write data from one stream to another
    private static void shovelInToOut(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1000];
        int len;
        var offset = 0;
        while((len = in.read(buffer)) > 0) {
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i] == 0x00) {offset = i+1; break;}
            }
            out.write(buffer, offset, len);
        }
    }

    // Parse a tree object and print the file names inside it
    private static void parseTreeObject(byte[] data) {
        int offset = 0;

        while (data[offset] != 0x00) {
            offset++;
        }
        offset++;

        while (offset < data.length) {
            int modeEnd = offset;
            while (data[modeEnd] != ' ') {
                modeEnd++;
            }
            String mode = new String(data, offset, modeEnd - offset, StandardCharsets.UTF_8);
            offset = modeEnd + 1;

            int nameEnd = offset;
            while (data[nameEnd] != 0x00) {
                nameEnd++;
            }
            String name = new String(data, offset, nameEnd - offset, StandardCharsets.UTF_8);
            offset = nameEnd + 1;

            byte[] shaBytes = Arrays.copyOfRange(data, offset, offset + 20);
            offset += 20;

            StringBuilder shaHex = new StringBuilder();
            for (byte b : shaBytes) {
                shaHex.append(String.format("%02x", b));
            }
            System.out.println(name); // Print the file names from the tree object
        }
    }

    // Write a tree object based on the directory structure
    private static String writeTree(String dirPath) throws IOException, NoSuchAlgorithmException {
        File dir = new File(dirPath);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Path is not a directory: " + dirPath);
        }

        // Collect files and directories
        File[] files = dir.listFiles();
        Arrays.sort(files, Comparator.comparing(File::getName));

        ByteArrayOutputStream treeContent = new ByteArrayOutputStream();
        for (File entry : files) {
            if (entry.getName().equals(".git")) continue; // Ignore .git directory

            String mode = entry.isDirectory() ? "40000" : "100644";
            String name = entry.getName();

            String sha;
            if (entry.isDirectory()) {
                sha = writeTree(entry.getPath()); // Recurse into subdirectories
            } else {
                byte[] fileContent = Files.readAllBytes(entry.toPath());
                sha = writeBlob(fileContent); // Create blob for file
            }

            // Write mode, name, and SHA hash to the tree content
            treeContent.write((mode + " " + name + "\0").getBytes(StandardCharsets.UTF_8));
            treeContent.write(hexToBytes(sha));
        }

        byte[] treeData = treeContent.toByteArray();
        String treeSha = writeObject("tree", treeData); // Write tree object and get its SHA
        return treeSha;
    }

    // Write a blob object (file) to the .git/objects directory
    private static String writeBlob(byte[] data) throws IOException, NoSuchAlgorithmException {
        return writeObject("blob", data);
    }

    // Write an object (tree, commit, blob) and return its SHA
    private static String writeObject(String type, byte[] data) throws IOException, NoSuchAlgorithmException {
        ByteArrayOutputStream objectContent = new ByteArrayOutputStream();
        objectContent.write((type + " " + data.length + "\0").getBytes(StandardCharsets.UTF_8));
        objectContent.write(data);

        byte[] fullContent = objectContent.toByteArray();
        String sha = byteToHex(hash(fullContent)); // Get the SHA hash of the object

        String objectDir = "./.git/objects/" + sha.substring(0, 2);
        String objectPath = objectDir + "/" + sha.substring(2);

        new File(objectDir).mkdirs();
        try (FileOutputStream out = new FileOutputStream(objectPath)) {
            out.write(compress(fullContent)); // Compress and save the object
        }

        return sha; // Return the object's SHA
    }

    // Convert a hex string to bytes
    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[20];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    // Write the data to the file in the .git/objects directory
    public static void writeToFile(byte[] data) throws IOException, NoSuchAlgorithmException {
        String len = String.valueOf(data.length);
        var outBytes = new ByteArrayOutputStream();
        outBytes.write("blob ".getBytes());
        outBytes.write(len.getBytes());
        outBytes.write(0x00);
        outBytes.write(data);
        outBytes.close();
        var hash = byteToHex(hash(outBytes.toByteArray()));
        System.out.println(hash);
        var path = "./.git/objects/" + hash.substring(0, 2) + "/"  +hash.substring(2);
        new File("./.git/objects/" + hash.substring(0, 2)).mkdirs();
        var out = new FileOutputStream(path);
        out.write(compress(outBytes.toByteArray())); // Compress and save the object
        out.close();
    }

    // Hash the content using SHA-1
    public static byte[] hash(byte[] content) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] messageDigest = md.digest(content);
        return messageDigest;
    }

    // Convert byte array to hexadecimal string
    public static String byteToHex(byte[] data ) {
        BigInteger no = new BigInteger(1, data);
        String hashtext = no.toString(16);
        while (hashtext.length() < 40) {
            hashtext = "0" + hashtext;
        }
        return hashtext;
    }

    // Compress the input data using Deflater
    public static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int compressedSize = deflater.deflate(buffer);
            outputStream.write(buffer, 0, compressedSize);
        }
        return outputStream.toByteArray();
    }

    // Clone a Git repository (not fully implemented here)
    private static void cloneRepo(String repoUrl, String dir) throws IOException, NoSuchAlgorithmException {
        GitCloner gitCloner = new GitCloner();
        gitCloner.cloneRepository(repoUrl, dir);
    }
  }
