/***
 * muse-core.h
 * copyright (c) 2012 by andrei borac
 ***/

struct struct_MUSE_TYPE_ST;
typedef struct struct_MUSE_TYPE_ST MUSE_TYPE_ST;

struct struct_MUSE_TYPE_ST
{
  const MUSE_TYPE_U1* c;
  /* */ MUSE_TYPE_U4* s;
  /* */ MUSE_TYPE_U4* u;
  /* */ MUSE_TYPE_U4  r;
  
  VOID* v;
  MUSE_TYPE_BL (*h)(MUSE_TYPE_ST*, const MUSE_TYPE_U1*, VOID*);
};

static inline MUSE_TYPE_U4 AddressHostToMuse(MUSE_TYPE_U4* host)
{
  /* hopefully, (1) logic shift will be used and (2) the AND operation will be optimized out */
  return ((((MUSE_TYPE_U4)((VOID*)(host))) >> 2) & 0x3FFFFFFF);
}

static inline MUSE_TYPE_U4* AddressMuseToHost(MUSE_TYPE_U4 muse)
{
  return ((MUSE_TYPE_U4*)((VOID*)(muse << 2)));
}

#ifdef MUSE_CAPTURE_MEMORY_ACCESS
MUSE_TYPE_U4 MuseMemoryGet(MUSE_TYPE_ST* e, MUSE_TYPE_U4 a);
VOID         MuseMemoryPut(MUSE_TYPE_ST* e, MUSE_TYPE_U4 a, MUSE_TYPE_U4 v);
#define MUSE_MEMORY_GET(what_e, what_a)         MuseMemoryGet((what_e), (what_a))
#define MUSE_MEMORY_PUT(what_e, what_a, what_v) MuseMemoryPut((what_e), (what_a), (what_v))
#else
#define MUSE_MEMORY_GET(what_e, what_a)         ((MUSE_TYPE_U4)((*(AddressMuseToHost((what_a))))))
#define MUSE_MEMORY_PUT(what_e, what_a, what_v) ((MUSE_TYPE_U4)((*(AddressMuseToHost((what_a)))) = (what_v)))
#endif
