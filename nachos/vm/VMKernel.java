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
		updatePPMapLock = new Lock();
		cvLock = new Lock();
		waitForUnPin = new Condition(cvLock);
		initializePpnPinMap();
		freeSpnList = new LinkedList<Integer>();
		freeSPNLock = new Lock();
		updateCurrSPNLock = new Lock();
		invertedPageTable = new HashMap<Integer, Entry<Integer,Integer>>();
		swapFile =  ThreadedKernel.fileSystem.open("swapFile", true);
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
		swapFile.close();
		ThreadedKernel.fileSystem.remove("swapFile");
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


	// map physical pages to if pinned
	public static HashMap<Integer, Boolean> ppnPinMap;

	// declare a lock for free spn editing
	private static Lock freeSPNLock;

	// declare a lock for updating the current spn
	private static Lock updateCurrSPNLock;
	
	// declare a lock for updating the ppnPin map
	private static Lock updatePPMapLock;

	// initializing condition variable 
	private static Lock cvLock;
	protected static Condition waitForUnPin;

	// declare a freeSpnList, which is used for SPNS for our swap file
	private static LinkedList<Integer> freeSpnList;

	// declare a currentSPN variable to hold the last allocated SPN
	private static int currentSPN = 0;

	// constant for SPN increase
	private final static int NUM_OF_SPNS_TO_ALLOCATE = 5;

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
		// allocate a total of 100 additional spns
		for(int i = currentSPN; i < currentSPN + NUM_OF_SPNS_TO_ALLOCATE; i++){
			deallocateSPN(i);
		}
		updateCurrSPNLock.acquire();
		currentSPN += NUM_OF_SPNS_TO_ALLOCATE;
		updateCurrSPNLock.release();
	}

	/**
	 * pin a page to the ppnPinMap
	 * 
	 * @param ppn physical page to pin
	 */
	public static boolean pin(int ppn, int pID) {
		updatePPMapLock.acquire();
		// check so a process can't pin another process's page
		if(pID != invertedPageTable.get(ppn).getKey()) {
			updatePPMapLock.release();
			return false;
		}
		// check that a valid page is being pinned (already exists in hashmap)
		if(ppnPinMap.get(ppn) == null) {
			updatePPMapLock.release();
			return false;
		}
		ppnPinMap.put(ppn, true);
		updatePPMapLock.release();
		return true;
	}
	
	/**
	 * unpin a page by setting its value to false
	 * 
	 * @param ppn physical page to unpin
	 * @return true on success, false on failure
	 */
	public static boolean unpin(int ppn, int pID) {
		updatePPMapLock.acquire();
		// need to check if current process is owner of pinned page 
		if (pID != invertedPageTable.get(ppn).getKey()){
			updatePPMapLock.release();
			return false;			
		}
		// check if the ppn exists within the hashmap
		if(ppnPinMap.get(ppn) == null) {
			updatePPMapLock.release();
			return false;
		}
		boolean ifAllPinned = checkAllPagesPinned();
		ppnPinMap.put(ppn, false);
		if(ifAllPinned) {
			waitForUnPin.wake();
		}
		updatePPMapLock.release();
		return true;
	}
	/**
	 * Iterate through the hashmap and check if all pages are pinned
	 * 
	 * @return true of all pages pinned, false if at least one page is unpinned
	 */
	public static boolean checkAllPagesPinned(){
		// if the ppnPin map is empty, no pages are pinned
		if(ppnPinMap.isEmpty()){
			return false;
		}
		// else if it is non empty, iterate through all pages and check if at least one of them is unpinned
		for (Iterator<Integer> keys = ppnPinMap.keySet().iterator(); keys.hasNext();) {
			// get the current key, a ppn
			Integer	 ppn = keys.next();
			// get the current value, boolean if pinned
			Boolean isPinned = ppnPinMap.get(ppn);
			// if any value is false (page is unpinned) then return false
			if (!isPinned) {
				return false;
			}
		}
		// other wise every page is pinned
		return true;		
	}

	/**
	 * This method initializes the ppn to pin map
	 */
	public static void initializePpnPinMap() {
		updatePPMapLock.acquire();
		// make a new ppn to pin map
		ppnPinMap = new HashMap<Integer, Boolean>();
		// get the number of physical pages
		int numPhysPages = Machine.processor().getNumPhysPages();
		// initialize the ppn to pin mapping to have all pages unpinned
		for (int index = 0; index < numPhysPages; index++) {
			// add the page to the map, and set it as unpinned
			ppnPinMap.put(index, false);
		}
		updatePPMapLock.release();
	}

	/**
	 * this method checks if the specified ppn is pinned or not
	 * @param ppn 
	 * @return true if the ppn is pinned, else false if not
	 */
	public static boolean isPinned(int ppn) {
		// if the ppn is null, then return false
		if(ppnPinMap.get(ppn) == null){
			// return false because it does not exist
			return false;
		}
		// return the pin status od the specified ppn
		return ppnPinMap.get(ppn);
	}

	/**
	 * This method prints the current ppn to pin mappings (debugging purposes)
	 */
	public static void printPpnPinMap() {
		// get the number of physical pages
		int numPhysPages = Machine.processor().getNumPhysPages();
		System.out.println("------PPN PIN MAP -------");
		for (int index = 0; index < numPhysPages; index++) {
			// print out each entry
			System.out.println("ppn: " + index + "| isPinned: " + ppnPinMap.get(index));
		}
	}
}
