/* matmult.c 
 *    Test program to do matrix multiplication on large arrays.
 *
 *    Intended to stress virtual memory system. Should return 7220 if Dim==20
 */

#include "syscall.h"

#define Dim 	8	/* sum total of the arrays doesn't fit in 
			 * physical memory 
			 */

int A[Dim][Dim];
int B[Dim][Dim];
int C[Dim][Dim];
int D[Dim][Dim];
int E[Dim][Dim];
int F[Dim][Dim];

int
main()
{
    int i, j, k;

    for (i = 0; i < Dim; i++)		/* first initialize the matrices */
	for (j = 0; j < Dim; j++) {
	     A[i][j] = 1;
	     B[i][j] = 1;
	     C[i][j] = 0;
		 D[i][j] = 0;
		 E[i][j] = 0;
		 F[i][j] = 0;
	}

    for (i = 0; i < Dim; i++)		/* then multiply them together */
	for (j = 0; j < Dim; j++)
            for (k = 0; k < Dim; k++)
		 C[i][j] += A[i][k] * B[k][j];
	
	for (i = 0; i < Dim; i++)		/* then multiply them together */
	for (j = 0; j < Dim; j++)
            for (k = 0; k < Dim; k++)
		 D[i][j] += C[i][k] * B[k][j];

	for (i = 0; i < Dim; i++)		/* then multiply them together */
	for (j = 0; j < Dim; j++)
            for (k = 0; k < Dim; k++)
		 E[i][j] += D[i][k] * B[k][j];
		 
	for (i = 0; i < Dim; i++)		/* then multiply them together */
	for (j = 0; j < Dim; j++)
            for (k = 0; k < Dim; k++)
		 F[i][j] += E[i][k] * B[k][j];


    printf("F[%d][%d] = %d\n", Dim-1, Dim-1, F[Dim-1][Dim-1]);
    return (F[Dim-1][Dim-1]);		/* and then we're done */
}
