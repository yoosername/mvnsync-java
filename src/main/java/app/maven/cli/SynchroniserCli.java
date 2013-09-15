package app.maven.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IteratorResultSet;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import app.maven.Aether;
import app.maven.MavenSearcher;
import app.maven.utils.FileSearch;

public class SynchroniserCli {

	private static Aether aether;
	private static Options options;
	private static CommandLine cmd;
	private static List<Option> required = new ArrayList<Option>();
	
	public static void main(String[] args) throws ExistingLuceneIndexMismatchException, IllegalArgumentException, ComponentLookupException, PlexusContainerException, IOException {
		options = buildOptions();
		cmd = parseOptions(options,args);
		
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
		
		aether = new Aether(cmd.getOptionValue("localRepository"));
		aether.setMethod(Aether.COLLECT); // default - dont get transitive deps
		
		if(cmd.hasOption("remoteRepository")){
			aether.setRemoteRepository(cmd.getOptionValue("remoteRepository"));
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
				
		MavenSearcher searcher = new MavenSearcher(aether);
		
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
		
		if(cmd.hasOption("resolve")){
			aether.setMethod(Aether.RESOLVE);
		}
		
		if(cmd.hasOption("summary")){
			if(cmd.hasOption("remoteRepository")){
				// print summary of remote versus local
				try{
					searcher.setupIndexer();
					searcher.summary(searcher.loadDependenciesFromIndex());
					searcher.closeIndex();
				}catch(Exception e){
					System.out.println("Error creating remote summary: " + e.getMessage());
				}
			}else{
				try{
					FileSearch fs = new FileSearch(aether.getLocalRepository().getBasedir(),aether.getGroupId(),aether.getArtifactId());
					Set<ArtifactInfo> results = fs.getResults();
					searcher.summary(results.iterator());
				}catch(Exception e){
					System.out.println("Error creating local summary: " + e.getMessage());
				}
			}
		}else if(cmd.hasOption("print")){
			if(cmd.hasOption("remoteRepository")){
				try{
					searcher.setupIndexer();
					searcher.print(searcher.loadDependenciesFromIndex());
					searcher.closeIndex();
				}catch(Exception e){
					System.out.println("Error printing local artifacts: " + e.getMessage());
				}
			}else{
				try{
					FileSearch fs = new FileSearch(aether.getLocalRepository().getBasedir(),
										aether.getGroupId(),aether.getArtifactId(),
										aether.getTypes());
					Set<ArtifactInfo> results = fs.getResults();
					searcher.print(results.iterator());
				}catch(Exception e){
					System.out.println("Error printing remote artifacts: " + e.getMessage());
				}
			}
		}else if(cmd.hasOption("validate")){
			try{
				FileSearch fs = new FileSearch(aether.getLocalRepository().getBasedir(),aether.getGroupId(),aether.getArtifactId());
				Set<ArtifactInfo> results = fs.getResults();
				aether.resolve(results.iterator());
			}catch(Exception e){
				System.out.println("Error validating local artifacts: " + e.getMessage());
			}
		}else if(cmd.hasOption("file")){
			try{
				aether.setMethod(Aether.DIRECT);
				File file = new File(cmd.getOptionValue("file"));
				Iterator<ArtifactInfo> it = searcher.loadDependenciesFromFile(file);
				aether.directDownload(it);
			}catch(Exception e){
				System.out.println("Error downloading artifacts (from file): " + e.getMessage());
			}
		}else{
			try{
				aether.setMethod(Aether.DIRECT);
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
		Option max = OptionBuilder.withArgName("int").hasArg().withLongOpt("max").withDescription("maximum dependencies to resolve").create("M");
		Option mirrors = OptionBuilder.withArgName("[mirror[,]]").hasArg().withLongOpt("mirrors").withDescription("comma seperated list of mirrors to use").create("m");
		Option file = OptionBuilder.withArgName("path").hasArg().withLongOpt("file").withDescription("resolve GAV dependencies from this file").create("f");
		Option types = OptionBuilder.withArgName("[type[,]]").hasArg().withLongOpt("types").withDescription("comma seperated list of types for index search").create("t");
		Option groupId = OptionBuilder.withArgName("string").hasArg().withLongOpt("groupId").withDescription("limit to artifacts with this groupId").create("G");
		Option artifactId = OptionBuilder.withArgName("string").hasArg().withLongOpt("artifactId").withDescription("limit to artifacts with this artifactId").create("A");
		Option maxThreads = OptionBuilder.withArgName("int").hasArg().withLongOpt("maxThreads").withDescription("Maximum threads to allocate to the direct downloader").create("mt");
		Option validate = new Option( "v", "validate local dependencies only");
		validate.setLongOpt("validate");
		Option resolve = new Option( "rs", "resolve transitive dependencies");
		resolve.setLongOpt("resolve");
		Option summary = new Option( "s", "print summary and quit");
		summary.setLongOpt("summary");
		Option print = new Option( "p", "print artifact list and quit");
		print.setLongOpt("print");
		
		Options options = new Options();
		options.addOption(help);
		options.addOption(remoteRepo);
		options.addOption(localRepo);
		options.addOption(max);
		options.addOption(mirrors);
		options.addOption(file);
		options.addOption(types);
		options.addOption(summary);
		options.addOption(print);
		options.addOption(groupId);
		options.addOption(artifactId);
		options.addOption(validate);
		options.addOption(resolve);
		options.addOption(maxThreads);
		
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
