/*
 * write1.c
 *
 * Write a string to stdout, one byte at a time.  Does not require any
 * of the other system calls to be implemented.
 *
 * Geoff Voelker
 * 11/9/15
 */
#include "syscall.h"

int
main (int argc, char *argv[])
{
    int fd = open("output.txt");
    char *str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";
    int i = 0;
    int totalBytesWritten = 0;
    while (i < 500) {
        while (*str) {
            int r = write (fd, str, 1);
            if (r != 1) {
                printf ("failed to write character (r = %d)\n", r);
                exit (-1);
            }
            totalBytesWritten += r;
            str++;
            printf("total bytes written: %d\n", totalBytesWritten);
        }

        str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";
        i++;
    }

    return 0;
}
