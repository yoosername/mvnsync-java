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
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.aether.util.ChecksumUtils;

public class Finder extends SimpleFileVisitor<Path> {

	private final PathMatcher matcher;
	private int numMatches = 0;
	private int numFailed = 0;

	public Finder(String pattern) {
		matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
	}

	// Compares the glob pattern against the file or directory name.
	void find(Path file) {
		// Check if SHA1 and then verify the file
		Path name = file.getFileName();
		if (name != null && matcher.matches(name)) {
			if(!validChecksum(file.toFile())){
				System.out.println("Checksum verify failed: " + file);
				numFailed++;
			}
			numMatches++;
		}
	}

	// Prints the total number of matches to standard out.
	public void done() {
		System.out.println("Verified " + numMatches);
		System.out.println("Failed " + numFailed);
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
	
	private boolean validChecksum(File local) {
		Map<String, Object> checksums = null;
		File sha1 = new File(local + ".sha1");
		
    	try {
			checksums = ChecksumUtils.calc( local, Arrays.asList( "SHA-1" ) );
			for ( Entry<String, Object> entry : checksums.entrySet() )
	        {
	            String actual = entry.getValue().toString();
	            String expected = ChecksumUtils.read( sha1 );
	            if(actual.equals(expected)){
	            	return true;
	            }
	        }
		} catch (IOException e) {}
    	
    	return false;
	}
}
