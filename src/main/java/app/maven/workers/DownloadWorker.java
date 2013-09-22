package app.maven.workers;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ChecksumUtils;

import app.maven.utils.Helper;

public class DownloadWorker implements Runnable {
    
    private File pom;
    private File local;
    private URL remotePom;
    private URL remote;
    private boolean skipExisting = false;
    
     
    public DownloadWorker(LocalRepository localRepository, RemoteRepository remoteRepository, ArtifactInfo ai, boolean skipExisting){
        this.pom = new File(localRepository.getBasedir(),Helper.calculatePom(ai));
        this.local = new File(localRepository.getBasedir(),Helper.calculatePath(ai));
        try {
        	this.remotePom = followRedirect(new URL(remoteRepository.getUrl() + "/" + Helper.calculatePom(ai)));
        	this.remote = followRedirect(new URL(remoteRepository.getUrl() + "/" + Helper.calculatePath(ai)));
		} catch (MalformedURLException e) {
			System.out.println("error determining remote URL: " + remote);
		}
        this.skipExisting = skipExisting;
    }

	public void run() {
		if(!(skipExisting == true && pom.exists())){
			boolean validPom = download(remotePom, pom);
			if(validPom){
            	System.out.println("downloaded ( checksum verified ): " + remotePom);
            }else{
            	try{
            		pom.delete();
            	}catch(Exception e){}
            	System.out.println("download failed ( checksum invalid ): " + remotePom);
            }
		}
		
        if(!pom.equals(local)){
	        if(!(skipExisting == true && local.exists())){
	        	boolean validFile = download(remote, local);
	        	if(validFile){
	            	System.out.println("downloaded( checksum verified ): " + remote);
	            }else{
	            	try{
	            		local.delete();
	            	}catch(Exception e){}
	            	System.out.println("download failed ( checksum invalid ): " + remote);
	            }
			}  
        }
    }
 
    private boolean download(URL url, File file){
    	System.out.println("downloading: " + url);
    	boolean outcome = false;
    	try {
    		URL urlSha1 = new URL(url + ".sha1");
    		File fileSha1 = new File(file + ".sha1");
    		FileUtils.copyURLToFile(urlSha1, fileSha1);
    		FileUtils.copyURLToFile(url, file); 
    		outcome = validateChecksum(file, fileSha1);
        } catch (IOException e) {
        	System.out.println("download failed: " +  e.getMessage());
		}
    	return outcome;
    }
    
	private URL followRedirect(URL url){
    	int status = 0;
    	boolean redirect = false;
    	
    	try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
						|| status == HttpURLConnection.HTTP_SEE_OTHER)
				redirect = true;
			}
			if(redirect == true){
				String newUrl = conn.getHeaderField("Location");
				return new URL(newUrl);
			}
		} catch (IOException e) {}
    	
    	return url;    	
    }
    
	private boolean validateChecksum(File file, File sha1File) {
		Map<String, Object> checksums = null;
		
    	try {
			checksums = ChecksumUtils.calc( file, Arrays.asList( "SHA-1" ) );
			for ( Entry<String, Object> entry : checksums.entrySet() )
	        {
	            String actual = entry.getValue().toString();
	            String expected = ChecksumUtils.read( sha1File );
	            if(actual.equals(expected)){
	            	return true;
	            }
	        }
		} catch (IOException e) {}
    	
    	return false;
	}
}