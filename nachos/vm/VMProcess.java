package nachos.vm;

import java.lang.reflect.Array;
import java.security.acl.LastOwnerException;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;

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
		int physPageNum;
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
				physPageNum = UserKernel.allocatePage();
				// check if the physical page was allocated
				if (physPageNum == -1) {
					return false;
				}
				// set if the page isReadable according to the section constraint
				isReadable = section.isReadOnly();
				// create new table entry with valid set to false
				pageTableEntry = new TranslationEntry(vpn, physPageNum, false, isReadable, false, false);
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
			physPageNum = UserKernel.allocatePage();
			// check if the physical page was allocated
			if (physPageNum == -1) {
				return false;
			}
			// create new table entry
			pageTableEntry = new TranslationEntry(vpn, physPageNum, false, false, false, false);
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
				if (prepareDemandedPage(processor)){
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
	public boolean prepareDemandedPage(Processor p) {
		// extract the bad virtual adress 
		int badAddress = p.readRegister(Processor.regBadVAddr);
		// extract the bad virtual page number from the bad virtual address
		int badVPN = Processor.pageFromAddress(badAddress);
		// get the page table entry for the bad virtual page number
		TranslationEntry pTEntry = super.pageTable[badVPN];
		// extract the page table entries phycsical page number
		int ppn = pTEntry.ppn;

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
				// create a new array that is completely zeroed out of size "pageSize"
				byte[] zeroedArray = new byte[pageSize];
				// source, source pos, dest. dest pos, length
				System.arraycopy(zeroedArray, 0, p.getMemory(), ppn, pageSize);
				
				// load section into physical 
				section.loadPage(badVPN, ppn);
				//update corresponding bits to valid 
				pTEntry.valid = true;
				pTEntry.readOnly = section.isReadOnly();
				pTEntry.used = true;
				return true;
			}

		}
		// else if faulting page is from its any other section 
		// zero contents of page
		// create a new array that is completely zeroed out of size "pageSize"
		byte[] zeroedArray = new byte[pageSize];
		// source, source pos, dest. dest pos, length
		System.arraycopy(zeroedArray, 0, p.getMemory(), ppn, pageSize);

		// update entries 
		pTEntry.valid = true;
		pTEntry.readOnly = false;
		pTEntry.used = true;


		return true;
	}
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static final int pageFault = Processor.exceptionPageFault;
}
