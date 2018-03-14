/**
 * CS 350, Computer Organization
 * Project 4: Cache Simulator
 * 
 * To run in Linux: java SimDriver < [chosen test file]
 * 
 * @author Kristin Pflug
 * @version 4/23/17
 */
import java.util.Scanner;
import java.util.ArrayList;

public class SimDriver {
	//StringBuilder that holds the results of the 
	//cache simulator for each reference
	StringBuilder result = new StringBuilder();
	
	//Scanner that reads stdin to get the information from the chosen test file
	Scanner input;
	
	//The number of sets
	int numSets;
	
	//The number of blocks/lines per set
	int setSize;
	
	//The size of each line
	int lineSize;
	
	//The number of hits and the number of misses, respectively
	int numHits = 0;
	int numMisses = 0;
	
	//Array to hold the oldest block in each set
	int[] oldestBlocks;
	
	//Boolean that indicates whether set is full
	boolean isSetFull = false;
	
	//The "cache" used for this simulator; each row's index corresponds to a 
	//cache line, the first element in each row holds the tag, the second line
	//holds the index, and the rest of the row holds the "data" 
	int[][] cache;
	
	//Constant integer representing the address size
	public static final int ADDRESS_SIZE = 32;
	
	//Constant integer representing an empty slot in the "cache"
	public static final int EMPTYSLOT = 999;
	
	/**
	 * main method
	 * 
	 * Starts the simulator
	 * @param args Optional command line arguments (not used)
	 */
	public static void main(String[] args){
		SimDriver c = new SimDriver();
		c.run();
	}
	
	/**
	 * run method
	 * 
	 * Pulls in data file from standard in, reads data and extracts 
	 * information needed for the rest of the program, then calls cacheSim()
	 */
	public void run(){
		//Initializes scanner and connects it to standard in
		input = new Scanner (System.in);

		//Parses the number of sets from the file
		numSets = Integer.parseInt(input.nextLine().replaceAll("[\\D]", ""));
		
		//Parses the number of blocks per set from the file
		setSize = Integer.parseInt(input.nextLine().replaceAll("[\\D]", ""));
		
		//Parses the block size from the file
		lineSize = Integer.parseInt(input.nextLine().replaceAll("[\\D]", ""));

		
		//Checks these values for possible errors
		if(errorCheck(numSets, setSize, lineSize)){
			System.out.println("Invalid data");
			System.exit(0);
		}
		
		//Initializes a 2d array where collections of rows map to a certain set 
		//(ex: 2 4-way set-associative cache = 8-row array; 
		//rows 1-4 -> set 1, rows 5-8 -> set 2)
		int numRows = numSets * setSize;
		cache = new int[numRows][lineSize + 1];
		
		//Flush the cache
		clearCache();
		
		//Initializes the oldestBlocks array
		oldestBlocks = new int[numSets];

		//Sets the initial oldest blocks for each set
		setOldestBlocks();
		
		//while loop, reading the lines of the file
		while(input.hasNext()){
			//Separate line using regexes, then set to respective variables
			String[] data = input.nextLine().split(":", 3);
			String address = data[0];
			String action = data[1];
			int bytesToRead = Integer.parseInt(data[2]);
			
			//Send this data to cacheSim() to run cache simulation
			cacheSim(address, action, bytesToRead);
		}
		
		//Print results of the cache simulation
		printTrace();
		
		//Clear out cache
		clearCache();
		
		//Close the scanner
		input.close();
	}
	
	/**
	 * cacheSim method
	 * 
	 * Run the cache simulation for the given entry
	 *  
	 * @param address String that holds hex representation of address
	 * @param action String that holds what action the entry is completing
	 * @param readAmt integer that holds the number of bytes to read
	 */
	public void cacheSim(String address, String action, int readAmt){
		//String that will hold the results for this entry
		StringBuilder resultLine = new StringBuilder();

		//Convert the address from hex to binary using toBinaryString()
		String binAddress = Integer.toBinaryString
											(Integer.parseInt(address, 16));
		
		//Ensures all binary representations of the addresses are 32 bits long
		for(int n = binAddress.length(); n < ADDRESS_SIZE; n++){
			binAddress = "0" + binAddress;
		}
		
		//Sees if the entry is for a read or a write
		if(action.equals("R")){
			resultLine.append("  " + "read\t");
		}else if(action.equals("W")){
			resultLine.append("  " + "write\t");
		}
		
		//Calculate length of index portion of binary string by taking
		//the log of the number of sets
		int indexLength = (int) (Math.log10(numSets)/Math.log10(2.0));

		//Calculate length of offset portion of binary string by taking
		//the log of the block size
		int offsetLength = (int) (Math.log10(lineSize)/Math.log10(2.0));

		//Calculate length of tag portion of binary string by subtracting
		//the index length and the offset length from the address size
		int tagLength = binAddress.length() - indexLength - offsetLength;
		
		
		//Calculate the value of the tag by extracting it from
		//the bit string, then converting it to decimal
		String tagBin = binAddress.substring(0, tagLength);
		int tag = Integer.parseInt(tagBin, 2);
		
		//Calculate the value of the index by extracting it from 
		//the bit string, then converting it to decimal
		String indexBin = binAddress.substring
										(tagLength, tagLength + indexLength);
		int index = Integer.parseInt(indexBin, 2);
		
		//Calculate the value of the offset by extracting it from
		//the bit string, then converting it to decimal
		String offsetBin = binAddress.substring(tagLength + indexLength);
		int offset = Integer.parseInt(offsetBin, 2);
		
		
		//Figure out which set the data will go into
		int set = setSize % numSets;
		
		//Starting row of the desired set
		int setRow = set * setSize;

		int memRefs = 0;
		String accessIs = "";
		
		//Initialize boolean variable that will indicate a hit or miss
		boolean isMatch = false;
		
		//Check if entry is a hit or a miss
		for(int i = 0; i < setSize; i++){
			if(cache[setRow+i][0] == tag &&
					cache[setRow + i][1] == index){
				isMatch = true;
				break;
			}
			else{
				isMatch = false;
			}
		}
		
		//Oldest block in the desired set
		int oldestBlock = oldestBlocks[set];
		
		//If entry is a hit, add to hit counter; else, add to miss counter
		//and call writeMiss()
		if(isMatch){
			numHits++;
			accessIs = "hit";
			memRefs = 0;
		}
		else{
			numMisses++;
			accessIs = "miss";
			memRefs = 1;

			//Finds the block in the set where the data will be placed
			int blockPlace = oldestBlock;
			for(int i = 0; i < setSize; i++){
				//Checks for any empty blocks
				if(cache[setRow + i][0] == 999){
					blockPlace = setRow + i;
					isSetFull = false;
					break;
				}
				isSetFull = true;
			}

			//Write entry into the cache at the given block
			writeMiss(blockPlace, tag, index);
			
			//Increment the oldest block when necessary
			if(blockPlace == oldestBlock && isSetFull){
				if(oldestBlock == (set * setSize)+(setSize-1)){
					oldestBlocks[set] = set * setSize;
				}else{
					oldestBlocks[set]++;
				}		
			}
		}
		
		
		//Append to the resultLine stringbuilder after each run
		resultLine.append("  " + address + "\t " + Integer.toHexString(tag) + 
				   "\t" + index + "\t "+ offset + " " + accessIs + 
				   "\t " + memRefs);
		
		//Append entry's result line to the overall result stringbuilder
		result.append(resultLine + "\n");
	}
	
	/**
	 * writeMiss method
	 * 
	 * Helper method for when result is a miss. Writes tag and index to block 
	 * in cache specified by insertHere.
	 */
	public void writeMiss(int insertHere, int tagToPlace, int index){
		cache[insertHere][0] = tagToPlace;
		cache[insertHere][1] = index;
	}
	
	/**
	 * clearCache method
	 * 
	 * Helper method to initialize the "cache" array in the beginning and 
	 * clear the cache after each run of the cache simulator. Fills all 
	 * elements in the array with 999, a number
	 * ((numSets * setSize) - 1)
	 */
	public void clearCache(){
		for(int i = 0; i < cache.length - 1; i++){
			for(int j = 0; j < cache[i].length - 2; j++){
				cache[i][j] = 999;
			}
		}
	}
	
	/**
	 * 
	 * @param numSets
	 * @param setSize
	 * @param lineSize
	 * @return true if there is an error, or false if there are none
	 */
	public boolean errorCheck(int numSets, int setSize, int lineSize){
		//check if lineSize > 8; if yes, error
		if(setSize > 8){
			System.out.println("ERROR: Associativity must be less than 8");
			return true;
		}
		
		if(lineSize < 4){
			System.out.println("ERROR: Line size must be at least 4");
			return true;
		}
		
		//check if they are all powers of 2 using bitwise operations
		if(((numSets & (numSets-1)) != 0) || ((lineSize & (lineSize-1)) != 0)){
			System.out.println("ERROR: Cache dimensions must be powers of 2");
			return true;
		}
		return false;
	}
	
	/**
	 * setOldestBlocks method
	 * 
	 * Sets the initial value of the oldestBlocks array, the oldest blocks in
	 * each set at the start
	 */
	public void setOldestBlocks(){
		for(int i = 0; i < setSize; i++){
			oldestBlocks[i] = i * setSize;
		}
	}
	
	/**
	 * printTrace method
	 * 
	 * Prints out the results of the cache simulator
	 */
	public void printTrace(){
		System.out.println("Cache Configuration\n");
		System.out.println("\t" + numSets + " " + setSize + "-way set " + 
													"associative entries");
		System.out.println("\t" + "of line size " + lineSize + " bytes\n\n");
		
		System.out.println("Results for Each Reference\n");
		System.out.println("Access Address" + "\t " + "Tag" + "\t" + "Index" 
												+ " Offset Result Memrefs");
		System.out.println("------ -------- ------- -----" 
												+ " ------ ------ -------");
		System.out.println(result + "\n");
		//use tabs
		System.out.println("Simulation Summary Statistics");
		System.out.println("-----------------------------");
		System.out.println("Total hits       : " + numHits);
		System.out.println("Total misses     : " + numMisses);
		System.out.println("Total accesses   : " + (numHits + numMisses));
		double hitRate = (double)numHits/(numHits + numMisses);
		System.out.println("Hit ratio        : " + hitRate);
		double missRate = (double)numMisses/(numHits + numMisses);
		System.out.println("Miss ratio       : " + missRate);
	}
}
