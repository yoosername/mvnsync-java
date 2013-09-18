package app.maven.workers;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.util.ChecksumUtils;

public class DownloadWorker implements Runnable {
    
    private URL remote;
    private URL remoteSha1;
    private File local;
    private File partial;
    private File localSha1;
    
     
    public DownloadWorker(File local, URL remote){
        this.local=local;
        this.partial = new File(local + ".partial");
        this.localSha1 = new File(local + ".sha1");
        this.remote = remote;
        try {
			this.remoteSha1 = new URL(remote.toString() + ".sha1");
		} catch (MalformedURLException e) {
			System.out.println("error determining remote SHA1: " + remote);
		}
    }
 
	public void run() {
        boolean valid = downloadFile();
        if(valid){
        	partial.renameTo(local);
        	System.out.println("downloaded file: " + remote);
        }else{
        	partial.delete();
        	System.out.println("downloaded failed ( checksum invalid ): " + remote);
        }
    }
 
    private boolean downloadFile(){
    	System.out.println("downloading file: " + remote);
    	boolean outcome = false;
    	
    	try {
    		FileUtils.copyURLToFile(remoteSha1, localSha1);
    		FileUtils.copyURLToFile(remote, partial); 
    		outcome = validateChecksum();
        } catch (IOException e) {
        	System.out.println("download failed: " +  e.getMessage());
		}
    	return outcome;
    }

	private boolean validateChecksum() {
		Map<String, Object> checksums = null;
		
    	try {
			checksums = ChecksumUtils.calc( partial, Arrays.asList( "SHA-1" ) );
			for ( Entry<String, Object> entry : checksums.entrySet() )
	        {
	            String actual = entry.getValue().toString();
	            String expected = ChecksumUtils.read( localSha1 );
	            if(actual.equals(expected)){
	            	return true;
	            }
	        }
		} catch (IOException e) {}
    	
    	return false;
	}
}