#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"
#define BYTES 1
int main (void){
    int bytesRead;
    int fileDescriptor;
    int totalBytesRead = 0;
    // open the file for reading
    fileDescriptor = open("garbage.txt");
    if(fileDescriptor < 0 || fileDescriptor > 15){
        close(fileDescriptor);
        printf("Failed to open garbage.txt\n");
        exit(-1);
    }
    // read from file
    bytesRead = read(fileDescriptor, NULL, 18);
    totalBytesRead += bytesRead;
    printf("Bytes Read: %d\n", bytesRead);
    if(bytesRead < 0){
        printf("Failed to read garbage.txt --> STATUS: %d\n", bytesRead);
        close(fileDescriptor);
        exit(-1);
    }
    printf("\n");
    close(fileDescriptor);
    return 0;
    
}