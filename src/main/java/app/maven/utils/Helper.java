package app.maven.utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

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
	
	public static String calculateGav(File root, File file){
		String path = file.getPath();
		String base = root.getPath();
		String relative = new File(base).toURI().relativize(new File(path).toURI()).getPath();
		String str = relative.replace("\\","/");

		try
        {
            String s = str.startsWith( "/" ) ? str.substring( 1 ) : str;
            int vEndPos = s.lastIndexOf( '/' );
            if ( vEndPos == -1 ){
                return null;
            }
            int aEndPos = s.lastIndexOf( '/', vEndPos - 1 );
            if ( aEndPos == -1 ){
                return null;
            }
            int gEndPos = s.lastIndexOf( '/', aEndPos - 1 );
            if ( gEndPos == -1 ){
                return null;
            }
            String groupId = s.substring( 0, gEndPos ).replace( '/', '.' );
            String artifactId = s.substring( gEndPos + 1, aEndPos );
            String version = s.substring( aEndPos + 1, vEndPos );
            return calculateGav(groupId,artifactId,version);
        }
        catch ( NumberFormatException e )
        {
            return null;
        }
        catch ( StringIndexOutOfBoundsException e )
        {
            return null;
        }
	}
	
	public static String calculateGav(String groupId, String artifactId, String version){
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

	public static String calculatePath(Dependency dep) {
		StringBuilder path = new StringBuilder( 128 );
		Artifact art = dep.getArtifact();
		path.append( art.getGroupId().replace( '.', '/' ) ).append( '/' );
		path.append( art.getArtifactId() ).append( '/' );
		path.append( art.getVersion() ).append( '/' );
		path.append( art.getArtifactId() ).append( '-' ).append( art.getVersion() );
		if ( art.getClassifier() != null && art.getClassifier().length() > 0 ){
			path.append( '-' ).append( art.getClassifier() );
		}
		path.append( '.' ).append( art.getExtension() );
		return path.toString();
	}

	public static String calculateGav(Dependency dep) {
		if(dep != null){
			return calculateGav(dep.getArtifact());
		}
		return null;
	}

	
	public static ArtifactInfo buildArtifactInfo(File root, File file) {
		String path = file.getPath();
		String base = root.getPath();
		String relative = new File(base).toURI().relativize(new File(path).toURI()).getPath();
		String str = relative.replace("\\","/");
		ArtifactInfo ai = null;
		try
        {
            String s = str.startsWith( "/" ) ? str.substring( 1 ) : str;
            int vEndPos = s.lastIndexOf( '/' );
            if ( vEndPos == -1 ){
                return null;
            }
            int aEndPos = s.lastIndexOf( '/', vEndPos - 1 );
            if ( aEndPos == -1 ){
                return null;
            }
            int gEndPos = s.lastIndexOf( '/', aEndPos - 1 );
            if ( gEndPos == -1 ){
                return null;
            }
            ai = new ArtifactInfo();
            ai.groupId = s.substring( 0, gEndPos ).replace( '/', '.' );
            ai.artifactId = s.substring( gEndPos + 1, aEndPos );
            ai.version = s.substring( aEndPos + 1, vEndPos );
            ai.fextension = "jar";
            ai.path = relative;
            ai.fname = file.getName();
            return ai;
        }
        catch ( NumberFormatException e )
        {
            return null;
        }
        catch ( StringIndexOutOfBoundsException e )
        {
            return null;
        }
	}

	public static ArtifactInfo buildArtifactInfo(String gav) {
		ArtifactInfo ai = null;
		try{
			ai = new ArtifactInfo();
			String[] parts = gav.split(":");
			ai.groupId = parts[0];
			ai.artifactId = parts[1];
			ai.version = parts[2];
			ai.fextension = "jar";
			return ai;
		}catch(Exception e){
			return null;
		}
		
	}
}
