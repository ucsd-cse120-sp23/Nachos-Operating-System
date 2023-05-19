#include "syscall.h"
#define BYTES 19
int main (void){
    int bytesRead = 0;
    int fieldDescriptor;
    char buffer[BYTES];
    char expected[] = "CSS ROCKS MY SOCKS";
    // read from keyboard input
    printf("Bytes Read: %d\n", bytesRead);
    bytesRead = read(fdStandardInput, buffer, BYTES);
    printf("Bytes Read: %d\n", bytesRead);
    if(bytesRead < 0){
        printf("Failed to read std input\n");
        close(fdStandardInput);
        exit(-1);
    }
    for(int i = 0; i < BYTES; i++){
    // print out STD INPUT info
        printf("%c", buffer[i]);
        if(buffer[i] != expected[i]) {
            printf("%c != %c\n", buffer[i], expected[i]);
            close(fdStandardInput);
            exit(-1);
        }
    }
    printf("\n");
    close(fdStandardInput);
    return 0;
    
}