/***
 * muse-core.c
 * copyright (c) 2012-2013 by andrei borac
 ***/

static MUSE_TYPE_U4 MUSE_SCAN_UNBE(MUSE_TYPE_ST* e, MUSE_TYPE_U4 n)
{
  MUSE_TYPE_U4 out = 0;
  
  while (n-- > 0) {
    out <<= 8;
    out |= (*((e->c)++));
  }
  
  return out;
}

static inline MUSE_TYPE_U4 MUSE_SCAN_JUMP(MUSE_TYPE_ST* e)
{
  return ((MUSE_TYPE_U4)(((MUSE_TYPE_S4)(((MUSE_TYPE_S1)(MUSE_SCAN_UNBE(e, 1)))))));
}

static inline MUSE_TYPE_U4 MUSE_SCAN_CALL(MUSE_TYPE_ST* e)
{
  return ((MUSE_TYPE_U4)(((MUSE_TYPE_S4)(((MUSE_TYPE_S2)(MUSE_SCAN_UNBE(e, 2)))))));
}

static inline MUSE_TYPE_U4 MUSE_SCAN_WORD(MUSE_TYPE_ST* e)
{
  return MUSE_SCAN_UNBE(e, 4);
}

static inline MUSE_TYPE_U4 MUSE_CISC_RVB(MUSE_TYPE_U4 x)
{
  MUSE_TYPE_U4 y = 0;
  
  MUSE_TYPE_U4 n = 32;
  
  while (n-- != 0) {
    y = ((y << 1) | (x & 0x00000001));
    x >>= 1;
  }
  
  return y;
}

static inline MUSE_TYPE_U4 MUSE_CISC_RVY(MUSE_TYPE_U4 x)
{
  MUSE_TYPE_U4 y = 0;
  
  MUSE_TYPE_U4 n = 4;
  
  while (n-- != 0) {
    y = ((y << 8) | (x & 0xFF));
    x >>= 8;
  }
  
  return y;
}

static inline MUSE_TYPE_U4 MUSE_CISC_DTB(MUSE_TYPE_U4 x)
{
  x |= x >>  1;
  x |= x >>  2;
  x |= x >>  4;
  x |= x >>  8;
  x |= x >> 16;
  
  return (-(x & 0x00000001));
}

static inline MUSE_TYPE_U4 MUSE_CISC_DTY(MUSE_TYPE_U4 x)
{
  x |= ((x & 0x01010101) << 1);
  x |= ((x & 0x03030303) << 2);
  x |= ((x & 0x0F0F0F0F) << 4);
  
  x &= ((x & 0x80808080)     );
  
  x |= (x >> 1);
  x |= (x >> 2);
  x |= (x >> 4);
  
  return x;
}

static inline MUSE_TYPE_U4 MUSE_CISC_SLS(MUSE_TYPE_U4 x)
{
  if (x == 0) return (((MUSE_TYPE_U4)(0)) - 1);
  
  MUSE_TYPE_U4 i = 0;
  
  while (MUSE_TRUE) {
    if ((x & 0x00000001) != 0) return i;
    x >>= 1;
    i++;
  }
  
  return i;
}

static inline MUSE_TYPE_U4 MUSE_CISC_SMS(MUSE_TYPE_U4 x)
{
  if (x == 0) return (((MUSE_TYPE_U4)(0)) - 1);
  
  MUSE_TYPE_U4 i = 0;
  
  while (MUSE_TRUE) {
    if ((x & 0x80000000) != 0) return (31 - i);
    x <<= 1;
    i++;
  }
}

static inline MUSE_TYPE_U4 MUSE_CISC_LOG(MUSE_TYPE_U4 x)
{
  if (x == 0) return 0;
  
  MUSE_TYPE_U4 i = MUSE_CISC_SMS(x);
  
  if (i == 31) {
    return ((x <= (((MUSE_TYPE_U4)(1)) << i)) ? (i) : (((MUSE_TYPE_U4)(0)) - 1));
  } else {
    return ((x <= (((MUSE_TYPE_U4)(1)) << i)) ? (i) : (i + 1));
  }
}

static inline MUSE_TYPE_U4 MUSE_CISC_RSR(MUSE_TYPE_U4 x, MUSE_TYPE_U4 y)
{
  y &= 31;
  
  if (y == 0) {
    return x;
  } else {
    return ((x >> y) | (x << (32 - y)));
  }
}
