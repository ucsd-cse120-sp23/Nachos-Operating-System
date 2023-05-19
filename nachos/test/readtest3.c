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
    bytesRead = read(fileDescriptor, buffer, BYTES);
    totalBytesRead += bytesRead;
    while(bytesRead == BYTES){
        printf("Bytes Read: %d\n", bytesRead);
        if(bytesRead < 0){
            printf("Failed to read garbage.txt\n");
            close(fileDescriptor);
            exit(-1);
        }
        // read from file
        bytesRead = read(fileDescriptor, buffer, BYTES);
        totalBytesRead += bytesRead;
    }
    printf("Total Bytes Read: %d", totalBytesRead);
    if(totalBytesRead != 34239){
        close(fileDescriptor);
        printf("Did not read all the chars! :( from garbage.txt\n");
        exit(-1);
    }
    printf("\n");
    close(fileDescriptor);
    return 0;
    
}