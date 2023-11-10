#include <stdio.h>
#include <stdlib.h>

void main (void)
{
  char code;

  while (1)
    {
      read (0, &code, 1);

      printf ("%c %d %x\n", code, code, code);
    }
}
