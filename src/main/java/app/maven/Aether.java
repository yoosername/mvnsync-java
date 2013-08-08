package app.maven;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
	private List<String> dependencies = new ArrayList<String>();
	
	public static final int COLLECT = 0;
	public static final int RESOLVE = 1;
	
	public Aether(String local){
		setLocalRepository(local);
		groupId = "";
		artifactId = "";		
		newRepositorySystem();
		newSession();	
	}
	
	public void addDependency(String gav){
		if(!dependencies.contains(gav)){
			dependencies.add(gav);
		}
	}
	
	public Iterator<String> getDependenciesIterator(){
		return dependencies.iterator();
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
    
    public void resolve(int amount){
    	resolve(amount, Aether.RESOLVE);
    }
    
    public void resolve(int amount, int perform){
    	System.out.println("resolving "+amount+" dependencies");
    	CollectRequest collectRequest = new CollectRequest();
		collectRequest.addRepository(remoteRepository);
		Iterator<String> deps = getDependenciesIterator();

		for(RemoteRepository mirror: mirrors){
			collectRequest.addRepository( mirror );
		}
		
        for(int i = 0;i<amount;i++){
        	if(deps.hasNext()){
        		String gav = deps.next();
	    		Artifact art = new DefaultArtifact(gav);
	        	Dependency dep = new Dependency(art,"compile");
	        	collectRequest.addDependency(dep);
		        deps.remove();
        	}else{
        		break;
        	}
        }
        
        DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, null );
        
        if(perform == Aether.RESOLVE){
	        //DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();
	    	//DependencyRequest dependencyRequest = new DependencyRequest( node, null );
	        try {
				system.resolveDependencies( session, dependencyRequest  );
			} catch (DependencyResolutionException e) {
				System.out.println("problem resolving dependencies: " + e.getMessage());
			}
        }else{
        	try {
				system.collectDependencies(session, collectRequest);
			} catch (DependencyCollectionException e) {
				System.out.println("problem collecting dependencies: " + e.getMessage());
			}
        }
	}
    
}
