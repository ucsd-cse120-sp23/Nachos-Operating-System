#include "syscall.h"
int main (void){
    int status = close(0);
    printf("status: %d\n", status);
    int fd = open("IEXISTNOW.txt");
    printf("fd: %d\n", fd);
    exit(fd);
    status = close(1);
    printf("status: %d\n", status);
    fd = open("IEXISTNOW.txt");
    printf("fd: %d\n", fd);
    return fd;
}