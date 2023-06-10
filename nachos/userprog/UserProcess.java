package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.*;
import java.util.Map.Entry;
import java.lang.String;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// assign PID, synchronization supported
		updatePIDLock.acquire(); // // acquire the lock before assigning PID
		setCurrentID(getNextAvailablePID()); // assign a PID to the current process
		// check against the first root process for exit
		// only two cases, a process is the root, or it is not
		if (!rootProcessCreated) {
			isRootProcess = true;
			rootProcessCreated = true;
			setParentID(-1);
		} else {
			isRootProcess = false;
		}
		updatePIDLock.release(); // release the lock after assigning PID

		updateProcessLock.acquire();
		// put process in hashmap
		currentProcesses.put(getCurrentID(), this);
		// increment the total number of current processes
		totalProcesses++;
		updateProcessLock.release();

		// creates a new ArrayList of file descriptors that are all comprised of null
		// file descriptors
		// has an initial capacity of 16 file descriptors
		fileDescriptors = new OpenFile[MAX_FILE_TABLE_SIZE];

		// Initialize file descriptors for stdin and stdout
		// a file that can read this console.
		fileDescriptors[0] = UserKernel.console.openForReading(); // File descriptor 0: stdin
		// a file that can write this console.
		fileDescriptors[1] = UserKernel.console.openForWriting(); // File descriptor 1: stdout

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// make copies of the virtual address and offset
		int currVaddr = vaddr;
		int currOffset = offset;
		// set a local for the total number of bytes read
		int totalBytesRead = 0;
		// set bytes to read to the length
		int bytesToRead = length;

		// adress translation for bytes to read
		while (bytesToRead > 0 && currOffset < data.length) {
			// get VPN from address
			int virtualPageNum = Processor.pageFromAddress(currVaddr);

			// check if the virtual page number is not out of bounds
			if (virtualPageNum < 0 || virtualPageNum >= pageTable.length) {
				break;
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
			// max ammount of single copy
			// int bytesRead = Math.min(bytesToRead, pageSize - vaOffset); //
			// Math.min(length, pageSize - vaOffset);
			// Adjust the number of bytes to read based on the remaining length of the
			// destination array
			int remainingLength = data.length - currOffset;
			int bytesRead = Math.min(bytesToRead, Math.min(pageSize - vaOffset, remainingLength));
			System.arraycopy(memory, physicalAddr, data, currOffset, bytesRead);

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
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
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
			// max amount of single copy
			// int bytesWritten = Math.min(bytesToWrite, pageSize - vaOffset); //
			// Math.min(length, pageSize - vaOffset);
			// Adjust the number of bytes to read based on the remaining length of the
			// destination array
			int remainingLength = data.length - currOffset;
			int bytesWritten = Math.min(bytesToWrite, Math.min(pageSize - vaOffset, remainingLength));
			System.arraycopy(data, currOffset, memory, physicalAddr, bytesWritten);

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
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;
		if (!loadSections())
			return false;
		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
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
				// create new table entry
				pageTableEntry = new TranslationEntry(vpn, physPageNum, true, isReadable, false, false);
				// insert the entry into the page table
				pageTable[vpn] = pageTableEntry;
				// load page into physical memory
				section.loadPage(i, physPageNum);
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
			pageTableEntry = new TranslationEntry(vpn, physPageNum, true, false, false, false);
			// insert the entry into the page table
			pageTable[vpn] = pageTableEntry;
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// update linked list data structure
		int physPageNum;
		for (int i = 0; i < numPages; i++) {
			physPageNum = pageTable[i].ppn;
			UserKernel.deallocatePage(physPageNum);
			// empty out contents of page table
			pageTable[i] = null;
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/*
	 * Halt the Nachos machine by calling Machine.halt(). Only the root process
	 * (the first process, executed by UserKernel.run()) should be allowed to
	 * execute this syscall. Any other process should ignore the syscall and return
	 * immediately.
	 */
	private int handleHalt() {
		// check to see if the root process is calling halt
		if (!this.isRootProcess) {
			/**
			 * If another process attempts to invoke halt,
			 * the system should not halt and the handler should
			 * return immediately with -1 to indicate an error.
			 */
			return -1;
		} else {
			// HALT can only be invoked by the "root" process
			// - that is, the initial process in the system
			Machine.halt();
			// if this is reached, then the machine did not halt
			Lib.assertNotReached("Machine.halt() did not halt machine!");
			return 0;
		}
	}

	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall.
	 *
	 * exit() never returns.
	 * 
	 * void exit(int status);
	 */
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");

		// close all file descriptors belonging to the current process
		for (int i = 0; i < this.fileDescriptors.length; i++) {
			// if the current file descriptor has data that is null, continue with the loop
			if (fileDescriptors[i] == null) {
				continue;
				// else the current file descriptor holds a valid file
			} else {
				// close the file
				fileDescriptors[i].close();
				// set the current file object to null
				fileDescriptors[i] = null;
			}
		}
		// release all memory calling unloadSection
		unloadSections();

		// close sections by calling
		coff.close();

		// if it has a parent process -> save child's exit status in parent
		if (this.getParentID() != -1) {
			UserProcess parentProcess = currentProcesses.get(this.getParentID());
			// check if accessing the childs parent process was successful
			if (parentProcess != null) {
				parentProcess.childrenExitStatuses.put(this.getCurrentID(), status);
			}
		}

		// Any children of the process no longer have a parent process.
		// set all children's parentPID to -1
		for (Iterator<Integer> keys = currentProcessChildren.keySet().iterator(); keys.hasNext();) {
			// get the current key, a PID
			Integer key = keys.next();
			// get the current value, a UserProcess
			UserProcess childProcess = currentProcessChildren.get(key);
			childProcess.setParentID(-1);
		}

		// if this is last running running process then terminate kernel
		if (totalProcesses == 1) {
			Kernel.kernel.terminate();
		}

		updateProcessLock.acquire();
		// remove exiting process from our process hashmap
		currentProcesses.remove(this.getCurrentID());
		// decrement the total number of current processes
		totalProcesses--;
		updateProcessLock.release();

		updatePIDLock.acquire();
		// recycle the pid of the current process that called exit
		recycledPIDS.add(this.getCurrentID());
		updatePIDLock.release();

		// close Kthread by calling Kthread
		// wakes up the parent process if one exists
		KThread.finish();
		return 0;
	}

	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 * 
	 * int exec(char *file, int argc, char *argv[]);
	 */
	private int handleExec(int file, int argc, int argv) {
		// Get the name of the file to execute.
		String fileName = readVirtualMemoryString(file, MAX_STRING_LENGTH);
		// check if the file name is null, or invalid
		if (fileName == null || fileName.equals("")) {
			return -1;
		}
		// check to see if the number of arguements is non-negative
		if (argc < 0) {
			return -1;
		}

		// check if the file is an appropriate object file (.coff) file,
		// string must include the ".coff" extension.
		if (!fileName.endsWith(".coff")) {
			return -1;
		}

		// Declare a byte buffer to read argument addresses
		byte[] byteBuffer;

		// Create an array to store the argument strings of size argc
		String[] args = new String[argc];

		// Loop through each argument
		for (int index = 0; index < argc; index++) {
			// Initialize the byte buffer to store one argument address
			byteBuffer = new byte[BYTES_OF_INT];
			// Read the argument address from the virtual memory
			int bytesRead = readVirtualMemory(argv + (index * BYTES_OF_INT), byteBuffer);

			// Ensure the correct number of bytes were read
			if (bytesRead != BYTES_OF_INT) {
				return -1;
			}

			// Convert the byte buffer into an integer to get the argument address
			int argAddr = Lib.bytesToInt(byteBuffer, 0);

			// Read the argument string from the virtual memory
			args[index] = readVirtualMemoryString(argAddr, MAX_STRING_LENGTH);

			// Check if the argument is null or an empty string
			if (args[index] == null || args[index].equals("")) {
				return -1;
			}
		}

		// create a new child process
		UserProcess childProcess = newUserProcess();

		// Try to load the executable and prepare it to run with the given arguments
		if (!childProcess.execute(fileName, args)) {

			updateProcessLock.acquire();
			// remove the process from our process hashmap
			currentProcesses.remove(childProcess.getCurrentID());
			// decrement the total number of current processes
			totalProcesses--;
			updateProcessLock.release();

			/**
			 * If the loading fails, recycle the process ID,
			 * decrement the total number of current processes
			 * and return -1
			 */
			updatePIDLock.acquire();
			recyclePID(childProcess.getCurrentID());
			updatePIDLock.release();

			// return -1 for error
			return -1;
		}

		// Set the child process's parent ID to the current process ID
		childProcess.setParentID(this.getCurrentID());

		// Add the child process to the parent's children hashmap
		this.currentProcessChildren.put(childProcess.currentPID, childProcess);

		// return the child's process ID
		return childProcess.getCurrentID();
	}

	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 * 
	 * int join(int processID, int *status);
	 * 
	 */
	private int handleJoin(int processID, int status) {
		// If processID is invalid or if processID does not refer
		// to a child process of the current process, return -1.
		if ((processID < 0) || !this.currentProcessChildren.containsKey(processID)) {
			return -1;
		}

		// write the child's status to the specified virtual address,
		// if the parent contains the childs exit status before join was called
		if (this.childrenExitStatuses.containsKey(processID)) {
			UserProcess childProcess = this.currentProcessChildren.get(processID);
			byte[] statusByteBuffer = Lib.bytesFromInt(this.childrenExitStatuses.get(processID));
			int bytesWritten = writeVirtualMemory(status, statusByteBuffer);

			// if the total bytes written was not the total number of bytes in an int,
			// return 0 (child exited as a result of an unhandled exception)
			if (bytesWritten != BYTES_OF_INT) {
				return 0;
			}
			/**
			 * If the child exited normally, returns 1. If
			 * the child exited as a result of an unhandled exception,
			 * returns 0.
			 */
			if (childProcess.getProcessExitedNormally()) {
				return 1;
			} else {
				return 0;
			}
		}

		// Extract the child process from the current process's children map
		UserProcess childProcess = this.currentProcessChildren.get(processID);

		// remove the child from the current process's children map
		this.currentProcessChildren.remove(processID);

		// If the child process is still running, join with it
		childProcess.thread.join();

		// after child has called exit(), determine the child's exit status
		byte[] statusByteBuffer = Lib.bytesFromInt(this.childrenExitStatuses.get(processID));
		int bytesWritten = writeVirtualMemory(status, statusByteBuffer);

		// if the total bytes written was not the total number of bytes in an int,
		// return 0 (child exited as a result of an unhandled exception)
		if (bytesWritten != BYTES_OF_INT) {
			return 0;
		}
		/**
		 * If the child exited normally, returns 1. If
		 * the child exited as a result of an unhandled exception,
		 * returns 0.
		 */
		if (childProcess.getProcessExitedNormally()) {
			return 1;
		} else {
			return 0;
		}
	}

	/**
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file. If
	 * the file already exists, creat truncates it.
	 *
	 * Note that creat() can only be used to create files on disk; creat() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 * 
	 * int creat(char *name);
	 * 
	 */
	private int handleCreate(int name) {
		// Attempt to open the named disk file
		String fileName = readVirtualMemoryString(name, MAX_STRING_LENGTH);
		// check if the file name is null, which indicates virtual memory is empty
		if (fileName == null || fileName.equals("")) {
			return -1;
		}
		// return a file descriptor that can be used to access the file
		int availableFileDescriptor = getNextAvailableFileDescriptor();
		// if no space exists inside our fileDescriptors array, then
		// we should throw an error return -1
		if (availableFileDescriptor == -1) {
			return -1;
		}
		// create a new file object by passing in the file name and the value true
		OpenFile createdFile = ThreadedKernel.fileSystem.open(fileName, true);
		// if the file was not opened, return an error
		if (createdFile == null) {
			return -1;
		}
		// put the created file in our fileDescriptor array
		fileDescriptors[availableFileDescriptor] = createdFile;
		// return the avialable file descriptor
		return availableFileDescriptor;
	}

	/**
	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 * 
	 * int open(char *name);
	 */
	private int handleOpen(int name) {
		// Attempt to open the named disk file
		String fileName = readVirtualMemoryString(name, MAX_STRING_LENGTH);
		// check if the file name is null, which indicates virtual memory is empty
		if (fileName == null || fileName.equals("")) {
			return -1;
		}
		// return a file descriptor that can be used to access the file
		int availableFileDescriptor = getNextAvailableFileDescriptor();
		// if no space exists inside our fileDescriptors array, then
		// we should throw an error return -1
		if (availableFileDescriptor == -1) {
			return -1;
		}
		// open a file object by passing in the file name and the value true
		OpenFile openedFile = ThreadedKernel.fileSystem.open(fileName, false);
		// if the file was not opened, return an error
		if (openedFile == null) {
			return -1;
		}
		// put the opened file in our fileDescriptor array
		fileDescriptors[availableFileDescriptor] = openedFile;
		// return the avialable file descriptor
		return availableFileDescriptor;
	}

	/**
	 * Attempt to read up to "count" bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk, this
	 * indicates that the end of the file has been reached. If the file descriptor
	 * refers to a stream, this indicates that the fewer bytes are actually
	 * available right now than were requested, but more bytes may become available
	 * in the future. Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 * 
	 * int read(int fileDescriptor, void *buffer, int count);
	 */
	private int handleRead(int fileDescriptor, int buffer, int count) {
		/**
		 * check to see if the file descriptor is in a valid range,
		 * and check if the file descriptor actually contains a file in our
		 * file descriptor array
		 */
		if (fileDescriptor < 0
				|| fileDescriptor >= MAX_FILE_TABLE_SIZE
				|| fileDescriptors[fileDescriptor] == null) {
			return -1;
		}
		// checking for invalid count
		if (count < 0) {
			return -1;
		}
		// checking for invalid buffer address
		if (buffer < 0 || buffer > numPages * pageSize) {
			return -1;
		}
		// Check if the buffer address and size exceed the address space
		if (buffer + count > numPages * pageSize) {
			return -1;
		}

		// bytes buffer
		byte[] byteBuffer;
		// virtual address of buffer
		int vaBuffer = buffer;
		// total bytes to read and write to virtual memory
		int bytesLeft = count;
		// total bytes actually read
		int totalBytesRead = 0;

		// the file should be accessible, so access it using the fileDescriptor index
		OpenFile fileToRead = fileDescriptors[fileDescriptor];

		// while there are still bytes left to read from count
		while (bytesLeft > 0) {
			// determine to read the minimum between pageSize and bytesLeft
			int bufferSize = Math.min(pageSize, bytesLeft);

			// byte buffer of size buffersize
			byteBuffer = new byte[bufferSize];

			// return the number of bytes read
			int bytesRead = fileToRead.read(byteBuffer, 0, bufferSize);
			// check if read is valid
			if (bytesRead == -1) {
				return -1;
			}
			// check for valid vaBuffer
			if (vaBuffer < 0 || vaBuffer > numPages * pageSize) {
				return -1;
			}
			// write to virtual memory
			int bytesWritten = writeVirtualMemory(vaBuffer, byteBuffer, 0, bytesRead);

			// checking for errors in writting in virtual memory
			if (bytesWritten < bytesRead) {
				return -1;
			}
			// or reached End of file break
			if (bytesRead < bufferSize) {
				// update in case byteread > 0 but still < buff size
				totalBytesRead += bytesRead;
				break;
			}
			// update the number of bytes left to read
			bytesLeft -= bytesRead;
			// the virtual address to write to
			vaBuffer += bytesRead;
			// update total bytes read
			totalBytesRead += bytesRead;
		}
		// return number of bytes read
		return totalBytesRead;
	}

	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 * 
	 * int write(int fileDescriptor, void *buffer, int count);
	 */
	private int handleWrite(int fileDescriptor, int buffer, int count) {
		/**
		 * check to see if the file descriptor is in a valid range,
		 * and check if the file descriptor actually contains a file in our
		 * file descriptor array
		 */
		if (fileDescriptor < 0
				|| fileDescriptor >= MAX_FILE_TABLE_SIZE
				|| fileDescriptors[fileDescriptor] == null) {
			return -1;
		}
		// checking for invalid count
		if (count < 0) {
			return -1;
		}
		// checking for invalid buffer address
		if (buffer < 0 || buffer > numPages * pageSize) {
			return -1;
		}

		// Check if the buffer address and size exceed the address space
		if (buffer + count > numPages * pageSize) {
			return -1;
		}

		// bytes buffer
		byte[] byteBuffer;
		// virtual address of buffer
		int vaBuffer = buffer;
		// total bytes to read and write to virtual memory
		int bytesLeft = count;
		// total bytes actually written
		int totalBytesWritten = 0;

		// the file should be accessible, so access it using the fileDescriptor index
		OpenFile fileToWrite = fileDescriptors[fileDescriptor];

		// while there are still bytes left to write from count
		while (bytesLeft > 0) {

			// determine to write the minimum between pageSize and bytesLeft
			int bufferSize = Math.min(pageSize, bytesLeft);
			//
			byteBuffer = new byte[bufferSize];

			// check for valid vaBuffer
			if (vaBuffer < 0 || vaBuffer > numPages * pageSize) {
				return -1;
			}
			// read user buffer into the local buffer
			int bytesRead = readVirtualMemory(vaBuffer, byteBuffer, 0, bufferSize);

			// write to file
			int bytesWritten = fileToWrite.write(byteBuffer, 0, bytesRead);
			// check if write is valid
			if (bytesWritten == -1) {
				return -1;
			}
			// update the number of bytes left to write to the file, the virtual address to
			// read from,
			// and update the total number of bytes written
			bytesLeft -= bytesWritten;
			vaBuffer += bytesWritten;
			totalBytesWritten += bytesWritten;
			// if we have reach the end of file, break out the loop
			if ((bytesWritten < bufferSize) || (bytesWritten < bytesRead)) {
				break;
			}
		}
		// return the total bytes written
		return totalBytesWritten;
	}

	/**
	 * Close a file descriptor, so that it no longer refers to any file or
	 * stream and may be reused. The resources associated with the file
	 * descriptor are released.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 * 
	 * int close(int fileDescriptor);
	 */
	private int handleClose(int fileDescriptor) {
		/**
		 * check to see if the file descriptor is in a valid range,
		 * and check if the file descriptor actually contains a file in our
		 * file descriptor array
		 */
		if (fileDescriptor < 0
				|| fileDescriptor >= MAX_FILE_TABLE_SIZE
				|| fileDescriptors[fileDescriptor] == null) {
			return -1;
		}
		// Close a file descriptor, so that it no longer refers to any file or
		// stream and may be reused.
		fileDescriptors[fileDescriptor].close();
		// set the current file object to null
		fileDescriptors[fileDescriptor] = null;
		// removal successful
		return 0;
	}

	/**
	 * Delete a file from the file system.
	 *
	 * If another process has the file open, the underlying file system
	 * implementation in StubFileSystem will cleanly handle this situation
	 * (this process will ask the file system to remove the file, but the
	 * file will not actually be deleted by the file system until all
	 * other processes are done with the file).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 * 
	 * int unlink(char *name);
	 */
	private int handleUnlink(int name) {
		// Attempt to open the named disk file
		String fileName = readVirtualMemoryString(name, MAX_STRING_LENGTH);
		// check if the file name is null, which indicates virtual memory is empty
		if (fileName == null) {
			return -1;
		}
		// attempt to Delete a file from the file system. If open, handled
		// by the file system
		boolean isFileRemoved = ThreadedKernel.fileSystem.remove(fileName);
		// return 0 if the file was removed, else return -1
		return (isFileRemoved ? 0 : -1);
	}

	/**
	 * This method is sctrictly designed to acquire the index position of the next
	 * available
	 * file descriptor index if there is one avaialable
	 * 
	 * @return the index of the next available file descriptor
	 */
	private int getNextAvailableFileDescriptor() {
		// keep a local variable to the index of the next available file descriptor
		int index;
		for (index = 0; index < MAX_FILE_TABLE_SIZE; index++) {
			if (this.fileDescriptors[index] == null) {
				return index;
			}
		}

		// if -1 is returned, there is no available space in our list
		return -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument. // what is this exactly?
	 * @param a1      the second syscall argument. // what is this exactly?
	 * @param a2      the third syscall argument. // what is this exactly?
	 * @param a3      the fourth syscall argument. // what is this exactly?
	 * @return the value to be returned to the user. // what exactly do we return?
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		// Implement the file system calls creat, open, read, write, close, and unlink
		switch (syscall) {
			case syscallHalt:
				// is void
				return handleHalt();
			// exit system call to implement
			case syscallExit:
				// a0 represents the integer status
				return handleExit(a0);
			// exec system call to implement
			case syscallExec:
				// a0 is the name, a1 and a2 are argc and argv respectively
				return handleExec(a0, a1, a2);
			// join system call to implement
			case syscallJoin:
				// a0 is PID and a1 is th status
				return handleJoin(a0, a1);
			// create system call to implement
			case syscallCreate:
				// a0 is the name of the file you wish to create
				return handleCreate(a0);
			// open system call to implement
			case syscallOpen:
				// a0 is the name of the file you wish to open
				return handleOpen(a0);
			// read system call to implement
			case syscallRead:
				// a0 filedescriptor, a1 buffer, a2 is the count
				return handleRead(a0, a1, a2);
			// write system call to implement
			case syscallWrite:
				// a0 filedescriptor, a1 buffer, a2 is the count
				return handleWrite(a0, a1, a2);
			// close system call to implement
			case syscallClose:
				// a0 is the file descriptor
				return handleClose(a0);
			// unlink system call to implement
			case syscallUnlink:
				// a0 is the name of the file
				return handleUnlink(a0);
			// the system call was not recognized
			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				// call handleExit
				this.exitedNormally = false;
				handleExit(0);
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	// make a private fiinal variable containing the max file table size
	private final int MAX_FILE_TABLE_SIZE = 16;

	// make a private final variable contain the max string length for a buffer
	private static final int MAX_STRING_LENGTH = 256;

	private static final int BYTES_OF_INT = 4;

	// list of available file descriptors
	private OpenFile[] fileDescriptors;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	// keep track of the number of total processes in the system
	private static int totalProcesses = 0;

	// process information
	private boolean isRootProcess;
	private static boolean rootProcessCreated = false;

	// indicates if the process exited due to an unhandled exception
	private boolean exitedNormally = true;

	// PID INFORMATION
	// personal process ID
	private int currentPID;
	// parent process ID
	private int parentPID;
	// static accessible and editable next process ID
	private static int nextPID = 0;
	// children map stores the child processes of this process, keyed by their pids.
	private HashMap<Integer, UserProcess> currentProcessChildren = new HashMap<Integer, UserProcess>();
	// data structure to hold recycling of PIDS
	private static LinkedList<Integer> recycledPIDS = new LinkedList<Integer>();
	// data structure to hold children exit statuses K = PID, V = exit status
	private HashMap<Integer, Integer> childrenExitStatuses = new HashMap<Integer, Integer>();
	// data structure that holds every process
	protected static HashMap<Integer, UserProcess> currentProcesses = new HashMap<Integer, UserProcess>();
	// data structure to hold vpn to spn mappings
	protected HashMap<Integer, Integer> vpnToSpnMap = new HashMap<Integer,Integer>();


	// Synchronization Support
	private static Lock updatePIDLock = new Lock();
	private static Condition cvPID = new Condition(updatePIDLock);
	private static Lock updateProcessLock = new Lock();
	private static Condition cvProcesses = new Condition(updateProcessLock);
	/**
	 * this method gets the next available PID
	 * It first checks to see if any recycled PIDS are available,
	 * else it simply assigns the next available PID if no recycled
	 * PIDS exist
	 * 
	 * @return the next available PID
	 */
	public static int getNextAvailablePID() {
		// check if there are recycled PIDS. If none,
		// simply return the next PID
		if (recycledPIDS.isEmpty()) {
			return (nextPID++);
		} else {
			// remove the first avalable and recycled PID
			return recycledPIDS.removeFirst();
		}
	}

	/**
	 * This method recycles PIDS for reuse by the OS when making new processes
	 */
	public static void recyclePID(int pid) {
		// add the PID to the pool of recycled PIDs
		recycledPIDS.add(pid);
	}

	/**
	 * setter method for current process parent ID
	 */
	public void setParentID(int pid) {
		this.parentPID = pid;
	}

	/**
	 * getter method for current process parent ID
	 * 
	 * @return current parent PID
	 */
	public int getParentID() {
		return this.parentPID;
	}

	/**
	 * setter method for current process ID
	 */
	public void setCurrentID(int pid) {
		this.currentPID = pid;
	}

	/**
	 * getter method for current process ID
	 * 
	 * @return current PID
	 */
	public int getCurrentID() {
		return this.currentPID;
	}

	/**
	 * getter method for current proccess exited normally
	 * 
	 * @return true or false, if it exited normally
	 */
	public boolean getProcessExitedNormally() {
		return this.exitedNormally;
	}
	// getter method to get a translation entry from a process' pageTable
	public TranslationEntry getTranslationEntry(int vpn) {
		return this.pageTable[vpn];
	}

	public TranslationEntry[] getPageTable(){
		return this.pageTable;
	}

	
	public void setVPNToSPNMap(int vpn, int spn){
		vpnToSpnMap.put(vpn, spn);
	}
}
