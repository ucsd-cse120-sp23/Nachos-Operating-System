package nachos.vm;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		freeSpnList = new LinkedList<Integer>();
		freeSPNLock = new Lock();
		invertedPageTable = new HashMap<Integer, Entry<Integer,Integer>>();
		swapFile =  ThreadedKernel.fileSystem.open("swapFile",true);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	// data structure that represents an inverted page table, mapping PPNs (key)
	// to a user processes' PID and VPN (value)
	public static HashMap<Integer, Entry<Integer,Integer>> invertedPageTable;
	// swap file 
	public static OpenFile swapFile;




	// declare a lock for free spn editing
	private static Lock freeSPNLock;
	// declare a freeSpnList, which is used for SPNS for our swap file
	private static LinkedList<Integer> freeSpnList;

	// declare a currentSPN variable to hold the last allocated SPN
	private static int currentSPN = 0;

	// constant for SPN increase
	private final static int NUM_OF_SPNS_TO_ALLOCATE = 100;

	/**
	 * This method allocates a spn by removing a free spn from our linked list of
	 * free spns. It does things synchronously.
	 * 
	 * @return -1 if no spn was allocated, else return a number greater than -1
	 */
	public static int allocateSPN() {
		// we will return -1 if no page was allocated
		int spn = -1;
		// acquire the lock
		freeSPNLock.acquire();
		if (!freeSpnList.isEmpty()) {
			spn = freeSpnList.removeFirst();
		}
		// release the lock
		freeSPNLock.release();
		return spn;
	}

	/**
	 * This method deallocates page by adding a spn to our linked list of free
	 * pages.
	 * 
	 * @param spn number of spn that is free
	 */
	public static void deallocateSPN(int spn) {
		// acquire the lock
		freeSPNLock.acquire();
		// add a page to the linked list
		freeSpnList.add(spn);
		// release the lock
		freeSPNLock.release();
	}

	/**
	 * this method just returns the number of free spns in our freeSpn list
	 * @return size of the free spn list
	 */
	public static int getNumOfFreeSPNS() {
		return freeSpnList.size();
	}

	/**
	 * this method will add a certain amount of spns to the spnlist
	 */
	public static void addMoreSPNSToList(){
		// acquire the lock
		freeSPNLock.acquire();
		// allocate a total of 100 additional spns
		for(int i = currentSPN; i < currentSPN + NUM_OF_SPNS_TO_ALLOCATE; i++){
			deallocateSPN(i);
		}
		currentSPN += NUM_OF_SPNS_TO_ALLOCATE;
		// release the lock
		freeSPNLock.release();
	}
}
