package app.maven.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.index.context.ExistingLuceneIndexMismatchException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import app.maven.Aether;
import app.maven.MavenSearcher;

public class SynchroniserCli {

	private static Aether aether;
	private static Options options;
	private static CommandLine cmd;
	private static final int BATCH = 50; //50 = best tested download rate out of 15,24,48,50,100
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
		aether.setRemoteRepository(cmd.getOptionValue("remoteRepository"));
		
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
			}
		}else{
			searcher.addType("jar");
		}
		
		if(cmd.hasOption("list")){
			searcher.setupIndexer();
			searcher.report();
		}else if(cmd.hasOption("validate")){
			searcher.loadDependenciesFromFileSystem();
			Iterator<String> deps = aether.getDependenciesIterator();
			while(deps.hasNext()){
				aether.resolve(BATCH,Aether.COLLECT);
			}
		}else if(cmd.hasOption("createBatchFiles")){
			String val = cmd.getOptionValue("createBatchFiles");
			if(val != null){
				int num = Integer.parseInt(val);
				if(num > 0){
					searcher.setupIndexer();
					searcher.updateIndex();
					searcher.createBatchFiles(Integer.parseInt(cmd.getOptionValue("createBatchFiles")));
				}else{
					System.out.println("Must be a number greater than 0");
					dieWithUsage();
				}
			}else{
				System.out.println("Missing argument: batch number");
				dieWithUsage();
			}
		}else{
			if(cmd.hasOption("file")){
				File file = new File(cmd.getOptionValue("file"));
				searcher.loadDependenciesFromFile(file);
			}else{
				searcher.setupIndexer();
				searcher.updateIndex();
				searcher.loadDependenciesFromIndex();
			}
			
			Iterator<String> deps = aether.getDependenciesIterator();
			while(deps.hasNext()){
				aether.resolve(BATCH);
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
		required.add(remoteRepo);
		required.add(localRepo);
		Option max = OptionBuilder.withArgName("int").hasArg().withLongOpt("max").withDescription("maximum dependencies to resolve").create("M");
		Option mirrors = OptionBuilder.withArgName("[mirror[,]]").hasArg().withLongOpt("mirrors").withDescription("comma seperated list of mirrors to use").create("m");
		Option file = OptionBuilder.withArgName("path").hasArg().withLongOpt("file").withDescription("resolve GAV dependencies from this file").create("f");
		Option types = OptionBuilder.withArgName("[type[,]]").hasArg().withLongOpt("types").withDescription("comma seperated list of types for index search").create("t");
		Option createBatches = OptionBuilder.withArgName("int").hasArg().withLongOpt("createBatchFiles").withDescription("create specified amount of gav batch files from index").create("c");
		Option groupId = OptionBuilder.withArgName("string").hasArg().withLongOpt("groupId").withDescription("limit to artifacts with this groupId").create("G");
		Option artifactId = OptionBuilder.withArgName("string").hasArg().withLongOpt("artifactId").withDescription("limit to artifacts with this artifactId").create("A");
		Option validate = new Option( "v", "validate local dependencies only");
		validate.setLongOpt("validate");
		Option list = new Option( "L", "print download summary and quit");
		list.setLongOpt("list");
		
		Options options = new Options();
		options.addOption(help);
		options.addOption(remoteRepo);
		options.addOption(localRepo);
		options.addOption(max);
		options.addOption(mirrors);
		options.addOption(file);
		options.addOption(types);
		options.addOption(list);
		options.addOption(createBatches);
		options.addOption(groupId);
		options.addOption(artifactId);
		options.addOption(validate);
		
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
