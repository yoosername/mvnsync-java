package app.maven.test;

import org.junit.Test;

import app.maven.Aether;

public class AetherTest {

	@Test
	public void testNewRepositorySystem() {
		Aether aether = new Aether("C:\\Users\\Dell\\.m2\\junit-test-repo");
		aether.setRemoteRepository("http://repo1.maven.org/maven2");
		aether.newRepositorySystem();
		assert aether.getRepositorySystem() != null; 
	}
	
	@Test
	public void testNewRepositorySession() {
		Aether aether = new Aether("C:\\Users\\Dell\\.m2\\junit-test-repo");
		aether.setRemoteRepository("http://repo1.maven.org/maven2");
		aether.newRepositorySystem();
		aether.newSession();
		assert aether.getRepositorySession() != null; 
	}
	
}
