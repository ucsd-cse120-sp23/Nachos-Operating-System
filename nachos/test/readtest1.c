#include "syscall.h"
#define BYTES 40
int main (void){
    int bytesRead;
    int fieldDescriptor;
    char buffer[BYTES];
    char expected[] = "I am a very short file...";
    // open the file for reading
    fieldDescriptor = open("shortfile.txt");
    if(fieldDescriptor < 0 || fieldDescriptor > 15){
        printf("Failed to open shortfile.txt\n");
        exit(-1);
    }
    // read from file
    bytesRead = read(fieldDescriptor, buffer, BYTES);
    printf("Bytes Read: %d\n", bytesRead);
    if(bytesRead < 0){
        printf("Failed to read shortfile.txt\n");
        close(fieldDescriptor);
        exit(-1);
    }
    for(int i = 0; i < 25; i++){
    // I am a very short file...
        printf("%c", buffer[i]);
        if(buffer[i] != expected[i]) {
            printf("%c != %c\n", buffer[i], expected[i]);
            exit(-1);
        }
    }
    printf("\n");
    close(fieldDescriptor);
    return 0;
    
}