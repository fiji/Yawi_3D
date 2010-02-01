/////////////////////////////////////////////////////////////////
//
//			Yawi3D (Yet Another Wand for ImageJ 3D)
//						SVN version
//				http://yawi3d.sourceforge.net
//
// This is the selection tool (magic wand) used on 2D slices 
// to select ROIs. It uses an algorithm based on region growing 
//
// This software is released under GPL license, you can find a 
// copy of this license at http://www.gnu.org/copyleft/gpl.html
//
//
// Last update date: 
// 	20010-02-01 
//
// Authors:
// 	Davide Coppola - dmc@dev-labs.net
//	Mario Rosario Guarracino - mario.guarracino@na.icar.cnr.it
//	Giorgio Cadoro - cat0rgi0@gmail.com
//
/////////////////////////////////////////////////////////////////

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.measure.Measurements;
import ij.plugin.MacroInstaller;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;
import ij.process.StackConverter;
import ij.process.StackStatistics;

import java.awt.Button;
import java.awt.Color;
import java.awt.Scrollbar;


import java.awt.GridBagLayout;
import java.awt.GridLayout;

import java.awt.Panel;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;



public class Yawi_3D implements PlugIn{
//    static final int WIDTH = 25;
//    static final int HEIGHT = 25;
	static final int DecPlaces=3;
	static final String macroLevelWindow="Adjust Window Level Tool";
    private ImagePlus impSrc;    

    public void run(String arg) {
    	impSrc=WindowManager.getCurrentImage();
        if (impSrc!=null){
        	String macro=
        	"macro '"+macroLevelWindow+" - T0910W T9910L'{}" +
        	"macro '"+macroLevelWindow+" Selected'{call('WindowLevelMacro.initialize');}";
        	new MacroInstaller().install(macro);
        	
//        	if(impSrc.getType()!=ImagePlus.GRAY8 && impSrc.getStackSize()>1)
//        		new StackConverter(impSrc).convertToGray8();
        	//CustomCanvas ic = new CustomCanvas(impSrc);        
        	new CustomStackWindow(impSrc,new ImageCanvas(impSrc));      
        }
    }

    class CustomStackWindow extends StackWindow implements ActionListener,MouseListener,Runnable {
    	private Istogramma histWind;    	
    	private Istogramma StackWind;
        private Button button1, button2,button3,button4,button5,button6;
        Thread thread;
        int currslice;
        boolean exit;
        ImagePlus imp8;
       
        CustomStackWindow(ImagePlus imp, ImageCanvas ic) {
        	super(imp);    
        	imp.getCanvas().addMouseListener(this);        	
        //	setSize(1280, 1024);              
       	buildPanel();
       	if(imp.getSlice()==1)
			IJ.runPlugIn(imp,"ij.plugin.Converter","8-bit");
       	else
       		createImage8();
       	thread = new Thread(this, "Stack_Region");
        thread.start();
        }  
        void buildPanel() {
        	Analyzer.setMeasurements(Measurements.SLICE|Measurements.AREA|Measurements.MEAN|Measurements.STD_DEV|Measurements.SKEWNESS|Measurements.KURTOSIS);
        	Analyzer.setPrecision(DecPlaces);
        	GridBagLayout layout=new GridBagLayout();
        	setLayout(layout);        
        	histWind= new Istogramma(imp);             
             add(histWind,new GBC(0,0,2,1).setAnchor(GBC.NORTHWEST));                         
            ImageStatistics Stkstats = new StackStatistics(imp);            
            StackWind= new Istogramma("",imp,Stkstats);
            add(StackWind,new GBC(0,1,2,1));
            Panel panel = new Panel();
            panel.setBackground(Color.WHITE);
			panel.setLayout(new GridLayout(3,2));
			((GridLayout) panel.getLayout()).setVgap(3);
			((GridLayout) panel.getLayout()).setHgap(3);
            button1 = new Button(" Find Roi ");
            button1.addActionListener(this);
            panel.add(button1);
//         add(button1,new GBC(0,4,1,1).setAnchor(GBC.NORTHWEST));
            button2 = new Button(" Clear Roi ");
           button2.addActionListener(this);
           panel.add(button2);
           button3 = new Button("LevelWindow");
           button3.addActionListener(this);
           panel.add(button3);
           button4 = new Button("Show RoiManager");
           button4.addActionListener(this);
           panel.add(button4);
           button5 = new Button("Update Results");
           button5.addActionListener(this);
           panel.add(button5);
//           button6 = new Button("Show RoiResults");
//           button6.addActionListener(this);
//           panel.add(button6);
           add(panel,new GBC(0,2,2,1).setFill(GBC.HORIZONTAL).setAnchor(GBC.WEST));
           add(ic,new GBC(2,0,1,2).setAnchor(GBC.NORTH)); 
           if (imp.getStackSize()==1)
   				this.sliceSelector = new Scrollbar(Scrollbar.HORIZONTAL, 1, 1, 1, 1); 
           add(this.sliceSelector,new GBC(2,2,1,1).setFill(GBC.HORIZONTAL).setAnchor(GBC.NORTH));                 
           pack();                          
           setVisible(true);
         }
                         
        public void actionPerformed(ActionEvent e) {
            Object b = e.getSource();
            if (b==button1) {
            	synchronized (this) {		
            		exit=false; 
            		imp8.setRoi(imp.getRoi());
            		imp8.setSlice(currslice);
            		RoiFrom_Seed roiseed=(RoiFrom_Seed) IJ.runPlugIn(imp8,"RoiFrom_Seed","Back");            		
            		if(roiseed.exec()){            		
            			RefreshStack(new MyStat(imp,RoiManager.getInstance()));            			
            			imp.setSlice(currslice);
            			RefreshRoiSlice(currslice);
            			UpdateLabelRM(true);
            		}
                /* if((new MyGrowRegion(imp).exec(false))){
                	 button4.setLabel("Hide RoiManager");   
                	 RefreshStack(new MyStat(imp,RoiManager.getInstance()));
                 }*/
            	}
//                 if(gr!=null)
//                	 gr.drawGrid(imp.getCanvas(),imp.getCurrentSlice());            	
            } else
            if(b==button2){            	
            	RoiManager rm=RoiManager.getInstance();
            	imp.killRoi();
    			if (rm!=null){ 		    
    				rm.getList().clear(); 
    				RefreshSlice();
    				RefreshStack(null);
    				}    			
            }            
            else if (b==button4){
            	RoiManager rm=RoiManager.getInstance();		    		    
    			if (rm==null) 
    				rm=(RoiManager)IJ.runPlugIn("ij.plugin.frame.RoiManager","");
    			if(rm.getCount()>0){
    					if(rm.isVisible())
    						rm.setVisible(false);				   				
    					else
    						rm.setVisible(true); 				    				 				
    			}
    			else
    				rm.setVisible(false);
    			UpdateLabelRM(rm.isVisible());
            }
            else if (b==button3){                	           
            	Toolbar.getInstance().setTool(Toolbar.getInstance().getToolId(macroLevelWindow));
            	MacroInstaller.runMacroCommand(macroLevelWindow+" Selected");
            	
            }
            else if(b==button5){
            	RoiManager rm=RoiManager.getInstance();
            	if(rm!=null && rm.getCount()>0)
            		RefreshStack(new MyStat(imp,RoiManager.getInstance()));
            	else
            		RefreshStack(null);
            	
            }
            else if(b==button6){
            	//
            }
            }
        
        private void UpdateLabelRM(boolean visible){        	
        	if(!visible)
        		button4.setLabel("Show RoiManager");
        	else
        		button4.setLabel("Hide RoiManager");          		        
        }
        public void mouseWheelMoved(MouseWheelEvent event){        
        		super.mouseWheelMoved(event);    
        		adjustmentValueChanged(null);         		
    			}
        /*Scroll Bar*/
        public void adjustmentValueChanged(AdjustmentEvent e){     
        	if (e!=null){
        		super.adjustmentValueChanged(e);
        		imp.setSlice(slice);
        	}
        	RefreshRoiSlice(imp.getCurrentSlice());        	}                      
         //   IJ.write("mousePressed: ("+offScreenX(e.getX())+","+offScreenY(e.getY())+")");
               	            
        public void RefreshStack(ImageStatistics StackStat){
        	if(StackStat!=null)
        		StackWind.refreshStack(imp,StackStat);
        	else
        		StackWind.refreshStack(imp);        		               
        }
        
        public synchronized void RefreshSlice(){        
        	if(histWind!=null){
        		histWind.refreshSlice(imp);    
        		currslice=imp.getCurrentSlice();
        	}
        }
        
        public void RefreshRoiSlice(int indexImg){        	        		       
        		RoiManager rm=RoiManager.getInstance();		        	
        		if (rm!=null){        			                	
        			imp.killRoi();
        			Roi [] aRoi= rm.getRoisAsArray();	
        			boolean findIt=false;
        			int i=0;
        			while(i<aRoi.length && !findIt){
        				if(rm.getSliceNumber(rm.getList().getItem(i))==indexImg)
        					findIt=true;
        				else
        					i++;
        			}		
        			if(findIt){										
        				imp.setRoi(aRoi[i]);
        				imp.updateAndDraw();
        				rm.select(i);
					}        		
				}    
        		RefreshSlice();
        }
		public void mouseClicked(MouseEvent e) {		
		}
		
		public synchronized void mouseEntered(MouseEvent e) {
			exit=false;
			notify();
		}
	
		public synchronized void mouseExited(MouseEvent e) {
			exit=true;
			notify();
	
		}
		public void mousePressed(MouseEvent e) {
			
		}
		public void mouseReleased(MouseEvent e) {
			RefreshSlice();
		}	         
//		 public void run() {
//		        while (!done) {		        	
//		            synchronized(this) {
//		                try {			                
//		                	if(!exit)
//		                		wait();
//		                }		                
//		                catch(InterruptedException e) {}
//		               if(currslice!=imp.getCurrentSlice())
//		                	RefreshSlice();		              
//		            }		            		            
//		        }
//		    }		 
		public void run() {
			while (!done) {
				try {Thread.sleep(100);}
				catch(InterruptedException e) {}
				if(currslice!=imp.getCurrentSlice())
				    RefreshSlice();		
			}
		}
		   public boolean close() {		        		        
		        done = true;
		        synchronized(this) {
		          notify();
		        }
		        super.close();
		        return true;
		    }
private void createImage8(){   	
		int type=imp.getType();		
		int nSlices=imp.getStackSize();
		int inc = nSlices/20;
		if (inc<1) inc = 1;	
		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());   	
		for(int i=1; i<=nSlices; i++) {
			stack.addSlice(null,imp.getStack().getProcessor(i));   			   			   		
		}
		imp8=new ImagePlus(null,stack);
		if(nSlices>1)
		new StackConverter(imp8).convertToGray8();	
	}   
}     
	

}
