#include "syscall.h"
#define BYTES 1
int main (void){
    int bytesRead;
    int fileDescriptor;
    char buffer[BYTES];
    int totalBytesRead = 0;
    // open the file for reading
    fileDescriptor = open("garbage.txt");
    if(fileDescriptor < 0 || fileDescriptor > 15){
        close(fileDescriptor);
        printf("Failed to open garbage.txt\n");
        exit(-1);
    }
    // read from file
    bytesRead = read(-1, buffer, BYTES);
    totalBytesRead += bytesRead;
    printf("Bytes Read: %d\n", bytesRead);
    if(bytesRead < 0){
        printf("Invalid FD\n");
        close(fileDescriptor);
        exit(-1);
    }
    printf("\n");
    close(fileDescriptor);
    return 0;
}