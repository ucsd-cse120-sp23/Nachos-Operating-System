package nachos.threads;

import nachos.machine.*;
import java.util.HashMap;
/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    // ------------- GLOBAL VARIABLES -------------
    private Lock lock;
    //private int exchangeValue;

    // a hash map containing waiting threads and their tags <tag, waiting thread>
    private HashMap<Integer, WaitingThread> waitMap;

    /**
     * Allocate a new Rendezvous.
     */
    
    public Rendezvous () {
        this.lock = new Lock();
        this.waitMap = new HashMap<Integer, WaitingThread>();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        // acquire the lock
        this.lock.acquire();
        
        /* 
         * check waitMap if any waitingThread with same tag exists
         * if none, then create a waitingThread object for the thread and sleep it
         * if waitingThread exists, get the value of the waitingThread and 
         * give the first thread the second thread's value
         * remove the waitingThread from waitMap if it exchanges with a second thread
         */
        WaitingThread currentThread;
        if(!this.waitMap.containsKey(tag)){
            currentThread = new WaitingThread(value, false, this.lock);
            this.waitMap.put(tag, currentThread);
            
        } else {
            currentThread = this.waitMap.get(tag);
        }

        int valueExchanged;
        // a call excahnage a is in the hm
        // b calls exchange b is not in the hm
        if(!currentThread.isWaiting){
            currentThread.isWaiting = true;
            currentThread.condition.sleep();
            // set return value to B's value
            valueExchanged = currentThread.value;
            waitMap.remove(tag);
        } else {
            valueExchanged = currentThread.value; // B has A
            currentThread.value = value;          // A has B
            currentThread.isWaiting = false;
            currentThread.condition.wake();
        }

        /* 
        int valueExchanged;
        // check to see if a thread is waiting
        if(!currentWaitingThread.isWaiting){ // run by thread A
            // set the exchange value to value passed in
            this.exchangeValue = value;
            // set flag to signal that a thread is waiting for exchange to be called
            this.isThreadWaiting = true;
            // put the current waiting thread to sleep
            //condition.sleep();
            // once awake, set this threads value exchanged to the exchange value
            valueExchanged = this.exchangeValue;
        } else { // run by thread B
            // get thread A's value
            valueExchanged = this.exchangeValue;
            // set the shared exchangeValue variable to thread B's value
            this.exchangeValue = value;
            // reset isThreadWaiting for the next pair of threads
            this.isThreadWaiting = false;
            // wake up thread A
            //this.condition.wake();
        }     
        */

        // release the lock
        lock.release();
        // return the exchanged value
	    return valueExchanged;
    }

    private class WaitingThread {
        public int value;
        public boolean isWaiting;
        public Condition2 condition;

        public WaitingThread(int value, boolean isWaiting, Lock lock){
            this.value = value;
            this.isWaiting = isWaiting;
            this.condition = new Condition2(lock);
        }
    }

    // Place Rendezvous test code inside of the Rendezvous class.
    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
        System.out.println("TOLD YOU SO!");
    }
    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()


    // r1 and r2 rendez should not affect one another
    public static void rendezTest2() {
        final Rendezvous r1 = new Rendezvous();
        final Rendezvous r2 = new Rendezvous();
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv1 = r1.exchange (tag, send);
                int recv2 = r2.exchange (tag, send);
                Lib.assertTrue (recv1 == 1, "Was expecting " + 1 + " but received " + recv1);
                Lib.assertTrue (recv2 == 1, "Was expecting " + 1 + " but received " + recv2);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv1);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv2);
            }
        });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv1 = r1.exchange (tag, send);
                int recv2 = r2.exchange (tag, send);
                Lib.assertTrue (recv1 == -1, "Was expecting " + -1 + " but received " + recv1);
                Lib.assertTrue (recv2 == -1, "Was expecting " + -1 + " but received " + recv2);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv1);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv2);
            }
        });
        t2.setName("t2");
        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
    }
    
    
    // t1 and t3 should exchange, t2 and t4 should exchange
    public static void rendezTest3() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -2;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");
        

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

        
        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 2;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -2, "Was expecting " + -2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t3.setName("t3");
        
        
        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t4.setName("t4");


        t1.fork(); t2.fork(); t3.fork(); t4.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join(); t3.join(); t4.join();
    }

    public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        rendezTest2();
        rendezTest3();
    }
}
