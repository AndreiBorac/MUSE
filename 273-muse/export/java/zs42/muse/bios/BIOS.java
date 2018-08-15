/***
 * BIOS.java
 * copyright (c) 2012-2013 by andrei borac
 ***/

package zs42.muse.bios;

import zs42.muse.*;

import zs42.parts.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class BIOS
{
  static String opcodesToArray(byte[] opcodes)
  {
    StringBuilder out = (new StringBuilder());
    
    for (byte opcode : opcodes) {
      out.append("0x");
      out.append(Integer.toString((opcode & 0xFF), 16));
      out.append(", ");
    }
    
    out.append("0x00");
    
    return out.toString();
  }
  
  public static class Options
  {
    final AsciiTreeMap<String> map = (new AsciiTreeMap<String>());
    
    public void put(String key, String val)
    {
      map.put(key, val);
    }
    
    public String get(String key)
    {
      String val = map.get(key);
      
      if (val == null) throw (new RuntimeException("BIOS: expected option '" + key + "' to be defined"));
      
      return val;
    }
  }
  
  /***
   * BIOS_003 addresses the single-module limitation of BIOS_002,
   * allowing multiple modules that can communicate through
   * channels. Custom handlers are supported on a per-module
   * basis. Also, custom "peripherals" can be attached to channels to
   * emulate peripheral communications. Each module runs in it's own
   * thread, as does each peripheral. Each module has it's own stack,
   * unwind and code memory. Modules in the same hardware group share
   * the same data memory region (with different regions being the
   * each module's "own" memory). Channel buffers must also be
   * allocated in a hardware group. Channels cannot be connected
   * between modules in different hardware groups, but peripherals lie
   * outside any hardware group and can have channels connected to
   * them without restrictions.
   * 
   * The configuration is input as a list of strings, each being one
   * of the following commands:
   * 
   * "hg (hg_name) (source) (length)" - declares a new hardware group
   *  with the given source code file and data memory length
   * 
   * "cp (hg_name) (cp_name) (length)" - declares a channel pair in
   * the given hardware group with the given initial buffer size
   * 
   * "ph (ph_name)" - declares a peripheral
   * 
   * "md (hg_name) (md_name) (length) (initrv)" - declares a module in
   * the given hardware group with the given memory size and initial
   * register value
   * 
   * "at (pm_name) (cp_name) (o/r)" - attaches the owner or respondent
   * side (as indicated) of the given channel to the given peripheral
   * or module
   * 
   * Along with the configuration, an array of handler objects must be
   * supplied, containing:
   * 
   * - for each peripheral, a PeripheralHandler
   * 
   * - for each module, a ModuleHandler
   ***/
  public static class _003
  {
    public static class HardwareGroup
    {
      final MUSE.Model.MachineParameters parameters;
      final MUSE.Model.MachineMemory memory;
      
      final MUSE.Compiler.ProgramContainer container;
      
      final ArrayList<MUSE.Model.MachineBridge> bridges = (new ArrayList<MUSE.Model.MachineBridge>());
      
      int allocation_tail;
      
      HardwareGroup(MUSE.Model.MachineParameters parameters, MUSE.Compiler.ProgramContainer container, int data_memory_length)
      {
        this.parameters = parameters;
        this.container = container;
        
        this.memory = (new MUSE.Model.MachineMemory(parameters, parameters.data_memory_offset, data_memory_length, null));
        
        this.allocation_tail = parameters.data_memory_offset;
      }
      
      byte[] getOpcodes()
      {
        return MUSE.Compiler.sequenceToBytes(container.getProgramSequence());
      }
      
      int allocate(int len)
      {
        int off = allocation_tail;
        
        allocation_tail += len;
        
        return off;
      }
    }
    
    public static class Channel
    {
      public Channel peer;
      public boolean ready;
      public boolean unread;
      
      public int off;
      public int lim;
      
      public MUSE.Model.MachineBridge bridge;
      
      Channel()
      {
      }
      
      void init(Channel peer, boolean ready, int off, int lim)
      {
        this.peer = peer;
        this.ready = ready;
        
        this.off = off;
        this.lim = lim;
      }
      
      Channel select(String which)
      {
        /****/ if (which.equals("o")) {
          return this;
        } else if (which.equals("r")) {
          return peer;
        } else {
          throw (new RuntimeException("BIOS: illegal channel endpoint directive '" + which + "'"));
        }
      }
      
      public void checkReady()
      {
        if (!ready) throw (new RuntimeException("BIOS: channel not ready when expected"));
      }
      
      public int[] scan(int len)
      {
        checkReady();
        
        int[] out = (new int[len]);
        
        for (int pos = 0; pos < out.length; pos++) {
          out[pos] = bridge.a_get((off + pos));
        }
        
        return out;
      }
      
      public void flip()
      {
        checkReady();
        
        ready = false;
        unread = false;
        
        peer.ready = true;
        peer.unread = true;
      }
      
      public void flip(int[] dat)
      {
        checkReady();
        
        for (int pos = 0; pos < dat.length; pos++) {
          bridge.a_put((off + pos), dat[pos]);
        }
        
        flip();
      }
      
      public static void exchangeBuffers(Channel channelA, Channel channelB)
      {
        channelA.checkReady();
        channelB.checkReady();
        
        {
          int tmp      = channelA.off;
          channelA.off = channelB.off;
          channelB.off =          tmp;
          
          channelA.peer.off = channelA.off;
          channelB.peer.off = channelB.off;
        }
        
        {
          int tmp      = channelA.lim;
          channelA.lim = channelB.lim;
          channelB.lim =          tmp;
          
          channelA.peer.lim = channelA.lim;
          channelB.peer.lim = channelB.lim;
        }
      }
    }
    
    public static abstract class PeripheralHandler
    {
      protected abstract void run(MUSE.Model.MachineThread thread, Channel[] channels);
    }
    
    public static abstract class ModuleHandler
    {
      protected abstract boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic);
    }
    
    static abstract class Emulation extends MUSE.Model.MachineThread
    {
      final HardwareGroup hgroup;
      
      final ArrayList<Channel> channels = (new ArrayList<Channel>());
      
      Emulation(HardwareGroup hgroup)
      {
        this.hgroup = hgroup;
      }
      
      final Channel[] newChannelArray()
      {
        return channels.toArray((new Channel[0]));
      }
      
      abstract MUSE.Model.MachineBridge getBridge();
      
      abstract boolean dumps();
      abstract void dump(ObjectOutputStream oos) throws IOException;
    }
    
    public static void main(final Options opts, String[] conf, Object[] handlers)
    {
      try {
        final int jump_target_bytes = MUSE.Compiler.parseIntegerLiteral(opts.get("jump-target-bytes"));
        final int ifun_target_bytes = MUSE.Compiler.parseIntegerLiteral(opts.get("ifun-target-bytes"));
        
        final int stack_length  = MUSE.Compiler.parseIntegerLiteral(opts.get("stack-length"));
        final int unwind_length = MUSE.Compiler.parseIntegerLiteral(opts.get("unwind-length"));
        
        final int code_memory_offset = MUSE.Compiler.parseIntegerLiteral(opts.get("code-memory-offset"));
        final int code_memory_length = MUSE.Compiler.parseIntegerLiteral(opts.get("code-memory-length"));
        final int data_memory_offset = MUSE.Compiler.parseIntegerLiteral(opts.get("data-memory-offset"));
        final int data_memory_length = MUSE.Compiler.parseIntegerLiteral(opts.get("data-memory-length"));
        
        final int maximum_trace_length = MUSE.Compiler.parseIntegerLiteral(opts.get("maximum-trace-length"));
        
        final int maximum_iterations = MUSE.Compiler.parseIntegerLiteral(opts.get("maximum-iterations"));
        
        final MUSE.Model.MachineParameters parameters = (new MUSE.Model.MachineParameters(jump_target_bytes, ifun_target_bytes, stack_length, unwind_length, code_memory_offset, code_memory_length, data_memory_offset, data_memory_length, (new TreeSet<String>())));
        
        final TreeMap<String, HardwareGroup> hardwareGroups = (new TreeMap<String, HardwareGroup>());
        final TreeMap<String, Channel> channelPairs = (new TreeMap<String, Channel>());
        
        final TreeMap<String, Emulation> emulations = (new TreeMap<String, Emulation>());
        
        final ArrayList<String> printouts = (new ArrayList<String>());
        
        final F1<Void, Throwable> die =
          (new F1<Void, Throwable>()
           {
             public Void invoke(Throwable e)
             {
               try {
                 // save the specific cause
                 {
                   BufferedOutputStream bos = (new BufferedOutputStream(new FileOutputStream("cause.txt")));
                   bos.write(NoUTF.str2bin(MUSE.depict(e)));
                   bos.close();
                 }
                 
                 // save printouts
                 {
                   BufferedOutputStream bos = (new BufferedOutputStream(new FileOutputStream("trace.txt")));
                   
                   for (String line : printouts) {
                     bos.write(NoUTF.str2bin(line));
                     bos.write('\n');
                   }
                   
                   bos.close();
                 }
                 
                 // save per-emulation details
                 {
                   for (Map.Entry<String, Emulation> entry : emulations.entrySet()) {
                     if (entry.getValue().dumps()) {
                       ObjectOutputStream oos = (new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(entry.getKey() + ".out"))));
                       entry.getValue().dump(oos);
                       oos.close();
                     }
                   }
                 }
               } catch (Throwable error) {
                 throw MUSE.fatal(error);
               }
               
               System.exit(0);
               
               return null;
             }
           });
        
        int handleri = 0;
        
        for (String line : conf) {
          System.err.println("BIOS: configuration: handling line '" + line + "'");
          System.err.flush();
          
          String[] word = line.split("\\s+");
          
          String command = word[0];
          
          /****/ if (command.equals("hg")) {
            final String hg_name = word[1];
            final String source  = word[2];
            final int    length  = MUSE.Compiler.parseIntegerLiteral(word[3]);
            
            if (!(length >= 0)) throw null;
            
            final MUSE.Compiler.ProgramContainer container = MUSE.Compiler.compileSource(parameters, source);
            
            try {
              FileOutputStream fos = (new FileOutputStream(source + ".asm"));
              fos.write(PartsUtils.str2arr(MUSE.Compiler.sequenceToAscii(container.getProgramSequence())));
              fos.close();
            } catch (Throwable e) {
              throw (new RuntimeException(e));
            }
            
            hardwareGroups.put(hg_name, (new HardwareGroup(parameters, container, length)));
          } else if (command.equals("cp")) {
            final String hg_name = word[1];
            final String cp_name = word[2];
            final int    length  = MUSE.Compiler.parseIntegerLiteral(word[3]);
            
            if (!(length >= 0)) throw null;
            
            HardwareGroup hgroup = hardwareGroups.get(hg_name);

            int off = hgroup.allocate(length);
            int lim = (off + length);
            
            Channel ownerChannel      = (new Channel());
            Channel respondentChannel = (new Channel());
            
            ownerChannel.init(respondentChannel, true,  off, lim);
            respondentChannel.init(ownerChannel, false, off, lim);
            
            channelPairs.put(cp_name, ownerChannel);
          } else if (command.equals("ph")) {
            final String ph_name = word[1];
            
            final PeripheralHandler peripheral_handler = ((PeripheralHandler)(handlers[handleri++]));
            
            emulations.put
              (ph_name,
               (new Emulation(null)
                 {
                   protected void run(MUSE.Model.MachineThread thread)
                   {
                     Throwable cause = null;
                     
                     try {
                       peripheral_handler.run(thread, newChannelArray());
                     } catch (Throwable e) {
                       cause = e;
                     }
                     
                     if (cause != null) {
                       die.invoke(cause);
                     }
                   }
                   
                   MUSE.Model.MachineBridge getBridge()
                   {
                     return null;
                   }
                   
                   boolean dumps()
                   {
                     return false;
                   }
                   
                   void dump(ObjectOutputStream oos)
                   {
                     throw null;
                   }
                 }));
          } else if (command.equals("md")) {
            final String hg_name = word[1];
            final String md_name = word[2];
            final int    length  = MUSE.Compiler.parseIntegerLiteral(word[3]);
            final int    initrv  = MUSE.Compiler.parseIntegerLiteral(word[4]);
            
            final ModuleHandler module_handler = ((ModuleHandler)(handlers[handleri++]));
            
            final HardwareGroup hgroup = hardwareGroups.get(hg_name);
            
            final int actual_length;
            
            /****/ if (length >= 0) {
              actual_length = length;
            } else if (length == -1) {
              actual_length = (hgroup.memory.getMemoryExtent() - hgroup.allocation_tail);
            } else {
              throw null;
            }
            
            final int module_off = hgroup.allocate(actual_length);
            final int module_lim = (module_off + actual_length);
            
            emulations.put
              (md_name,
               (new Emulation(hgroup)
                 {
                   final MUSE.Model.MachineEnvironment environment =
                     (new MUSE.Model.MachineEnvironment()
                       {
                         public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
                         {
                           switch (((int)(magic))) {
                           case 0: /* QUERY_MACHINE_PARAMETER */
                             {
                               final int r = bridge.r_get();
                               
                               bridge.x_msg("BIOS: program querying machine parameter " + MUSE.hexify(r) + " (" + r + ")");
                               
                               switch (r) {
                               case 0:
                                 {
                                   bridge.r_put(code_memory_offset);
                                   return true;
                                 }
                                 
                               case 1:
                                 {
                                   bridge.r_put((code_memory_offset + code_memory_length));
                                   return true;
                                 }
                                 
                               case 2:
                                 {
                                   bridge.r_put(module_off);
                                   return true;
                                 }
                                 
                               case 3:
                                 {
                                   bridge.r_put(module_lim);
                                   return true;
                                 }
                                 
                               default:
                                 {
                                   throw (new RuntimeException("BIOS: illegal machine parameter index " + MUSE.hexify(r) + " (" + r + ")"));
                                 }
                               }
                             }
                             
                           case 1: /* EXIT */
                             {
                               final int r = bridge.r_get();
                               
                               bridge.x_msg("BIOS: program exited with status " + MUSE.hexify(r) + " (" + r + ")");
                               
                               return false;
                             }
                             
                           case 3: /* EMIT INTEGER */
                             {
                               final int r = bridge.r_get();
                               
                               String mesg = ("BIOS: program emitted " + MUSE.hexify(r) + " (" + r + ")");
                               
                               thread.trace(mesg);
                               bridge.x_msg(mesg);
                               
                               return true;
                             }
                             
                           case 4: /* TRACE */
                             {
                               throw (new RuntimeException("BIOS: trace not yet implemented"));
                             }
                             
                           case 5: /* ARITHMETIC */
                             {
                               int retv;
                               
                               final int mode = bridge.r_get();
                               
                               switch (mode) {
                               case 0: /* mul */
                                 {
                                   int a = bridge.s_get(0);
                                   int b = bridge.s_get(1);
                                   
                                   retv = (a * b);
                                   
                                   break;
                                 }
                                 
                               case 1: /* div */
                                 {
                                   int a = bridge.s_get(0);
                                   int b = bridge.s_get(1);
                                   
                                   retv = (a / b);
                                   
                                   break;
                                 }
                                 
                               default:
                                 {
                                   throw (new RuntimeException("BIOS: illegal arithmetic operation " + MUSE.hexify(mode) + " (" + mode + ")"));
                                 }
                               }
                               
                               bridge.r_put(retv);
                               
                               return true;
                             }
                             
                           case 6: /* HARDLOOP */
                             {
                               final int mode = bridge.r_get();
                               
                               switch (mode) {
                               case 0: /* valinc */
                                 {
                                   int off = bridge.s_get(0);
                                   int lim = bridge.s_get(1);
                                   int val = bridge.s_get(2);
                                   int inc = bridge.s_get(3);
                                   
                                   while (off < lim) {
                                     bridge.a_put(off, val);
                                     
                                     off += 1;
                                     val += inc;
                                   }
                                   
                                   break;
                                 }
                                 
                               default:
                                 {
                                   throw (new RuntimeException("BIOS: illegal hardloop operation " + MUSE.hexify(mode) + " (" + mode + ")"));
                                 }
                               }
                               
                               return true;
                             }
                             
                           case 10: /* PKTS_WAIT */
                             {
                               int msk = bridge.s_get(0);
                               int off = bridge.s_get(1);
                               int lim = bridge.s_get(2);
                               
                               if (!(((off == 0) && (lim == 0)) || (off < lim))) throw null;
                               
                               int selected_idx;
                               Channel selected = null;
                               
                               outer:
                               while (true) {
                                 for (int idx = 0; idx < channels.size(); idx++) {
                                   if (((1 << idx) & msk) != 0) {
                                     selected = channels.get(idx);
                                     
                                     if (selected.unread) {
                                       selected = channels.get((selected_idx = idx));
                                       break outer;
                                     }
                                   }
                                 }
                                 
                                 thread.yield();
                               }
                               
                               selected.unread = false;
                               
                               /* "refresh" channel contents for them to display correctly when replaying the bridge's events */
                               /* OLD -- SHOULD NOT BE NECESSARY NOW
                               {
                                 for (int pos = selected.off; pos < selected.lim; pos++) {
                                   bridge.a_put(pos, bridge.a_get(pos));
                                 }
                               }
                               */
                               
                               for (int pos = off; pos < lim; pos++) {
                                 bridge.a_put(pos, bridge.a_get(selected.off + (pos - off)));
                               }
                               
                               bridge.r_put(selected_idx);
                               
                               return true;
                             }
                             
                           case 11: /* PKTS_POST */
                             {
                               int tgt = bridge.s_get(0);
                               int off = bridge.s_get(1);
                               int lim = bridge.s_get(2);
                               
                               if (!(((off == 0) && (lim == 0)) || (off < lim))) throw null;
                               
                               Channel selected = channels.get(tgt);
                               
                               thread.trace("PKTS_POST selected channel " + selected + " (peer " + selected.peer + ")");
                               
                               while (!(selected.ready)) {
                                 thread.yield();
                               }
                               
                               for (int pos = off; pos < lim; pos++) {
                                 bridge.a_put((selected.off + (pos - off)), bridge.a_get(pos));
                               }
                               
                               thread.trace("PKTS_POST flipping channel " + selected + " (peer " + selected.peer + ")");
                               
                               selected.flip();
                               
                               return true;
                             }
                             
                           case 12: /* PKTS_DMAO */
                             {
                               int tgt = bridge.s_get(0);
                               
                               Channel selected = channels.get(tgt);
                               
                               while (!(selected.ready)) {
                                 thread.yield();
                               }
                               
                               bridge.r_put(selected.off);
                               
                               return true;
                             }
                             
                           case 13: /* PKTS_DMAL */
                             {
                               int tgt = bridge.s_get(0);
                               
                               Channel selected = channels.get(tgt);
                               
                               while (!(selected.ready)) {
                                 thread.yield();
                               }
                               
                               bridge.r_put((selected.lim - selected.off));
                               
                               return true;
                             }
                             
                           case 14: /* PKTS_DMAX */
                             {
                               if (true) { throw null; } // new designs should not require DMAX
                               
                               int chA = bridge.s_get(0);
                               int chB = bridge.s_get(1);
                               
                               Channel selectedA = channels.get(chA);
                               Channel selectedB = channels.get(chB);
                               
                               while (!(selectedA.ready && selectedB.ready)) {
                                 thread.yield();
                               }
                               
                               Channel.exchangeBuffers(selectedA, selectedB);
                               
                               return true;
                             }
                           }
                           
                           return module_handler.onTrap(thread, bridge, magic);
                         }
                       });
                   
                   final ArrayList<MUSE.Model.Trace.Event> events = (new ArrayList<MUSE.Model.Trace.Event>());
                   
                   final MUSE.Model.MachineBridge bridge = (new MUSE.Model.MachineBridge(hgroup.getOpcodes(), parameters, environment, (new MUSE.Model.Trace.Collector.ToArrayList(events)), hgroup.memory));
                   
                   { hgroup.bridges.add(bridge); }
                   
                   MUSE.Model.MachineBridge getBridge() { return bridge; }
                   
                   final MUSE.Model.MachineState state = (new MUSE.Model.MachineState(bridge));
                   
                   protected void run(MUSE.Model.MachineThread thread)
                   {
                     Throwable cause = null;
                     
                     try {
                       bridge.r_put(initrv);
                       state.execute(thread, maximum_trace_length);
                       if (!bridge.h_get()) throw (new RuntimeException("BIOS: maximum execution trace length reached"));
                     } catch (Throwable e) {
                       cause = e;
                       
                       final String txt = MUSE.depict(e);
                       
                       MUSE.Model.MachineBridge.col_x_msg
                         ((new MUSE.Model.Trace.Collector.ToArrayList(events)),
                          txt);
                     }
                     
                     if (cause != null) {
                       die.invoke(cause);
                     }
                   }
                   
                   boolean dumps()
                   {
                     return true;
                   }
                   
                   void dump(ObjectOutputStream oos) throws IOException
                   {
                     oos.writeObject(parameters);
                     oos.writeObject(hgroup.getOpcodes());
                     oos.writeObject(hgroup.container.getSymbolTable());
                     oos.writeObject(events);
                   }
                 }));
          } else if (command.equals("at")) {
            String em_name = word[1];
            String cp_name = word[2];
            String which   = word[3];
            
            Emulation emulation = emulations.get(em_name);
            
            Channel channel = channelPairs.get(cp_name).select(which);
            
            channel.bridge = emulation.getBridge();
            
            emulation.channels.add(channel);
          } else {
            throw (new RuntimeException("BIOS: illegal configuration command '" + command + "'"));
          }
        }
        
        Throwable cause = null;
        
        try {
          /* cross-link bridge peers */
          {
            for (HardwareGroup hgroup : hardwareGroups.values()) {
              for (MUSE.Model.MachineBridge src : hgroup.bridges) {
                for (MUSE.Model.MachineBridge dst : hgroup.bridges) {
                  if (dst != src) {
                    src.addPeer(dst);
                  }
                }
              }
            }
          }
          
          ArrayList<MUSE.Model.MachineThread> threads = (new ArrayList<MUSE.Model.MachineThread>());
          
          threads.addAll(emulations.values());
          
          if (!(MUSE.Model.MachineThread.launch(maximum_iterations, printouts, threads))) {
            throw (new RuntimeException("BIOS: maximum iterations reached"));
          } else {
            throw (new RuntimeException("BIOS: maximum iterations not reached"));
          }
        } catch (Throwable e) {
          cause = e;
        }
        
        if (cause != null) {
          die.invoke(cause);
        }
      } catch (Throwable e) {
        throw (new RuntimeException(e));
      }
    }
  }
  
  public static class _002
  {
    static class ReactionFile
    {
      final Pattern COMMENT = Pattern.compile("#.*");
      final Pattern WORDS   = Pattern.compile("\\s+");
      
      final BufferedReader inp;
      
      int lineo = 0;
      
      ReactionFile(String filename)
      {
        try {
          inp =
            (new BufferedReader
             (new FileReader
              (filename)));
        } catch (IOException e) {
          throw (new RuntimeException(e));
        }
      }
      
      int getLineNumber()
      {
        return lineo;
      }
      
      void nextLine(SimpleDeque<String> tokens, SimpleDeque<Integer> values)
      {
        while (true) {
          final String line;
          
          try {
            lineo++;
            
            line = inp.readLine();
            
            if (line == null) {
              throw (new RuntimeException("BIOS: unexpected EOF while scanning reaction file"));
            }
          } catch (IOException e) {
            throw (new RuntimeException(e));
          }
          
          String[] words = WORDS.split(COMMENT.matcher(line).replaceFirst(""));
          
          tokens.clear();
          values.clear();
          
          boolean number = false;
          
          for (String word : words) {
            if (word.length() > 0) {
              if (number) {
                if (!word.equals(".")) {
                  values.addLast(MUSE.Compiler.parseIntegerLiteral(word));
                }
              } else {
                if (word.equals(".")) {
                  number = true;
                } else {
                  tokens.addLast(word);
                }
              }
            }
          }
          
          if ((tokens.size() | values.size()) > 0) {
            break;
          }
        }
      }
    }
    
    public static class Channel
    {
      public final int idx;
      
      public boolean own; // true iff currently owned by module; false otherwise
      
      public int off;
      public int lim;
      
      public Channel(int idx, boolean own, int off, int lim)
      {
        this.idx = idx;
        this.own = own;
        this.off = off;
        this.lim = lim;
      }
      
      public int capacity()
      {
        return (lim - off);
      }
      
      public static void exchangeBuffers(Channel channelA, Channel channelB)
      {
        {
          int tmp      = channelA.off;
          channelA.off = channelB.off;
          channelB.off =          tmp;
        }
        
        {
          int tmp      = channelA.lim;
          channelA.lim = channelB.lim;
          channelB.lim =          tmp;
        }
      }
    }
    
    public static void main(final Options opts, MUSE.Model.MachineEnvironment more)
    {
      try {
        final int jump_target_bytes = MUSE.Compiler.parseIntegerLiteral(opts.get("jump-target-bytes"));
        final int ifun_target_bytes = MUSE.Compiler.parseIntegerLiteral(opts.get("ifun-target-bytes"));
        
        final int stack_length  = MUSE.Compiler.parseIntegerLiteral(opts.get("stack-length"));
        final int unwind_length = MUSE.Compiler.parseIntegerLiteral(opts.get("unwind-length"));
        
        final int code_memory_offset = MUSE.Compiler.parseIntegerLiteral(opts.get("code-memory-offset"));
        final int code_memory_length = MUSE.Compiler.parseIntegerLiteral(opts.get("code-memory-length"));
        final int data_memory_offset = MUSE.Compiler.parseIntegerLiteral(opts.get("data-memory-offset"));
        final int data_memory_length = MUSE.Compiler.parseIntegerLiteral(opts.get("data-memory-length"));
        
        final MUSE.Model.MachineParameters parameters = (new MUSE.Model.MachineParameters(jump_target_bytes, ifun_target_bytes, stack_length, unwind_length, code_memory_offset, code_memory_length, data_memory_offset, data_memory_length, (new TreeSet<String>())));
        
        final ArrayList<Channel> channels = (new ArrayList<Channel>());
        
        final int data_memory_length_remaining;
        
        {
          final int channel_count = MUSE.Compiler.parseIntegerLiteral(opts.get("channel-count"));
          
          int channel_alloc = (data_memory_offset + data_memory_length);
          
          for (int i = 0; i < channel_count; i++) {
            final boolean channel_ownership = Boolean.parseBoolean(opts.get("channel-" + i + "-ownership"));
            final int channel_length = MUSE.Compiler.parseIntegerLiteral(opts.get("channel-" + i + "-length"));
            
            channel_alloc -= channel_length;
            
            channels.add((new Channel(i, channel_ownership, channel_alloc, (channel_alloc + channel_length))));
          }
          
          data_memory_length_remaining = (channel_alloc - data_memory_offset);
        }
        
        final String filepath_program = opts.get("filepath-program");
        
        final MUSE.Compiler.ProgramContainer programContainer = MUSE.Compiler.compileSource(parameters, filepath_program);
        
        // emit sizes before elaborating code so that excessively long
        // jump offsets that exceed the machine parameters can be
        // isolated
        {
          StringBuilder output = (new StringBuilder());
          
          {
            for (Map.Entry<String, Integer> entry : programContainer.getFunctionSizes().entrySet()) {
              output.append((entry.getKey() + ": " + entry.getValue() + "\n"));
            }
          }
          
          String content = output.toString();
          
          {
            BufferedOutputStream bos = (new BufferedOutputStream(new FileOutputStream(filepath_program + ".siz")));
            
            bos.write(PartsUtils.str2arr(content));
            
            bos.close();
          }
          
          System.err.print(content);
        }
        
        final String assembly = MUSE.Compiler.sequenceToAscii(programContainer.getProgramSequence());
        final byte[] opcodes  = MUSE.Compiler.sequenceToBytes(programContainer.getProgramSequence());
        
        {
          FileOutputStream fos = (new FileOutputStream(filepath_program + ".k.c"));
          fos.write(PartsUtils.str2arr(MUSE.Model.generateKernelCode()));
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(filepath_program + ".asm"));
          fos.write(PartsUtils.str2arr(assembly));
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(filepath_program + ".p.c"));
          fos.write(PartsUtils.str2arr("MUSE_OPCODES_DECLARATOR_A MUSE_TYPE_U1 MUSE_OPCODES_DECLARATOR_B opcodes_" + filepath_program.replaceAll("[^0-9A-Za-z]", "_") + "[] MUSE_OPCODES_DECLARATOR_C = { " + opcodesToArray(opcodes) + " };\n"));
          {
            for (Map.Entry<String, Integer> entry : programContainer.getFunctionSizes().entrySet()) {
              fos.write(PartsUtils.str2arr("// " + entry.getKey() + ": " + entry.getValue() + " bytes\n"));
            }
          }
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(filepath_program + ".bin"));
          fos.write(opcodes);
          fos.close();
        }
        
        if (Boolean.parseBoolean(opts.get("compile-only"))) return;
        
        final ReactionFile reaction = (new ReactionFile(opts.get("filepath-reaction")));
        
        final SimpleDeque<String>  reaction_tokens = (new SimpleDeque<String>());
        final SimpleDeque<Integer> reaction_values = (new SimpleDeque<Integer>());
        
        final ArrayList<MUSE.Model.MachineEnvironment> handlers = (new ArrayList<MUSE.Model.MachineEnvironment>());
        
        // add the supplied handler as the first handler in the chain,
        // so that it can override the behavior of the stock handlers
        {
          if (more != null) {
            handlers.add(more);
          }
        }
        
        // handle machine parameter queries, if desired
        {
          if (Boolean.parseBoolean(opts.get("handle-machine-parameter-queries"))) {
            handlers.add
              ((new MUSE.Model.MachineEnvironment()
                {
                  public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
                  {
                    switch (((int)(magic))) {
                    case 0:
                      {
                        int r = bridge.r_get();
                        
                        bridge.x_msg("BIOS: program querying machine parameter " + MUSE.hexify(r) + " (" + r + ")");
                        
                        switch (r) {
                        case 0:
                          {
                            bridge.r_put(code_memory_offset);
                            return true;
                          }
                          
                        case 1:
                          {
                            bridge.r_put((code_memory_offset + code_memory_length));
                            return true;
                          }
                          
                        case 2:
                          {
                            bridge.r_put(data_memory_offset);
                            return true;
                          }
                          
                        case 3:
                          {
                            bridge.r_put((data_memory_offset + data_memory_length_remaining));
                            return true;
                          }
                          
                        default:
                          {
                            throw (new RuntimeException("BIOS: illegal machine parameter index " + MUSE.hexify(r) + " (" + r + ")"));
                          }
                        }
                      }
                    }
                    
                    return false;
                  }
                }));
          }
        }
        
        // handle channel interrupts, if desired (i.e., if there are channels)
        {
          if (channels.size() > 0) {
            handlers.add
              ((new MUSE.Model.MachineEnvironment()
                {
                  void grab(Channel channel)
                  {
                    if (!(channel.own)) {
                      reaction.nextLine(reaction_tokens, reaction_values);
                      
                      if (reaction_tokens.removeFirst().equals("fc")) {
                        if (!(reaction_values.removeFirst().equals(channel.idx))) {
                          throw (new RuntimeException("BIOS: channels: flip channel of wrong channel on line " + reaction.getLineNumber()));
                        }
                        
                        channel.own = true;
                      } else {
                        throw (new RuntimeException("BIOS: channels: expected channel ownership or \"fc\" (flip channel) directive on line " + reaction.getLineNumber()));
                      }
                    }
                  }
                  
                  public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
                  {
                    switch (((int)(magic))) {
                    case 5: /* PKTS_WAIT */
                      {
                        int msk = bridge.s_get(0);
                        int off = bridge.s_get(1);
                        int lim = bridge.s_get(2);
                        
                        reaction.nextLine(reaction_tokens, reaction_values);
                        
                        String cmd = reaction_tokens.removeFirst();
                        
                        if (!(cmd.equals("ac"))) throw (new RuntimeException("BIOS: channels: expected \"ac\" (assign channel) directive on line " + reaction.getLineNumber()));
                        
                        int tgt = reaction_values.removeFirst();
                        
                        if (!(tgt < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        
                        if (!((msk & (1 << tgt)) != 0)) throw (new RuntimeException("BIOS: channels: channel identifier for channel supply is not being listened for"));
                        
                        Channel channel = channels.get(tgt);
                        
                        if (channel.own) throw (new RuntimeException("BIOS: channels: impossible scenario as the assigned channel is currently owned by module"));
                        
                        if (!(reaction_values.size() <= channel.capacity())) throw (new RuntimeException("BIOS: channels: value train exceeds channel capacity"));
                        
                        for (int ptr = channels.get(tgt).off; (!(reaction_values.isEmpty())); ptr++) {
                          bridge.a_put(ptr, reaction_values.removeFirst());
                        }
                        
                        channel.own = true;
                        
                        if (!((lim - off) <= channel.capacity())) throw (new RuntimeException("BIOS: channels: program read exceeds channel capacity"));
                        
                        // copy-out
                        {
                          int ptr = channels.get(tgt).off;
                          
                          while (off < lim) {
                            bridge.a_put(off++, bridge.a_get(ptr++));
                          }
                        }
                        
                        bridge.r_put(tgt);
                        
                        // pretend we didn't handle the event to fall through to reaction mechanism
                        return false;
                      }
                      
                    case 6: /* PKTS_POST */
                      {
                        int tgt = bridge.s_get(0);
                        int off = bridge.s_get(1);
                        int lim = bridge.s_get(2);
                        
                        if (!(tgt < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        
                        Channel channel = channels.get(tgt);
                        
                        grab(channel);
                        
                        if (!((lim - off) <= channel.capacity())) throw (new RuntimeException("BIOS: channels: program write exceeds channel capacity"));
                        
                        // copy-in
                        {
                          int ptr = channels.get(tgt).off;
                          
                          while (off < lim) {
                            bridge.a_put(ptr++, bridge.a_get(off++));
                          }
                        }
                        
                        channel.own = false;
                        
                        reaction.nextLine(reaction_tokens, reaction_values);
                        
                        String cmd = reaction_tokens.removeFirst();
                        
                        if (!(cmd.equals("ec"))) throw (new RuntimeException("BIOS: channels: expected \"ec\" (expect channel) directive on line " + reaction.getLineNumber()));
                        
                        int exp = reaction_values.removeFirst();
                        
                        if (!(tgt == exp)) throw (new RuntimeException("BIOS: channels: unexpected channel identifier " + exp));
                        
                        if (!(reaction_values.size() <= channel.capacity())) throw (new RuntimeException("BIOS: channels: value train exceeds channel capacity on line " + reaction.getLineNumber()));
                        
                        for (int ptr = channels.get(tgt).off; (!(reaction_values.isEmpty())); ptr++) {
                          int v, t;
                          
                          if (!((v = bridge.a_get(ptr)) == (t = reaction_values.removeFirst()))) throw (new RuntimeException("BIOS: channels: unexpected datum " + MUSE.hexify(v) + ", expected " + MUSE.hexify(t) + " on line " + reaction.getLineNumber()));
                        }
                        
                        // pretend we didn't handle the event to fall through to reaction mechanism
                        return false;
                      }
                      
                    case 7: /* PKTS_DMAO */
                      {
                        int tgt = bridge.s_get(0);
                        
                        if (!(tgt < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        
                        Channel channel = channels.get(tgt);
                        
                        grab(channel);
                        
                        bridge.r_put(channel.off);
                        
                        return true;
                      }
                      
                    case 8: /* PKTS_DMAL */
                      {
                        int tgt = bridge.s_get(0);
                        
                        if (!(tgt < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        
                        Channel channel = channels.get(tgt);
                        
                        grab(channel);
                        
                        bridge.r_put((channel.lim - channel.off));
                        
                        return true;
                      }
                      
                    case 9: /* PKTS_DMAX */
                      {
                        int chA = bridge.s_get(0);
                        int chB = bridge.s_get(1);
                        
                        if (!(chA < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        if (!(chB < channels.size())) throw (new RuntimeException("BIOS: channels: channel identifier exceeds defined range"));
                        
                        Channel channelA = channels.get(chA);
                        Channel channelB = channels.get(chB);
                        
                        Channel.exchangeBuffers(channelA, channelB);
                        
                        return true;
                      }
                    }
                    
                    return false;
                  }
                }));
          }
        }
        
        // handle "remaining" interrupts by the general reaction mechanism
        {
          if (Boolean.parseBoolean(opts.get("handle-remaining-interrupts"))) {
            handlers.add
              ((new MUSE.Model.MachineEnvironment()
                {
                  int pointer = 0;
                  
                  public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
                  {
                    while (true) {
                      reaction.nextLine(reaction_tokens, reaction_values);
                      
                      String cmd = reaction_tokens.removeFirst();
                      
                      /****/ if (cmd.equals("eg")) { /* expect magic */
                        if (magic != reaction_values.removeFirst()) throw (new RuntimeException("BIOS: reaction: unexpected magic value " + MUSE.hexify(((int)(magic))) + " on line " + reaction.getLineNumber()));
                      } else if (cmd.equals("er")) { /* expect register */
                        int r;
                        if ((r = bridge.r_get()) != reaction_values.removeFirst()) throw (new RuntimeException("BIOS: reaction: unexpected register value " + MUSE.hexify(r)));
                      } else if (cmd.equals("em")) { /* expect memory */
                        int v;
                        if ((v = bridge.a_get(reaction_values.removeFirst())) != reaction_values.removeFirst()) throw (new RuntimeException("BIOS: reaction: unexpected memory value " + MUSE.hexify(v) + " on line " + reaction.getLineNumber()));
                      } else if (cmd.equals("es")) { /* expect stack */
                        int v;
                        if ((v = bridge.s_get(reaction_values.removeFirst())) != reaction_values.removeFirst()) throw (new RuntimeException("BIOS: reaction: unexpected stack value " + MUSE.hexify(v) + " on line " + reaction.getLineNumber()));
                      } else if (cmd.equals("ar")) { /* assign register */
                        bridge.r_put(reaction_values.removeFirst());
                      } else if (cmd.equals("am")) { /* assign memory */
                        bridge.a_put(reaction_values.removeFirst(), reaction_values.removeFirst());
                      } else if (cmd.equals("bp")) { /* set pointer to channel buffer */
                        pointer = channels.get(reaction_values.removeFirst()).off;
                      } else if (cmd.equals("sp")) { /* set pointer */
                        pointer = reaction_values.removeFirst();
                      } else if (cmd.equals("xp")) { /* adjust pointer */
                        pointer += reaction_values.removeFirst();
                      } else if (cmd.equals("mp")) { /* multiply pointer */
                        pointer *= reaction_values.removeFirst();
                      } else if (cmd.equals("dp")) { /* divide pointer */
                        pointer /= reaction_values.removeFirst();
                      } else if (cmd.equals("lp")) { /* load pointer */
                        pointer = bridge.s_get(reaction_values.removeFirst());
                      } else if (cmd.equals("pp")) { /* pointer from pointer */
                        pointer = bridge.a_get(pointer);
                      } else if (cmd.equals("ep")) { /* expect pointer (indirect) */
                        int v;
                        if ((v = bridge.a_get(pointer++)) != reaction_values.removeFirst()) throw (new RuntimeException("BIOS: reaction: unexpected memory value " + MUSE.hexify(v) + " on line " + reaction.getLineNumber()));
                      } else if (cmd.equals("epnz")) { /* expect pointer (indirect) */
                        int v;
                        if ((v = bridge.a_get(pointer++)) == 0) throw (new RuntimeException("BIOS: reaction: unexpected memory value " + MUSE.hexify(v) + " on line " + reaction.getLineNumber()));
                      } else if (cmd.equals("ap")) { /* assign pointer (indirect) */
                        bridge.a_put(pointer++, reaction_values.removeFirst());
                      } else if (cmd.equals("nx")) { /* continue execution ("next") */
                        return true;
                      } else if (cmd.equals("br")) { /* clean exit ("break") */
                        bridge.h_put(true);
                        return true;
                      } else {
                        throw (new RuntimeException("BIOS: reaction: illegal command '" + cmd + "' on line " + reaction.getLineNumber()));
                      }
                    }
                  }
                }));
          }
        }
        
        final MUSE.Model.MachineEnvironment environment =
          (new MUSE.Model.MachineEnvironment()
            {
              public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
              {
                for (MUSE.Model.MachineEnvironment handler : handlers) {
                  if (handler.onTrap(thread, bridge, magic)) {
                    return true;
                  }
                }
                
                return false;
              }
            });
        
        final ArrayList<MUSE.Model.Trace.Event> events = (new ArrayList<MUSE.Model.Trace.Event>());
        
        Throwable cause = null;
        
        try {
          MUSE.Model.MachineBridge bridge = (new MUSE.Model.MachineBridge(opcodes, parameters, environment, (new MUSE.Model.Trace.Collector.ToArrayList(events))));
          MUSE.Model.MachineState state = (new MUSE.Model.MachineState(bridge));
          state.execute(null, MUSE.Compiler.parseIntegerLiteral(opts.get("maximum-trace-length")));
          if (!bridge.h_get()) throw (new RuntimeException("MUSE: maximum execution trace length reached"));
        } catch (Throwable e) {
          cause = e;
          
          final String txt = MUSE.depict(e);
          
          MUSE.Model.MachineBridge.col_x_msg
            ((new MUSE.Model.Trace.Collector.ToArrayList(events)),
             txt);
        }
        
        {
          ObjectOutputStream oos = (new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(filepath_program + ".evt"))));
          oos.writeObject(parameters);
          oos.writeObject(opcodes);
          oos.writeObject(programContainer.getSymbolTable());
          oos.writeObject(events);
          oos.close();
        }
        
        if (cause != null) {
          throw (new RuntimeException(cause));
        }
      } catch (Throwable e) {
        throw MUSE.fatal(e);
      }
    }
    
    public static Options parseOptions(int argi, String[] args)
    {
      final Options opts = (new Options());
      
      for (int i = (argi + 0), j = (argi + 1); j < args.length; i += 2, j += 2) {
        System.err.println("option: '" + args[i] + "' => '" + args[j] + "'");
        
        opts.put(args[i], args[j]);
      }
      
      return opts;
    }
    
    public static void main(int argi, String[] args)
    {
      main(parseOptions(argi, args), null);
    }
    
    public static void main(String[] args)
    {
      main(0, args);
    }
  }
  
  public static class _001
  {
    public static void exec(int argi, final String[] args, final MUSE.Model.MachineEnvironment more)
    {
      try {
        final TreeSet<String> options = (new TreeSet<String>());
        
        for (String option : args[argi++].split("\\s+")) {
          options.add(option);
        }
        
        final int hardcoded_jump_target_bytes = 1;
        final int hardcoded_ifun_target_bytes = 2;
        
        final String basename = args[argi++];
        final AsciiTreeMap<Integer> sizes = (new AsciiTreeMap<Integer>());
        final MUSE.Compiler.ProgramContainer programContainer = MUSE.Compiler.compileSource((new MUSE.Model.MachineParameters(hardcoded_jump_target_bytes, hardcoded_ifun_target_bytes, 0, 0, 0, 0, 0, 0, (new TreeSet<String>()))), basename);
        
        // print sizes before elaborating code so that excessively
        // long jump offsets that exceed the machine parameters can be
        // isolated
        {
          for (Map.Entry<String, Integer> entry : programContainer.getFunctionSizes().entrySet()) {
            MUSE.trace((entry.getKey() + ": " + entry.getValue()));
          }
        }
        
        final String assembly = MUSE.Compiler.sequenceToAscii(programContainer.getProgramSequence());
        final byte[] opcodes = MUSE.Compiler.sequenceToBytes(programContainer.getProgramSequence());
        
        {
          FileOutputStream fos = (new FileOutputStream(basename + ".k.c"));
          fos.write(PartsUtils.str2arr(MUSE.Model.generateKernelCode()));
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(basename + ".asm"));
          fos.write(PartsUtils.str2arr(assembly));
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(basename + ".p.c"));
          fos.write(PartsUtils.str2arr("MUSE_OPCODES_DECLARATOR_A MUSE_TYPE_U1 MUSE_OPCODES_DECLARATOR_B opcodes_" + basename.replaceAll("[^0-9A-Za-z]", "_") + "[] MUSE_OPCODES_DECLARATOR_C = { " + opcodesToArray(opcodes) + " };\n"));
          {
            for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
              fos.write(PartsUtils.str2arr("// " + entry.getKey() + ": " + entry.getValue() + " bytes\n"));
            }
          }
          fos.close();
        }
        
        {
          FileOutputStream fos = (new FileOutputStream(basename + ".bin"));
          fos.write(opcodes);
          fos.close();
        }
        
        MUSE.trace("===== ENTER COMPILED PROGRAM =====");
        MUSE.trace_noln(assembly);
        MUSE.trace("===== LEAVE COMPILED PROGRAM =====");
        
        final int      stack_length = Integer.parseInt(args[argi++]);
        final int      unwind_length = Integer.parseInt(args[argi++]);
        
        final int code_memory_offset = 0x0C000000;
        final int code_memory_length = ((opcodes.length + 3) / 4);
        
        final int data_memory_offset = 0x0D000000;
        final int data_memory_length = Integer.parseInt(args[argi++]);
        
        final MUSE.Model.MachineParameters parameters = (new MUSE.Model.MachineParameters(hardcoded_jump_target_bytes, hardcoded_ifun_target_bytes, stack_length, unwind_length, code_memory_offset, code_memory_length, data_memory_offset, data_memory_length, options));
        
        final int argj = argi;
        
        final MUSE.Model.MachineEnvironment environment =
          (new MUSE.Model.MachineEnvironment()
            {
              int argi = argj;
              
              public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
              {
                switch (((int)(magic))) {
                case 0:
                  {
                    int r = bridge.r_get();
                    
                    MUSE.trace("BIOS: program querying machine parameter " + MUSE.hexify(r) + " (" + r + ")");
                    
                    switch (r) {
                    case 0:
                      {
                        bridge.r_put(code_memory_offset);
                        break;
                      }
                      
                    case 1:
                      {
                        bridge.r_put(code_memory_length);
                        break;
                      }
                      
                    case 2:
                      {
                        bridge.r_put(data_memory_offset);
                        break;
                      }
                      
                    case 3:
                      {
                        bridge.r_put(data_memory_length);
                        break;
                      }
                      
                    default:
                      {
                        return more.onTrap(thread, bridge, magic);
                      }
                    }
                    
                    MUSE.trace("BIOS: machine parameter query resulting in " + MUSE.hexify(r) + " (" + r + ")");
                    
                    break;
                  }
                  
                  /*
                    OLD: all of the following can be emulated in
                    testing using the new "reaction" mechanism;
                    therefore, query machine parameter is the only
                    defined BIOS operation
                  */
                  
                  /*
                case 1:
                  {
                    trace("BIOS: program exited");
                    
                    return false;
                  }
                  
                case 2:
                  {
                    int a = ((argi < args.length) ? (Integer.parseInt(args[argi++])) : 0);
                    trace("BIOS: program reading integer " + hexify(a) + " (" + a + ")");
                    state.r = a;
                    break;
                  }
                  
                case 3:
                  {
                    trace("BIOS: program writing integer " + hexify(state.r) + " (" + state.r + ")");
                    break;
                  }
                  */
                  
                default:
                  {
                    return more.onTrap(thread, bridge, magic);
                  }
                }
                
                return true;
              }
            });
        
        final ArrayList<MUSE.Model.Trace.Event> events = (new ArrayList<MUSE.Model.Trace.Event>());
        Throwable cause = null;
        
        try {
          MUSE.Model.MachineBridge bridge = (new MUSE.Model.MachineBridge(opcodes, parameters, environment, (new MUSE.Model.Trace.Collector.ToArrayList(events))));
          MUSE.Model.MachineState state = (new MUSE.Model.MachineState(bridge));
          state.execute(null, 100000);
          if (!bridge.h_get()) throw (new RuntimeException("MUSE: maximum execution trace length reached"));
        } catch (Throwable e) {
          cause = e;
          
          final String txt = MUSE.depict(e);
          
          events.add
            ((new MUSE.Model.Trace.Event()
              {
                public Level getLevel() { return Level.FLOW; }
                public int getDepth() { return -1; }
                public String depict(MUSE.Model.Trace.SymbolTable table) { return txt; }
                public void effect(MUSE.Model.MachineBridge bridge) { }
              }));
        }
        
        {
          ObjectOutputStream oos = (new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(basename + ".evt"))));
          oos.writeObject(parameters);
          oos.writeObject(opcodes);
          oos.writeObject(programContainer.getSymbolTable());
          oos.writeObject(events);
          oos.close();
        }
        
        if (cause != null) {
          throw (new RuntimeException(cause));
        }
      } catch (Throwable e) {
        throw MUSE.fatal(e);
      }
    }
    
    public static void main(int argi, String[] args, final MUSE.Model.MachineEnvironment auto)
    {
      final BufferedReader reaction;
      
      try {
        reaction = (new BufferedReader(new InputStreamReader(new FileInputStream(args[argi++]))));
      } catch (IOException e) {
        throw (new RuntimeException(e));
      }
      
      exec
        (argi,
         args,
         (new MUSE.Model.MachineEnvironment()
           {
             final Pattern WORDS = Pattern.compile("\\s+");
             final Pattern COMMENT = Pattern.compile("#.*");
             
             final int[] pointer = (new int[1]);
             
             int lineo = 0;
             
             public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic)
             {
               try {
                 /* first see if the automatic handler accepts the trap signal */
                 {
                   if (auto.onTrap(thread, bridge, magic)) {
                     return true;
                   }
                 }
                 
                 while (true) {
                   lineo++;
                   String line = reaction.readLine();
                   
                   if (line == null) throw (new RuntimeException("BIOS: reaction: unexpected one-of-file"));
                   
                   String[] words = WORDS.split(COMMENT.matcher(line).replaceFirst(""));
                   
                   {
                     ArrayList<String> tokens = (new ArrayList<String>());
                     
                     for (String word : WORDS.split(COMMENT.matcher(line).replaceFirst(""))) {
                       if (!(word.isEmpty())) {
                         tokens.add(word);
                       }
                     }
                     
                     words = tokens.toArray((new String[0]));
                   }
                   
                   if (words.length > 0) {
                     String cmd = words[0];
                     int[] args = (new int[(words.length - 1)]);
                     
                     for (int i = 0; i < args.length; i++) {
                       args[i] = MUSE.Compiler.parseIntegerLiteral(words[(i + 1)]);
                     }
                     
                     int argi = 0;
                     
                     /****/ if (cmd.equals("eg")) { /* expect magic */
                       if (magic != args[argi++]) throw (new RuntimeException("BIOS: reaction: unexpected magic value " + magic + " on line " + lineo));
                     } else if (cmd.equals("er")) { /* expect register */
                       int r;
                       if ((r = bridge.r_get()) != args[argi++]) throw (new RuntimeException("BIOS: reaction: unexpected register value " + r));
                     } else if (cmd.equals("em")) { /* expect memory */
                       int v;
                       if ((v = bridge.a_get(args[argi++])) != args[argi++]) throw (new RuntimeException("BIOS: reaction: unexpected memory value " + v + " on line " + lineo));
                     } else if (cmd.equals("es")) { /* expect stack */
                       int v;
                       if ((v = bridge.s_get(args[argi++])) != args[argi++]) throw (new RuntimeException("BIOS: reaction: unexpected stack value " + v + " on line " + lineo));
                     } else if (cmd.equals("ar")) { /* assign register */
                       bridge.r_put(args[argi++]);
                     } else if (cmd.equals("am")) { /* assign memory */
                       bridge.a_put(args[argi++], args[argi++]);
                     } else if (cmd.equals("sp")) { /* set pointer */
                       pointer[0] = args[argi++];
                     } else if (cmd.equals("xp")) { /* adjust pointer */
                       pointer[0] += args[argi++];
                     } else if (cmd.equals("mp")) { /* multiply pointer */
                       pointer[0] *= args[argi++];
                     } else if (cmd.equals("dp")) { /* divide pointer */
                       pointer[0] /= args[argi++];
                     } else if (cmd.equals("lp")) { /* load pointer */
                       pointer[0] = bridge.s_get(args[argi++]);
                     } else if (cmd.equals("pp")) { /* pointer from pointer */
                       pointer[0] = bridge.a_get(pointer[0]);
                     } else if (cmd.equals("ep")) { /* expect pointer (indirect) */
                       int v;
                       if ((v = bridge.a_get(pointer[0]++)) != args[argi++]) throw (new RuntimeException("BIOS: reaction: unexpected memory value " + v + " on line " + lineo));
                     } else if (cmd.equals("ap")) { /* assign pointer (indirect) */
                       bridge.a_put(pointer[0]++, args[argi++]);
                     } else if (cmd.equals("nx")) { /* continue execution ("next") */
                       return true;
                     } else if (cmd.equals("br")) { /* clean exit ("break") */
                       bridge.h_put(true);
                       return true;
                     } else {
                       throw (new RuntimeException("BIOS: reaction: illegal command '" + cmd + "'"));
                     }
                   }
                 }
               } catch (IOException e) {
                 throw (new RuntimeException(e));
               }
             }
           }));
    }
    
    public static void main(String[] args)
    {
      main(0, args, (new MUSE.Model.MachineEnvironment() { public boolean onTrap(MUSE.Model.MachineThread thread, MUSE.Model.MachineBridge bridge, long magic) { return false; } }));
    }
  }
}
