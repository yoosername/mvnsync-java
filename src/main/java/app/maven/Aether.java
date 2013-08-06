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
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.wagon.WagonProvider;
import org.eclipse.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

import app.maven.listeners.ConsoleRepositoryListener;
import app.maven.providers.ManualWagonProvider;
import app.maven.utils.Helper;

public class Aether {
	private RepositorySystem system;
	private RepositorySystemSession session;
	
	private LocalRepository localRepository;
	private List<RemoteRepository> mirrors = new ArrayList<RemoteRepository>();
	private RemoteRepository remoteRepository;
	private int max;
	private boolean hasMax = false;
	private List<String> failedDownloads = new ArrayList<String>();
	private int downloaded = 0;
	
	public Aether(String local){
		setLocalRepository(local);
		newRepositorySystem();
		newSession();	
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
    
    public void resolveAndRetry(Iterator<Dependency> deps){
    	while(deps.hasNext()){
    		try {
				deps = resolveDependencies(deps);
			} catch (DependencyCollectionException e) {
				System.out.println("Problem collecting dependency: " + e.getMessage());
				//failedDownloads.add(Helper.calculateGav(e.getResult().getRoot().getArtifact()));
			} catch (DependencyResolutionException e) {
				System.out.println("Problem resolving dependency: " + e.getMessage());
				//failedDownloads.add(Helper.calculateGav(e.getResult().getRoot().getArtifact()));
			}
    	}
    	System.out.println("Finished resolving dependencies");
    	if(!failedDownloads.isEmpty()){
    		System.out.println("The following dependencies failed:");
    		for(String gav: failedDownloads){
        		System.out.println(gav);
        	}
    	}	
    }
    
    public Iterator<Dependency> resolveDependencies(Iterator<Dependency> deps) throws DependencyCollectionException, DependencyResolutionException{
		if(deps.hasNext()){
	    	CollectRequest collectRequest = new CollectRequest();
			collectRequest.addRepository(remoteRepository);

			for(RemoteRepository mirror: mirrors){
				collectRequest.addRepository( mirror );
			}
			
	        while(deps.hasNext()){
	        	if(!(hasMax() && downloaded >= getMax())){
	        		Dependency dep = deps.next();
		        	if(! failedDownloads.contains(Helper.calculateGav(dep.getArtifact()))){        	
			        	collectRequest.addDependency(dep);
			        	deps.remove();
			        	downloaded++;
		        	}
	        	}
	        }
	        
	       	//system.collectDependencies(session, collectRequest);
	    	DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();
	        DependencyRequest dependencyRequest = new DependencyRequest( node, null );
	        system.resolveDependencies( session, dependencyRequest  );
		}
        return deps;
	}
}
