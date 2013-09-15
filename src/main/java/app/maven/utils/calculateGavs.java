package app.maven.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class calculateGavs {

	public static void main(String[] args) {
		//System.out.println("Printing GAV dependencies from file: c:\\development\\poms.txt");
		PrintWriter fileOut = null;
		Scanner sc = null;
		File file = new File("c:\\development\\poms.txt");
		
		if(file.exists() && file.isFile()){
			try {
				fileOut = new PrintWriter(new FileWriter("c:\\development\\required.txt"));
				sc = new Scanner(file);
				while (sc.hasNextLine()) {
					String gav = Helper.calculateGav(new File(sc.nextLine()));
					System.out.println(gav);
					fileOut.println(gav);
				}
			} catch (FileNotFoundException e) {
				System.out.println("file not found: " + e.getMessage());
			} catch (IOException e) {
				System.out.println("cant write to file: " + e.getMessage());
			} finally{
				sc.close();
				fileOut.close();
				//System.out.println("file closed");
			}
		}
	}

}
