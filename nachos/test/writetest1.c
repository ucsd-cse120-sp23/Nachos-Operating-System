#include "syscall.h"
#include "stdlib.h"
//#include "string.h"  // To use strlen function

int main (void)
{
    char *str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";
    int bytesToWrite = 61;  // Calculate the exact number of bytes in the string
    int bytesWritten = write(fdStandardOutput, str, bytesToWrite);
    printf("BYTES WRITTEN: %d\n", bytesWritten);
    if (bytesWritten != bytesToWrite) {
        printf("test failed actual bytes written %d expected bytes written %d\n", bytesWritten, bytesToWrite);
        close(fdStandardOutput);
        exit(-1);
    }
    //close(fdStandardOutput);
    //exit(0);

    return 0;
}