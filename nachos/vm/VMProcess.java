package nachos.vm;

import java.lang.reflect.Array;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	@Override
	protected boolean loadSections() {
		System.out.println("loading sections from VMKernal ");
		// // if (numPages > UserKernel.getNumOfFreePages()) {
		// 	coff.close();
		// 	Lib.debug(dbgProcess, "\tinsufficient physical memory");
		// 	// return false;
		// }
		pageTable = new TranslationEntry[numPages];
		// boolean isReadable variable for if the section is readable
		boolean isReadable;
		// page table entry reference
		TranslationEntry pageTableEntry;
		// physical page number reference
		int vpn = 0;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				// get the virtual page number
				vpn = section.getFirstVPN() + i;
				// check if vpn is within bounds of memory
				if (vpn < 0 || vpn >= numPages) {
					return false;
				}
				// allocate a physical page
				// set if the page isReadable according to the section constraint
				isReadable = section.isReadOnly();
				// create new table entry with valid set to false
				pageTableEntry = new TranslationEntry(vpn, -1, false, isReadable, false, false);
				// insert the entry into the page table
				pageTable[vpn] = pageTableEntry;

			}
		}
		// reserve pages for stack and args
		for (int i = 0; i < stackPages + 1; i++) {
			// vpns are contigous so keep incrementing where previous value left off
			vpn++;
			if (vpn < 0 || vpn >= numPages) {
				return false;
			}
			// allocate a physical page
			// check if the physical page was allocated
			// create new table entry
			pageTableEntry = new TranslationEntry(vpn, -1, false, false, false, false);
			// insert the entry into the page table
			pageTable[vpn] = pageTableEntry;
		}

		return true;	
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied). If the valid bit is not valid, we need to demand a page
	 * from the operating system, else it will break if nothing was allocated
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 *
	 */
	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		// extract current memory
		byte[] memory = Machine.processor().getMemory();

		// make copies of the virtual address and offset
		int currVaddr = vaddr;
		int currOffset = offset;
		// set a local for the total number of bytes read
		int totalBytesRead = 0;
		// set bytes to read to the length
		int bytesToRead = length;

		// address translation for bytes to read
		
		while (bytesToRead > 0 && currOffset < data.length) {
			// get VPN from address
			int virtualPageNum = Processor.pageFromAddress(currVaddr);
			// check if the virtual page number is not out of bounds
			if (virtualPageNum < 0 || virtualPageNum >= pageTable.length) {
				break;
			}
			// System.out.println("reading VPN " + virtualPageNum);
			// extract the current page table entry specified by the virtual page number
			TranslationEntry pEntry = pageTable[virtualPageNum];
			// check if page table entry is valid 
			if(!pEntry.valid){
				// if preparing a demanded page failed, break out the loop
				// bad address = vpn * pagesize
				// System.out.println("faulting page" + virtualPageNum);
				if (!prepareDemandedPage(pEntry.vpn * pageSize)){
					break;
				}
			}
			// get offset from address
			int vaOffset = Processor.offsetFromAddress(currVaddr);
			// get ppn fron vpn
			int physicalPageNum = pageTable[virtualPageNum].ppn;
			// check of physical page num is not out of bounds
			if (physicalPageNum < 0 || physicalPageNum >= Machine.processor().getNumPhysPages()) {
				break;
			}

			// get the current process's pID
			int pID = super.getCurrentID();
			// pin the page when reading from it
			VMKernel.pin(physicalPageNum, pID);

			// compute physical address
			int physicalAddr = (pageSize * physicalPageNum) + vaOffset;
			// Adjust the number of bytes to read based on the remaining length of the
			// destination array
			int remainingLength = data.length - currOffset;
			int bytesRead = Math.min(bytesToRead, Math.min(pageSize - vaOffset, remainingLength));
			System.arraycopy(memory, physicalAddr, data, currOffset, bytesRead);
			// update page table entries 
			pEntry.used = true;
			// update the byte data, address data and offset date
			currVaddr += bytesRead;
			currOffset += bytesRead;
			totalBytesRead += bytesRead;
			bytesToRead -= bytesRead;
			// unpin the page when done reading from it
			VMKernel.unpin(physicalPageNum, pID);
		}
		// return the total number of bytes that were successfully read
		return totalBytesRead;
	}
	
	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied). If the valid bit is not valid, we need to demand a page
	 * from the operating system, else it will break if nothing was allocated.
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		// extract current memory
		byte[] memory = Machine.processor().getMemory();
		// make copies of the virtual address and offset
		int currVaddr = vaddr;
		int currOffset = offset;
		// set a local for the total number of bytes read
		int totalBytesWritten = 0;
		// set bytes to read to the length
		int bytesToWrite = length;
		// adress translation for bytes to read
		while (bytesToWrite > 0 && currOffset < data.length) {
			// get VPN from address
			int virtualPageNum = Processor.pageFromAddress(currVaddr);
			// check if the virtual page number is not out of bounds
			if (virtualPageNum < 0 || virtualPageNum >= pageTable.length) {
				break;
			}
			// System.out.println("writing to VPN " + virtualPageNum);

			// extract the current page table entry specified by the virtual page number
			TranslationEntry pEntry = pageTable[virtualPageNum];
			// check if page table entry is valid 
			if(!pEntry.valid){
				// if preparing a demanded page failed, break out the loop
				// bad address = vpn * pagesize
				// System.out.println("write fault VPN " + virtualPageNum);

				if (!prepareDemandedPage(pEntry.vpn * pageSize)){
					break;
				}
			}
			// get offset from address
			int vaOffset = Processor.offsetFromAddress(currVaddr);
			// get ppn fron vpn
			int physicalPageNum = pageTable[virtualPageNum].ppn;
			// check if physical page num is not out of bounds
			if (physicalPageNum < 0 || physicalPageNum >= Machine.processor().getNumPhysPages()) {
				break;
			}

			// get the current process's pID
			int pID = super.getCurrentID();
			// pin the page when writing to it
			VMKernel.pin(physicalPageNum, pID);

			// compute physical address
			int physicalAddr = (pageSize * physicalPageNum) + vaOffset;
			
			// Adjust the number of bytes to read based on the remaining length of the
			// destination array
			int remainingLength = data.length - currOffset;
			int bytesWritten = Math.min(bytesToWrite, Math.min(pageSize - vaOffset, remainingLength));
			System.arraycopy(data, currOffset, memory, physicalAddr, bytesWritten);
			// update page table entries 
			pEntry.used = true;
			pEntry.dirty = true;
			
			// update the byte data, address data and offset date
			currVaddr += bytesWritten;
			currOffset += bytesWritten;
			totalBytesWritten += bytesWritten;
			bytesToWrite -= bytesWritten;
			// unpin the page when done writing to it
			VMKernel.unpin(physicalPageNum, pID);
		}

		// return the total number of bytes that were successfully written
		return totalBytesWritten;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case pageFault:
				int badAddr = processor.readRegister(processor.regBadVAddr);
				if (prepareDemandedPage(badAddr)){
					// System.out.println("prepared page on demand successful...");
					return;
				}
				// System.out.println("failed to prepare page on demand, throwing execption...");
				super.handleException(cause);
				break;
			default:
				super.handleException(cause);
				break;

		}
	}
	/**
	 * This method prepares a page on demand
	 * @param p int badAddress which is the bad adress that caused the page fault
	 * @return boolean if page was allocated
	 */
	public boolean prepareDemandedPage(int badAddress) {
		pageFaultLock.acquire();
		// extract the bad virtual page number from the bad virtual address
		int badVPN = Processor.pageFromAddress(badAddress);
		// if the virtual page number is an invalid one, return false
		// System.out.println("VPN TO FIX: " + badVPN);
		if (badVPN < 0 || badVPN >= super.pageTable.length){
			pageFaultLock.release();
			return false;
		}
		// get the page table entry for the bad virtual page number
		TranslationEntry pTEntry = super.pageTable[badVPN];
		// System.out.println("-------- Before Allocation: ----------------");
		// UserKernel.printFreePages();
		// try to allocate a physical page
		int ppn = UserKernel.allocatePage();
		// failed to allocated a physical page, so prepare to evict a page via clock algo
		if (ppn == -1) {
			// if all the pages are then block this process
			while (VMKernel.checkAllPagesPinned()) {
				System.out.println("if this prints more than once then INFITE LOOP");
				pageFaultLock.release();
				VMKernel.waitForUnPin.sleep();
				pageFaultLock.acquire();
			}
			// call the clock algorithm, which returns an evicted pp
			ppn = selectVictimPage();
			// remove victim page from list 
			UserKernel.allocatePage();
		}

		int pID = super.getCurrentID();
		updateIPT(badVPN, ppn);
		// pin the page before reading COFF file/ swap file/ empty page for stack/args
		VMKernel.pin(ppn, pID);

		// System.out.println("-------- After Allocation: ------------");
		// UserKernel.printFreePages();
		// System.out.println("PPN ALLOCATED " + ppn);

		// if the entry is dirty, load it from the swap file
		if(pTEntry.dirty){
			boolean wasSwapped = loadFromSwapFile(badVPN, ppn, pTEntry);
			// failed to load from swap file
			if(!wasSwapped){
				VMKernel.unpin(ppn, pID);
				VMKernel.invertedPageTable.remove(ppn);
				pageFaultLock.release();
				return wasSwapped;
			// else successful load from swap
			} else {
				VMKernel.unpin(ppn, pID);
				pageFaultLock.release();
				return wasSwapped;	
			}
		} else {
			// if the page is coff, load from it
			if (loadFromCoffSection(badVPN, ppn, pTEntry)) {
				// printTranslationEntry(pTEntry);
				// printInvertedPageTable();
				VMKernel.unpin(ppn, pID);
				pageFaultLock.release();
				return true;
			// else if the page is stack/arg, load from it
			} else if(loadFromStackOrArgs(badVPN, ppn, pTEntry)){
				// printTranslationEntry(pTEntry);
				// printInvertedPageTable();
				VMKernel.unpin(ppn, pID);
				pageFaultLock.release();
				return true;
			} else {
				// failed to allocate a physical page
				VMKernel.unpin(ppn, pID);
				VMKernel.invertedPageTable.remove(ppn);
				pageFaultLock.release();
				return false;
			}
		}
	}
	public void updateIPT(int badVPN, int ppn){
		// System.out.println("update IPT badvpn :"+ badVPN +" to ppn "+ ppn);
		// get current process id 
		updateIPTLock.acquire();
		int pID = super.getCurrentID();
		// store the mapping of the physical page to the current process
		// store process ID with VPN mapping will be useful for checking process' page table
		Entry<Integer, Integer> pidVPNEntry = new SimpleEntry<Integer, Integer>(pID, badVPN);
		VMKernel.invertedPageTable.put(ppn, pidVPNEntry);
		updateIPTLock.release();

	}
	
	public boolean loadFromCoffSection(int badVPN, int ppn, TranslationEntry pTEntry){
		// if page is clean 
		for (int s = 0; s < coff.getNumSections(); s++) {
			// extract the current coff section
			CoffSection section = coff.getSection(s);
			// get the current sections low bound virtual page number
			int lowVPN = section.getFirstVPN();
			// get the current sections upper bound virtual page number
			int upperVPN = lowVPN + section.getLength();
			
			// checking to see if faulting page is within current section
			if (badVPN >= lowVPN && badVPN < upperVPN){
				// System.out.println("loading from coffsection....");
				// load section into physical 
				section.loadPage(badVPN - lowVPN, ppn);
				//update corresponding bits to valid 
				pTEntry.ppn = ppn;
				pTEntry.valid = true;
				pTEntry.readOnly = section.isReadOnly();
				pTEntry.used = true;
				// updateIPT(badVPN, ppn);

				return true;
			}
		}
		// was not in the coff section, so return false
		return false;
	}

	public boolean loadFromStackOrArgs(int badVPN, int ppn, TranslationEntry pTEntry){
		// System.out.println("loading from stack/args");
		// create a new array that is completely zeroed out of size "pageSize"
		byte[] zeroedArray = new byte[pageSize];
		// source, source pos, dest. dest pos, length
		System.arraycopy(zeroedArray, 0, Machine.processor().getMemory(), ppn * pageSize, pageSize);

		// update entries 
		pTEntry.ppn = ppn;
		pTEntry.valid = true;
		pTEntry.readOnly = false;
		pTEntry.used = true;
		// updateIPT(badVPN, ppn);
		return true;
	}
	

	public boolean loadFromSwapFile(int badVPN, int ppn, TranslationEntry pTEntry) {
		// extract the spn from the entries ppn, as this is where we stored the spn
		// System.out.println("vpn to access: " + badVPN);
		// printVPNTOSPNMAP();
		int spn = vpnToSpnMap.get(badVPN);
		// read the page from swap file into physical memory
		int bytesRead = VMKernel.swapFile.read(spn * pageSize, Machine.processor().getMemory(), ppn * pageSize, pageSize);
		if(bytesRead == -1) {
			return false;			
		}
		pTEntry.ppn = ppn;
		pTEntry.valid = true;
		pTEntry.used = true;
		// updateIPT(badVPN, ppn);
		vpnToSpnMap.remove(badVPN);
		VMKernel.deallocateSPN(spn);

		return true;
	}
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static final int pageFault = Processor.exceptionPageFault;
	
	// initialize this clock hand to be 0
	private static int clockHand = 0;


	// initialize a lock for synchronization and editing of the clock hand static varaible
	private static Lock updateClockHandLock = new Lock();
	private static Lock updateIPTLock = new Lock();
	private static Lock pageFaultLock = new Lock();
	
		
	/**
	 * This method is a clock algorithm method designed to select a victim page
	 * to evict from memory
	 * @return the victim page to be evicted
	 */
	public int selectVictimPage(){
		updateClockHandLock.acquire();
		// System.out.println("evicting page ...");
		int ppn = clockHand;
		// int initialClockHand = clockHand;
		
		// loop through the physical pages until a page to evict is found
		while(true){
			// printCurrentProcess();
			// System.out.println("current clock hand/ppn checking: " + ppn);
			// printInvertedPageTable();

			// check that the physical page is actually in use by a process, also check if a page is pinned
			if(VMKernel.invertedPageTable.get(ppn) == null || VMKernel.isPinned(ppn)) {
				// check the next physical page
				ppn = (ppn + 1) % Machine.processor().getNumPhysPages();
				continue;
			}
			// extract process and VPN entry from the PPN
			Entry<Integer, Integer> pIDToVPNEntry = VMKernel.invertedPageTable.get(ppn);
			// extract the PID
			Integer pIDFromPage = pIDToVPNEntry.getKey();
			// extract the VPN
			int vpn = pIDToVPNEntry.getValue();
			// extract the UserProcess Object
			UserProcess processFromPage = UserProcess.currentProcesses.get(pIDFromPage);
			// extract the translationEntry associated with the VPN
			TranslationEntry ptEntry = processFromPage.getTranslationEntry(vpn);
			// check entry used bit
			if (ptEntry.used){
				// if used then set to false and check next entry
				ptEntry.used = false;
				// increment ppn 
				ppn = (ppn + 1) % Machine.processor().getNumPhysPages();

				// System.out.println("ppn incremented");
			}
			// found page to evict
			else {
				// System.out.println("evicting page " + ppn + "\n -----page table before------- ");
				// printProcessPageTable(processFromPage);
				int spn = -1;
				// if page is dirty must write out to swap
				if (ptEntry.dirty) {
					// if all free spns have been taken, add more spns to the list
					if(VMKernel.getNumOfFreeSPNS() == 0){
						VMKernel.addMoreSPNSToList();
					}
					
					// get spn number to write in file
					spn = VMKernel.allocateSPN();
					// System.out.println("Swap file page number "+spn);
					// System.out.println("mapping vpn: "+ vpn + " spn "+ spn);
					// store mapping for vpn to spn for swapping back in
					processFromPage.setVPNToSPNMap(vpn, spn);
					// write physical page to swap file at the position indicated by the spn
					int bytesWritten = VMKernel.swapFile.write(spn * pageSize, Machine.processor().getMemory(), ppn * pageSize, pageSize);
					if(bytesWritten == -1) {
						updateClockHandLock.release();
						return -1;
					}
				}
				
				//System.out.println("Invalidating entry breaking loop...");

				// invalidate Valid entry 
				ptEntry.valid = false;
				// ptEntry.ppn = -1;
				// set clockhand to point to the physical page next to the evicted page
				clockHand = (ppn + 1) % Machine.processor().getNumPhysPages();
				
				// remove the entry from the IPT
				updateIPTLock.acquire();
				VMKernel.invertedPageTable.remove(ppn);
				updateIPTLock.release();

				// add this page back to the list of free physical pages
				// System.out.println("num free pages: " + UserKernel.getNumOfFreePages());
				UserKernel.deallocatePage(ppn);
				// System.out.println("num free pages after deallocate: " + UserKernel.getNumOfFreePages());
				updateClockHandLock.release();
				// returns the page that was evicted
				// System.out.println("---------page table after----------- ");
				// printProcessPageTable(processFromPage);

				return ppn;
			}
		} 
	}

	/**
	 * this method simply prints out the inverted page table
	 */
	public static void printInvertedPageTable() {
		System.out.println("-------------Inverted page table ------------- ");
		for (Iterator<Integer> ppns = VMKernel.invertedPageTable.keySet().iterator(); ppns.hasNext();) {
			Integer ppn = ppns.next();
			Entry<Integer, Integer> pIDtoVPN = VMKernel.invertedPageTable.get(ppn);
			Integer pIDFromPage = pIDtoVPN.getKey();
			// extract the VPN
			int vpn = pIDtoVPN.getValue();
			// extract the UserProcess Object
			UserProcess processFromPage = UserProcess.currentProcesses.get(pIDFromPage);
			System.out.print(" ppn: " + ppn);
			System.out.print(" current process owner: " + processFromPage);
			System.out.print(" process vpn: " + vpn + "\n");
		}

	}

	/**
	 * this method simply prints out the current process page table
	 */
	public static void printProcessPageTable(UserProcess currentProcess) {
		TranslationEntry[] currentPageTable = currentProcess.getPageTable();
		// TranslationEntry(int vpn, int ppn, boolean valid, boolean readOnly, boolean
		// used, boolean dirty)
		System.out.println("------------- Process Page Table ------------- ");
		for (int i = 0; i < currentPageTable.length; i++) {
			TranslationEntry currentTranslationEntry = currentPageTable[i];
			System.out.print("Translation Entry: [" + i + "] (");
			System.out.print(" | vpn: " + currentTranslationEntry.vpn);
			System.out.print(" | ppn: " + currentTranslationEntry.ppn);
			System.out.print(" | valid: " + currentTranslationEntry.valid);
			System.out.print(" | readOnly: " + currentTranslationEntry.readOnly);
			System.out.print(" | used: " + currentTranslationEntry.used);
			System.out.print(" | dirty: " + currentTranslationEntry.dirty + ")\n");
		}
	}

	public static void printTranslationEntry(TranslationEntry currentTranslationEntry){
			System.out.print("Translation Entry: (");
			System.out.print(" | vpn: " + currentTranslationEntry.vpn);
			System.out.print(" | ppn: " + currentTranslationEntry.ppn);
			System.out.print(" | valid: " + currentTranslationEntry.valid);
			System.out.print(" | readOnly: " + currentTranslationEntry.readOnly);
			System.out.print(" | used: " + currentTranslationEntry.used);
			System.out.print(" | dirty: " + currentTranslationEntry.dirty + ")\n");
	}
	
	public void printVPNTOSPNMAP(){
		// if the ppnPin map is empty, no pages are pinned
		if(vpnToSpnMap.isEmpty()){
			System.out.println("vpn to spn map is empty!");
			return;
		}
		printCurrentProcess();
		System.out.println("---------VPNTOSPN--------SIZE:" + vpnToSpnMap.size());
		// else if it is non empty, iterate through all pages and check if at least one of them is unpinned
		for (Iterator<Integer> keys = vpnToSpnMap.keySet().iterator(); keys.hasNext();) {
			// get the current key, a ppn
			Integer	 vpn = keys.next();
			// get the current value, boolean if pinned
			Integer spn = vpnToSpnMap.get(vpn);
			System.out.println("VPN: " + vpn + " mapped to SPN: " + spn);
		}
	}

	public void printCurrentProcess(){
		int pid = super.getCurrentID();
		System.out.println("CURRENT PROCESS: " + super.currentProcesses.get(pid));
	}
	
}
