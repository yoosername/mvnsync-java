package app.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
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
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

import app.maven.listeners.ConsoleTransferListener;
import app.maven.utils.Helper;

public class MavenSearcher {
	
	private List<String> types = new ArrayList<String>();
	private PlexusContainer plexusContainer;
	private IndexingContext context;
	private Aether aether;
	private List<String> tried = new ArrayList<String>();
	
	public void addType(String type){
		this.types.add(type);
	}
	
	public MavenSearcher(Aether aether){
		this.aether = aether;        
	}
	
	public void setupIndexer()throws ExistingLuceneIndexMismatchException, IllegalArgumentException, IOException, ComponentLookupException, PlexusContainerException{
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
		IndexUpdater indexUpdater = plexusContainer.lookup( IndexUpdater.class );
		//plexusContainer.lookup( Wagon.class, "http" ); // didnt work: component lookup failure
		Wagon httpWagon = new HttpWagon();
			
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
									if(!tried.contains(gav)){
										Artifact art = new DefaultArtifact(gav);
										Dependency dep = new Dependency(art, "compile");
										System.out.println(art + " -> " + localFile.getPath());
										deps.add(dep);
										tried.add(gav);
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
		}
		System.out.println("Resolving "+amount+" dependencies");
		Iterator<Dependency> i = deps.iterator();
		return i;
	}
	
	public Iterator<Dependency> getDependenciesFromFile(File file,int amount){
		Scanner sc = null;
		List<Dependency> deps = null;
		File depFile = file;
		int added = 0;
		
		if(depFile.exists() && depFile.isFile()){
			try {
				sc = new Scanner(depFile);
				deps = new ArrayList<Dependency>();
				while (sc.hasNextLine()) {
					String gav = sc.nextLine();
					if(gav != null && ! "".equals(gav)){
						File localFile = new File(aether.getLocalRepository().getBasedir(),Helper.calculatePath(gav));
						if(!localFile.exists()){
							if(gav != null){
								if(!tried.contains(gav)){
									Artifact art = new DefaultArtifact(gav);
									Dependency dep = new Dependency(art, "compile");
									System.out.println(art + " -> " + localFile.getPath());
									deps.add(dep);
									tried.add(gav);
									if((amount > 0) && ++added >= amount){
										break;
									}
								}
							}
						}
					}
				}
			} catch (FileNotFoundException e) {
				System.out.println("GAV file not found: " + e.getMessage());
			} finally{
				sc.close();
			}
		}
		Iterator<Dependency> i = deps.iterator();
		return i;
	}

	public void report() throws IOException {
		System.out.println("Reporting on search of types: " + types);
		IndexSearcher searcher = context.acquireIndexSearcher();
		IndexReader ir = searcher.getIndexReader();
		int artifactsInIndex = 0;
		int artifactsLocally = 0;
		
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
							artifactsInIndex++;
							File localFile = new File(aether.getLocalRepository().getBasedir(),Helper.calculatePath(ai));
							if(localFile.exists()){
								artifactsLocally++;
							}
						}
					}
				}
			}
		}
		
		System.out.println("Indexed: " + artifactsInIndex);
		System.out.println("Local: " + artifactsLocally);
	}

	public void createBatchFiles(int amount) throws IOException {
		System.out.println("Creating " + amount + " batch files from indexed content");
		IndexSearcher searcher = context.acquireIndexSearcher();
		IndexReader ir = searcher.getIndexReader();
		List<String> gavs = new ArrayList<String>();
				
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
								gavs.add(Helper.calculateGav(ai));
							}
						}
					}
				}
			}
		}
		
		Iterator<String> gavIterator = gavs.iterator();
		File batchDir = new File(aether.getLocalRepository().getBasedir(),".batches");
		int amountPerFile = (gavs.size() / amount) + 1;
		System.out.println("Size per file is: " + amountPerFile);
		int added = 0;
		int batched = 1;
		FileWriter writer = new FileWriter(new File(batchDir,"batch_"+batched+".txt"));
		while(gavIterator.hasNext()){
			String gav = gavIterator.next();
			if(gav != null){
				writer.append(gav + "\n");
				if(++added >= amountPerFile){
					writer.flush();
				    writer.close();
				    writer = new FileWriter(new File(batchDir,"batch_"+(++batched)+".txt"));
					added = 0;
				}
			}
		}
		writer.flush();
	    writer.close();
		System.out.println("Added " + gavs.size() + " GAVs each to " + batched + " batch files");
		System.out.println("Batch files saved to: " + batchDir.getPath());		
	}
}
