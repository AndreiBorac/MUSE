/***
 * VIEW.java
 * copyright (c) 2013 by andrei borac
 ***/

package zs42.muse.view;

import zs42.muse.*;

import zs42.parts.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

public class VIEW
{
  static class APP
  {
    final MUSE.Model.MachineBridge bridge;
    
    final MUSE.Model.Trace.SymbolTable table;
    final ArrayList<MUSE.Model.Trace.Event> events;
    
    final EnumMap<MUSE.Model.Trace.Event.Level, ArrayList<MUSE.Model.Trace.Event>> levels = (new EnumMap<MUSE.Model.Trace.Event.Level, ArrayList<MUSE.Model.Trace.Event>>(MUSE.Model.Trace.Event.Level.class));
    
    APP(MUSE.Model.MachineBridge bridge, MUSE.Model.Trace.SymbolTable table, ArrayList<MUSE.Model.Trace.Event> events)
    {
      this.bridge = bridge;
      
      this.table = table;
      this.events = events;
      
      for (MUSE.Model.Trace.Event.Level level : MUSE.Model.Trace.Event.Level.values()) {
        levels.put(level, (new ArrayList<MUSE.Model.Trace.Event>()));
      }
      
      for (MUSE.Model.Trace.Event event : events) {
        for (MUSE.Model.Trace.Event.Level level : MUSE.Model.Trace.Event.Level.values()) {
          if (level.ordinal() <= event.getLevel().ordinal()) {
            levels.get(level).add(event);
          }
        }
      }
    }
    
    void newFrame(String label, int w, int h, F1<Void, Container> populate)
    {
      JFrame frame = (new JFrame(label));
      
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      
      populate.invoke(frame.getContentPane());
      
      frame.setSize(w, h);
      frame.setVisible(true);
    }
    
    void startUI(final String title, final int frame_H, final int frame_W)
    {
      abstract class TriggerableListModel extends AbstractListModel<String>
      {
        protected void trigger()
        {
          fireContentsChanged(this, 0, getSize());
        }
      }
      
      class EventArrayListTriggerableListModel extends TriggerableListModel
      {
        ArrayList<MUSE.Model.Trace.Event> contents;
        
        EventArrayListTriggerableListModel(ArrayList<MUSE.Model.Trace.Event> contents)
        {
          this.contents = contents;
        }
        
        public int getSize()
        {
          return contents.size();
        }
        
        public String getElementAt(int index)
        {
          MUSE.Model.Trace.Event event = contents.get(index);
          
          return (event.getLevel().getLeader() + event.depict(table));
        }
      }
      
      final TriggerableListModel list_model_stack =
        (new TriggerableListModel()
          {
            public int getSize()
            {
              return 8;
            }
            
            public String getElementAt(int rel)
            {
              return ("[" + rel + "]: " + MUSE.hexify(bridge.s_get(rel)));
            }
          });
      
      final TriggerableListModel list_model_data =
        (new TriggerableListModel()
          {
            final ArrayList<String> visual = (new ArrayList<String>());
            
            protected void trigger()
            {
              visual.clear();
              
              int p = -1;
              
              for (int i = bridge.par.data_memory_offset, l = (bridge.par.data_memory_offset + bridge.par.data_memory_length); i < l; i++) {
                int v = bridge.a_get(i);
                
                if (v != p) {
                  visual.add(("[" + MUSE.hexify(i) + "]: " + MUSE.hexify(v)));
                }
                
                p = v;
              }
              
              super.trigger();
            }
            
            public int getSize()
            {
              return visual.size();
            }
            
            public String getElementAt(int index)
            {
              return visual.get(index);
            }
          });
      
      final EventArrayListTriggerableListModel list_model_events_atom = (new EventArrayListTriggerableListModel(levels.get(MUSE.Model.Trace.Event.Level.ATOM)));
      final EventArrayListTriggerableListModel list_model_events_inst = (new EventArrayListTriggerableListModel(levels.get(MUSE.Model.Trace.Event.Level.INST)));
      final EventArrayListTriggerableListModel list_model_events_flow = (new EventArrayListTriggerableListModel(levels.get(MUSE.Model.Trace.Event.Level.FLOW)));
      
      final Font font = (new Font(Font.MONOSPACED, Font.BOLD, 12));
      
      final JList<String> list_stack =
        (new JList<String>(list_model_stack)
          {
            {
              setFont(font);
              
              setPrototypeCellValue("M");
            }
          });
      
      final JList<String> list_data =
        (new JList<String>(list_model_data)
          {
            {
              setFont(font);
              
              setPrototypeCellValue("M");
            }
          });
      
      final boolean[] list_originating = (new boolean[] { true });
      
      class JListOfEvents extends JList<String>
      {
        final MUSE.Model.Trace.Event.Level local_level;
        final EnumMap<MUSE.Model.Trace.Event.Level, JListOfEvents> peers;
        
        JListOfEvents(ListModel<String> model, MUSE.Model.Trace.Event.Level local_level_shadow, EnumMap<MUSE.Model.Trace.Event.Level, JListOfEvents> peers_shadow)
        {
          super(model);
          
          local_level = local_level_shadow;
          peers = peers_shadow;
          
          peers.put(local_level, this);
          
          setFont(font);
          
          setPrototypeCellValue("M");
          
          addListSelectionListener
            (new ListSelectionListener()
              {
                public void valueChanged(ListSelectionEvent e)
                {
                  if (!list_originating[0]) return;
                  
                  try {
                    list_originating[0] = false;
                    
                    for (int index : (new int[] { e.getFirstIndex(), e.getLastIndex() })) {
                      if (isSelectedIndex(index)) {
                        {
                          MUSE.Model.Trace.Event local_event = levels.get(local_level).get(index);
                          
                          // update the selection of each peer via selectionUpdate (including self)
                          {
                            for (MUSE.Model.Trace.Event.Level level : MUSE.Model.Trace.Event.Level.values()) {
                              peers.get(level).selectionUpdate(local_level, local_event);
                            }
                          }
                          
                          // reset bridge and replay up to selected event
                          {
                            bridge.reset();
                            
                            for (MUSE.Model.Trace.Event event : events) {
                              event.effect(bridge);
                              if (event == local_event) break;
                            }
                          }
                          
                          // invalidate views
                          list_model_stack.trigger();
                          list_model_data.trigger();
                        }
                      }
                    }
                  } finally {
                    list_originating[0] = true;
                  }
                }
              });
          
          addMouseListener
            ((new MouseAdapter()
              {
                public void mouseClicked(MouseEvent e)
                {
                  if (e.getClickCount() == 2) {
                    int index = locationToIndex(e.getPoint());
                    
                    final MUSE.Model.Trace.Event event = levels.get(local_level).get(index);
                    
                    final String message = (event.getLevel().getLeader() + event.depict(table)).replace('~', '\n');
                    
                    (new JFrame("Event Details")
                      {
                        {
                          setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                          
                          JTextArea text = (new JTextArea(message));
                          
                          text.setFont(font);
                          text.setEditable(false);
                          
                          getContentPane().add(text);
                          
                          pack();
                          
                          setVisible(true);
                        }
                      }).getClass();
                  }
                }
              }));
        }
        
        void selectionUpdate(MUSE.Model.Trace.Event.Level origin_level, MUSE.Model.Trace.Event origin_event)
        {
          // if we're at a lower level, select the same event
          // 
          // if we're at a higher level, deselect any selected entry
          {
            /****/ if (local_level.ordinal() < origin_level.ordinal()) {
              ArrayList<MUSE.Model.Trace.Event> local_events = levels.get(local_level);
              int local_index = local_events.indexOf(origin_event);
              
              clearSelection();
              setSelectionInterval(local_index, local_index);
              
              int spread = ((getLastVisibleIndex() - getFirstVisibleIndex()) >> 1);
              
              ensureIndexIsVisible(Math.max((local_index - spread), 0));
              ensureIndexIsVisible(Math.min((local_index + spread), (local_events.size() - 1)));
              ensureIndexIsVisible(local_index);
            } else if (local_level.ordinal() > origin_level.ordinal()) {
              clearSelection();
            }
          }
        }
      }
      
      final EnumMap<MUSE.Model.Trace.Event.Level, JListOfEvents> peers = (new EnumMap<MUSE.Model.Trace.Event.Level, JListOfEvents>(MUSE.Model.Trace.Event.Level.class));
      
      final JList<String> list_events_atom = (new JListOfEvents(list_model_events_atom, MUSE.Model.Trace.Event.Level.ATOM, peers));
      final JList<String> list_events_inst = (new JListOfEvents(list_model_events_inst, MUSE.Model.Trace.Event.Level.INST, peers));
      final JList<String> list_events_flow = (new JListOfEvents(list_model_events_flow, MUSE.Model.Trace.Event.Level.FLOW, peers));
      
      final JSplitPane[] splitpanes = (new JSplitPane[4]);
      
      newFrame
        (title, frame_W, frame_H,
         (new F1<Void, Container>()
          {
            public Void invoke(Container panel)
            {
              Component sp_atom = (new JScrollPane(list_events_atom));
              Component sp_inst = (new JScrollPane(list_events_inst));
              Component sp_flow = (new JScrollPane(list_events_flow));
              
              Component sp_stack = (new JScrollPane(list_stack));
              
              Component sp_data  = (new JScrollPane(list_data));
              
              splitpanes[0] = (new JSplitPane(JSplitPane.VERTICAL_SPLIT, sp_flow, sp_inst));
              splitpanes[1] = (new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitpanes[0], sp_atom));
              
              splitpanes[2] = (new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sp_stack, sp_data));
              
              splitpanes[3] = (new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitpanes[1], splitpanes[2]));
              
              splitpanes[0].setBorder(null);
              splitpanes[1].setBorder(null);
              splitpanes[2].setBorder(null);
              
              panel.add(splitpanes[3]);
              
              list_events_flow.addKeyListener
                ((new KeyAdapter()
                  {
                    public void keyTyped(KeyEvent e)
                    {
                      int selected = list_events_flow.getSelectedIndex();
                      
                      if (selected == -1) return;
                      
                      MUSE.Model.Trace.Event point = list_model_events_flow.contents.get(selected);
                      
                      int target = -1;
                      
                      switch (e.getKeyChar()) {
                      case 'e': /* enclosing */
                        {
                          for (int i = 0, l = list_model_events_flow.contents.size(); i < l; i++) {
                            MUSE.Model.Trace.Event event = list_model_events_flow.contents.get(i);
                            
                            if (event.getDepth() == (point.getDepth() - 1)) {
                              if (i < selected) {
                                target = i;
                              }
                            }
                          }
                          
                          break;
                        }
                        
                      case 'p': /* previous */
                        {
                          for (int i = 0, l = list_model_events_flow.contents.size(); i < l; i++) {
                            MUSE.Model.Trace.Event event = list_model_events_flow.contents.get(i);
                            
                            if (event.getDepth() == point.getDepth()) {
                              if (i < selected) {
                                target = i;
                              }
                            }
                          }
                          
                          break;
                        }
                        
                      case 'n': /* next */
                        {
                          for (int i = 0, l = list_model_events_flow.contents.size(); i < l; i++) {
                            MUSE.Model.Trace.Event event = list_model_events_flow.contents.get(i);
                            
                            if (event.getDepth() == point.getDepth()) {
                              if (i > selected) {
                                target = i;
                                break;
                              }
                            }
                          }
                          
                          break;
                        }
                        
                      default:
                        {
                          System.err.println("ignoring unbound key event");
                        }
                      }
                      
                      if (target != -1) {
                        list_events_flow.setSelectedIndex(target);
                        list_events_flow.ensureIndexIsVisible(target);
                      }
                    }
                  }));
              
              return null;
            }
          }));
      
      final F0<Void> revalidate = (new F0<Void>() { public Void invoke() { for (JSplitPane splitpane : splitpanes) { splitpane.invalidate(); } splitpanes[splitpanes.length - 1].validate(); return null; } });
      
      
      splitpanes[3].setDividerLocation(0.7);
      revalidate.invoke();
      
      
      splitpanes[2].setDividerLocation(0.4);
      revalidate.invoke();
      
      
      splitpanes[1].setDividerLocation((2.0 / 3.0));
      revalidate.invoke();
      
      splitpanes[0].setDividerLocation((1.0 / 2.0));
      revalidate.invoke();
    }
  }
  
  public static void main(String[] args)
  {
    try {
      int argi = 0;
      
      final int H = Integer.parseInt(args[argi++]);
      final int W = Integer.parseInt(args[argi++]);
      
      final String filename = args[argi++];
      
      final MUSE.Model.MachineBridge bridge;
      final MUSE.Model.Trace.SymbolTable table;
      final ArrayList<MUSE.Model.Trace.Event> events = (new ArrayList<MUSE.Model.Trace.Event>());
      
      {
        ObjectInputStream ois = (new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename))));
        
        MUSE.Model.MachineParameters parameters = ((MUSE.Model.MachineParameters)(ois.readObject()));
        
        byte[] opcodes = ((byte[])(ois.readObject()));
        
        table = ((MUSE.Model.Trace.SymbolTable)(ois.readObject()));
        
        bridge = (new MUSE.Model.MachineBridge(opcodes, parameters, null, (new MUSE.Model.Trace.Collector.Nullary())));
        
        ArrayList<?> list = ((ArrayList<?>)(ois.readObject()));
        
        for (Object item : list) {
          events.add(((MUSE.Model.Trace.Event)(item)));
        }
        
        ois.close();
      }
      
      {
        SwingUtilities.invokeLater
          ((new Runnable()
            {
              public void run()
              {
                (new APP(bridge, table, events)).startUI(("MUSE: " + filename), H, W);
              }
            }));
      }
    } catch (Throwable e) {
      MUSE.fatal(e);
    }
  }
}
