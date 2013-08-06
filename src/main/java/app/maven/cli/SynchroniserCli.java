package app.maven.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

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
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

import app.maven.Aether;
import app.maven.MavenIndexSearcher;
import app.maven.utils.Helper;

public class SynchroniserCli {

	private static Aether aether;
	private static Options options;
	private static CommandLine cmd;
	private static final int BATCH = 25;
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
		
		if(cmd.hasOption("max")){
			aether.setMax(Integer.parseInt(cmd.getOptionValue("max")));
		}
		
		if(cmd.hasOption("mirrors")){
			List<String> mirrors = Arrays.asList(cmd.getOptionValue("mirrors").split("\\s*,\\s*"));
			for(String mirror: mirrors){
				aether.addMirror(mirror);
			}
		}		
				
		Iterator<Dependency> deps;
		MavenIndexSearcher searcher = new MavenIndexSearcher(aether);
		
		if(cmd.hasOption("file")){
			System.out.println("Resolving dependencies from file: " + cmd.getOptionValue("file"));
			deps = getDependenciesFromFile();
			aether.resolveAndRetry(deps);
		}else{
			searcher.updateIndex();
			boolean moreToDo = true;
			while(moreToDo){
				deps = searcher.getDependenciesFromIndex(BATCH);
				if(!deps.hasNext()){
					moreToDo = false;
				}else{
					aether.resolveAndRetry(deps);
				}
			}
		}
		System.out.println("Finished");
	}
	
	private static Iterator<Dependency> getDependenciesFromFile(){
		Scanner sc = null;
		List<Dependency> deps = null;
		File depFile = new File(cmd.getOptionValue("file"));
		if(depFile.exists() && depFile.isFile()){
			try {
				sc = new Scanner(depFile);
				deps = new ArrayList<Dependency>();
				while (sc.hasNextLine()) {
					String gav = sc.nextLine();
					File localFile = new File(aether.getLocalRepository().getBasedir(),Helper.calculatePath(gav));
					if(!localFile.exists()){
						Artifact art = new DefaultArtifact(gav);
						Dependency dep = new Dependency(art, "compile");
						deps.add(dep);
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
		
		Options options = new Options();
		options.addOption(help);
		options.addOption(remoteRepo);
		options.addOption(localRepo);
		options.addOption(max);
		options.addOption(mirrors);
		options.addOption(file);
		
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
