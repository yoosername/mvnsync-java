package app.maven.workers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.util.ChecksumUtils;

public class DownloadWorker implements Runnable {
    
    private URL remote;
    private URL remoteChecksum;
    private File local;
    
     
    public DownloadWorker(File local, URL remote, URL remoteChecksum){
        this.local=local;
        this.remote = remote;
        this.remoteChecksum = remoteChecksum;
    }
 
    @Override
    public void run() {
        processCommand();
    }
 
    private void processCommand(){
    	if(!validChecksum(local)){
	    	try {
	    		System.out.println("downloading file: " + remote);
	    		FileUtils.copyURLToFile(remote, local);
	    		System.out.println("downloaded file: " + remote);
	        } catch (IOException e) {
	        	System.out.println("download failed: " +  e.getMessage());
			}
    	}else{
    		System.out.println("local checksum valid, skipping: " + remote);
    	}
    }

	private boolean validChecksum(File local) {
		File localChecksum = new File(local.getPath()+".sha1");
		Map<String, Object> checksums = null;
		
    	if(!localChecksum.exists()){
			try{
				System.out.println("downloading checksum: " + remoteChecksum);
				FileUtils.copyURLToFile(remoteChecksum, localChecksum);
				System.out.println("downloaded checksum: " + remoteChecksum);
			}catch(Exception e){
				return false;
			}
		}
		
    	if(!local.exists()){
			return false;
		}
		
    	try {
			checksums = ChecksumUtils.calc( local, Arrays.asList( "SHA-1" ) );
			for ( Entry<String, Object> entry : checksums.entrySet() )
	        {
	            String actual = entry.getValue().toString();
	            String expected = ChecksumUtils.read( localChecksum );
	            if(actual.equals(expected)){
	            	return true;
	            }
	        }
		} catch (IOException e) {}
    	System.out.println("invalid checksum: " + remote);
    	return false;
	}
}