package app.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
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

import app.maven.listeners.ConsoleTransferListener;
import app.maven.utils.Helper;

public class MavenSearcher {
	
	private List<String> types = new ArrayList<String>();
	private PlexusContainer plexusContainer;
	private IndexingContext context;
	private Aether aether;
	private Indexer indexer;
	
	public void addType(String type){
		this.types.add(type);
	}
	
	public MavenSearcher(Aether aether){
		this.aether = aether;        
	}
	
	public void setupIndexer()throws ExistingLuceneIndexMismatchException, IllegalArgumentException, IOException, ComponentLookupException, PlexusContainerException{
		plexusContainer = new DefaultPlexusContainer();
		indexer = plexusContainer.lookup( Indexer.class );
        
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

	public void loadDependenciesFromFile(File file){
		System.out.println("Resolving dependencies from file: " + file.getPath());
		Scanner sc = null;
		File depFile = file;
		
		if(depFile.exists() && depFile.isFile()){
			try {
				sc = new Scanner(depFile);
				while (sc.hasNextLine()) {
					String gav = sc.nextLine();
					if(gav != null && ! "".equals(gav)){
						aether.addDependency(gav);
					}
				}
			} catch (FileNotFoundException e) {
				System.out.println("GAV file not found: " + e.getMessage());
			} finally{
				sc.close();
				System.out.println("file closed");
			}
		}
	}
	
	public void loadDependenciesFromIndex() throws IOException{
		System.out.println("Searching index for artifacts of type(s): " + types);
		
        // construct the query for known GA
		final BooleanQuery query = new BooleanQuery();
		
		if(!aether.getGroupId().isEmpty()){
	        Query groupIdQ = indexer.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( aether.getGroupId() ) );
	        query.add( groupIdQ, Occur.MUST );
	        System.out.println("with groupId: " + aether.getGroupId());
		}
		
		if(!aether.getArtifactId().isEmpty()){
	        Query artifactIdQ = indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( aether.getArtifactId() ) );
	        query.add( artifactIdQ, Occur.MUST );
	        System.out.println("with artifactId: " + aether.getArtifactId());
		}

		for(String type: types){
			query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( type ) ), Occur.MUST );
		}
		
		IteratorSearchRequest request = new IteratorSearchRequest( query, Collections.singletonList( context ) );
	    IteratorSearchResponse response = indexer.searchIterator( request );
	    
	    System.out.println("start loop");
        for ( ArtifactInfo ai : response )
        {
        	if(ai != null){
        		String gav = Helper.calculateGav(ai);
				if(gav != null){
					aether.addDependency(gav);
				}
        	}
        }
        
        indexer.closeIndexingContext( context, false );	
        System.out.println("index closed");
	}
	
	public void loadDependenciesFromFileSystem(){
		System.out.println("Searching " + aether.getLocalRepository().getBasedir() + " for artifacts");
		loadDependenciesFromFileSystem(aether.getLocalRepository().getBasedir());
	}
	
    public void loadDependenciesFromFileSystem(File file){
    	if(file.isFile()){
        	String gav = Helper.calculateGav(aether.getLocalRepository().getBasedir(),file);
        	if(gav !=null){
        		aether.addDependency(gav);
        	}
        }else if (file.isDirectory()) {
          File[] listOfFiles = file.listFiles();
          if(listOfFiles!=null) {
            for(int i = 0; i < listOfFiles.length; i++){
            	loadDependenciesFromFileSystem(listOfFiles[i]);
            }
          }
        }
    }
	
	public void report() throws IOException {
		System.out.println("Reporting on search of types: " + types);
		int artifactsInIndex = 0;
		int artifactsLocally = 0;
		
		final BooleanQuery query = new BooleanQuery();
		
		if(!aether.getGroupId().isEmpty()){
	        Query groupIdQ = indexer.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( aether.getGroupId() ) );
	        query.add( groupIdQ, Occur.MUST );
	        System.out.println("with groupId: " + aether.getGroupId());
		}
		
		if(!aether.getArtifactId().isEmpty()){
	        Query artifactIdQ = indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( aether.getArtifactId() ) );
	        query.add( artifactIdQ, Occur.MUST );
	        System.out.println("with artifactId: " + aether.getArtifactId());
		}

		for(String type: types){
			query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( type ) ), Occur.MUST );
		}
		
		IteratorSearchRequest request = new IteratorSearchRequest( query, Collections.singletonList( context ) );
	    IteratorSearchResponse response = indexer.searchIterator( request );
	    
        for ( ArtifactInfo ai : response )
        {
        	if(ai != null){
				artifactsInIndex++;
				File localFile = new File(aether.getLocalRepository().getBasedir(),Helper.calculatePath(ai));
				if(localFile.exists()){
					artifactsLocally++;
				}
        	}
        }
        
        indexer.closeIndexingContext( context, false );
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
