/***
 * MUSE.java
 * copyright (c) 2012-2013 by andrei borac
 ***/

package zs42.muse;

import zs42.parts.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class MUSE
{
  /***
   * UTILITIES
   ***/
  
  public static void trace(String line)
  {
    System.out.println(line);
  }
  
  public static void trace_noln(String line)
  {
    System.out.print(line);
  }
  
  public static RuntimeException fatal(Throwable e)
  {
    for (PrintStream tgt : (new PrintStream[] { System.out })) { //, System.err })) {
      e.printStackTrace(tgt);
      tgt.flush();
    }
    
    System.exit(1);
    
    return null;
  }
  
  public static String depict(Throwable e)
  {
    ByteArrayOutputStream bos = (new ByteArrayOutputStream());
    PrintStream out = (new PrintStream(bos));
    e.printStackTrace(out);
    return PartsUtils.arr2str(bos.toByteArray()).replace('\n', '~');
  }
  
  static <T> T nonnull(T obj)
  {
    if (obj == null) throw null;
    return obj;
  }
  
  static boolean fitsInBitsSE(int value, int bitc)
  {
    return (((value << (32 - bitc)) >> (32 - bitc)) == value);
  }
  
  static boolean fitsInBitsUS(int value, int bitc)
  {
    return (((value << (32 - bitc)) >>> (32 - bitc)) == value);
  }
  
  static boolean fitsInBytesSE(int value, int bytec)
  {
    return fitsInBitsSE(value, (bytec << 3));
  }
  
  static boolean fitsInBytesUS(int value, int bytec)
  {
    return fitsInBitsUS(value, (bytec << 3));
  }
  
  static byte[] writeBE(int value, int bytec)
  {
    byte[] output = (new byte[bytec]);
    
    for (int i = 0; i < output.length; i++) {
      output[i] = ((byte)((value >> ((output.length - 1 - i) << 3))));
    }
    
    return output;
  }
  
  public static String[] arrayListToStringArray(ArrayList<String> inp)
  {
    String[] out = (new String[inp.size()]);
    
    for (int i = 0; i < out.length; i++) {
      out[i] = inp.get(i);
    }
    
    return out;
  }
  
  public static String[] arrayListClearToStringArray(ArrayList<String> inp)
  {
    String[] out = arrayListToStringArray(inp);
    inp.clear();
    return out;
  }
  
  public static String indent(int l)
  {
    int    pos = 0;
    char[] buf = (new char[((l << 1) + 2)]);
    
    buf[pos++] = ' ';
    buf[pos++] = ' ';
    
    for (int i = 0; i < l; i++) {
      buf[pos++] = '|';
      buf[pos++] = ' ';
    }
    
    return (new String(buf));
  }
  
  static final String[] hexify_prefix = (new String[] { null, "0x0000000", "0x000000", "0x00000", "0x0000", "0x000", "0x00", "0x0", "0x" });
  
  public static String hexify(int x)
  {
    if (x == 0) {
      return "0x00000000";
    } else {
      String s = Integer.toHexString(x);
      s = hexify_prefix[s.length()] + s;
      return s;
    }
  }
  
  static abstract class F2<R, A1>
  {
    abstract R invoke(A1 a1);
  }
  
  static <T> String generateRegexpMatchingAnyAliasOfAny(T[] src, F2<String[], T> map)
  {
    StringBuilder out = (new StringBuilder());
    
    out.append("(");
    
    boolean first = true;
    
    for (T obj : src) {
      for (String str : map.invoke(obj)) {
        if (first) {
          first = false;
        } else {
          out.append("|");
        }
        
        out.append(Pattern.quote(str));
      }
    }
    
    out.append(")");
    
    return out.toString();
  }
  
  static class ReferenceComparator<T> implements Comparator<T>
  {
    final HashMap<Integer, ArrayList<T>> canon = (new HashMap<Integer, ArrayList<T>>());
    
    int lookup(T obj, int obj_hashCode)
    {
      ArrayList<T> chain = canon.get(obj_hashCode);
      
      if (chain == null) {
        chain = (new ArrayList<T>());
        canon.put(obj_hashCode, chain);
      }
      
      for (int i = 0; i < chain.size(); i++) {
        if (chain.get(i) == obj) {
          return i;
        }
      }
      
      chain.add(obj);
      
      return (chain.size() - 1);
    }
    
    public int compare(T lhs, T rhs)
    {
      int lhs_hashCode = lhs.hashCode();
      int rhs_hashCode = rhs.hashCode();
      
      int lhs_lookupValue = lookup(lhs, lhs_hashCode);
      int rhs_lookupValue = lookup(rhs, rhs_hashCode);
      
      if (lhs.hashCode() < rhs.hashCode()) return -1;
      if (lhs.hashCode() > rhs.hashCode()) return +1;
      
      if (lhs_lookupValue < rhs_lookupValue) return -1;
      if (lhs_lookupValue > rhs_lookupValue) return +1;
      
      return 0;
    }
  }
  
  static class CheckedMap<K, V>
  {
    final TreeMap<K, V> inner;
    
    CheckedMap()
    {
      inner = (new TreeMap<K, V>());
    }
    
    CheckedMap(Comparator<K> comparator)
    {
      inner = (new TreeMap<K, V>(comparator));
    }
    
    void putAll(CheckedMap<K, V> peer)
    {
      inner.putAll(peer.inner);
    }
    
    void put(K key, V val)
    {
      if (((key == null) || (val == null))) throw null;
      inner.put(key, val);
    }
    
    boolean has(K key)
    {
      return inner.containsKey(key);
    }
    
    V get(K key)
    {
      V val = inner.get(key);
      if (val == null) throw null;
      return val;
    }
  }
  
  static interface GenericTokenClass
  {
    public String getFriendlyName();
    public String getRegexp();
  }
  
  static class Token<T extends GenericTokenClass>
  {
    final T      type;
    final String text;
    
    final String file;
    final int    line;
    final int    word;
    final String full;
    
    Token(T type, String text, String file, int line, int word, String full)
    {
      this.type = type;
      this.text = text;
      
      this.file = file;
      this.line = line;
      this.word = word;
      this.full = full;
    }
    
    String depict()
    {
      return ("'" + full + "' (file " + file + ", line " + (line + 1) + ", word " + (word + 1) + "), classified as " + type.getFriendlyName());
    }
    
    Token<T> expect(T type)
    {
      if (this.type == type) {
        return this;
      } else {
        throw (new RuntimeException("MUSE: scanner: token refused; parser expecting class " + type.getFriendlyName() + " only"));
      }
    }
  }
  
  static abstract class TokenHandler<T extends GenericTokenClass>
  {
    boolean finished = false;
    
    abstract String getParserState();
    abstract String getExpectedClasses();
    
    abstract boolean handle(Token<T> token);
  }
  
  static class TokenStream<T extends GenericTokenClass>
  {
    final T[] typeoids;
    final ArrayDeque<Token<T>> tokens = (new ArrayDeque<Token<T>>());
    
    TokenStream(T[] typeoids)
    {
      this.typeoids = typeoids;
    }
    
    Token<T> obtainToken()
    {
      Token<T> token = tokens.removeFirst();
      trace("MUSE: scanner: supplying token " + token.depict());
      return token;
    }
    
    void supplyToken(TokenHandler<T> handler)
    {
      Token<T> token = obtainToken();
      
      if (!handler.handle(token)) {
        throw (new RuntimeException("MUSE: scanner: token refused; parser in state \"" + handler.getParserState() + "\", expecting token classes: " + handler.getExpectedClasses() + "."));
      }
    }
    
    void repeatSupplyToken(TokenHandler<T> handler)
    {
      while (!handler.finished) {
        supplyToken(handler);
      }
    }
    
    static final ArrayList<String> filterEmptyOutput = (new ArrayList<String>());
    
    static String[] filterEmpty(String[] words)
    {
      for (String word : words) {
        if (!(word.isEmpty())) {
          filterEmptyOutput.add(word);
        }
      }
      
      return arrayListClearToStringArray(filterEmptyOutput);
    }
    
    static abstract class MacroPiece
    {
      abstract void evaluate(ArrayList<String> output, String[] words, int wordi);
      
      static class ConstantMacroPiece extends MacroPiece
      {
        final String yield;
        
        ConstantMacroPiece(String yield)
        {
          this.yield = yield;
        }
        
        void evaluate(ArrayList<String> output, String[] words, int wordi)
        {
          output.add(yield);
        }
      }
      
      static class ArgumentMacroPiece extends MacroPiece
      {
        final int index;
        
        ArgumentMacroPiece(int index)
        {
          this.index = index;
        }
        
        void evaluate(ArrayList<String> output, String[] words, int wordi)
        {
          output.add(words[wordi + index]);
        }
      }
    }
    
    static class MacroDefinition
    {
      final int narg;
      
      final ArrayList<MacroPiece> pieces = (new ArrayList<MacroPiece>());
      
      MacroDefinition(int narg)
      {
        this.narg = narg;
      }
      
      void addPiece(MacroPiece piece)
      {
        pieces.add(piece);
      }
    }
    
    final AsciiTreeMap<MacroDefinition> macros = (new AsciiTreeMap<MacroDefinition>());
    
    static final String macroDefineArgumentPrefix = ".arg_";
    
    void macroDefine(String[] words, int wordi)
    {
      String name = words[wordi++];
      int    narg = Integer.parseInt(words[wordi++]);
      
      MacroDefinition macro = (new MacroDefinition(narg));
      
      for (wordi = wordi; wordi < words.length; wordi++) {
        if (words[wordi].startsWith(macroDefineArgumentPrefix)) {
          int index = Integer.parseInt(words[wordi].substring(macroDefineArgumentPrefix.length()));
          macro.addPiece((new MacroPiece.ArgumentMacroPiece(index)));
        } else {
          macro.addPiece((new MacroPiece.ConstantMacroPiece(words[wordi])));
        }
      }
      
      macros.put(name, macro);
    }
    
    void macroDelete(String[] words, int wordi)
    {
      for (wordi = wordi; wordi < words.length; wordi++) {
        String macro = words[wordi];
        if (!(macros.containsKey(macro))) throw (new RuntimeException("MUSE: scanner: attempt to undefine unknown macro '" + macro + "'"));
        macros.remove(macro);
      }
    }
    
    final ArrayList<String> macroExpandOutput = (new ArrayList<String>());
    
    String[] macroExpand(String[] words)
    {
      ArrayList<String> output = macroExpandOutput;
      
      for (int wordi = 0; wordi < words.length; wordi++) {
        MacroDefinition macro = macros.get(words[wordi]);
        
        if (macro != null) {
          for (MacroPiece piece : macro.pieces) {
            piece.evaluate(output, words, wordi);
          }
          
          wordi += macro.narg;
        } else {
          output.add(words[wordi]);
        }
      }
      
      return arrayListClearToStringArray(output);
    }
    
    void appendTokenize(String file, Token<T> end_of_file)
    {
      final Pattern LINES = Pattern.compile("\\r?\\n");
      final Pattern WORDS = Pattern.compile("\\s+");
      
      final Pattern COMMENT = Pattern.compile("#.*");
      
      ArrayList<Pattern> class_pattern = (new ArrayList<Pattern> ());
      ArrayList<T>       class_typeoid = (new ArrayList<T>       ());
      
      for (T typeoid : typeoids) {
        class_pattern.add(Pattern.compile(typeoid.getRegexp()));
        class_typeoid.add(typeoid);
      }
      
      String[] lines = LINES.split(PartsUtils.arr2str(SimpleIO.slurp(file)));
      
      for (int line = 0; line < lines.length; line++) {
        trace("line: '" + lines[line] + "'");
        
        String[] words = filterEmpty(WORDS.split(COMMENT.matcher(lines[line]).replaceFirst("")));
        
        if (words.length > 0) {
          /****/ if (words[0].equals(".patch")) {
            String filename = words[1];
            if (!filename.startsWith("file:")) throw (new RuntimeException("MUSE: scanner: patch argument '" + filename + "' expected to begin with 'file:'"));
            filename = filename.substring(("file:").length());
            appendTokenize(filename, null);
          } else if (words[0].equals(".macro")) {
            macroDefine(words, 1);
          } else if (words[0].equals(".undef")) {
            macroDelete(words, 1);
          } else {
            words = macroExpand(words);
            
            for (int word = 0; word < words.length; word++) {
              trace("word: '" + words[word] + "'");
              
              outer:
              do {
                for (int i = 0; i < class_pattern.size(); i++) {
                  Matcher matcher;
                  
                  if ((matcher = class_pattern.get(i).matcher(words[word])).matches()) {
                    tokens.add((new Token<T>(class_typeoid.get(i), matcher.group(1), file, line, word, words[word])));
                    break outer;
                  }
                }
                
                throw (new RuntimeException("MUSE: scanner: input token '" + words[word] + "' did not match any token type regular expression (file '" + file + "', line " + (line + 1) + ", word " + word + ")"));
              } while (false);
            }
          }
        }
      }
      
      if (end_of_file != null) {
        tokens.add(end_of_file);
      }
    }
  }
  
  /***
   * MODEL/INTERPRETER
   ***/
  
  public static final class Model
  {
    public static enum MachineEndianness
    {
      EL
        {
          public int getByteSham(int addr_byte)
          {
            return ((addr_byte & 0x3) << 3);
          }
        },
      BE
        {
          public int getByteSham(int addr_byte)
          {
            return ((3 - (addr_byte & 0x3)) << 3);
          }
        };
      
      public final    int getByteWord(int addr_byte) { return (addr_byte >> 2); }
      public abstract int getByteSham(int addr_byte);
    }
    
    public static class Trace
    {
      public static class SymbolTable implements Serializable
      {
        public static class Frame implements Serializable
        {
          public final int address;
          public final String label;
          public final boolean callable;
          public final int arity;
          public final ArrayList<String> variables;
          
          public Frame(int address, String label, boolean callable, int arity, ArrayList<String> variables)
          {
            this.address = address;
            this.label = label;
            this.callable = callable;
            this.arity = arity;
            this.variables = variables;
          }
        }
        
        final TreeMap<Integer, Frame> frames = (new TreeMap<Integer, Frame>());
      }
      
      public static abstract class Event implements Serializable
      {
        public static enum Level
        {
          ATOM
            { public String getLeader() { return ". "; } },
          
          INST
            { public String getLeader() { return "+ "; } },
          
          FLOW
            { public String getLeader() { return "* "; } };
          
          public abstract String getLeader();
        }
        
        public abstract Level getLevel();
        
        public abstract int getDepth();
        
        public abstract String depict(SymbolTable table);
        
        public abstract void effect(MachineBridge bridge);
      }
      
      public static abstract class Collector
      {
        public abstract void collect(Event event);
        
        public static final class Nullary extends Collector
        {
          public void collect(Event event) { }
        }
        
        public static final class ToArrayList extends Collector
        {
          final ArrayList<Event> target;
          
          public ToArrayList(ArrayList<Event> target)
          {
            this.target = target;
          }
          
          public void collect(Event event)
          {
            target.add(event);
          }
        }
      }
    }
    
    public static final class MachineParameters implements Serializable
    {
      public final int jump_target_bytes;
      public final int ifun_target_bytes;
      
      public final int stack_length;
      public final int unwind_length;
      
      public final int code_memory_offset;
      public final int code_memory_length;
      
      public final int data_memory_offset;
      public final int data_memory_length;
      
      public final boolean halt_immediately;
      
      public final boolean trace_instructions;
      public final boolean trace_register;
      public final boolean trace_stack_reads;
      public final boolean trace_stack_writes;
      public final boolean trace_unwind;
      public final boolean trace_memory_reads;
      public final boolean trace_memory_writes;
      
      public MachineParameters(int jump_target_bytes, int ifun_target_bytes, int stack_length, int unwind_length, int code_memory_offset, int code_memory_length, int data_memory_offset, int data_memory_length, TreeSet<String> options)
      {
        this.jump_target_bytes = jump_target_bytes;
        this.ifun_target_bytes = ifun_target_bytes;
        
        this.stack_length  = stack_length;
        this.unwind_length = unwind_length;
        
        this.code_memory_offset = code_memory_offset;
        this.code_memory_length = code_memory_length;
        
        this.data_memory_offset = data_memory_offset;
        this.data_memory_length = data_memory_length;
        
        this.halt_immediately    = options.contains("halt-immediately");
        
        this.trace_instructions  = options.contains("trace-instructions");
        this.trace_register      = options.contains("trace-register");
        this.trace_stack_reads   = options.contains("trace-stack-reads");
        this.trace_stack_writes  = options.contains("trace-stack-writes");
        this.trace_unwind        = options.contains("trace-unwind");
        this.trace_memory_reads  = options.contains("trace-memory-reads");
        this.trace_memory_writes = options.contains("trace-memory-writes");
      }
    }
    
    public static final class MachineProgram
    {
      final MachineParameters par;
      
      final byte[] ops;
      
      public MachineProgram(MachineParameters par, byte[] ops)
      {
        this.par = par;
        
        this.ops = ops;
      }
      
      private final void chk(int ctr)
      {
        if (!((0 <= ctr) && (ctr < ops.length))) throw (new RuntimeException("MUSE: bad program counter " + hexify(ctr)));
      }
      
      public final byte get(int ctr)
      {
        chk(ctr);
        return ops[ctr];
      }
      
      public final void put(int ctr, byte val)
      {
        chk(ctr);
        ops[ctr] = val;
      }
    }
    
    public static final class MachineStack
    {
      final MachineParameters par;
      
      final int[] elm;
      
      int top = 0;
      
      public MachineStack(MachineParameters par, int cap)
      {
        this.par = par;
        
        elm = (new int[cap]);
      }
      
      private final int chk(int rel)
      {
        int off = (top + rel);
        if (!((0 <= off) && (off < elm.length))) throw (new RuntimeException("MUSE: bad stack offset " + hexify(off)));
        return off;
      }
      
      public final void adj(final int val)
      {
        if ((par.trace_stack_reads || par.trace_stack_writes)) {
          /****/ if (val > 0) {
            trace("  s += " + (+(val)));
          } else if (val < 0) {
            trace("  s -= " + (-(val)));
          } else {
            trace("  s = s");
          }
        }
        
        top = chk(val);
      }
      
      public final int get(final int rel)
      {
        final int lea = chk(rel);
        
        final int val = elm[lea];
        
        if (par.trace_stack_reads) {
          trace("  s[" + rel + "=" + lea + "] ==> " + hexify(val));
        }
        
        return val;
      }
      
      public final void put(final int rel, final int val)
      {
        int lea = chk(rel);
        
        elm[lea] = val;
        
        if (par.trace_stack_writes) {
          trace("  s[" + rel + "=" + lea + "] <== " + hexify(val));
        }
      }
    }
    
    public static final class MachineUnwind
    {
      final MachineParameters par;
      
      final int[] elm;
      
      int len = 0;
      
      public MachineUnwind(MachineParameters par, int cap)
      {
        this.par = par;
        
        this.elm = (new int[cap]);
      }
      
      final void push(final int val)
      {
        if (!(len < elm.length)) throw (new RuntimeException("MUSE: unwind stack overflow"));
        elm[len++] = val;
      }
      
      final int pull()
      {
        if (!(len > 0)) throw (new RuntimeException("MUSE: unwind stack underflow"));
        
        final int val = elm[--len];
        
        return val;
      }
    }
    
    public static final class MachineMemory
    {
      static final int RESET_VALUE = 0xDEADBEEF;
      
      final MachineParameters par;
      
      final int memory_offset;
      final int memory_extent;
      
      final int[] m;
      
      public MachineMemory(MachineParameters par, int memory_offset, int memory_length, byte[] contents)
      {
        this.par = par;
        
        this.memory_offset = memory_offset;
        this.memory_extent = (memory_offset + memory_length);
        
        this.m = (new int[memory_length]);
        
        if (contents != null) {
          int src = 0;
          int dst = 0;
          
          int n = ((contents.length + 3) / 4);
          
          while (n-- > 0) {
            int v = 0;
            
            /* TODO: allow an endianness parameter; for now the code assumes EL */
            
            for (int i = 0; i < 4; i++) {
              v >>>= 8;
              v |= (((src < contents.length) ? (contents[src++]) : (0)) << 24);
            }
            
            //System.out.println("m[" + hexify(dst) + "] = " + hexify(v));
            
            m[dst++] = v;
          }
        } else {
          Arrays.fill(m, RESET_VALUE);
        }
      }
      
      public final int getMemoryOffset()
      {
        return memory_offset;
      }
      
      public final int getMemoryExtent()
      {
        return memory_extent;
      }
      
      private final void chk(int idx)
      {
        if (!((memory_offset <= idx) && (idx < memory_extent))) throw (new RuntimeException("MUSE: bad memory address " + hexify(idx) + " (valid offset " + hexify(memory_offset) + " extent " + hexify(memory_extent) + ")"));
      }
      
      public final int get(int idx)
      {
        chk(idx);
        
        int val = m[(idx - memory_offset)];
        
        if (par.trace_memory_reads) {
          trace("  m[" + hexify(idx) + "] ==> " + hexify(val));
        }
        
        return val;
      }
      
      public final void put(int idx, int val)
      {
        chk(idx);
        
        m[(idx - memory_offset)] = val;
        
        if (par.trace_memory_writes) {
          trace("  m[" + hexify(idx) + "] <== " + hexify(val));
        }
      }
    }
    
    public static final class MachineAccess
    {
      public final MachineParameters par;
      
      final MachineMemory code;
      final MachineMemory data;
      
      MachineAccess(MachineParameters par, MachineMemory code, MachineMemory data)
      {
        this.par = par;
        
        this.code = code;
        this.data = data;
      }
      
      public final int get(final int idx)
      {
        final int val;
        
        /****/ if ((idx >> 24) == 0x0C) {
          val = code.get(idx);
        } else if ((idx >> 24) == 0x0D) {
          val = data.get(idx);
        } else {
          throw (new RuntimeException("MUSE: bad memory address " + hexify(idx) + " (outside of 0x0C00 (code) or 0x0D00 (data) planes)"));
        }
        
        return val;
      }
      
      public final void put(final int idx, final int val)
      {
        /****/ if ((idx >> 24) == 0x0C) {
          throw (new RuntimeException("MUSE: bad memory address " + hexify(idx) + " (attempted write to 0x0C00 (code) plane)"));
        } else if ((idx >> 24) == 0x0D) {
          data.put(idx, val);
        } else {
          throw (new RuntimeException("MUSE: bad memory address " + hexify(idx) + " (outside of 0x0C00 (code) or 0x0D00 (data) planes)"));
        }
      }
    }
    
    public static final class MachineBridge
    {
      public final MachineParameters par;
      
      final Trace.Collector    col;
      final MachineEnvironment env;
      
      final MachineProgram p;
      final MachineStack   s;
      final MachineUnwind  u;
      final MachineAccess  a;
      
      /** location (instruction pointer / program counter) */
      int l;
      
      /** "the" register */
      int r;
      
      /** "halt" flag */
      boolean h;
      
      /** recursion level (number of nested function calls) */
      int recursion_level;
      
      /** "peers" to receive memory writes */
      final ArrayList<MachineBridge> peers = (new ArrayList<MachineBridge>());
      
      public void addPeer(MachineBridge peer)
      {
        peers.add(peer);
      }
      
      public void reset()
      {
        Arrays.fill(s.elm, 0);
        s.top = 0;
        
        Arrays.fill(u.elm, 0);
        u.len = 0;
        
        Arrays.fill(a.data.m, MachineMemory.RESET_VALUE);
        
        l = 0;
        
        r = 0;
        
        h = false;
        
        recursion_level = 0;
      }
      
      public MachineBridge(byte[] opcodes, MachineParameters par_shadow, MachineEnvironment env_shadow, Trace.Collector col_shadow, MachineMemory common)
      {
        par = par_shadow;
        
        col = col_shadow;
        env = env_shadow;
        
        MachineMemory c;
        MachineMemory d;
        
        p = (new MachineProgram(par, opcodes));
        s = (new MachineStack(par, par.stack_length));
        u = (new MachineUnwind(par, par.unwind_length));
        c = (new MachineMemory(par, par.code_memory_offset, par.code_memory_length, opcodes));
        d = (new MachineMemory(par, par.data_memory_offset, par.data_memory_length, null));
        
        /* cheap hack to support common memory */
        {
          if (common != null) {
            d = common;
          }
        }
        
        a = (new MachineAccess(par, c, d));
        
        reset();
      }
      
      public MachineBridge(byte[] opcodes, MachineParameters par_shadow, MachineEnvironment env_shadow, Trace.Collector col_shadow)
      {
        this(opcodes, par_shadow, env_shadow, col_shadow, null);
      }
      
      public static void col_x_msg(Trace.Collector col, final String msg)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return msg; }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      
      static void col_p_get_0(Trace.Collector col, final int ctr)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("p[" + hexify(ctr) + "] ==> ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_p_get_1(Trace.Collector col, final int ctr, final byte val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("p[" + hexify(ctr) + "] ==> " + hexify((val & 0xFF))); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_p_put_0(Trace.Collector col, final int ctr, final byte val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("p[" + hexify(ctr) + "] <== " + hexify((val & 0xFF)) + " ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_p_put_1(Trace.Collector col, final int ctr, final byte val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("p[" + hexify(ctr) + "] <== " + hexify((val & 0xFF))); }
              public void effect(MachineBridge bridge) { bridge.p_put(ctr, val); }
            }));
      }
      
      
      static void col_s_get_0(Trace.Collector col, final int rel)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("s[" + rel + "] ==> ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_s_get_1(Trace.Collector col, final int rel, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("s[" + rel + "] ==> " + hexify(val)); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_s_put_0(Trace.Collector col, final int rel, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("s[" + rel + "] <== " + hexify(val) + " ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_s_put_1(Trace.Collector col, final int rel, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("s[" + rel + "] <== " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.s_put(rel, val); }
            }));
      }
      
      static void col_s_adj_0(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { if (val > 0) return ("s += " + val + " ?"); else if (val < 0) return ("s -= " + (-(val)) + " ?"); else return "nop ?"; }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_s_adj_1(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { if (val > 0) return ("s += " + val); else if (val < 0) return ("s -= " + (-(val))); else return "nop"; }
              public void effect(MachineBridge bridge) { bridge.s_adj(val); }
            }));
      }
      
      
      static void col_u_pull_0(Trace.Collector col)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("u[] ==> ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_u_pull_1(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("u[] ==> " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.u_pull(); }
            }));
      }
      
      static void col_u_push_0(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("u[] <== " + hexify(val) + " ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_u_push_1(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("u[] <== " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.u_push(val); }
            }));
      }
      
      
      static void col_a_get_0(Trace.Collector col, final int idx)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("a[" + hexify(idx) + "] ==> ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_a_get_1(Trace.Collector col, final int idx, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("a[" + hexify(idx) + "] ==> " + hexify(val)); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_a_put_0(Trace.Collector col, final int idx, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("a[" + hexify(idx) + "] <== " + hexify(val) + " ?"); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_a_put_1(Trace.Collector col, final int idx, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("a[" + hexify(idx) + "] <== " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.a_put(idx, val); }
            }));
      }
      
      
      static void col_l_get_1(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("l ==> " + hexify(val)); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_l_put_z(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("l <== " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.l_put(val); }
            }));
      }
      
      
      static void col_r_get_1(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("r ==> " + hexify(val)); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_r_put_z(Trace.Collector col, final int val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("r <== " + hexify(val)); }
              public void effect(MachineBridge bridge) { bridge.r_put(val); }
            }));
      }
      
      
      static void col_h_get_1(Trace.Collector col, final boolean val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("h ==> " + val); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_h_put_z(Trace.Collector col, final boolean val)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.ATOM; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { return ("h <== " + val); }
              public void effect(MachineBridge bridge) { bridge.h_put(val); }
            }));
      }
      
      
      
      public void x_msg(String msg) { col_x_msg(col, msg); }
      
      public byte p_get(int ctr) { col_p_get_0(col, ctr); byte val = p.get(ctr); col_p_get_1(col, ctr, val); return val; }
      public void p_put(int ctr, byte val) { col_p_put_0(col, ctr, val); p.put(ctr, val); col_p_put_1(col, ctr, val); }
      
      public int  s_get(int rel) { col_s_get_0(col, rel); int val = s.get(rel); col_s_get_1(col, rel, val); return val; }
      public void s_put(int rel, int val) { col_s_put_0(col, rel, val); s.put(rel, val); col_s_put_1(col, rel, val); }
      public void s_adj(int val) { col_s_adj_0(col, val); s.adj(val); col_s_adj_1(col, val); }
      
      public int  u_pull() { col_u_pull_0(col); int val = u.pull(); col_u_pull_1(col, val); return val; }
      public void u_push(int val) { col_u_push_0(col, val); u.push(val); col_u_push_1(col, val); }
      
      public int  a_get(int idx) { col_a_get_0(col, idx); int val = a.get(idx); col_a_get_1(col, idx, val); return val; }
      public void a_put(int idx, int val) { col_a_put_0(col, idx, val); a.put(idx, val); col_a_put_1(col, idx, val); for (MachineBridge peer : peers) { col_a_put_1(peer.col, idx, val); } }
      
      public int  l_get() { col_l_get_1(col, l); return l; }
      public void l_put(int val) { col_l_put_z(col, val); l = val; }
      
      public int  r_get() { col_r_get_1(col, r); return r; }
      public void r_put(int val) { col_r_put_z(col, val); r = val; }
      
      public boolean h_get() { col_h_get_1(col, h); return h; }
      public void    h_put(boolean val) { col_h_put_z(col, val); h = val; }
      
      /***
       * HIGH-LEVEL
       ***/
      
      public int l_inc() { int val = l_get(); l_put(val + 1); return val; }
      
      public int scan_unbe32(int n)
      {
        int x = 0;
        
        while (n-- > 0) {
          x <<= 8;
          x |= (p_get(l_inc()) & 0xFF);
        }
        
        return x;
      }
      
      public long scan_unbe64(int n)
      {
        long x = 0;
        
        while (n-- > 0) {
          x <<= 8;
          x |= (p_get(l_inc()) & 0xFF);
        }
        
        return x;
      }
      
      public static int sign_extend(int v, int b)
      {
        int a = (32 - b);
        return ((v << a) >> a);
      }
      
      public void a_get_bytes(int off_byte, byte[] buf_byte, MachineEndianness endianness)
      {
        for (int i = 0, pos_byte = off_byte; i < buf_byte.length; i++, pos_byte++) {
          buf_byte[i] = ((byte)(a_get(endianness.getByteWord(pos_byte)) >> endianness.getByteSham(pos_byte)));
        }
      }
      
      public void a_put_bytes(int off_byte, byte[] buf_byte, MachineEndianness endianness)
      {
        for (int i = 0, pos_byte = off_byte; i < buf_byte.length; i++, pos_byte++) {
          int word = endianness.getByteWord(pos_byte);
          int sham = endianness.getByteSham(pos_byte);
          
          int valu = a_get(word);
          
          valu &= (~(0xFF << sham));
          valu |= ((buf_byte[i] & 0xFF) << sham);
          
          a_put(word, valu);
        }
      }
    }
    
    /***
     * Basically, we want to allow multiple threads for ease of
     * programming while only allowing one thread to actually modify
     * machine states at any given time.
     * 
     * Also, determinism is preferred ...
     ***/
    public static abstract class MachineThread
    {
      final SynchronousQueue<Class<Void>> sq_enter = (new SynchronousQueue<Class<Void>>());
      final SynchronousQueue<Class<Void>> sq_leave = (new SynchronousQueue<Class<Void>>());
      
      Thread owner;
      
      final ArrayList<MachineThread> spawn = (new ArrayList<MachineThread>());
      
      int iteration = 0;
      
      boolean shutdown = false; // signal shutdown
      boolean terminal = false; // acknowledgement
      
      Throwable cause;
      
      public void fork(MachineThread thread)
      {
        spawn.add(thread);
      }
      
      ArrayList<String> printouts;
      
      public void trace(String line)
      {
        printouts.add(line);
      }
      
      public void yield()
      {
        if (Thread.currentThread() != owner) throw null;
        
        try {
          sq_leave.put(Void.TYPE);
          sq_enter.take();
        } catch (InterruptedException e) {
          throw (new RuntimeException(e));
        }
      }
      
      public void yieldInterval(int interval)
      {
        int expiry = (iteration + interval);
        
        while (iteration < expiry) {
          yield();
        }
      }
      
      void robin()
      {
        try {
          sq_enter.put(Void.TYPE);
          sq_leave.take();
        } catch (InterruptedException e) {
          throw (new RuntimeException(e));
        }
      }
      
      protected abstract void run(MachineThread thread);
      
      private static void launchem(ArrayList<MachineThread> threads)
      {
        for (final MachineThread thread : threads) {
          (new Thread()
            {
              public void run()
              {
                try {
                  thread.owner = Thread.currentThread();
                  
                  thread.sq_enter.take();
                  
                  try {
                    thread.run(thread);
                  } catch (Throwable e) {
                    thread.cause = e;
                  }
                  
                  thread.terminal = true;
                  
                  thread.sq_leave.put(Void.TYPE);
                } catch (Throwable e) {
                  throw fatal(e);
                }
              }
            }).start();
        }
      }
      
      public static boolean launch(int maximum_iterations, ArrayList<String> printouts, ArrayList<MachineThread> threads_initial)
      {
        ArrayList<MachineThread> threads = (new ArrayList<MachineThread>());
        
        ArrayList<MachineThread> pending = (new ArrayList<MachineThread>());
        
        pending.addAll(threads_initial);
        
        boolean shutdown = false;
        
        for (int iteration = 0; iteration < maximum_iterations; iteration++) {
          printouts.add("running iteration " + iteration);
          
          boolean terminal = true;
          
          if (!pending.isEmpty()) {
            launchem(pending);
            
            threads.addAll(pending);
            pending.clear();
          }
          
          for (final MachineThread thread : threads) {
            thread.iteration = iteration;
            
            thread.shutdown = shutdown;
            
            if (!thread.terminal) {
              terminal = false;
              
              //printouts.add("enter robin: " + thread);
              
              thread.printouts = printouts;
              
              thread.robin();
              
              if (thread.cause != null) {
                throw (new RuntimeException(thread.cause));
              }
              
              pending.addAll(thread.spawn);
              thread.spawn.clear();
              
              //printouts.add("leave robin: " + thread);
            }
            
            if (thread.shutdown) {
              shutdown = thread.shutdown;
            }
          }
          
          if (terminal) {
            return true;
          }
        }
        
        return false;
      }
    }
    
    public static abstract class MachineEnvironment
    {
      public abstract boolean onTrap(MachineThread thread, MachineBridge bridge, long magic);
    }
    
    public static final class MachineState
    {
      public final MachineBridge bridge;
      
      public MachineState(MachineBridge bridge_shadow)
      {
        this.bridge = bridge_shadow;
        
        if (bridge.par.halt_immediately) {
          bridge.h_put(true);
        }
      }
      
      int scan_unbe32(int n)
      {
        int x = 0;
        
        while (n-- > 0) {
          x <<= 8;
          x |= (bridge.p_get(bridge.l_inc()) & 0xFF);
        }
        
        return x;
      }
      
      long scan_unbe64(int n)
      {
        long x = 0;
        
        while (n-- > 0) {
          x <<= 8;
          x |= (bridge.p_get(bridge.l_inc()) & 0xFF);
        }
        
        return x;
      }
      
      int sign_extend(int v, int b)
      {
        int a = (32 - b);
        return ((v << a) >> a);
      }
      
      static void col_trace(Trace.Collector col, final byte opi)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.INST; }
              public int getDepth() { return -1; }
              public String depict(Trace.SymbolTable table) { InstructionFields fields = decodeInstruction(opi); return fields.functional.depict(fields); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      public int execute(MachineThread thread, int rem)
      {
        while (((!(bridge.h_get())) && (rem > 0))) {
          rem--;
          
          int  loc = bridge.l_inc();
          byte opi = bridge.p_get(loc);
          
          col_trace(bridge.col, opi);
          
          if (bridge.par.trace_instructions) {
            trace("");
            trace("  loc: " + hexify(loc) + " => opi: " + hexify((opi & 0xFF)));
          }
          
          InstructionFields fields = decodeInstruction(opi);
          
          if (bridge.par.trace_instructions) {
            trace(fields.functional.depict(fields));
          }
          
          fields.functional.effect(thread, bridge, fields);
          
          if (bridge.par.trace_register) {
            trace("  reg: " + hexify(bridge.r_get()));
          }
        }
        
        return rem;
      }
    }
    
    static final class InstructionFields
    {
      final GenericFunctional functional;
      
      final int opcode;
      final int versatile;
      
      final boolean opcode_even;
      
      InstructionFields(GenericFunctional functional, int opcode, int versatile)
      {
        this.functional = functional;
        
        this.opcode     = opcode;
        this.versatile  = versatile;
        
        this.opcode_even = ((opcode & 0x1) == 0);
      }
    }
    
    static interface GenericFunctional
    {
      public boolean hasFixedVersatile();
      
      public boolean isNopFor(int rhs);
      
      public String  depict(InstructionFields fields);
      public void    effect(MachineThread thread, MachineBridge bridge, InstructionFields fields);
      
      public void    kernel(StringBuilder out, InstructionFields fields);
    }
    
    static enum BranchConditionEnum
    {
      AW { BranchConditionEnum invert() { return NW; } boolean apply(int x) { return true;  }       String kernel() { return "MUSE_TRUE";  } },
      NW { BranchConditionEnum invert() { return AW; } boolean apply(int x) { return false; }       String kernel() { return "MUSE_FALSE"; } },
      
      EZ { BranchConditionEnum invert() { return NZ; } boolean apply(int x) { return ( (x == 0)); } String kernel() { return "( (e->r == 0))"; } },
      NZ { BranchConditionEnum invert() { return EZ; } boolean apply(int x) { return (!(x == 0)); } String kernel() { return "(!(e->r == 0))"; } },
      
      SP { BranchConditionEnum invert() { return NP; } boolean apply(int x) { return ( (x > 0)); }  String kernel() { return "( (((MUSE_TYPE_S4)(e->r)) > 0))"; } },
      NP { BranchConditionEnum invert() { return SP; } boolean apply(int x) { return (!(x > 0)); }  String kernel() { return "(!(((MUSE_TYPE_S4)(e->r)) > 0))"; } },
      
      SN { BranchConditionEnum invert() { return NN; } boolean apply(int x) { return ( (x < 0)); }  String kernel() { return "( (((MUSE_TYPE_S4)(e->r)) < 0))"; } },
      NN { BranchConditionEnum invert() { return SN; } boolean apply(int x) { return (!(x < 0)); }  String kernel() { return "(!(((MUSE_TYPE_S4)(e->r)) < 0))"; } };
      
      String[] getAliases()
      {
        return (new String[] { toString() });
      }
      
      abstract boolean apply(int x);
      abstract String kernel();
      
      boolean effect(MachineThread thread, MachineBridge bridge)
      {
        return apply(bridge.r_get());
      }
      
      abstract BranchConditionEnum invert();
      
      static BranchConditionEnum[] byOrdinal = values();
      
      static String generateRegexpMatchingAnyAliasOfAny()
      {
        return MUSE.generateRegexpMatchingAnyAliasOfAny(byOrdinal, (new F2<String[], BranchConditionEnum>() { String[] invoke(BranchConditionEnum obj) { return obj.getAliases(); } }));
      }
    }
    
    static enum ControlSequenceEnum implements GenericFunctional
    {
      xTRAP
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            if (!(bridge.env.onTrap(thread, bridge, bridge.scan_unbe64(versatile)))) {
              throw (new RuntimeException("MUSE: interpreter: unhandled trap"));
            }
          }
          
          void kernel(StringBuilder out)
          {
            out.append("if (versatile != 0) { MUSE_TYPE_BL remain = e->h(e, e->c, e->v); e->c += versatile; if (!remain) return MUSE_FALSE; }\n");
          }
        },
      
      cADD
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            bridge.r_put((bridge.r_get() + (versatile + 1)));
          }
          
          void kernel(StringBuilder out)
          {
            out.append("e->r += (versatile + 1);\n");
          }
        },
      
      cSEB
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            int immediate = MachineBridge.sign_extend(bridge.scan_unbe32(1), 8);
            
            bridge.r_put(immediate);
            
            bridge.s_put(versatile, immediate);
          }
          
          void kernel(StringBuilder out)
          {
            out.append("e->s[versatile] = e->r = ((MUSE_TYPE_U4)(((MUSE_TYPE_S4)(((MUSE_TYPE_S1)(*((e->c)++)))))));\n");
          }
        },
      
      cWRD
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            int immediate = bridge.scan_unbe32(4);
            
            bridge.r_put(immediate);
            
            bridge.s_put(versatile, immediate);
          }
          
          void kernel(StringBuilder out)
          {
            out.append("e->s[versatile] = e->r = MUSE_SCAN_WORD(e);\n");
          }
        },
      
      jUMP
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            int a = bridge.par.jump_target_bytes;
            
            int t = bridge.scan_unbe32(a);
            
            if (BranchConditionEnum.byOrdinal[versatile].effect(thread, bridge)) {
              bridge.l_put((bridge.l_get() + MachineBridge.sign_extend(t, (a << 3))));
            }
          }
          
          void kernel(StringBuilder out)
          {
            out.append("MUSE_TYPE_U4 offset = MUSE_SCAN_JUMP(e);\n");
            out.append("switch (versatile) {\n");
            
            for (int i = 0; i < 8; i++) {
              out.append("case " + i + ": { if (" + BranchConditionEnum.byOrdinal[i].kernel() + ") { e->c += offset; }; break; }\n");
            }
            
            out.append("}\n");
          }
        },
      
      iFUN
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            int a = bridge.par.ifun_target_bytes;
            int t = bridge.scan_unbe32(a);
            int o = MachineBridge.sign_extend(t, (a << 3));
            
            bridge.u_push(+(versatile));
            bridge.s_adj(+(versatile));
            
            int loc = bridge.l_get();
            
            bridge.u_push(loc);
            bridge.l_put(loc + o);
            
            col_iFUN(bridge.col, bridge.recursion_level, (loc + o), bridge.s_get(0), bridge.s_get(1), bridge.s_get(2), bridge.s_get(3));
            
            bridge.recursion_level++;
          }
          
          void kernel(StringBuilder out)
          {
            out.append("MUSE_TYPE_U4 offset = MUSE_SCAN_CALL(e);\n");
            out.append("*((e->u)++) = ((MUSE_TYPE_U4)((VOID*)(e->s)));\n");
            out.append("e->s += versatile;\n");
            out.append("*((e->u)++) = ((MUSE_TYPE_U4)((VOID*)(e->c)));\n");
            out.append("e->c += offset;\n");
          }
        },
      
      iRET
        {
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            while (versatile-- > 0) {
              bridge.u_pull();
            }
            
            bridge.l_put(bridge.u_pull());
            bridge.s_adj((-(bridge.u_pull())));
            
            bridge.recursion_level--;
            
            col_iRET(bridge.col, bridge.recursion_level, bridge.r_get());
          }
          
          void kernel(StringBuilder out)
          {
            out.append("e->u -= versatile;\n");
            out.append("e->c = ((MUSE_TYPE_U1*)((VOID*)(*(--(e->u)))));\n");
            out.append("e->s = ((MUSE_TYPE_U4*)((VOID*)(*(--(e->u)))));\n");
          }
        };
      
      static void col_iFUN(Trace.Collector col, final int rec, final int loc, final int arg1, final int arg2, final int arg3, final int arg4)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.FLOW; }
              
              public int getDepth() { return rec; }
              
              public String depict(Trace.SymbolTable table)
              {
                Trace.SymbolTable.Frame frame = table.frames.get(loc);
                
                if (frame != null) {
                  StringBuilder line = (new StringBuilder());
                  
                  line.append((indent(rec) + "invoke :" + frame.label + "("));
                  
                  if (frame.arity > 0) { line.append(hexify(arg1)); }
                  if (frame.arity > 1) { line.append(", "); line.append(hexify(arg2)); }
                  if (frame.arity > 2) { line.append(", "); line.append(hexify(arg3)); }
                  if (frame.arity > 3) { line.append(", "); line.append(hexify(arg4)); }
                  if (frame.arity > 4) { line.append(", ..."); }
                  
                  line.append(")");
                  
                  return line.toString();
                } else {
                  return (indent(rec) + "invoke " + hexify(loc) + " :UNKNOWN !!!");
                }
              }
              
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      static void col_iRET(Trace.Collector col, final int rec, final int ret)
      {
        col.collect
          ((new Trace.Event()
            {
              public Level getLevel() { return Level.FLOW; }
              public int getDepth() { return rec; }
              public String depict(Trace.SymbolTable table) { return (indent(rec) + "return " + hexify(ret)); }
              public void effect(MachineBridge bridge) { }
            }));
      }
      
      String[] getAliases()
      {
        return (new String[] { toString() });
      }
      
      abstract void effect(MachineThread thread, MachineBridge bridge, int versatile);
      abstract void kernel(StringBuilder out);
      
      public boolean hasFixedVersatile() { return false; }
      
      public boolean isNopFor(int rhs) { return false; }
      
      public String depict(InstructionFields fields)                       { return (getAliases()[0] + " [" + fields.versatile + "]"); }
      public void   effect(MachineThread thread, MachineBridge bridge, InstructionFields fields) { effect(thread, bridge, fields.versatile); }
      
      public void   kernel(StringBuilder out, InstructionFields fields)  { kernel(out); }
      
      static ControlSequenceEnum[] byOrdinal = values();
      
      static String generateRegexpMatchingAnyAliasOfAny()
      {
        return MUSE.generateRegexpMatchingAnyAliasOfAny(byOrdinal, (new F2<String[], ControlSequenceEnum>() { String[] invoke(ControlSequenceEnum obj) { return obj.getAliases(); } }));
      }
      
      static final CheckedMap<String, ControlSequenceEnum> fromString = (new CheckedMap<String, ControlSequenceEnum>());
      
      static
      {
        for (ControlSequenceEnum unary : byOrdinal) {
          for (String alias : unary.getAliases()) {
            fromString.put(alias, unary);
          }
        }
      }
    }
    
    static enum UnaryOperatorEnum implements GenericFunctional
    {
      rURYCLR
        {
          String[] getAliases() { return aliases_rURYCLR; }
          int apply(int x) { return 00; }
          void kernel(StringBuilder out) { out.append("e->r = 0;\n"); }
        },
      
      rURYUNI
        {
          String[] getAliases() { return aliases_rURYUNI; }
          int apply(int x) { return +1; }
          void kernel(StringBuilder out) { out.append("e->r = 1;\n"); }
        },
      
      rURYSET
        {
          String[] getAliases() { return aliases_rURYSET; }
          int apply(int x) { return -1; }
          void kernel(StringBuilder out) { out.append("e->r = -1;\n"); }
        },
      
      
      
      rURYDRF
        {
          String[] getAliases() { return aliases_rURYDRF; }
          int apply(int x) { throw null; }
          void effect(MachineThread thread, MachineBridge bridge) { bridge.r_put(bridge.a_get(bridge.r_get())); }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_MEMORY_GET(e, e->r);\n"); }
        },
      
      rURYSTO
        {
          String[] getAliases() { return aliases_rURYSTO; }
          int apply(int x) { throw null; }
          void effect(MachineThread thread, MachineBridge bridge) { bridge.u_push(bridge.r_get()); }
          void kernel(StringBuilder out) { out.append("(*((e->u)++)) = e->r;\n"); }
        },
        
      rURYRCL
        {
          String[] getAliases() { return aliases_rURYRCL; }
          int apply(int x) { throw null; }
          void effect(MachineThread thread, MachineBridge bridge) { bridge.r_put(bridge.u_pull()); }
          void kernel(StringBuilder out) { out.append("e->r = (*(--(e->u)));\n"); }
        },
      
      
      
      rURYINV
        {
          String[] getAliases() { return aliases_rURYINV; }
          int apply(int x) { return (~(x)); }
          void kernel(StringBuilder out) { out.append("e->r = (~(e->r));\n"); }
        },
      
      rURYNEG
        {
          String[] getAliases() { return aliases_rURYNEG; }
          int apply(int x) { return (-(x)); }
          void kernel(StringBuilder out) { out.append("e->r = (-(e->r));\n"); }
        },
      
      rURYDEC
        {
          String[] getAliases() { return aliases_rURYDEC; }
          int apply(int x) { return (x - 1); }
          void kernel(StringBuilder out) { out.append("e->r = ((e->r) - 1);\n"); }
        },
      
      
      
      rURYRSA
        {
          String[] getAliases() { return aliases_rURYRSA; }
          int apply(int x) { return (x >> 1); }
          void kernel(StringBuilder out) { out.append("e->r = ((MUSE_TYPE_U4)((((MUSE_TYPE_S4)(e->r)) >> 1)));\n"); }
        },
      
      rURYRSL
        {
          String[] getAliases() { return aliases_rURYRSL; }
          int apply(int x) { return (x >>> 1); }
          void kernel(StringBuilder out) { out.append("e->r = (e->r >> 1);\n"); }
        },
      
      rURYRSR
        {
          String[] getAliases() { return aliases_rURYRSR; }
          int apply(int x) { return Integer.rotateRight(x, 1); }
          void kernel(StringBuilder out) { out.append("e->r = ((e->r << 31) | (e->r >> 1));\n"); }
        },
      
      
      
      rURYLS1
        {
          String[] getAliases() { return aliases_rURYLS1; }
          int apply(int x) { return (x << 1); }
          void kernel(StringBuilder out) { out.append("e->r = (e->r << 1);\n"); }
        },
      
      rURYLS2
        {
          String[] getAliases() { return aliases_rURYLS2; }
          int apply(int x) { return (x << 2); }
          void kernel(StringBuilder out) { out.append("e->r = (e->r << 2);\n"); }
        },
      
      rURYLS3
        {
          String[] getAliases() { return aliases_rURYLS3; }
          int apply(int x) { return (x << 3); }
          void kernel(StringBuilder out) { out.append("e->r = (e->r << 3);\n"); }
        },
      
      rURYLS4
        {
          String[] getAliases() { return aliases_rURYLS4; }
          int apply(int x) { return (x << 4); }
          void kernel(StringBuilder out) { out.append("e->r = (e->r << 4);\n"); }
        },
      
      
      
      rURYRVB
        {
          String[] getAliases() { return aliases_rURYRVB; }
          int apply(int x) { return Integer.reverse(x); }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_RVB(e->r);\n"); }
        },
      
      rURYRVY
        {
          String[] getAliases() { return aliases_rURYRVY; }
          int apply(int x) { return Integer.reverseBytes(x); }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_RVY(e->r);\n"); }
        },
      
      rURYDTB
        {
          String[] getAliases() { return aliases_rURYDTB; }
          int apply(int x) { return ((x == 0) ? (0) : (-1)); }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_DTB(e->r);\n"); }
        },
      
      rURYDTY
        {
          String[] getAliases() { return aliases_rURYDTY; }
          
          int apply(int x)
          {
            for (int i = 0; i < 32; i += 8) {
              int y = (0xFF << i);
              x |= (((x & y) != 0) ? y : 0);
            }
            
            return x;
          }
          
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_DTY(e->r);\n"); }
        },
      
      
      
      rURYSLS
        {
          String[] getAliases() { return aliases_rURYSLS; }
          int apply(int x) { int y = Integer.numberOfTrailingZeros(x); return ((y < 32) ? (y + 1) : (-1)); }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_SLS(e->r);\n"); }
        },
      
      rURYSMS
        {
          String[] getAliases() { return aliases_rURYSMS; }
          int apply(int x) { int y = Integer.numberOfLeadingZeros(x); return (31 - y); /* tricky (!) */ }
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_SMS(e->r);\n"); }
        },
      
      rURYEXP
        {
          String[] getAliases() { return aliases_rURYEXP; }
          int apply(int x) { return ((x < 0) ? 0 : (((x < 32) ? (1 << x) : 0))); }
          void kernel(StringBuilder out) { out.append("e->r = ((e->r < 32) ? (((MUSE_TYPE_U4)(1)) << e->r) : 0);\n"); }
        },
      
      rURYLOG
        {
          String[] getAliases() { return aliases_rURYLOG; }
          
          int apply(int x)
          {
            if (x == 0) return 0;
            
            int y = (31 - Integer.numberOfLeadingZeros(x));
            
            if (x == (1 << y)) return y;
            
            if ((x & 0x80000000) != 0) return -1;
            
            return (y + 1);
          }
          
          void kernel(StringBuilder out) { out.append("e->r = MUSE_CISC_LOG(e->r);\n"); }
        };
      
      static final String[] aliases_rURYCLR = { "rURYCLR", ".CLR", "0=r" };
      static final String[] aliases_rURYUNI = { "rURYUNI", ".UNI", "1=r" };
      static final String[] aliases_rURYSET = { "rURYSET", ".SET", "-1=r", "~0=r" };
      
      static final String[] aliases_rURYDRF = { "rURYDRF", ".DRF", "[]" };
      static final String[] aliases_rURYSTO = { "rURYSTO", ".STO", };
      static final String[] aliases_rURYRCL = { "rURYRCL", ".RCL", };
      
      static final String[] aliases_rURYINV = { "rURYINV", ".INV", "~" };
      static final String[] aliases_rURYNEG = { "rURYNEG", ".NEG", "0-" };
      static final String[] aliases_rURYDEC = { "rURYDEC", ".DEC", "-1" };
      
      static final String[] aliases_rURYRSA = { "rURYRSA", ".RSA", ">>1" };
      static final String[] aliases_rURYRSL = { "rURYRSL", ".RSL", ">>>1" };
      static final String[] aliases_rURYRSR = { "rURYRSR", ".RSR", ">>>>1" };
      
      static final String[] aliases_rURYLS1 = { "rURYLS1", ".LS1", "<<1" };
      static final String[] aliases_rURYLS2 = { "rURYLS2", ".LS2", "<<2" };
      static final String[] aliases_rURYLS3 = { "rURYLS3", ".LS3", "<<3" };
      static final String[] aliases_rURYLS4 = { "rURYLS4", ".LS4", "<<4" };
      
      static final String[] aliases_rURYRVB = { "rURYRVB", ".RVB" };
      static final String[] aliases_rURYRVY = { "rURYRVY", ".RVY" };
      static final String[] aliases_rURYDTB = { "rURYDTB", ".DTB" };
      static final String[] aliases_rURYDTY = { "rURYDTY", ".DTY" };
      
      static final String[] aliases_rURYSLS = { "rURYSLS", ".SLS" };
      static final String[] aliases_rURYSMS = { "rURYSMS", ".SMS" };
      static final String[] aliases_rURYEXP = { "rURYEXP", ".EXP" };
      static final String[] aliases_rURYLOG = { "rURYLOG", ".LOG" };
      
      abstract String[] getAliases();
      
      abstract int apply(int x);
      
      abstract void kernel(StringBuilder out);
      
      void effect(MachineThread thread, MachineBridge bridge)
      {
        bridge.r_put(apply(bridge.r_get()));
      }
      
      public boolean hasFixedVersatile() { return true; }
      
      public boolean isNopFor(int rhs) { return false; }
      
      public String depict(InstructionFields fields)                       { return getAliases()[0]; }
      public void   effect(MachineThread thread, MachineBridge bridge, InstructionFields fields) { effect(thread, bridge); }
      
      public void   kernel(StringBuilder out, InstructionFields fields) { kernel(out); }
      
      static UnaryOperatorEnum[] byOrdinal = values();
      
      static String generateRegexpMatchingAnyAliasOfAny()
      {
        return MUSE.generateRegexpMatchingAnyAliasOfAny(byOrdinal, (new F2<String[], UnaryOperatorEnum>() { String[] invoke(UnaryOperatorEnum obj) { return obj.getAliases(); } }));
      }
      
      static final CheckedMap<String, UnaryOperatorEnum> fromString = (new CheckedMap<String, UnaryOperatorEnum>());
      
      static
      {
        for (UnaryOperatorEnum unary : byOrdinal) {
          for (String alias : unary.getAliases()) {
            fromString.put(alias, unary);
          }
        }
      }
    }
    
    static enum BinaryOperatorEnum implements GenericFunctional
    {
      rMOV
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rMOV; }
          
          boolean isnop(int rhs) { return isnop_zMOV(rhs); }
          int apply(int L, int R) { return apply_zMOV(L, R); }
          String kernel() { return kernel_zMOV(); }
        },
      
      vMOV
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vMOV; }
          
          boolean isnop(int rhs) { return isnop_zMOV(rhs); }
          int apply(int L, int R) { return apply_zMOV(L, R); }
          String kernel() { return kernel_zMOV(); }
        },
      
      rAND
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rAND; }
          
          boolean isnop(int rhs) { return isnop_zAND(rhs); }
          int apply(int L, int R) { return apply_zAND(L, R); }
          String kernel() { return kernel_zAND(); }
        },
      
      vAND
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vAND; }
          
          boolean isnop(int rhs) { return isnop_zAND(rhs); }
          int apply(int L, int R) { return apply_zAND(L, R); }
          String kernel() { return kernel_zAND(); }
        },
      
      rIOR
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rIOR; }
          
          boolean isnop(int rhs) { return isnop_zIOR(rhs); }
          int apply(int L, int R) { return apply_zIOR(L, R); }
          String kernel() { return kernel_zIOR(); }
        },
      
      vIOR
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vIOR; }
          
          boolean isnop(int rhs) { return isnop_zIOR(rhs); }
          int apply(int L, int R) { return apply_zIOR(L, R); }
          String kernel() { return kernel_zIOR(); }
        },
      
      rXOR
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rXOR; }
          
          boolean isnop(int rhs) { return isnop_zXOR(rhs); }
          int apply(int L, int R) { return apply_zXOR(L, R); }
          String kernel() { return kernel_zXOR(); }
        },
      
      vXOR
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vXOR; }
          
          boolean isnop(int rhs) { return isnop_zXOR(rhs); }
          int apply(int L, int R) { return apply_zXOR(L, R); }
          String kernel() { return kernel_zXOR(); }
        },
      
      rADD
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rADD; }
          
          boolean isnop(int rhs) { return isnop_zADD(rhs); }
          int apply(int L, int R) { return apply_zADD(L, R); }
          String kernel() { return kernel_zADD(); }
        },
      
      vADD
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vADD; }
          
          boolean isnop(int rhs) { return isnop_zADD(rhs); }
          int apply(int L, int R) { return apply_zADD(L, R); }
          String kernel() { return kernel_zADD(); }
        },
      
      rSUB
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rSUB; }
          
          boolean isnop(int rhs) { return isnop_zSUB(rhs); }
          int apply(int L, int R) { return apply_zSUB(L, R); }
          String kernel() { return kernel_zSUB(); }
        },
      
      vSUB
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vSUB; }
          
          boolean isnop(int rhs) { return isnop_zSUB(rhs); }
          int apply(int L, int R) { return apply_zSUB(L, R); }
          String kernel() { return kernel_zSUB(); }
        },
      
      rLSH
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rLSH; }
          
          boolean isnop(int rhs) { return isnop_zLSH(rhs); }
          int apply(int L, int R) { return apply_zLSH(L, R); }
          String kernel() { return kernel_zLSH(); }
        },
      
      vLSH
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vLSH; }
          
          boolean isnop(int rhs) { return isnop_zLSH(rhs); }
          int apply(int L, int R) { return apply_zLSH(L, R); }
          String kernel() { return kernel_zLSH(); }
        },
      
      rRSA
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rRSA; }
          
          boolean isnop(int rhs) { return isnop_zRSA(rhs); }
          int apply(int L, int R) { return apply_zRSA(L, R); }
          String kernel() { return kernel_zRSA(); }
        },
      
      vRSA
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vRSA; }
          
          boolean isnop(int rhs) { return isnop_zRSA(rhs); }
          int apply(int L, int R) { return apply_zRSA(L, R); }
          String kernel() { return kernel_zRSA(); }
        },
      
      rRSL
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rRSL; }
          
          boolean isnop(int rhs) { return isnop_zRSL(rhs); }
          int apply(int L, int R) { return apply_zRSL(L, R); }
          String kernel() { return kernel_zRSL(); }
        },
      
      vRSL
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vRSL; }
          
          boolean isnop(int rhs) { return isnop_zRSL(rhs); }
          int apply(int L, int R) { return apply_zRSL(L, R); }
          String kernel() { return kernel_zRSL(); }
        },
      
      rRSR
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rRSR; }
          
          boolean isnop(int rhs) { return isnop_zRSR(rhs); }
          int apply(int L, int R) { return apply_zRSR(L, R); }
          String kernel() { return kernel_zRSR(); }
        },
      
      vRSR
        {
          boolean isReversed() { return true; }
          boolean isExported() { return true; }
          
          String[] getAliases() { return aliases_vRSR; }
          
          boolean isnop(int rhs) { return isnop_zRSR(rhs); }
          int apply(int L, int R) { return apply_zRSR(L, R); }
          String kernel() { return kernel_zRSR(); }
        },
      
      rIND
        {
          boolean isReversed() { return false; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_rIND; }
          
          boolean isnop(int rhs) { return false; }
          int apply(int L, int R) { throw null; }
          String kernel() { throw null; }
          
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            bridge.a_put(bridge.r_get(), bridge.s_get(versatile));
          }
          
          void kernel(StringBuilder out, int versatile)
          {
            out.append("MUSE_MEMORY_PUT(e, e->r, e->s[versatile]);\n");
          }
        },
      
      vIND
        {
          boolean isReversed() { return true; }
          boolean isExported() { return false; }
          
          String[] getAliases() { return aliases_vIND; }
          
          boolean isnop(int rhs) { throw null; }
          int apply(int L, int R) { throw null; }
          String kernel() { throw null; }
          
          void effect(MachineThread thread, MachineBridge bridge, int versatile)
          {
            bridge.a_put(bridge.s_get(versatile), bridge.r_get());
          }
          
          void kernel(StringBuilder out, int versatile)
          {
            out.append("MUSE_MEMORY_PUT(e, e->s[versatile], e->r);\n");
          }
        };
      
      static final String[] aliases_rMOV = { "rMOV", "," };
      static final String[] aliases_vMOV = { "vMOV", "=" };
      
      static final String[] aliases_rAND = { "rAND", "&"  };
      static final String[] aliases_vAND = { "vAND", "=&" };
      
      static final String[] aliases_rIOR = { "rIOR", "|"  };
      static final String[] aliases_vIOR = { "vIOR", "=|" };
      
      static final String[] aliases_rXOR = { "rXOR", "^"  };
      static final String[] aliases_vXOR = { "vXOR", "=^" };
      
      static final String[] aliases_rADD = { "rADD", "+"  };
      static final String[] aliases_vADD = { "vADD", "=+" };
      
      static final String[] aliases_rSUB = { "rSUB", "-"  };
      static final String[] aliases_vSUB = { "vSUB", "=-" };
      
      static final String[] aliases_rLSH = { "rLSH", "<<"  };
      static final String[] aliases_vLSH = { "vLSH", "=<<" };
      
      static final String[] aliases_rRSA = { "rRSA", ">>"  };
      static final String[] aliases_vRSA = { "vRSA", "=>>" };
      
      static final String[] aliases_rRSL = { "rRSL", ">>>"  };
      static final String[] aliases_vRSL = { "vRSL", "=>>>" };
      
      static final String[] aliases_rRSR = { "rRSR", ">>>>"  };
      static final String[] aliases_vRSR = { "vRSR", "=>>>>" };
      
      static final String[] aliases_rIND = { "rIND", "[]<==" };
      static final String[] aliases_vIND = { "vIND", "==>[]" };
      
      static boolean isnop_zMOV(int rhs) { return false; }
      static boolean isnop_zAND(int rhs) { return (rhs == -1); }
      static boolean isnop_zIOR(int rhs) { return (rhs == 0); }
      static boolean isnop_zXOR(int rhs) { return (rhs == 0); }
      static boolean isnop_zADD(int rhs) { return (rhs == 0); }
      static boolean isnop_zSUB(int rhs) { return (rhs == 0); }
      static boolean isnop_zLSH(int rhs) { return (rhs == 0); }
      static boolean isnop_zRSA(int rhs) { return (rhs == 0); }
      static boolean isnop_zRSL(int rhs) { return (rhs == 0); }
      static boolean isnop_zRSR(int rhs) { return ((rhs & 0x1F) == 0); }
      static boolean isnop_zIND(int rhs) { return false; }
      
      static int apply_zMOV(int L, int R) { return R; }
      static int apply_zAND(int L, int R) { return (L & R); }
      static int apply_zIOR(int L, int R) { return (L | R); }
      static int apply_zXOR(int L, int R) { return (L ^ R); }
      static int apply_zADD(int L, int R) { return (L + R); }
      static int apply_zSUB(int L, int R) { return (L - R); }
      static int apply_zLSH(int L, int R) { return ((R < 32) ? (L << R) : (0)); }
      static int apply_zRSA(int L, int R) { return ((R < 32) ? (L >> R) : (L >> 31)); }
      static int apply_zRSL(int L, int R) { return ((R < 32) ? (L >>> R) : (0)); }
      static int apply_zRSR(int L, int R) { return Integer.rotateRight(L, R); }
      
      static String kernel_zMOV() { return "(L & 0) | R /* basically just 'R', but silence compiler on non-use of L */"; }
      static String kernel_zAND() { return "(L & R)"; }
      static String kernel_zIOR() { return "(L | R)"; }
      static String kernel_zXOR() { return "(L ^ R)"; }
      static String kernel_zADD() { return "(L + R)"; }
      static String kernel_zSUB() { return "(L - R)"; }
      static String kernel_zLSH() { return "((R < 32) ? (L << R) : (0))"; }
      static String kernel_zRSA() { return "(((MUSE_TYPE_U4)((((MUSE_TYPE_S4)(L)) >> ((R < 32) ? ((MUSE_TYPE_S4)(R)) : ((MUSE_TYPE_S4)(31)))))))"; }
      static String kernel_zRSL() { return "((R < 32) ? (L >> R) : (0))"; }
      static String kernel_zRSR() { return "MUSE_CISC_RSR(L, R)"; }
      
      abstract boolean isReversed();
      abstract boolean isExported();
      
      abstract String[] getAliases();
      
      abstract boolean isnop(int rhs);
      abstract int apply(int L, int R);
      abstract String kernel();
      
      void effect(MachineThread thread, MachineBridge bridge, int versatile)
      {
        if (!isReversed()) {
          bridge.r_put(apply(bridge.r_get(), bridge.s_get(versatile)));
        } else {
          bridge.s_put(versatile, apply(bridge.s_get(versatile), bridge.r_get()));
        }
      }
      
      void kernel(StringBuilder out, int versatile)
      {
        if (!isReversed()) {
          out.append("MUSE_TYPE_U4 L = e->r, R = e->s[versatile]; e->r = "); out.append(kernel()); out.append(";\n");
        } else {
          out.append("MUSE_TYPE_U4 L = e->s[versatile], R = e->r; e->s[versatile] = "); out.append(kernel()); out.append(";\n");
        }
      }
      
      public boolean hasFixedVersatile() { return false; }
      
      public boolean isNopFor(int rhs) { return isnop(rhs); }
      
      public String depict(InstructionFields fields)
      {
        return (toString() + " [" + fields.versatile + "]");
      }
      
      public void effect(MachineThread thread, MachineBridge bridge, InstructionFields fields)
      {
        effect(thread, bridge, fields.versatile);
      }
      
      public void kernel(StringBuilder out, InstructionFields fields)
      {
        kernel(out, fields.versatile);
      }
      
      static BinaryOperatorEnum[] byOrdinal = values();
      
      static String generateRegexpMatchingAnyAliasOfAny()
      {
        return
          (MUSE.generateRegexpMatchingAnyAliasOfAny
           (byOrdinal,
            (new F2<String[], BinaryOperatorEnum>()
             {
               String[] invoke(BinaryOperatorEnum obj)
               {
                 ArrayList<String> aliases = (new ArrayList<String>());
                 
                 aliases.addAll(Arrays.asList(obj.getAliases()));
                 
                 return aliases.toArray((new String[0]));
               }
             })));
      }
      
      static final CheckedMap<String, BinaryOperatorEnum> fromString = (new CheckedMap<String, BinaryOperatorEnum>());
      
      static
      {
        for (BinaryOperatorEnum binop : byOrdinal) {
          for (String alias : binop.getAliases()) {
            fromString.put(alias, binop);
          }
        }
      }
    }
    
    static final CheckedMap<GenericFunctional, Integer> functionalToInstruction = (new CheckedMap<GenericFunctional, Integer>(new ReferenceComparator<GenericFunctional>()));
    static final CheckedMap<Integer, GenericFunctional> instructionToFunctional = (new CheckedMap<Integer, GenericFunctional>());
    
    static
    {
      int instructionBase = 0;
      
      // control sequence opcodes
      {
        if (!((instructionBase & 0x07) == 0)) throw null;
        
        for (ControlSequenceEnum controlSequence : ControlSequenceEnum.byOrdinal) {
          functionalToInstruction.put(controlSequence, instructionBase);
          
          for (int i = 0; i < 8; i++) {
            instructionToFunctional.put(instructionBase++, controlSequence);
          }
        }
      }
      
      // unary operator opcodes
      {
        if (!((instructionBase & 0x07) == 0)) throw null;
        
        for (UnaryOperatorEnum unaryOperator : UnaryOperatorEnum.byOrdinal) {
          functionalToInstruction.put(unaryOperator, instructionBase);
          instructionToFunctional.put(instructionBase++, unaryOperator);
        }
      }
      
      // binary operator opcodes
      {
        if (!((instructionBase & 0x07) == 0)) throw null;
        
        for (BinaryOperatorEnum binaryOperator : BinaryOperatorEnum.byOrdinal) {
          functionalToInstruction.put(binaryOperator, instructionBase);
          
          for (int j = 0; j < 8; j++) {
            instructionToFunctional.put(instructionBase++, binaryOperator);
          }
        }
      }
      
      if (!(instructionBase <= 256)) throw null;
      
      trace("MUSE: instructionBase=" + instructionBase + "/256");
    }
    
    static byte encodeInstruction(GenericFunctional functional, Integer versatile)
    {
      int instruction = functionalToInstruction.get(functional);
      
      if (functional.hasFixedVersatile()) {
        if (versatile != null) throw null;
      } else {
        if (!fitsInBitsUS(versatile, 3)) throw (new RuntimeException("MUSE: compiler: versatile value " + versatile + " exceeds the representable range (unsigned 3-bit field)"));
        
        instruction += versatile;
      }
      
      if (!((0 <= instruction) && (instruction < 256))) throw null;
      
      return ((byte)(instruction));
    }
    
    static InstructionFields decodeInstruction(byte instruction)
    {
      int opcode = ((instruction >> 3) & 0x1F);
      int versatile = (instruction & 0x7);
      
      GenericFunctional functional = instructionToFunctional.get((instruction & 0xFF));
      
      if (functional == null) throw (new RuntimeException("MUSE: interpreter: illegal instruction " + (instruction & 0xFF)));
      
      return (new InstructionFields(functional, opcode, versatile));
    }
    
    static void generateKernelCode(StringBuilder out, int opcode, int versatile)
    {
      if (!((0 <= versatile) && (versatile < 8))) throw null;
      
      final int               instruction = ((opcode << 3) + versatile);
      final GenericFunctional functional  = nonnull(instructionToFunctional.get(instruction));
      final InstructionFields fields      = (new InstructionFields(functional, opcode, versatile));
      
      out.append("/* " + functional.depict(fields) + " */\n");
      
      functional.kernel(out, fields);
    }
    
    static void generateKernelCode(StringBuilder out, int opcode)
    {
      final int instruction = (opcode << 3);
      
      final GenericFunctional functional = nonnull(instructionToFunctional.get(instruction));
      
      if (functional.hasFixedVersatile()) {
        // for fixed versatile, must branch on versatile
        {
          out.append("switch (versatile) {\n");
          
          for (int versatile = 0; versatile < 8; versatile++) {
            InstructionFields fields = (new InstructionFields(instructionToFunctional.get((opcode << 3)), versatile, -1));
            
            out.append("case " + versatile + ":\n");
            out.append("{\n");
            generateKernelCode(out, opcode, versatile);
            out.append("break;\n");
            out.append("}\n");
          }
          
          out.append("}\n");
        }
      } else {
        // variable versatile is simpler
        generateKernelCode(out, opcode, 0);
      }
    }
    
    static void generateKernelCode(StringBuilder out)
    {
      // expects e->{c,s,u,r,h} of MUSE_TYPE_ST
      // e->c of MUSE_TYPE_U1*
      // e->s of MUSE_TYPE_U4*
      // e->u of MUSE_TYPE_U4*
      // s->r of MUSE_TYPE_U4
      // s->h of (MUSE_TYPE_BL (*)(MUSE_TYPE_ST*, MUSE_TYPE_U1*))
      // with helpers
      // MUSE_TYPE_U4 MUSE_SCAN_JUMP(MUSE_TYPE_ST*)
      // MUSE_TYPE_U4 MUSE_SCAN_CALL(MUSE_TYPE_ST*)
      // MUSE_TYPE_U4 MUSE_SCAN_WORD(MUSE_TYPE_ST*)
      // MUSE_TYPE_U4 MUSE_CISC_{RVB,RVY,DTB,DTY,SMS,LOG}(MUSE_TYPE_U4)
      // MUSE_TYPE_U4 MUSE_CISC_{RSR}(MUSE_TYPE_U4, MUSE_TYPE_U4)
      
      out.append("/* ENTER GENERATED CODE -- DO NOT EDIT */\n");
      
      out.append("MUSE_TYPE_BL MUSE_CORE_PROC(MUSE_TYPE_ST* e, MUSE_TYPE_U4 n)\n");
      out.append("{\n");
      {
        out.append("while (n-- > 0) {\n");
        {
          out.append("MUSE_TYPE_U1 instruction = (*((e->c)++));\n");
          out.append("MUSE_TYPE_U1 opcode      = (instruction >> 3);\n");
          out.append("MUSE_TYPE_U1 versatile   = (instruction & 0x7);\n");
          
          out.append("switch (opcode) {\n");
          {
            for (int i = 0; i < 32; i++) {
              out.append("case " + i + ":\n");
              out.append("{\n");
              generateKernelCode(out, i);
              out.append("break;\n");
              out.append("}\n");
            }
          }
          out.append("}\n");
        }
        out.append("}\n");
      }
      out.append("return MUSE_TRUE;\n");
      out.append("}\n");
      
      out.append("/* LEAVE GENERATED CODE -- DO NOT EDIT */\n");
    }
    
    public static String generateKernelCode()
    {
      StringBuilder out = (new StringBuilder());
      
      generateKernelCode(out);
      
      return out.toString();
    }
  }
  
  /***
   * COMPILER/ASSEMBLER
   ***/
  
  public static final class Compiler
  {
    /***
     * COMPILER INPUT
     ***/
    
    static enum SourceTokenClassEnum implements GenericTokenClass
    {
      END_OF_FILE_INCLUDED
        {
          public String getFriendlyName() { return "end-of-file-included"; }
          public String getRegexp()       { return "[[a]&&[z]]()"; }
        },
      
      END_OF_FILE
        {
          public String getFriendlyName() { return "end-of-file"; }
          public String getRegexp()       { return "[[a]&&[z]]()"; }
        },
      
      KEYWORD_PATCH
        {
          public String getFriendlyName() { return "keyword-patch"; }
          public String getRegexp()       { return "\\.patch()"; }
        },
      
      KEYWORD_ENTER
        {
          public String getFriendlyName() { return "keyword-enter"; }
          public String getRegexp()       { return "\\.enter()"; }
        },
        
      KEYWORD_LEAVE
        {
          public String getFriendlyName() { return "keyword-leave"; }
          public String getRegexp()       { return "\\.leave()"; }
        },
      
      KEYWORD_CONST
        {
          public String getFriendlyName() { return "keyword-const"; }
          public String getRegexp()       { return "\\.const()"; }
        },
      
      KEYWORD_SLICE
        {
          public String getFriendlyName() { return "keyword-slice"; }
          public String getRegexp()       { return "\\.slice()"; }
        },
      
      KEYWORD_DEFER
        {
          public String getFriendlyName() { return "keyword-defer"; }
          public String getRegexp()       { return "\\.defer()"; }
        },
      
      KEYWORD_FRAME
        {
          public String getFriendlyName() { return "keyword-frame"; }
          public String getRegexp()       { return "\\.frame()"; }
        },
      
      KEYWORD_EVALUATE
        {
          public String getFriendlyName() { return "keyword-evaluate"; }
          public String getRegexp()       { return "\\.(|eval)"; }
        },
      
      KEYWORD_TRAP
        {
          public String getFriendlyName() { return "keyword-trap"; }
          public String getRegexp()       { return "\\.trap()"; }
        },
      
      KEYWORD_CONTINUE_OR_BREAK
        {
          public String getFriendlyName() { return "keyword-continue-or-break"; }
          public String getRegexp()       { return "\\.(continue|break)"; }
        },
      
      KEYWORD_RETURN
        {
          public String getFriendlyName() { return "keyword-return"; }
          public String getRegexp()       { return "\\.return()"; }
        },
      
      KEYWORD_INVOKE
        {
          public String getFriendlyName() { return "keyword-invoke"; }
          public String getRegexp()       { return "\\.invoke()"; }
        },
      
      KEYWORD_IF
        {
          public String getFriendlyName() { return "keyword-if"; }
          public String getRegexp()       { return "\\.(?:if|ifso|soif)()"; }
        },
      
      KEYWORD_ELIF
        {
          public String getFriendlyName() { return "keyword-elif"; }
          public String getRegexp()       { return "\\.elif()"; }
        },
      
      KEYWORD_ELSE
        {
          public String getFriendlyName() { return "keyword-else"; }
          public String getRegexp()       { return "\\.else()"; }
        },
      
      KEYWORD_SCOPE
        {
          public String getFriendlyName() { return "keyword-scope"; }
          public String getRegexp()       { return "\\.scope()"; }
        },
      
      KEYWORD_WHILE
        {
          public String getFriendlyName() { return "keyword-while"; }
          public String getRegexp()       { return "\\.while()"; }
        },
      
      KEYWORD_FOR
        {
          public String getFriendlyName() { return "keyword-for"; }
          public String getRegexp()       { return "\\.for()"; }
        },
      
      LABEL
        {
          public String getFriendlyName() { return "label"; }
          public String getRegexp()       { return "\\:([_A-Za-z][_0-9A-Za-z]*)"; }
        },
      
      VARIABLE_ALLOCATION
        {
          public String getFriendlyName() { return "variable-allocation"; }
          public String getRegexp()       { return "\\%(([_A-Za-z][_0-9A-Za-z]*)?)[!]+"; }
        },
      
      VARIABLE_REFERENCE
        {
          public String getFriendlyName() { return "variable-reference"; }
          public String getRegexp()       { return "\\%([_A-Za-z][_0-9A-Za-z]*)"; }
        },
      
      STATEMENT_TERMINATOR
        {
          public String getFriendlyName() { return "statement-terminator"; }
          public String getRegexp()       { return "[;]+()"; }
        },
      
      BLOCK_ENTER
        {
          public String getFriendlyName() { return "block-enter"; }
          public String getRegexp()       { return "\\{()"; }
        },
      
      BLOCK_LEAVE
        {
          public String getFriendlyName() { return "block-leave"; }
          public String getRegexp()       { return "\\}()"; }
        },
      
      BRANCH_CONDITION
        {
          public String getFriendlyName() { return "branch-condition"; }
          public String getRegexp()       { return Model.BranchConditionEnum.generateRegexpMatchingAnyAliasOfAny(); }
        },
      
      ARBITRARY_INITIALIZER
        {
          public String getFriendlyName() { return "arbitrary-initializer"; }
          public String getRegexp()       { return "z()"; }
        },
      
      DUMMY_OPERATOR
        {
          public String getFriendlyName() { return "dummy-operator"; }
          public String getRegexp()       { return "r()"; }
        },
      
      UNARY_OPERATOR
        {
          public String getFriendlyName() { return "unary-operator"; }
          public String getRegexp()       { return Model.UnaryOperatorEnum.generateRegexpMatchingAnyAliasOfAny(); }
        },
      
      BINARY_OPERATOR
        {
          public String getFriendlyName() { return "binary-operator"; }
          public String getRegexp()       { return Model.BinaryOperatorEnum.generateRegexpMatchingAnyAliasOfAny(); }
        },
      
      PARENTHESIS_ENTER
        {
          public String getFriendlyName() { return "parenthesis-enter"; }
          public String getRegexp()       { return (Model.BinaryOperatorEnum.generateRegexpMatchingAnyAliasOfAny() + "\\("); }
        },
      
      PARENTHESIS_LEAVE
        {
          public String getFriendlyName() { return "parenthesis-leave"; }
          public String getRegexp()       { return "\\)()"; }
        },
      
      INCREMENT_VERSATILE
        {
          public String getFriendlyName() { return "increment-versatile"; }
          public String getRegexp()       { return "\\+([0-9]+)"; }
        },
      
      INCREMENT_VERSATILE_INDIRECT
        {
          public String getFriendlyName() { return "increment-versatile"; }
          public String getRegexp()       { return "\\+\\:([_A-Za-z][_0-9A-Za-z]*)"; }
        },
      
      INTEGER_LITERAL
        {
          public String getFriendlyName() { return "integer-literal"; }
          public String getRegexp()       { return "((\\-|\\+)?(([0-9]+)|(0x[0-9A-Fa-f]+)))"; }
        },
      
      HIDDEN_LITERAL
        {
          public String getFriendlyName() { return "hidden-literal"; }
          public String getRegexp()       { return "\\@([0-9]+)"; }
        },
      
      FILENAME_LITERAL
        {
          public String getFriendlyName() { return "filename-literal"; }
          public String getRegexp()       { return "file\\:([-,+./=_0-9A-Za-z]*)"; }
        };
      
      static SourceTokenClassEnum[] byOrdinal = values();
    }
    
    /***
     * COMPILER OUTPUT
     ***/
    
    static abstract class ProgramElement
    {
      int address;
      
      abstract int determine_extent();
      
      final int calculate(int address)
      {
        this.address = address;
        
        return (address + determine_extent());
      }
      
      abstract void elaborateAscii(StringBuilder out);
      abstract void elaborateBytes(ByteArrayOutputStream out);
    }
    
    static class ProgramBreak extends ProgramElement
    {
      private static final boolean debug_break = false;
      
      final String wherefore;
      
      ProgramBreak(String wherefore)
      {
        this.wherefore = wherefore;
      }
      
      int determine_extent()
      {
        return 0;
      }
      
      void elaborateAscii(StringBuilder out)
      {
        if (debug_break) {
          out.append(" :::" + wherefore + "\n");
        } else {
          out.append("\n");
        }
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        // nothing to do
      }
    }
    
    static class ProgramLabel extends ProgramElement
    {
      final String label;
      
      ProgramLabel(String label)
      {
        this.label = label;
      }
      
      int determine_extent()
      {
        return 0;
      }
      
      String elaborateString()
      {
        return (":" + label + " @" + hexify(address) + "\n");
      }
      
      void elaborateAscii(StringBuilder out)
      {
        out.append(elaborateString());
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        System.err.print("MUSE: elaboration: reached " + elaborateString());
        System.err.flush();
      }
    }
    
    static class ProgramInstruction extends ProgramElement
    {
      final byte instruction;
      
      ProgramInstruction(Model.GenericFunctional functional, Integer versatile)
      {
        this.instruction = Model.encodeInstruction(functional, versatile);
      }
      
      int determine_extent()
      {
        return 1;
      }
      
      void elaborateAscii(StringBuilder out)
      {
        Model.InstructionFields fields = Model.decodeInstruction(instruction);
        
        out.append(fields.functional.depict(fields));
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        out.write(instruction);
      }
    }
    
    static class ProgramAlignmentImmediate extends ProgramElement
    {
      final int mask;
      
      ProgramAlignmentImmediate(int boundary)
      {
        this.mask = (boundary - 1);
      }
      
      int determine_extent()
      {
        return ((-address) & mask);
      }
      
      void elaborateAscii(StringBuilder out)
      {
        out.append(".align " + determine_extent());
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        int n = determine_extent();
        
        while (n-- > 0) {
          out.write(0x00);
        }
      }
    }
    
    static class ProgramDataImmediate extends ProgramElement
    {
      final byte[] immediate;
      
      ProgramDataImmediate(byte[] immediate)
      {
        this.immediate = immediate;
      }
      
      ProgramDataImmediate(int immediate, boolean signed, int length)
      {
        if (!(signed ? fitsInBytesSE(immediate, length) : fitsInBytesUS(immediate, length))) throw (new RuntimeException("MUSE: compiler: immediate value " + immediate + " exceeds the representable range (" + (signed ? "signed" : "unsigned") + " " + length + "-byte field)"));
        this.immediate = writeBE(immediate, length);
      }
      
      int determine_extent()
      {
        return immediate.length;
      }
      
      void elaborateAscii(StringBuilder out)
      {
        out.append(" +imm");
        
        for (int i = 0; i < immediate.length; i++) {
          out.append(" " + (immediate[i] & 0xFF));
        }
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        try {
          out.write(immediate);
        } catch (IOException e) {
          throw (new RuntimeException(e));
        }
      }
    }
    
    static class ProgramLabelAddressImmediate extends ProgramElement
    {
      final ProgramLabel target;
      final boolean      signed;
      final int          length;
      
      ProgramLabelAddressImmediate(ProgramLabel target, boolean signed, int length)
      {
        this.target = target;
        this.signed = signed;
        this.length = length;
      }
      
      int determine_extent()
      {
        return length;
      }
      
      void elaborateAscii(StringBuilder out)
      {
        out.append(" +lea :" + target.label + " = " + target.address);
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        if (!(signed ? fitsInBytesSE(target.address, length) : fitsInBytesUS(target.address, length))) throw (new RuntimeException("MUSE: compiler: address value " + target.address + " exceeds the representable range (" + (signed ? "signed" : "unsigned") + " " + length + "-byte field)"));
        
        try {
          out.write(writeBE(target.address, length));
        } catch (IOException e) {
          throw (new RuntimeException(e));
        }
      }
    }
    
    static class ProgramLabelOffsetImmediate extends ProgramElement
    {
      final ProgramLabel target;
      final int length;
      
      ProgramLabelOffsetImmediate(ProgramLabel target, int length)
      {
        this.target = target;
        this.length = length;
      }
      
      int determine_extent()
      {
        return length;
      }
      
      int signedOffset()
      {
        return (target.address - (address + length));
      }
      
      void elaborateAscii(StringBuilder out)
      {
        out.append(" +off :" + target.label + " = " + signedOffset());
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        int offset = signedOffset();
        
        if (!fitsInBytesSE(offset, length)) throw (new RuntimeException("MUSE: compiler: program offset immediate (" + offset + ") exceeds representable range (signed " + length + "-byte field)"));
        
        try {
          out.write(writeBE(offset, length));
        } catch (IOException e) {
          throw (new RuntimeException(e));
        }
      }
    }
    
    static class ProgramSequence extends ProgramElement
    {
      final ArrayList<ProgramElement> elements = (new ArrayList<ProgramElement>());
      
      void append(ProgramElement element)
      {
        elements.add(element);
      }
      
      int determine_extent()
      {
        int address_forward = address;
        
        for (ProgramElement element : elements) {
          address_forward = element.calculate(address_forward);
        }
        
        return (address_forward - address);
      }
      
      void elaborateAscii(StringBuilder out)
      {
        for (ProgramElement element : elements) {
          element.elaborateAscii(out);
        }
      }
      
      void elaborateBytes(ByteArrayOutputStream out)
      {
        for (ProgramElement element : elements) {
          element.elaborateBytes(out);
        }
      }
    }
    
    public static class ProgramContainer
    {
      final Model.Trace.SymbolTable table;
      final AsciiTreeMap<Integer> sizes;
      final ProgramSequence sequence;
      
      public ProgramContainer(Model.Trace.SymbolTable table, AsciiTreeMap<Integer> sizes, ProgramSequence sequence)
      {
        this.table = table;
        this.sizes = sizes;
        this.sequence = sequence;
      }
      
      public Model.Trace.SymbolTable getSymbolTable()
      {
        return table;
      }
      
      public AsciiTreeMap<Integer> getFunctionSizes()
      {
        return sizes;
      }
      
      public ProgramSequence getProgramSequence()
      {
        return sequence;
      }
    }
    
    /***
     * COMPILER STATE
     ***/
    
    static class State
    {
      final String path;
      
      final Model.MachineParameters parameters;
      final TokenStream<SourceTokenClassEnum> tokenStream;
      
      final ProgramSequence programSequence;
      final ProgramLabel startLabel;
      
      final CheckedMap<String, String>       mapS = (new CheckedMap<String, String>      ());
      final CheckedMap<String, Integer>      mapI = (new CheckedMap<String, Integer>     ());
      final CheckedMap<String, ProgramLabel> mapL = (new CheckedMap<String, ProgramLabel>());
      
      State(String path, Model.MachineParameters parameters, TokenStream<SourceTokenClassEnum> tokenStream)
      {
        this.path = path;
        
        this.parameters = parameters;
        this.tokenStream = tokenStream;
        
        programSequence = (new ProgramSequence());
        startLabel = appendProgramLabel();
      }
      
      private int makeNameCounter = 0;
      
      private String makeName()
      {
        return (path + "." + (makeNameCounter++));
      }
      
      /***
       * when state is forked:
       * - the child state gets a unique path, or label namespace
       * - the child state and the parent state read from the same input token stream (aliased)
       * - the child state's instructions are embedded in the parent's instruction stream as if appended instead of the fork call (routed)
       * - the child state gets a copy of the parent's maps (cloned)
       ***/
      State fork()
      {
        State peer = (new State(makeName(), parameters, tokenStream));
        
        peer.mapS.putAll(mapS);
        peer.mapI.putAll(mapI);
        peer.mapL.putAll(mapL);
        
        programSequence.append(peer.programSequence);
        
        return peer;
      }
      
      Token<SourceTokenClassEnum> obtainToken()
      {
        return tokenStream.obtainToken();
      }
      
      void supplyToken(TokenHandler<SourceTokenClassEnum> handler)
      {
        tokenStream.supplyToken(handler);
      }
      
      void repeatSupplyToken(TokenHandler<SourceTokenClassEnum> handler)
      {
        tokenStream.repeatSupplyToken(handler);
      }
      
      void obtainTokenBlockEnter()
      {
        obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
      }
      
      String obtainTokenLabel()
      {
        return obtainToken().expect(SourceTokenClassEnum.LABEL).text;
      }
      
      int obtainTokenIntegerLiteral()
      {
        return parseIntegerLiteral(obtainToken().expect(SourceTokenClassEnum.INTEGER_LITERAL).text);
      }
      
      int obtainTokenHiddenLiteral()
      {
        return parseIntegerLiteral(obtainToken().expect(SourceTokenClassEnum.HIDDEN_LITERAL).text);
      }
      
      ProgramLabel getStartLabel()
      {
        return startLabel;
      }
      
      void append(ProgramElement element)
      {
        programSequence.append(element);
      }
      
      void appendProgramBreak(String wherefore)
      {
        append((new ProgramBreak(wherefore)));
      }
      
      ProgramLabel appendProgramLabel(String name)
      {
        ProgramLabel label = (new ProgramLabel(name));
        append(label);
        return label;
      }
      
      ProgramLabel appendProgramLabel()
      {
        return appendProgramLabel(makeName());
      }
      
      void appendProgramInstruction(Model.GenericFunctional functional, Integer versatile)
      {
        append((new ProgramInstruction(functional, versatile)));
      }
      
      void appendProgramAlignmentImmediate(int boundary)
      {
        append((new ProgramAlignmentImmediate(boundary)));
      }
      
      void appendProgramDataImmediate(byte[] immediate)
      {
        append((new ProgramDataImmediate(immediate)));
      }
      
      void appendProgramDataImmediate(int immediate, boolean signed, int length)
      {
        append((new ProgramDataImmediate(immediate, signed, length)));
      }
      
      void appendProgramLabelAddressImmediate(ProgramLabel label, boolean signed, int length)
      {
        append((new ProgramLabelAddressImmediate(label, signed, length)));
      }
      
      void appendProgramLabelOffsetImmediate(ProgramLabel label, int length)
      {
        append((new ProgramLabelOffsetImmediate(label, length)));
      }
      
      void appendConditionalJump(Model.BranchConditionEnum condition, ProgramLabel target)
      {
        appendProgramInstruction(Model.ControlSequenceEnum.jUMP, condition.ordinal());
        appendProgramLabelOffsetImmediate(target, parameters.jump_target_bytes);
        appendProgramBreak("appendConditionalJump.1");
      }
      
      void appendConditionalJump(Model.BranchConditionEnum condition, State target)
      {
        appendConditionalJump(condition, target.getStartLabel());
      }
      
      void appendUnconditionalJump(ProgramLabel target)
      {
        appendConditionalJump(Model.BranchConditionEnum.AW, target);
      }
      
      void appendUnconditionalJump(State target)
      {
        appendUnconditionalJump(target.getStartLabel());
      }
      
      void appendSelfLoop()
      {
        appendUnconditionalJump(appendProgramLabel());
      }
      
      boolean hasS(String key) { return mapS.has(key); }
      boolean hasI(String key) { return mapI.has(key); }
      boolean hasL(String key) { return mapL.has(key); }
      
      State putS(String key, String val)       { mapS.put(key, val); return this; }
      State putI(String key, Integer val)      { mapI.put(key, val); return this; }
      State putL(String key, ProgramLabel val) { mapL.put(key, val); return this; }
      
      String       getS(String key) { return mapS.get(key); }
      Integer      getI(String key) { return mapI.get(key); }
      ProgramLabel getL(String key) { return mapL.get(key); }
      
      /** returns val++ */
      Integer incI(String key)
      {
        Integer val = getI(key);
        putI(key, (val + 1));
        return val;
      }
      
      /** returns --val */
      /*
      Integer decI(String key, Token anchor)
      {
        Integer val = (getI(key) - 1);
        putI(key, val);
        return val;
      }
      */
    }
    
    /***
     * COMPILER CODE
     ***/
    
    public static Integer parseIntegerLiteral(String literal)
    {
      String literal_shadow = literal;
      
      boolean negate = (literal.charAt(0) == '-');
      
      if (((literal.charAt(0) == '-') || (literal.charAt(0) == '+'))) literal = literal.substring(1);
      
      int radix;
      
      if (literal.startsWith("0x")) {
        radix = 16;
        literal = literal.substring(2);
      } else {
        radix = 10;
      }
      
      long value = Long.parseLong(literal, radix);
      
      if (!(value < (1L << 32))) throw null;
      
      if (negate) {
        if (value == Integer.MIN_VALUE) throw null;
        value = (-(value));
      }
      
      trace("MUSE: compiler: integer literal parsed as decimal " + value);
      
      return ((int)(value));
    }
    
    static String joinExpectedClasses(SourceTokenClassEnum... list)
    {
      StringBuilder out = (new StringBuilder());
      
      String delimiter = ", ";
      
      for (SourceTokenClassEnum item : list) {
        out.append(item.getFriendlyName());
        out.append(delimiter);
      }
      
      if (out.length() > 0) {
        out.setLength(out.length() - delimiter.length());
      }
      
      return out.toString();
    }
    
    /***
     * scope contents:
     * 
     * const-X - integer literal definition
     * defer-X - label for calling function X
     * frame-X - indicator that function X has been defined
     * 
     * stack-ground - offset of last argument on stack
     * stack-offset - offset of next variable to be allocated from stack
     * stack-offset-variable-X - offset of variable X on stack
     * target-continue-X - label corresponding to "continue" of scope X
     * target-break-X - label corresponding to "break (out)" of scope X
     * 
     * all compile...() functions modify the passed scope on variable
     * allocation. therefore, "insulation" is used -- compile...()
     * functions are typically invoked on a fork() or a
     * scope. generally, insulation should be added unless there is a
     * specific reason not to.
     ***/
    
    static void compileVariableSelectionLiteral(final State state, final Model.GenericFunctional functional, final int immediate)
    {
      if (functional.isNopFor(immediate)) {
        /* no code necessary */
        return;
      }
      
      if (functional instanceof Model.BinaryOperatorEnum) {
        if (((Model.BinaryOperatorEnum)(functional)).isExported()) {
          throw (new RuntimeException("MUSE: compiler: expected non-exported operator for binary operator constant expression"));
        }
      }
      
      if ((functional == Model.BinaryOperatorEnum.rADD) && ((1 <= immediate) && (immediate <= 8))) {
        state.appendProgramInstruction(Model.ControlSequenceEnum.cADD, (immediate - 1));
      } else if ((functional == Model.BinaryOperatorEnum.rSUB) && (immediate == 1)) {
        state.appendProgramInstruction(Model.UnaryOperatorEnum.rURYDEC, null);
      } else {
        int offset_temp = state.getI(("stack-offset-variable-TEMP"));
        
        state.appendProgramInstruction(Model.UnaryOperatorEnum.rURYSTO, null);
        state.appendProgramBreak("compileVariableSelectionLiteral.1");
        
        {
          Model.ControlSequenceEnum sequence;
          
          boolean signed;
          int trailing;
          
          /**/ if (((Byte.MIN_VALUE <= immediate) && (immediate <= Byte.MAX_VALUE))) { sequence = Model.ControlSequenceEnum.cSEB; signed = true;  trailing = 1; }
          else                                                                       { sequence = Model.ControlSequenceEnum.cWRD; signed = false; trailing = 4; }
          
          state.appendProgramInstruction(sequence, offset_temp);
          state.appendProgramDataImmediate(immediate, signed, trailing);
          state.appendProgramBreak("compileVariableSelectionLiteral.2");
        }
        
        state.appendProgramInstruction(Model.UnaryOperatorEnum.rURYRCL, null);
        state.appendProgramBreak("compileVariableSelectionLiteral.3");
        
        state.appendProgramInstruction(functional, offset_temp);
      }
    }
    
    static void compileVariableSelection(final State state, final Model.GenericFunctional functional, final boolean allow_immediate)
    {
      state.supplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            String getParserState()
            {
              return "variable-selection";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.VARIABLE_ALLOCATION, SourceTokenClassEnum.VARIABLE_REFERENCE, SourceTokenClassEnum.INTEGER_LITERAL, SourceTokenClassEnum.LABEL);
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case VARIABLE_ALLOCATION:
                {
                  int offset = state.incI("stack-offset");
                  
                  if (!token.text.isEmpty()) {
                    state.putI(("stack-offset-variable-" + token.text), offset);
                  }
                  
                  state.appendProgramInstruction(functional, offset);
                  
                  return true;
                }
                
              case VARIABLE_REFERENCE:
                {
                  int offset = state.getI(("stack-offset-variable-" + token.text));
                  
                  state.appendProgramInstruction(functional, offset);
                  
                  return true;
                }
                
              case INTEGER_LITERAL:
                {
                  if (!allow_immediate) throw (new RuntimeException("MUSE: compiler: expected variable reference rather than immediate"));
                  
                  compileVariableSelectionLiteral(state, functional, parseIntegerLiteral(token.text));
                  
                  return true;
                }
                
              case LABEL:
                {
                  if (!allow_immediate) throw (new RuntimeException("MUSE: compiler: expected variable reference rather than immediate"));
                  
                  compileVariableSelectionLiteral(state, functional, state.getI(("const-" + token.text)));
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
    }
    
    static void compileAddVersatile(final State state, int versatile)
    {
      if (versatile != 0) {
        state.appendProgramInstruction(Model.ControlSequenceEnum.cADD, (versatile - 1));
        state.appendProgramBreak("compileAddVersatile.1");
      } else {
        /* nothing to do; adding zero does not require code generation */
      }
    }
    
    static void compileLoadImmediateCommon(final State state)
    {
      Token<SourceTokenClassEnum> token = state.obtainToken().expect(SourceTokenClassEnum.BINARY_OPERATOR);
      
      Model.BinaryOperatorEnum operator = Model.BinaryOperatorEnum.fromString.get(token.text);
      
      if (!(operator == Model.BinaryOperatorEnum.vMOV)) throw (new RuntimeException("MUSE: compiler: expected immediate assignment of immediate"));
    }
    
    static void compileLoadImmediate(final State state, int immediate)
    {
      compileLoadImmediateCommon(state);
      
      Model.ControlSequenceEnum sequence;
      
      boolean signed;
      int trailing;
      
      /**/ if (((Byte.MIN_VALUE <= immediate) && (immediate <= Byte.MAX_VALUE))) { sequence = Model.ControlSequenceEnum.cSEB; signed = true;  trailing = 1; }
      else                                                                       { sequence = Model.ControlSequenceEnum.cWRD; signed = false; trailing = 4; }
      
      compileVariableSelection(state, sequence, false);
      state.appendProgramDataImmediate(immediate, signed, trailing);
      state.appendProgramBreak("compileLoadImmediate.1");
    }
    
    static void compileLoadImmediateLabelAddress(final State state, final ProgramLabel label)
    {
      compileLoadImmediateCommon(state);
      
      compileVariableSelection(state, Model.ControlSequenceEnum.cWRD, false);
      state.appendProgramLabelAddressImmediate(label, false, 4);
      state.appendProgramBreak("compileLoadImmediateLabelAddress.1");
    }
    
    static void compileIntegerExpression(final State state, final int depth)
    {
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            boolean initialized = false;
            
            String getParserState()
            {
              return "integer-expression";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.STATEMENT_TERMINATOR, SourceTokenClassEnum.ARBITRARY_INITIALIZER, SourceTokenClassEnum.DUMMY_OPERATOR, SourceTokenClassEnum.UNARY_OPERATOR, SourceTokenClassEnum.BINARY_OPERATOR, SourceTokenClassEnum.PARENTHESIS_ENTER, SourceTokenClassEnum.PARENTHESIS_LEAVE, SourceTokenClassEnum.INCREMENT_VERSATILE, SourceTokenClassEnum.INCREMENT_VERSATILE_INDIRECT, SourceTokenClassEnum.INTEGER_LITERAL, SourceTokenClassEnum.LABEL);
            }
            
            void checkHeadless(Token<SourceTokenClassEnum> token)
            {
              if (!initialized) throw (new RuntimeException("MUSE: compiler: headless pipe"));
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case STATEMENT_TERMINATOR:
                {
                  if (depth == 0) {
                    finished = true;
                    return true;
                  } else {
                    throw (new RuntimeException("MUSE: compiler: mismatched parenthesis"));
                  }
                }
                
              case ARBITRARY_INITIALIZER:
                {
                  token = state.obtainToken().expect(SourceTokenClassEnum.BINARY_OPERATOR);
                  
                  Model.BinaryOperatorEnum operator = Model.BinaryOperatorEnum.fromString.get(token.text);
                  
                  if (!(operator == Model.BinaryOperatorEnum.vMOV)) throw (new RuntimeException("MUSE: compiler: expected immediate assignment of arbitrary"));
                  
                  token = state.obtainToken().expect(SourceTokenClassEnum.VARIABLE_ALLOCATION);
                  
                  if (token.text.isEmpty()) throw (new RuntimeException("MUSE: compiler: expected named (non-anonymous) variable for assignment of arbitrary"));
                  
                  int offset = state.incI("stack-offset");
                  
                  state.putI(("stack-offset-variable-" + token.text), offset);
                  
                  initialized = false;
                  
                  return true;
                }
                
              case DUMMY_OPERATOR:
                {
                  initialized = true;
                  
                  return true;
                }
                
              case UNARY_OPERATOR:
                {
                  Model.UnaryOperatorEnum operator = Model.UnaryOperatorEnum.fromString.get(token.text);
                  
                  if (operator == null) throw null;
                  
                  switch (operator) {
                  case rURYCLR:
                  case rURYUNI:
                  case rURYSET:
                  case rURYRCL:
                    {
                      initialized = true;
                      break;
                    }
                    
                  default:
                    {
                      checkHeadless(token);
                      break;
                    }
                  }
                  
                  state.appendProgramInstruction(operator, null);
                  
                  state.appendProgramBreak("compileIntegerExpression.1");
                  
                  return true;
                }
                
              case BINARY_OPERATOR:
                {
                  Model.BinaryOperatorEnum operator = Model.BinaryOperatorEnum.fromString.get(token.text);
                  
                  if (operator == Model.BinaryOperatorEnum.rMOV) {
                    initialized = true;
                  } else {
                    checkHeadless(token);
                  }
                  
                  compileVariableSelection(state, operator, true);
                  
                  state.appendProgramBreak("compileIntegerExpression.2");
                  
                  return true;
                }
                
              case PARENTHESIS_ENTER:
                {
                  int offset_temp = state.getI(("stack-offset-variable-TEMP"));
                  
                  Model.BinaryOperatorEnum operator = Model.BinaryOperatorEnum.fromString.get(token.text);
                  
                  if (operator.isExported()) throw (new RuntimeException("MUSE: compiler: expected non-exported operator for parenthesized expression (got " + operator + ")"));
                  
                  state.appendProgramInstruction(Model.UnaryOperatorEnum.rURYSTO, null);
                  state.appendProgramBreak("compileIntegerExpression.3");
                  
                  compileIntegerExpression(state, (depth + 1));
                  
                  state.appendProgramInstruction(Model.BinaryOperatorEnum.vMOV, offset_temp);
                  state.appendProgramBreak("compileIntegerExpression.4");
                  
                  state.appendProgramInstruction(Model.UnaryOperatorEnum.rURYRCL, null);
                  state.appendProgramBreak("compileIntegerExpression.5");
                  
                  state.appendProgramInstruction(operator, offset_temp);
                  state.appendProgramBreak("compileIntegerExpression.6");
                  
                  return true;
                }
                
              case PARENTHESIS_LEAVE:
                {
                  if (depth == 0) {
                    throw (new RuntimeException("MUSE: compiler: mismatched parenthesis"));
                  } else {
                    finished = true;
                    return true;
                  }
                }
                
              case INCREMENT_VERSATILE:
                {
                  compileAddVersatile(state, parseIntegerLiteral(token.text));
                  
                  return true;
                }
                
              case INCREMENT_VERSATILE_INDIRECT:
                {
                  compileAddVersatile(state, state.getI(("const-" + token.text)));
                  
                  return true;
                }
                
              case INTEGER_LITERAL:
                {
                  compileLoadImmediate(state, parseIntegerLiteral(token.text));
                  
                  initialized = true;
                  
                  return true;
                }
                
              case LABEL:
                {
                  if (token.text.equals("HERE")) {
                    compileLoadImmediate(state, (token.line + 1));
                  } else {
                    boolean isI = state.hasI(("const-" + token.text));
                    boolean isL = state.hasL(("slice-" + token.text));
                    
                    /****/ if (isI && !isL) {
                      compileLoadImmediate(state, state.getI(("const-" + token.text)));
                    } else if (isL && !isI) {
                      compileLoadImmediateLabelAddress(state, state.getL(("slice-" + token.text)));
                    } else {
                      throw null;
                    }
                  }
                  
                  initialized = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
    }
    
    static void compileTrapStatement(final State state)
    {
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            ArrayList<Integer> immediates = (new ArrayList<Integer>());
            
            String getParserState()
            {
              return "trap-statement";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.LABEL, SourceTokenClassEnum.INTEGER_LITERAL, SourceTokenClassEnum.STATEMENT_TERMINATOR);
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case LABEL:
                {
                  immediates.add(state.getI(("const-" + token.text)));
                  
                  return true;
                }
                
              case INTEGER_LITERAL:
                {
                  immediates.add(parseIntegerLiteral(token.text));
                  
                  return true;
                }
                
              case STATEMENT_TERMINATOR:
                {
                  state.appendProgramInstruction(Model.ControlSequenceEnum.xTRAP, immediates.size());
                  
                  for (Integer immediate : immediates) {
                    state.appendProgramDataImmediate(immediate, false, 1);
                  }
                  
                  state.appendProgramBreak("compileTrapStatement.1");
                  
                  finished = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
    }
    
    static void compileIfElif(final State state, final State state_beyond)
    {
      Model.BranchConditionEnum condition = nonnull(Model.BranchConditionEnum.valueOf(state.obtainToken().expect(SourceTokenClassEnum.BRANCH_CONDITION).text));
      
      State state_test = state.fork();
      State state_then = state.fork();
      State state_skip = state.fork();
      
      // test
      compileIntegerExpression(state_test, 0);
      state_test.appendConditionalJump(condition.invert(), state_skip);
      
      // then
      state.obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
      compileBlock(state_then);
      state_then.appendUnconditionalJump(state_beyond);
    }
    
    static void compileBlock(final State state)
    {
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            String getParserState()
            {
              return "block";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.KEYWORD_EVALUATE, SourceTokenClassEnum.KEYWORD_TRAP, SourceTokenClassEnum.KEYWORD_CONTINUE_OR_BREAK, SourceTokenClassEnum.KEYWORD_RETURN, SourceTokenClassEnum.KEYWORD_INVOKE, SourceTokenClassEnum.KEYWORD_IF, SourceTokenClassEnum.KEYWORD_SCOPE, SourceTokenClassEnum.KEYWORD_WHILE, SourceTokenClassEnum.KEYWORD_FOR, SourceTokenClassEnum.BLOCK_LEAVE);
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case KEYWORD_EVALUATE:
                {
                  compileIntegerExpression(state.fork(), 0);
                  
                  return true;
                }
                
              case KEYWORD_TRAP:
                {
                  compileTrapStatement(state.fork());
                  
                  return true;
                }
                
              case KEYWORD_CONTINUE_OR_BREAK:
                {
                  state.appendUnconditionalJump(state.getL(("target-" + token.text + "-" + state.obtainTokenLabel())));
                  
                  return true;
                }
                
              case KEYWORD_RETURN:
                {
                  int stack_decrement = state.obtainTokenHiddenLiteral();
                  
                  state.appendProgramInstruction(Model.ControlSequenceEnum.iRET, stack_decrement);
                  
                  state.appendProgramBreak("compileBlock.1");
                  
                  return true;
                }
                
              case KEYWORD_INVOKE:
                {
                  ProgramLabel target = state.getL(("defer-" + state.obtainTokenLabel()));
                  
                  int hidden = state.obtainTokenHiddenLiteral();
                  
                  int stack_increment = (state.getI("stack-offset") - hidden);
                  
                  compileIntegerExpression(state.fork(), 0);
                  
                  state.appendProgramInstruction(Model.ControlSequenceEnum.iFUN, stack_increment);
                  state.appendProgramLabelOffsetImmediate(target, state.parameters.ifun_target_bytes);
                  
                  state.appendProgramBreak("compileBlock.2");
                  
                  return true;
                }
                
              case KEYWORD_IF:
                {
                  State state_before = state.fork();
                  State state_beyond = state.fork();
                  
                  compileIfElif(state_before, state_beyond);
                  
                  // elif/else ...
                  {
                    Token<SourceTokenClassEnum> ifse;
                    
                    while ((ifse = state.obtainToken()).type == SourceTokenClassEnum.KEYWORD_ELIF) {
                      compileIfElif(state_before, state_beyond);
                    }
                    
                    ifse.expect(SourceTokenClassEnum.KEYWORD_ELSE);
                  }
                  
                  // ... else
                  state.obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
                  compileBlock(state_before);
                  
                  return true;
                }
                
              case KEYWORD_SCOPE:
                {
                  String label = state.obtainToken().expect(SourceTokenClassEnum.LABEL).text;
                  
                  State state_body = state.fork();
                  State state_done = state.fork();
                  
                  compileIntegerExpression(state_body, 0);
                  
                  // body
                  state_body.obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
                  state_body.putL(("target-continue-" + label), state_body.getStartLabel());
                  state_body.putL(("target-break-" + label), state_done.getStartLabel());
                  compileBlock(state_body);
                  
                  return true;
                }
                
              case KEYWORD_WHILE:
                {
                  String label = state.obtainToken().expect(SourceTokenClassEnum.LABEL).text;
                  
                  State state_head = state.fork();
                  State state_body = state.fork();
                  State state_test = state.fork();
                  State state_done = state.fork();
                  
                  // head
                  state_head.appendUnconditionalJump(state_test);
                  
                  // test
                  Model.BranchConditionEnum condition = nonnull(Model.BranchConditionEnum.valueOf(state.obtainToken().expect(SourceTokenClassEnum.BRANCH_CONDITION).text));
                  compileIntegerExpression(state_test, 0);
                  state_test.appendConditionalJump(condition, state_body);
                  
                  // body
                  state_body.obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
                  state_body.putL(("target-continue-" + label), state_test.getStartLabel());
                  state_body.putL(("target-break-" + label), state_done.getStartLabel());
                  compileBlock(state_body);
                  
                  return true;
                }
                
              case KEYWORD_FOR:
                {
                  String label = state.obtainToken().expect(SourceTokenClassEnum.LABEL).text;
                  
                  State state_head = state.fork();
                  State state_done = state.fork();
                  
                  // head
                  compileIntegerExpression(state_head, 0);
                  
                  State state_jump = state_head.fork();
                  State state_body = state_head.fork();
                  State state_incr = state_head.fork();
                  State state_test = state_head.fork();
                  
                  // jump
                  state_jump.appendUnconditionalJump(state_test);
                  
                  // test
                  Model.BranchConditionEnum condition = nonnull(Model.BranchConditionEnum.valueOf(state.obtainToken().expect(SourceTokenClassEnum.BRANCH_CONDITION).text));
                  compileIntegerExpression(state_test, 0);
                  state_test.appendConditionalJump(condition, state_body);
                  
                  // incr
                  compileIntegerExpression(state_incr, 0);
                  
                  // body
                  state_body.obtainToken().expect(SourceTokenClassEnum.BLOCK_ENTER);
                  state_body.putL(("target-continue-" + label), state_test.getStartLabel());
                  state_body.putL(("target-break-" + label), state_done.getStartLabel());
                  compileBlock(state_body);
                  
                  return true;
                }
                
              case BLOCK_LEAVE:
                {
                  finished = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
    }
    
    static ArrayList<String> compileFrame(final State state)
    {
      final ArrayList<String> parameters = (new ArrayList<String>());
      
      state.putI("stack-offset", 0);
      
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            String getParserState()
            {
              return "frame";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.VARIABLE_ALLOCATION, SourceTokenClassEnum.BLOCK_ENTER);
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case VARIABLE_ALLOCATION:
                {
                  parameters.add(token.text);
                  
                  state.putI(("stack-offset-variable-" + token.text), state.incI("stack-offset"));
                  
                  return true;
                }
                
              case BLOCK_ENTER:
                {
                  state.putI("stack-ground", state.getI("stack-offset"));
                  
                  compileBlock(state);
                  
                  state.appendSelfLoop();
                  
                  finished = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
      
      return parameters;
    }
    
    static void compileSlice(final State state)
    {
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            String getParserState()
            {
              return "slice-directive";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.INTEGER_LITERAL, SourceTokenClassEnum.STATEMENT_TERMINATOR);
            }
            
            final ArrayList<Byte> immediateValues = (new ArrayList<Byte>());
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case INTEGER_LITERAL:
                {
                  int val = parseIntegerLiteral(token.text);
                  
                  if (!((-128 <= val) && (val <= 255))) throw (new RuntimeException("MUSE: compiler: slice immediate exceeds representable range (1-byte field)"));
                  
                  immediateValues.add(((byte)(val)));
                  
                  return true;
                }
                
              case STATEMENT_TERMINATOR:
                {
                  byte[] immediates = (new byte[immediateValues.size()]);
                  
                  for (int i = 0; i < immediates.length; i++) {
                    immediates[i] = immediateValues.get(i);
                  }
                  
                  state.appendProgramDataImmediate(immediates);
                  state.appendProgramBreak("compileSlice.1");
                  
                  finished = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
    }
    
    static void compileProgram(final State state, final Model.Trace.SymbolTable table, final AsciiTreeMap<Integer> sizes)
    {
      final TreeMap<String, ArrayList<String>> frameParameters = (new TreeMap<String, ArrayList<String>>());
      
      state.repeatSupplyToken
        ((new TokenHandler<SourceTokenClassEnum>()
          {
            /*
              since compileProgram is not recursive, extra state
              information can be stored here.
            */
            TreeSet<String> included = (new TreeSet<String>());
            
            String getParserState()
            {
              return "program";
            }
            
            String getExpectedClasses()
            {
              return joinExpectedClasses(SourceTokenClassEnum.KEYWORD_PATCH, SourceTokenClassEnum.KEYWORD_CONST, SourceTokenClassEnum.KEYWORD_SLICE, SourceTokenClassEnum.KEYWORD_DEFER, SourceTokenClassEnum.KEYWORD_FRAME, SourceTokenClassEnum.END_OF_FILE);
            }
            
            boolean handle(Token<SourceTokenClassEnum> token)
            {
              switch (token.type) {
              case KEYWORD_PATCH:
                {
                  throw null;
                  
                  /*
                  
                  String file = state.obtainToken().expect(SourceTokenClassEnum.FILENAME_LITERAL).text;
                  
                  if (!included.contains(file)) {
                    state.tokenStream.appendTokenize(file, (new Token<SourceTokenClassEnum>(SourceTokenClassEnum.END_OF_FILE_INCLUDED, "", "(none)", -1, 0, "<<<EOF/INCLUDED>>>")));
                    included.add(file);
                  }
                  
                  return true;
                  */
                }
                
              case KEYWORD_ENTER:
                {
                  throw (new RuntimeException("not yet implemented"));
                }
                
              case KEYWORD_LEAVE:
                {
                  throw (new RuntimeException("not yet implemented"));
                }
                
              case KEYWORD_CONST:
                {
                  state.putI(("const-" + state.obtainTokenLabel()), state.obtainTokenIntegerLiteral());
                  
                  return true;
                }
                
              case KEYWORD_SLICE:
                {
                  String label = state.obtainTokenLabel();
                  
                  state.appendProgramAlignmentImmediate(state.obtainTokenIntegerLiteral());
                  state.appendProgramBreak("compileProgram.1");
                  
                  {
                    String name = ("slice-" + label + "_off");
                    state.putL(name, state.appendProgramLabel(name));
                  }
                  
                  compileSlice(state.fork());
                  
                  {
                    String name = ("slice-" + label + "_lim");
                    state.putL(name, state.appendProgramLabel(name));
                  }
                  
                  return true;
                }
                
              case KEYWORD_DEFER:
                {
                  String label = state.obtainTokenLabel();
                  
                  if (state.hasL(("defer-" + label))) throw null;
                  state.putL(("defer-" + label), (new ProgramLabel("defer-" + label)));
                  
                  return true;
                }
                
              case KEYWORD_FRAME:
                {
                  String label = state.obtainTokenLabel();
                  
                  if (state.hasS(("frame-" + label))) throw null;
                  state.putS(("frame-" + label), "true");
                  
                  if (!state.hasL(("defer-" + label))) {
                    state.putL(("defer-" + label), (new ProgramLabel("defer-" + label)));
                  }
                  
                  state.append(state.getL(("defer-" + label)));
                  
                  State state_frame = state.fork();
                  frameParameters.put(label, compileFrame(state_frame));
                  sizes.put(label, state_frame.programSequence.calculate(0));
                  
                  return true;
                }
                
              case END_OF_FILE_INCLUDED:
                {
                  // nothing to do
                  
                  return true;
                }
                
              case END_OF_FILE:
                {
                  finished = true;
                  
                  return true;
                }
                
              default:
                {
                  return false;
                }
              }
            }
          }));
      
      /*
        previously, the base address for code generation did not
        matter. since the introduction of slice offsets, it must
        either be zero or the actual base offset of the bytecode
        memory region. for now, it's hardcoded to zero. this means
        that programs must correct slice offsets. the other option is
        to support relocations, which seems more cumbersome.
       */
      
      state.programSequence.calculate(0);
      
      for (Map.Entry<String, ArrayList<String>> frameParametersEntry : frameParameters.entrySet()) {
        String label = frameParametersEntry.getKey();
        ArrayList<String> parameters = frameParametersEntry.getValue();
        
        int address = state.getL(("defer-" + label)).address;
        
        table.frames.put(address, (new Model.Trace.SymbolTable.Frame(address, label, true, parameters.size(), (new ArrayList<String>()))));
      }
    }
    
    public static ProgramContainer compileSource(Model.MachineParameters parameters, String basename)
    {
      final Model.Trace.SymbolTable table = (new Model.Trace.SymbolTable());
      
      final AsciiTreeMap<Integer> sizes = (new AsciiTreeMap<Integer>());
      
      State state = (new State("ns.0", parameters, (new TokenStream<SourceTokenClassEnum>(SourceTokenClassEnum.byOrdinal))));
      
      state.tokenStream.appendTokenize
        (basename,
         (new Token<SourceTokenClassEnum>(SourceTokenClassEnum.END_OF_FILE,          "", "(none)", -1, 0, "<<<EOF>>>")));
      
      compileProgram(state, table, sizes);
      
      return (new ProgramContainer(table, sizes, state.programSequence));
    }
    
    public static String sequenceToAscii(ProgramSequence programSequence)
    {
      StringBuilder out = (new StringBuilder());
      programSequence.elaborateAscii(out);
      return out.toString();
    }
    
    public static byte[] sequenceToBytes(ProgramSequence programSequence)
    {
      ByteArrayOutputStream out = (new ByteArrayOutputStream());
      programSequence.elaborateBytes(out);
      return out.toByteArray();
    }
  }
}
