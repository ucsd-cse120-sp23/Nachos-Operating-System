package nachos.vm;

import java.lang.reflect.Array;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;
import java.util.HashMap;

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
	protected boolean loadSections() {
		if (numPages > UserKernel.getNumOfFreePages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		// allocate page table, size of pagetable should be numPages as
		// this is a linear style table
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
			// extract the current page table entry specified by the virtual page number
			TranslationEntry pEntry = pageTable[virtualPageNum];
			// check if page table entry is valid 
			if(!pEntry.valid){
				// if preparing a demanded page failed, break out the loop
				// bad address = vpn * pagesize
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
			// extract the current page table entry specified by the virtual page number
			TranslationEntry pEntry = pageTable[virtualPageNum];
			// check if page table entry is valid 
			if(!pEntry.valid){
				// if preparing a demanded page failed, break out the loop
				// bad address = vpn * pagesize
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
			// determine cause of fault either out of physical pags
				if (UserKernel.getNumOfFreePages() == 0){
					selectVictimPage();
				}
			// or entry is invalid 
				int badAddr = processor.readRegister(processor.regBadVAddr);
				if (prepareDemandedPage(badAddr)){
					return;
				}
				super.handleException(cause);
				break;
			default:
				super.handleException(cause);
				break;

		}
	}

	/**
	 * This method 
	 * @param p processor object 
	 * @return boolean if page was allocated
	 */
	public boolean prepareDemandedPage(int badAddress) {
		// extract the bad virtual adress 
		// int badAddress = processor.readRegister(Processor.regBadVAddr);
		// extract the bad virtual page number from the bad virtual address
		
		int badVPN = Processor.pageFromAddress(badAddress);

		// get the page table entry for the bad virtual page number
		TranslationEntry pTEntry = super.pageTable[badVPN];

		// if the virtual page number is an invalid one, return false
		if (badVPN < 0 || badVPN >= super.pageTable.length){
			return false;
		}
		
		// if faulting page is from coff sections 
		// load page into memory 
		for (int s = 0; s < coff.getNumSections(); s++) {
			// extract the current coff section
			CoffSection section = coff.getSection(s);
			// get the current sections low bound virtual page number
			int lowVPN = section.getFirstVPN();
			// get the current sections upper bound virtual page number
			int upperVPN = lowVPN + section.getLength();
			
			// checking to see if faulting page is within current section
			if (badVPN >= lowVPN && badVPN < upperVPN){
				// allocate a physical page
				int ppn = UserKernel.allocatePage();
				// check if the physical page was allocated
				if (ppn == -1) {
					return false;
				}
				// get current process id 
				int pID = super.getCurrentID();
				// store the mapping of the physical page to the current process
				updateIPTLock.acquire();
				invertedPageTable.put(ppn,pID);
				updateIPTLock.release();

				// load section into physical 
				section.loadPage(badVPN - lowVPN, ppn);
				//update corresponding bits to valid 
				pTEntry.ppn = ppn;
				pTEntry.valid = true;
				pTEntry.readOnly = section.isReadOnly();
				pTEntry.used = true;
				return true;
			}

		}
		// else if faulting page is from its any other section 
		// allocate physical page
		int ppn = UserKernel.allocatePage();
		// check if the physical page was allocated
		if (ppn == -1) {
			return false;
		}
		// get current process id
		int pID = super.getCurrentID();
		// acquire the lock to update the IPT
		updateIPTLock.acquire();
		// store the mapping of the physical page to the current process
		invertedPageTable.put(ppn,pID);
		// release the lock
		updateIPTLock.release();
		// zero contents of page
		// create a new array that is completely zeroed out of size "pageSize"
		byte[] zeroedArray = new byte[pageSize];
		// source, source pos, dest. dest pos, length
		System.arraycopy(zeroedArray, 0, Machine.processor().getMemory(), ppn * pageSize, pageSize);

		// update entries 
		pTEntry.ppn = ppn;
		pTEntry.valid = true;
		pTEntry.readOnly = false;
		pTEntry.used = true;

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
	
	// data structure that represents an inverted page table, mapping ppn's (key)
	// to a user processes' PID (value)
	private static HashMap<Integer, Integer> invertedPageTable;
	
	/**
	 * This method is a clock algorithm method designed to select a victim page
	 * to evict from memory
	 * @return the victim page to be evicted
	 */
	public static int selectVictimPage(){
		//
		updateClockHandLock.acquire();
		// check the used bit for each translationEntry
		//while(pageTable[clockHand].used == true) {
			// set the used bit to 0
		//	pageTable[clockHand].used = false;
			// increment the clockhand

		//}


		updateClockHandLock.release();
		
		return 0;
	}


}
