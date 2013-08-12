package app.maven;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IteratorResultSet;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.wagon.WagonProvider;
import org.eclipse.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

import app.maven.listeners.ConsoleRepositoryListener;
import app.maven.providers.ManualWagonProvider;
import app.maven.utils.Helper;
import app.maven.workers.DownloadWorker;

public class Aether {
	private RepositorySystem system;
	private RepositorySystemSession session;
	
	private LocalRepository localRepository;
	private List<RemoteRepository> mirrors = new ArrayList<RemoteRepository>();
	private RemoteRepository remoteRepository;
	private String groupId;
	private String artifactId;
	private int max;
	private boolean hasMax = false;
	private int MAX_THREADS = 16;
	
	private int BATCH = 50; //50 = best tested download rate out of 15,24,48,50,100
	
	public static final int COLLECT = 0;
	public static final int RESOLVE = 1;
	public static final int DIRECT = 2;
	
	private int method = RESOLVE;
	
	public Aether(String local){
		setLocalRepository(local);
		groupId = "";
		artifactId = "";		
		newRepositorySystem();
		newSession();	
	}
	
	public RepositorySystem getRepositorySystem(){
		return system;
	}
	
	public RepositorySystemSession getRepositorySession(){
		return session;
	}
	
	public void setMethod(int method){
		this.method = method;
	}
	
	public String getGroupId(){
		return groupId;
	}
	
	public void setGroupId(String groupId){
		this.groupId = groupId;
	}
	
	public String getArtifactId(){
		return artifactId;
	}
	
	public void setArtifactId(String artifactId){
		this.artifactId = artifactId;
	}
	
	public List<RemoteRepository> mirrors(){
		return mirrors;
	}
	
	public LocalRepository getLocalRepository() {
		return localRepository;
	}
	
	public void setLocalRepository(String localRepository) {
		this.localRepository = new LocalRepository(localRepository);
	}
	
	public RemoteRepository getRemoteRepository() {
		return remoteRepository;
	}

	public void setRemoteRepository(String remoteRepository) {
		String name = "";
		try {
			URL uri = new URL(remoteRepository);
			name = uri.getHost();
		} catch (MalformedURLException e) {}
		this.remoteRepository = new RemoteRepository.Builder( name, "default", remoteRepository ).build();
	}
	
	public void addMirror(String mirror){
		String name = "";
		try {
			URL uri = new URL(mirror);
			name = uri.getHost();
		} catch (MalformedURLException e) {}
		mirrors.add(new RemoteRepository.Builder( name, "default", mirror ).build());
	}

	public boolean hasMax(){
		return hasMax;
	}
	
	public int getMax() {
		return max;
	}
	
	public void setMax(int max) {
		this.max = max;
		this.hasMax = true;
		this.BATCH = max;
	}
	
	public void setMaxThreads(int max){
		this.MAX_THREADS = max;
	}
	
	public void newRepositorySystem()
    {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService( RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class );
        locator.setServices( WagonProvider.class, new ManualWagonProvider() );
        this.system = locator.getService( RepositorySystem.class );
    }
    
    public void newSession()
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepository ) );
        session.setRepositoryListener(new ConsoleRepositoryListener());
        this.session = session;
    }

    public CollectRequest aetherCollectRequest(){
    	CollectRequest collectRequest = new CollectRequest();
		collectRequest.addRepository(remoteRepository);
		for(RemoteRepository mirror: mirrors){
			collectRequest.addRepository( mirror );
		}
		return collectRequest;
    }
    
    public void directDownload(IteratorResultSet deps){
    	ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
    	mirrors.add(remoteRepository);
    	RoundRobin<RemoteRepository> roundRobin = new RoundRobin<RemoteRepository>(mirrors);
    	
    	Iterator<RemoteRepository> m = roundRobin.iterator();
        
    	while(deps.hasNext()){
    		ArtifactInfo ai = deps.next();
			try {
				URL remoteBase = new URL(m.next().getUrl());
				URL remote = followRedirect(new URL(remoteBase.toString() + "/" + Helper.calculatePath(ai)));
				URL remoteChecksum = followRedirect(new URL(remoteBase.toString() + "/" + Helper.calculatePath(ai) + ".sha1"));
	    		File local = new File(localRepository.getBasedir(),Helper.calculatePath(ai));
	    		Runnable worker = new DownloadWorker(local,remote,remoteChecksum);
	            executor.execute(worker);
			} catch (MalformedURLException e) {
				System.out.println("error creating remote url: " + e.getMessage());
			}
    	}
    	while (!executor.isTerminated()) {}
        System.out.println("Finished all threads");
    	executor.shutdown();
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
    
    public void resolve(IteratorResultSet deps){
    	CollectRequest collectRequest;
    	int total = 0;
		
		while(deps.hasNext()){
			collectRequest = aetherCollectRequest();
			for(int i=0;i<BATCH;i++){
				ArtifactInfo ai = deps.next();
	    		Artifact art = new DefaultArtifact(ai.groupId,ai.artifactId,ai.classifier,"jar",ai.version);
	        	if(art!=null){
	        		File local = new File(localRepository.getBasedir(),Helper.calculatePath(ai));
	        		if((method == Aether.RESOLVE)&&local.exists()){
	        			continue;
	        		}
        			Dependency dep = new Dependency(art,"compile");
        			collectRequest.addDependency(dep);
        			total++;
	        	}
			}
	        DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, null );
	        request(dependencyRequest);
	        if(hasMax && total >= max){
        		return;
        	}
		}
	}
    
    public void resolve(Iterator<ArtifactInfo> deps){
    	CollectRequest collectRequest;
    	int total = 0;
    	
		while(deps.hasNext()){
			collectRequest = aetherCollectRequest();
			for(int i=0;i<BATCH;i++){
				ArtifactInfo ai = deps.next();
	    		Artifact art = new DefaultArtifact(ai.groupId,ai.artifactId,ai.classifier,"jar",ai.version);
	    		if(art!=null){
	    			File local = new File(localRepository.getBasedir(),Helper.calculatePath(ai));
	        		if((method == Aether.RESOLVE)&&local.exists()){
	        			continue;
	        		}
        			Dependency dep = new Dependency(art,"compile");
        			collectRequest.addDependency(dep);
        			total++;
	    		}
			}
	        DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, null );
	        request(dependencyRequest);
	        if(hasMax && total >= max){
        		return;
        	}
		}
	}
    
    private void request(DependencyRequest request){
    	if(method == Aether.RESOLVE){
    		//DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();
        	//DependencyRequest dependencyRequest = new DependencyRequest( node, null );
            try {
            	System.out.println("Attempting to resolve "+BATCH+" dependencies: ");
    			system.resolveDependencies( session, request  );
    		} catch (DependencyResolutionException e) {
    			System.out.println("problem resolving dependencies: " + e.getMessage());
    		}
        }else{
        	try {
        		System.out.println("Attempting to collect "+BATCH+" dependencies: ");
        		system.collectDependencies(session, request.getCollectRequest());
    		} catch (DependencyCollectionException e) {
    			System.out.println("problem collecting dependencies: " + e.getMessage());
    		}
        }
    }
  
}
