package app.maven.utils;

import java.util.Arrays;
import java.util.List;

import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.artifact.Artifact;

public class Helper {

	public static String calculateGav(ArtifactInfo ai){
		String gav = null;
		if(
			ai.groupId != null &&
			ai.artifactId != null &&
			ai.version.matches("^[0-9]+.*$")
		){
			gav = ai.groupId;
			gav += ":" + ai.artifactId;
			gav += ":" + ai.fextension;
			if(ai.classifier != null){
				gav += ":" + ai.classifier;
			}
			gav += ":" + ai.version;
		}
		return gav;
	}
	
	public static String calculateGav(String artifactId, String groupId, String version){
		String gav = null;
		if(
			groupId != null &&
			artifactId != null &&
			version.matches("^[0-9]+.*$")
		){
			gav = groupId;
			gav += ":" + artifactId;
			gav += ":jar";
			gav += ":" + version;
		}
		return gav;
	}
	
	public static String calculateGav(Artifact artifact){
		String gav = null;
		if(artifact != null){
			if(
				artifact.getGroupId() != null &&
				artifact.getArtifactId() != null &&
				artifact.getVersion().matches("^[0-9]+.*$")
			){
				gav = artifact.getGroupId();
				gav += ":" + artifact.getArtifactId();
				gav += ":" + artifact.getExtension();
				if(artifact.getClassifier() != null){
					gav += ":" + artifact.getClassifier();
				}
				gav += ":" + artifact.getVersion();
			}
		}
		return gav;
	}
	
	// Build relative path from remote artifact
	public static String calculatePath( ArtifactInfo ai ){
		StringBuilder path = new StringBuilder( 128 );
		path.append( ai.groupId.replace( '.', '/' ) ).append( '/' );
		path.append( ai.artifactId ).append( '/' );
		path.append( ai.version ).append( '/' );
		path.append( ai.artifactId ).append( '-' ).append( ai.version );
		if ( ai.classifier != null && ai.classifier.length() > 0 ){
			path.append( '-' ).append( ai.classifier );
		}
		path.append( '.' ).append( ai.fextension );
		return path.toString();
	}
	
	public static String calculatePath( String gav ){
		StringBuilder path = new StringBuilder( 128 );
		List<String> parts = Arrays.asList(gav.split("\\s*:\\s*"));
		path.append( parts.get(0).replace( '.', '/' ) ).append( '/' ); //groupId
		path.append( parts.get(1).replace( '.', '/' ) ).append( '/' ); //artifactId
		if(path.length() == 5){
			path.append( parts.get(4) ).append( '/' ); //version
			path.append( parts.get(1) ).append( '-' ); //artifact-
			path.append( parts.get(4) ).append( '-' ); //version-
			path.append( parts.get(3) );				//classifier
			path.append('.').append( parts.get(2) ); //extension
		}else if(path.length() == 4){
			path.append( parts.get(3) ).append( '/' ); //version
			path.append( parts.get(1) ).append( '-' ); //artifact-
			path.append( parts.get(3) ); //version-
			path.append('.').append( parts.get(2) ); //extension
		}else{
			path.append( parts.get(2) ).append( '/' ); //version
			path.append( parts.get(1) ).append( '-' ); //artifact-
			path.append( parts.get(2) ); //version-
			path.append('.').append( "jar" ); //extension
		}
		return path.toString();
	}
}
