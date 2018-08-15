/***
 * test.c
 * copyright (c) 2012 by andrei borac
 ***/

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

#include <assert.h>

#define VOID void

typedef bool MUSE_TYPE_BL;

typedef  int8_t MUSE_TYPE_S1;
typedef uint8_t MUSE_TYPE_U1;

typedef  int16_t MUSE_TYPE_S2;
typedef uint16_t MUSE_TYPE_U2;

typedef  int32_t MUSE_TYPE_S4;
typedef uint32_t MUSE_TYPE_U4;

struct struct_MUSE_TYPE_ST;
typedef struct struct_MUSE_TYPE_ST MUSE_TYPE_ST;

#define MUSE_FALSE false
#define MUSE_TRUE  true

#include "../../export/c/muse-core.h"
#include "../../export/c/muse-core.c"

#include "../../build/p6.txt.k.c"
#include "../../build/p6.txt.p.c"

MUSE_TYPE_U4 sbrk[128];

MUSE_TYPE_BL trap(MUSE_TYPE_ST* e, MUSE_TYPE_U1* m)
{
  switch (*m) {
  case 0:
    {
      switch (e->r) {
      case 0:
        {
          e->r = (sbrk - ((MUSE_TYPE_U4*)(NULL)));
          return MUSE_TRUE;
        }
        
      case 1:
        {
          e->r = ((sbrk + ((sizeof(sbrk)) / sizeof(sbrk[0]))) - ((MUSE_TYPE_U4*)(NULL)));
          return MUSE_TRUE;
        }
      }
      
      assert(false);
      return MUSE_FALSE;
    }
    
  case 1:
    {
      printf("test: program exited\n");
      return MUSE_FALSE;
    }
    
  case 2:
    {
      e->r = 0;
      return MUSE_TRUE;
    }
    
  case 3:
    {
      printf("%zu\n", ((size_t)(e->r)));
      return MUSE_TRUE;
    }
  }
  
  assert(false);
  return MUSE_FALSE;
}

int main(int argc __attribute__((unused)), char** argv __attribute__((unused)))
{
  MUSE_TYPE_U4 backing_stack[128];
  MUSE_TYPE_U4 backing_unwind[128];
  
  MUSE_TYPE_ST e;
  
  e.c = opcodes_p6_txt;
  e.s = backing_stack;
  e.u = backing_unwind;
  e.r = 0;
  e.h = trap;
  
  while (MUSE_CORE_PROC(&e, 8));
  
  printf("test: done\n");
  
  return 0;
}
