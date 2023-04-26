package nachos.threads;

import nachos.machine.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	// store waiting threads until alarm is finished
	private HashMap<KThread, Long> listOfThreads = new HashMap<KThread, Long>();

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		// create a new dummy hashmap
		HashMap<KThread, Long> dummy = new HashMap<KThread, Long>();
		// get the current walltime
		long time = Machine.timer().getTime();
		// put all the current threads in the ready queue into the dummy list
		dummy.putAll(listOfThreads);
		// for loop that iterates through the list of threads in dummy
		for (Iterator<KThread> keys = dummy.keySet().iterator(); keys.hasNext();) {
			// get the current key, Kthread object
			KThread key = keys.next();
			// get the current value, which is a wake time
			Long val = listOfThreads.get(key);
			// if the current threads wakeTime is less than or equals to the time, enter
			if (val <= time) {
				// set the current thread to ready
				key.ready();
				// remove the thread from our list of threads
				listOfThreads.remove(key);
			}
		}
		// current running thread not related to threads we are looking for
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// If the wait parameter x is 0 or negative, return without waiting (do not
		// assert).
		if (x <= 0) {
			return;
		}
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		// populate the list of threads Array list with a new pair of Kthread and wake
		// time
		listOfThreads.put(KThread.currentThread(), wakeTime);
		// disable interrupts from machine
		Machine.interrupt().disable();
		// put the threat to sleep, disabling the thread from running any further
		KThread.sleep();
		// reenable interrupts
		Machine.interrupt().enable();
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
		// check to see if the thread is contained in the hasmap data structure
		if(listOfThreads.containsKey(thread)) {
			// remove it
			listOfThreads.remove(thread);
			// set it to ready
			thread.ready();
			return true;
		}
		// the thread has no timer set, so return false
		return false;
	}

	// ----------------------------------------------------- THE CODE BELOW IS A
	// TESTING METHOD
	public static void alarmTest1() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Implement more test methods here ...
	public static void alarmTest2() {
		int duration = -300;
		long t0, t1;
		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(duration);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTest2: waited for " + (t1 - t0) + " ticks");
	}

	public static void alarmTest3(){
		
		int duration1 = 10000;
		long t0, t1;
		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(duration1);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTest3: waited for " + (t1 - t0) + " ticks");

		KThread child1 = new KThread(new Runnable() {
			public void run() {
				System.out.println("I (heart) Nachos!");
			}
		});
		child1.setName("child1").fork();

		int duration2 = 1000;
		long t2, t3;
		t2 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(duration2);
		t3 = Machine.timer().getTime();

		System.out.println("alarmTest3: waited for " + (t3 - t2) + " ticks");
		
	}

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
		alarmTest1();
		// Invoke your other test methods here ...
		alarmTest2();
		// Invoke your other test methods here ...
		alarmTest3();
    }
}
