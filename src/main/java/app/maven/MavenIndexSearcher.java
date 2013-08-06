package app.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

import app.maven.listeners.ConsoleTransferListener;
import app.maven.utils.Helper;

public class MavenIndexSearcher {
	
	private List<String> types;
	private PlexusContainer plexusContainer;
	private IndexingContext context;
	private Aether aether;
	
	public MavenIndexSearcher(Aether aether) throws ExistingLuceneIndexMismatchException, IllegalArgumentException, IOException, ComponentLookupException, PlexusContainerException{
		types = Arrays.asList("jar");
		this.aether = aether;
		
		plexusContainer = new DefaultPlexusContainer();
		Indexer indexer = plexusContainer.lookup( Indexer.class );
        
		// Files where local cache is (if any) and Lucene Index should be located
        File mavenLocalCache = new File( aether.getLocalRepository().getBasedir(), ".remote-index/repo-cache" );
        File mavenIndexDir = new File( aether.getLocalRepository().getBasedir(), ".remote-index/repo-index" );

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        // Create context for repository index
        context = indexer.createIndexingContext( 
            "maven-context", "maven", mavenLocalCache, mavenIndexDir,
            aether.getRemoteRepository().getUrl().toString(), null, true, true, indexers
        );
        
	}
	
	public IndexingContext getIndexingContext(){
		return context;
	}
	
	public void updateIndex() throws PlexusContainerException, ComponentLookupException, IOException{
		
		//IndexUpdater indexUpdater = new DefaultIndexUpdater();
		//Wagon httpWagon = new HttpWagon();		
		
		IndexUpdater indexUpdater = plexusContainer.lookup( IndexUpdater.class );
		Wagon httpWagon = plexusContainer.lookup( Wagon.class, "http" );
			
        System.out.println( "Updating Index..." );
        System.out.println( "This might take a while on first run, so please be patient!" );
        
        TransferListener listener = new ConsoleTransferListener();
        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );
        Date centralContextCurrentTimestamp = context.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest( context, resourceFetcher );
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex( updateRequest );

        if ( updateResult.isFullUpdate() )
        {
            System.out.println( "Full update happened!" );
        }
        else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) )
        {
            System.out.println( "No update needed, index is up to date!" );
        }
        else
        {
            System.out.println( "Incremental update happened, change covered " + centralContextCurrentTimestamp
                + " - " + updateResult.getTimestamp() + " period." );
        }
	}

	public Iterator<Dependency> getDependenciesFromIndex(int amount) throws IOException{
		System.out.println("Building dependency list from remote index....");
		List<Dependency> deps = new ArrayList<Dependency>();
		IndexSearcher searcher = context.acquireIndexSearcher();
		IndexReader ir = searcher.getIndexReader();
		int added = 0;
		
		for ( int i = 0; i < ir.maxDoc(); i++ ){
			if ( !ir.isDeleted( i ) ){
				Document doc = null;
				try {
					doc = ir.document( i );
				} catch (Exception e) {
					e.printStackTrace();
				}

				if(doc != null){
					ArtifactInfo ai = IndexUtils.constructArtifactInfo( doc, context );
					
					if( ai != null ){
						if( types.contains(ai.fextension) ){
							File localFile = new File(aether.getLocalRepository().getBasedir(),Helper.calculatePath(ai));
							if(!localFile.exists()){
								String gav = Helper.calculateGav(ai);
								if(gav != null){
									Artifact art = new DefaultArtifact(gav);
									Dependency dep = new Dependency(art, "compile");
									deps.add(dep);
									if((amount > 0) && ++added >= amount){
										break;
									}
								}
							}
						}
					}
				}
			}
		}
		System.out.println("Resolving next "+amount+" dependencies");
		Iterator<Dependency> i = deps.iterator();
		return i;
	}
}
