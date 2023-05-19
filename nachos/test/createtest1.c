#include "syscall.h"
void throwError(int fd){
    if(fd > 15 || fd < 0){
        printf("fd: %d\n", fd);
        exit(-1);
    }
    printf("\nValid file descriptor: %d\n", fd);
}
int main(void) {    
    int fd = creat("IEXISTNOW.txt");
    throwError(fd);
    fd = creat("shortfile.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
    fd = creat("garbage.txt");
    throwError(fd);
}