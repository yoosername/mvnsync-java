package app.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorResultSet;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.apache.maven.index.context.IndexCreator;
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
        }else{
	        if(updateResult.getTimestamp() != null){
	        	if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) )
		        {
		            System.out.println( "No update needed, index is up to date!" );
		        }
		        else
		        {
		            System.out.println( "Incremental update happened, change covered " + centralContextCurrentTimestamp
		                + " - " + updateResult.getTimestamp() + " period." );
		        }
	        }else{
	        	System.out.println( "Incremental update happened: unknown timestamp");
	        }
        }
	}
	
	public void closeIndex() {
		try {
			indexer.closeIndexingContext( context, false );
			System.out.println("Indexer closed");
		} catch (IOException e) {}
	}

	public Iterator<ArtifactInfo> loadDependenciesFromFile(File file){
		System.out.println("Resolving dependencies from file: " + file.getPath());
		Set<ArtifactInfo> results = new HashSet<ArtifactInfo>();
		Scanner sc = null;
		File depFile = file;
		
		if(depFile.exists() && depFile.isFile()){
			try {
				sc = new Scanner(depFile);
				while (sc.hasNextLine()) {
					ArtifactInfo ai = Helper.buildArtifactInfo(sc.nextLine());
					if(ai != null){
						results.add(ai);
					}
				}
			} catch (FileNotFoundException e) {
				System.out.println("GAV file not found: " + e.getMessage());
			} finally{
				sc.close();
				System.out.println("file closed");
			}
		}
		Iterator<ArtifactInfo> it = results.iterator();
		return it;
	}
	
	public IteratorResultSet loadDependenciesFromIndex() throws IOException{
		System.out.println("Searching index for artifacts of type(s): " + types);
		
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

		final BooleanQuery typeQuery = new BooleanQuery();
		for(String type: types){
			typeQuery.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( type ) ), Occur.SHOULD );
		}
		query.add(typeQuery, Occur.MUST);
		
		IteratorSearchResponse response = indexer.searchIterator(new IteratorSearchRequest(query, context));
	    	
        return response.getResults();
	}
	
	public Set<ArtifactInfo> loadDependenciesFromFileSystem(){
		System.out.println("Searching " + aether.getLocalRepository().getBasedir() + " for artifacts");
		Set<ArtifactInfo> results = null;
		loadDependenciesFromFileSystem(aether.getLocalRepository().getBasedir(),results);
		return results;
	}
	
    public void loadDependenciesFromFileSystem(File file,Set<ArtifactInfo> results){
    	File basedir = aether.getLocalRepository().getBasedir();
    	
    	if(file.isFile()){
    		ArtifactInfo ai = Helper.buildArtifactInfo(basedir,file);
        	if(ai != null){
        		 results.add(ai);
        	}
        }else if (file.isDirectory()) {
          File[] listOfFiles = file.listFiles();
          if(listOfFiles!=null) {
            for(int i = 0; i < listOfFiles.length; i++){
            	loadDependenciesFromFileSystem(listOfFiles[i],results);
            }
          }
        }
    }
	
	public void report(IteratorResultSet results) throws IOException {
		int artifactsLocally = 0;
		int artifactsRemote = 0;
		File basedir = aether.getLocalRepository().getBasedir();

        for ( ArtifactInfo ai : results )
        {
        	if(ai != null){
				artifactsRemote++;
        		File localFile = new File(basedir,Helper.calculatePath(ai));
				if(localFile.exists()){
					artifactsLocally++;
				}
        	}
        }
		System.out.println("Remote: " + artifactsRemote);
		System.out.println("Local: " + artifactsLocally);
	}

	public void createBatchFiles(IteratorResultSet results, int amount) throws IOException {
		System.out.println("Creating " + amount + " batch files from indexed content");

		File baseDir = aether.getLocalRepository().getBasedir();
		File batchDir = new File(baseDir,".batches");
		System.out.println("Writing " + amount + " GAVs in each batch file");
		int added = 0;
		int batched = 1;
		FileWriter writer = new FileWriter(new File(batchDir,"batch_"+batched+".txt"));
		
		for(ArtifactInfo ai: results){
			String gav = Helper.calculateGav(ai);
			if(gav != null){
				writer.append(gav + "\n");
				if(++added >= amount){
					writer.flush();
				    writer.close();
				    writer = new FileWriter(new File(batchDir,"batch_"+(++batched)+".txt"));
					added = 0;
				}
			}
		}
		writer.flush();
	    writer.close();
		System.out.println("Created " + batched + " batch files in " + batchDir.getPath());
	}
}
