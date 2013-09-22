package app.maven.utils;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import app.maven.workers.DownloadWorker;

public class VerifyPomFinder extends SimpleFileVisitor<Path> {

	private final PathMatcher matcher;
	private int numMatches = 0;
	private int numFailed = 0;
	private LocalRepository localRepository;
	private ExecutorService es;
	private Iterator<RemoteRepository> m;

	public VerifyPomFinder(LocalRepository localRepository, ExecutorService executor, Iterator<RemoteRepository> m) {
		this.localRepository = localRepository;
		this.es = executor;
		this.m = m;
		matcher = FileSystems.getDefault().getPathMatcher("glob:*.jar");
	}

	// Compares the glob pattern against the file or directory name.
	void find(Path file) {
		// Check if SHA1 and then verify the file
		Path name = file.getFileName();
		if (name != null && matcher.matches(name)) {
			ArtifactInfo ai = Helper.buildArtifactInfo(localRepository.getBasedir(), file.toFile());
			if(ai != null){
				File pom = new File(localRepository.getBasedir(),Helper.calculatePom(ai));
				if(!pom.exists()){
					System.out.println("Missing pom: " + pom);
					Runnable worker = new DownloadWorker(localRepository,m.next(),Helper.buildPom(ai),true);
					es.execute(worker);
					numFailed++;
				}
				numMatches++;
			}
		}
	}

	// Prints the total number of matches to standard out.
	public void done() {
		System.out.println("Found " + numMatches);
		System.out.println("Tried to resolve " + numFailed);
	}

	// Invoke the pattern matching method on each file.
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
		find(file);
		return CONTINUE;
	}

	// Invoke the pattern matching method on each directory.
	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
		find(dir);
		return CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) {
		System.err.println(exc);
		return CONTINUE;
	}

}
