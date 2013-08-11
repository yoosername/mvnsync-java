package app.maven.listeners;

import java.io.PrintStream;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

/**
 * A simplistic repository listener that logs events to the console.
 */
public class ConsoleRepositoryListener extends AbstractRepositoryListener
{
    private PrintStream out = System.out;
    private PrintStream err = System.err;

    public void artifactDescriptorInvalid(RepositoryEvent event) {
        err.println("artifact descriptor invalid: " + event.getArtifact()
                + " : " + event.getException().getMessage());
    }

    public void artifactDescriptorMissing(RepositoryEvent event) {
        err.println("artifact descriptor missing: " + event.getArtifact());
    }

    public void artifactInstalled(RepositoryEvent event) {
        out.println("artifact installed: " + event.getArtifact() + " : " + event.getFile());   
    }

    public void artifactResolved(RepositoryEvent event) {
        out.println("artifact resolved: " + event.getArtifact() + " : " + event.getRepository());
    }

    public void artifactDownloading(RepositoryEvent event) {
        out.println("downloading " + event.getArtifact() + " : " + event.getRepository());
    }

    public void artifactDownloaded(RepositoryEvent event) {
        out.println("downloaded " + event.getArtifact() + " : " + event.getRepository());
    }

    public void metadataInvalid(RepositoryEvent event) {
        err.println("metadata invalid: " + event.getMetadata());
    }

    public void metadataResolved(RepositoryEvent event) {
        out.println("metadata resolved: " + event.getMetadata() + " : " + event.getRepository());
    }
 }
