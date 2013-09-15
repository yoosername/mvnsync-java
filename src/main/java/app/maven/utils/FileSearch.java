package app.maven.utils;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.artifact.M2GavCalculator;
 
public class FileSearch {
   
  private M2GavCalculator gavCalculator;
  private File basedir;
  private String groupId;
  private String artifactId;
  private List<String> types;
  private Set<ArtifactInfo> results;

  public FileSearch(File basedir){
	  this(basedir, null, null, null);
  }
  
  public FileSearch(File basedir, String groupId) {
	  this(basedir, groupId, null, null);
  }
  
  public FileSearch(File basedir, String groupId, String artifactId){
	  this(basedir, groupId, artifactId, null);
	  
  }
  
  public FileSearch(File basedir, String groupId, String artifactId,List<String> types) {
	  this.basedir = basedir;
	  this.groupId = groupId;
	  this.artifactId = artifactId;
	  this.gavCalculator = new M2GavCalculator();
	  searchDirectory();
  }

  public String getGroupId() {
	return groupId;
  }
  
  public String getArtifactId() {
	return artifactId;
  }
 
  public Set<ArtifactInfo> getResults() {
	return results;
  }
 
  public void searchDirectory() {
 
    if (basedir.isDirectory()) {
	    search(basedir);
	} else {
	    System.out.println(basedir.getAbsoluteFile() + " is not a directory!");
	}
 
  }
 
  private void search(File file) {
	if (file.isDirectory()) {
	    if (file.canRead()) {
			boolean filtered = false;
	    	for (File candidate : file.listFiles()) {
			    if (candidate.isDirectory()) {
					search(candidate);
			    } else {
			    	String relative = basedir.toURI().relativize(candidate.toURI()).getPath();
			    	relative = relative.replace("\\","/");
			    	Gav gav = gavCalculator.pathToGav(relative);
			    	if(gav!=null){
			    		ArtifactInfo ai = Helper.buildArtifactInfo(gav);
			    	  	filtered = (groupId!=null&&groupId!=ai.groupId)?true:false;
			    	  	filtered = (artifactId!=null&&artifactId!=ai.artifactId)?true:false;
			    	  	filtered = (types!=null&&!types.contains(ai.fextension))?true:false;
			    	  	if (!filtered) {
			    	  		System.out.println("Candidate: " + relative);
			    	  		results.add(ai);
			    	  	}
			    	}
			    }
		    }
	    } else {
	    	System.out.println(file.getAbsoluteFile() + "Permission Denied");
	    }
	}
  }
 
}