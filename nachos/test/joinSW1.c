#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog1 = "swap4.coff";
    char *prog2 = "write10.coff";

    int pid1, r1, status1 = 0;
    int pid2, r2, status2 = 0;

    printf ("execing prog1 %s...\n", prog1);
    pid1 = exec (prog1, 0, 0);

    if (pid1 > 0) {
        printf ("...passed\n");
    } else {
        printf ("...failed (pid = %d)\n", pid1); 
        exit (-1);
    }


    printf ("execing prog2 %s...\n", prog2);
    pid2 = exec (prog2, 0, 0);

    if (pid2 > 0) {
        printf ("...passed\n");
    } else {
        printf ("...failed (pid = %d)\n", pid2); 
        exit (-1);
    }

    printf ("joining %d...\n", pid1);
    r1 = join (pid1, &status1);

    if (r1 > 0) {
        printf ("...passed (status from child 1 = %d)\n", status1);
    } else if (r1 == 0) {
        printf ("...child 1 exited with unhandled exception\n");
        exit (-1);
    } else {
        printf ("...failed (r = %d)\n", r1);
        exit (-1);
    }

    r2 = join (pid2, &status2);
    if (r2 > 0) {
        printf ("...passed (status from child 2 = %d)\n", status2);
    } else if (r2 == 0) {
        printf ("...child 2 exited with unhandled exception\n");
        exit (-1);
    } else {
        printf ("...failed (r = %d)\n", r2);
        exit (-1);
    }

    // the return value from main is used as the status to exit
    return 0;
}