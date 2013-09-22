package app.maven.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.index.IteratorResultSet;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.repository.RemoteRepository;

import app.maven.Aether;
import app.maven.MavenSearcher;
import app.maven.RoundRobin;
import app.maven.utils.VerifyPomFinder;

public class SynchroniserCli {

	private static Aether aether;
	private static Options options;
	private static CommandLine cmd;
	private static List<Option> required = new ArrayList<Option>();
	
	public static void main(String[] args) throws ExistingLuceneIndexMismatchException, IllegalArgumentException, ComponentLookupException, PlexusContainerException, IOException {
		options = buildOptions();
		cmd = parseOptions(options,args);
		
		// Validate CLI args
		if(cmd == null){
			System.out.println("Error parsing cmdline args");
			System.exit(1);
		}
		
		if(cmd.hasOption("help")){
			dieWithUsage();
		}
		
		if(cmd.hasOption("print") || cmd.hasOption("summary")){
			required.remove(options.getOption("remoteRepository"));
		}
		
		boolean missing = false;
		
		for( Option opt: required ){
			if(!cmd.hasOption(opt.getLongOpt())){
				System.out.println("Missing required argument: " + opt.getLongOpt());
				missing = true;
			}
		}
		
		if(missing){
			dieWithUsage();
		}
		
		// Setup new Aether (downloader)
		aether = new Aether(cmd.getOptionValue("localRepository"));
				
		if(cmd.hasOption("skipExisting")){
			aether.setSkipExisting(true);
		}
		
		if(cmd.hasOption("remoteRepository")){
			aether.setRemoteRepository(cmd.getOptionValue("remoteRepository"));
			aether.addMirror(cmd.getOptionValue("remoteRepository"));
		}
		
		if(cmd.hasOption("groupId")){
			aether.setGroupId(cmd.getOptionValue("groupId"));
		}
		
		if(cmd.hasOption("artifactId")){
			aether.setArtifactId(cmd.getOptionValue("artifactId"));
		}
		
		if(cmd.hasOption("max")){
			aether.setMax(Integer.parseInt(cmd.getOptionValue("max")));
		}
		
		if(cmd.hasOption("mirrors")){
			List<String> mirrors = Arrays.asList(cmd.getOptionValue("mirrors").split("\\s*,\\s*"));
			for(String mirror: mirrors){
				aether.addMirror(mirror);
			}
		}		
		
		// Setup new MavenSearcher (lucene index)
		MavenSearcher searcher = new MavenSearcher(aether);
		
		if(cmd.hasOption("index")){
			searcher.setRemoteIndex(cmd.getOptionValue("index"));
		}
		
		if(cmd.hasOption("types")){
			for(String type: cmd.getOptionValue("types").split("\\s*,\\s*")){
				searcher.addType(type);
				aether.addType(type);
			}
		}else{
			searcher.addType("jar");
			aether.addType("jar");
		}
		
		if(cmd.hasOption("maxThreads")){
			aether.setMaxThreads(Integer.parseInt(cmd.getOptionValue("maxThreads")));
		}
		
		// Main
		if(cmd.hasOption("fixMissingPoms")){
			ExecutorService executor = Executors.newFixedThreadPool(aether.getMaxThreads());
			RoundRobin<RemoteRepository> roundRobin = new RoundRobin<RemoteRepository>(aether.getMirrors());
			Iterator<RemoteRepository> m = roundRobin.iterator();
			VerifyPomFinder finder = new VerifyPomFinder(aether.getLocalRepository(),executor,m); //every jar should have at least one parent pom
	        Files.walkFileTree(aether.getLocalRepository().getBasedir().toPath(), finder);
	        executor.shutdown();
	        while (!executor.isTerminated()) {}
			finder.done();
		}else if(cmd.hasOption("summary")){
			if(cmd.hasOption("remoteRepository") || cmd.hasOption("index")){
				// print summary of remote versus local
				try{
					searcher.setupIndexer();
					searcher.summary(searcher.loadDependenciesFromIndex());
					searcher.closeIndex();
				}catch(Exception e){
					System.out.println("Error creating remote summary: " + e.getMessage());
				}
			}else{
				System.out.println("No index specified to search");
			}
		}else if(cmd.hasOption("print")){
			if(cmd.hasOption("remoteRepository") || cmd.hasOption("index")){
				// print lucene search results to the console
				try{
					searcher.setupIndexer();
					searcher.print(searcher.loadDependenciesFromIndex());
					searcher.closeIndex();
				}catch(Exception e){
					System.out.println("Error printing local artifacts: " + e.getMessage());
				}
			}else{
				System.out.println("No index specified to search");
			}
		}else{
			try{
				// download lucene search results using download workers
				searcher.setupIndexer();
				searcher.updateIndex();
				IteratorResultSet results = searcher.loadDependenciesFromIndex();
				aether.directDownload(results);
				searcher.closeIndex();
			}catch(Exception e){
				System.out.println("Error downloading artifacts: " + e.getMessage());
			}
		}
		System.out.println("Finished");
	}
	
	@SuppressWarnings("static-access")
	private static Options buildOptions(){
		Option help = new Option( "h", "print usage" );
		help.setLongOpt("help");
		Option remoteRepo = OptionBuilder.withArgName("url").hasArg().withLongOpt("remoteRepository").withDescription("remote repository").create("r");
		Option localRepo = OptionBuilder.withArgName("path").hasArg().withLongOpt("localRepository").withDescription("local repository").create("l");
		required.add(localRepo);
		Option max = OptionBuilder.withArgName("int").hasArg().withLongOpt("max").withDescription("maximum dependencies to download").create("M");
		Option mirrors = OptionBuilder.withArgName("[mirror[,]]").hasArg().withLongOpt("mirrors").withDescription("comma seperated list of mirrors to use").create("m");
		Option types = OptionBuilder.withArgName("[type[,]]").hasArg().withLongOpt("types").withDescription("comma seperated list of types for index search").create("t");
		Option groupId = OptionBuilder.withArgName("string").hasArg().withLongOpt("groupId").withDescription("limit to artifacts with this groupId").create("G");
		Option artifactId = OptionBuilder.withArgName("string").hasArg().withLongOpt("artifactId").withDescription("limit to artifacts with this artifactId").create("A");
		Option maxThreads = OptionBuilder.withArgName("int").hasArg().withLongOpt("maxThreads").withDescription("Maximum threads to allocate to the direct downloader").create("mt");
		Option index = OptionBuilder.withArgName("url").hasArg().withLongOpt("index").withDescription("Alternative lucene index to search").create("i");
		Option fixMissingPoms = OptionBuilder.withLongOpt("fixMissingPoms").withDescription("verify and download missing poms in local repo").create("fmp");
		Option summary = new Option( "s", "print summary and quit");
		summary.setLongOpt("summary");
		Option print = new Option( "p", "print artifact list and quit");
		print.setLongOpt("print");
		Option skipExisting = new Option( "se", "skip existing file(s)");
		skipExisting.setLongOpt("skipExisting");
		
		Options options = new Options();
		options.addOption(help);
		options.addOption(remoteRepo);
		options.addOption(localRepo);
		options.addOption(max);
		options.addOption(mirrors);
		options.addOption(types);
		options.addOption(summary);
		options.addOption(print);
		options.addOption(groupId);
		options.addOption(artifactId);
		options.addOption(maxThreads);
		options.addOption(index);
		options.addOption(skipExisting);
		options.addOption(fixMissingPoms);
		
		return options;
	}
	
	public static CommandLine parseOptions(Options opts, String[] args){
		CommandLineParser parser = new GnuParser();
		CommandLine line = null;
		
	    try {
	        line = parser.parse( opts, args );
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
	        line = null;
	    }
	    
	    return line;
	}
	
	private static void dieWithUsage(){
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "mvnsync", options );
		System.exit(0);
	}
}
