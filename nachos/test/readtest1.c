#include "syscall.h"
#define BYTES 1
int main (void){
    int bytesRead;
    int fieldDescriptor;
    char buffer[BYTES];
    // open the file for reading
    fieldDescriptor = open("verylongfile.txt");
    if(fieldDescriptor < 0 || fieldDescriptor > 15){
        printf("Failed to open verylongfile.txt\n");
        exit(-1);
    }
    // read from file
    bytesRead = read(fieldDescriptor, buffer, BYTES);
    printf("Bytes Read: %d\n", bytesRead);
    if(bytesRead < 0){
        printf("Failed to read verylongfile.txt\n");
        close(fieldDescriptor);
        exit(-1);
    }
    printf("\n");
    close(fieldDescriptor);
    return 0;
    
}