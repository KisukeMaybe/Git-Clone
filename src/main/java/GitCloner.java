import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import java.io.File;

public class GitCloner {

    public void cloneRepository(String repoUrl, String destinationDirPath) {
        try {
            // Specify the directory where you want to clone the repo
            File destinationDir = new File(destinationDirPath);

            // Cloning the repository using JGit
            Git.cloneRepository()
                .setURI(repoUrl)                 // The GitHub repository URL
                .setDirectory(destinationDir)    // The directory where to clone the repo
                .call();                         // Executes the clone operation

            System.out.println("Repository cloned successfully!");

        } catch (GitAPIException e) {
            System.err.println("Error during clone operation: " + e.getMessage());
        }
    }
}
