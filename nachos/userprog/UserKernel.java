package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

		// initialize the freePages list with all available physical pages
		initializePageLinkedList();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
			System.out.println("Could not find executable '" +
					shellProgram + "', trying '" +
					shellProgram + ".coff' instead.");
			shellProgram += ".coff";
			if (!process.execute(shellProgram, new String[] {})) {
				System.out.println("Also could not find '" +
						shellProgram + "', aborting.");
				Lib.assertTrue(false);
			}

		}

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	// a static linked list of free physical pages
	private static LinkedList<Integer> freePhysicalPages = new LinkedList<Integer>();

	// a static lock for free pages to make methods synchronous
	private static Lock freePagesLock = new Lock();

	/**
	 * This method initializes the linked list of free physical pages with the
	 * number of pages of physical memory attached to the simulated
	 * processor
	 */
	public static void initializePageLinkedList() {
		// get the number of physical pages
		int numPhysPages = Machine.processor().getNumPhysPages();
		// initialize the linked list of free physical pages with the number of
		// of pages of physical memory attached to this simulated processor.
		for (int index = 0; index < numPhysPages; index++) {
			// add the page
			freePhysicalPages.add(index);
		}
	}

	/**
	 * This method allocates a page by removing a free page from our linked list of
	 * free
	 * pages. It does things synchronously.
	 * 
	 * @return -1 if no page was allocated, else return a number greater than -1
	 */
	public static int allocatePage() {
		// we will return -1 if no page was allocated
		int page = -1;
		// Be sure to use synchronization where necessary when accessing this list to
		// prevent race conditions.
		Machine.interrupt().disable();
		// acquire the lock
		freePagesLock.acquire();
		if (!freePhysicalPages.isEmpty()) {
			page = freePhysicalPages.removeFirst();
		}
		// release the lock
		freePagesLock.release();
		// reenable interrupts
		Machine.interrupt().enable();
		return page;
	}

	/**
	 * This method deallocates page by adding a free page to our linked list of free
	 * pages.
	 * 
	 * @param page number of page that is free
	 */
	public static void deallocatePage(int page) {
		// disable interrupts
		Machine.interrupt().disable();
		// acquire the lock
		freePagesLock.acquire();
		// add a page to the linked list
		freePhysicalPages.add(page);
		// release the lock
		freePagesLock.release();
		// reenable interrupts
		Machine.interrupt().enable();

	}
}
