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
// 	2009-07-16 
//
// Authors:
// 	Davide Coppola - dmc@dev-labs.net
//	Mario Rosario Guarracino - mario.guarracino@na.icar.cnr.it
//	Giorgio Cadoro - cat0rgi0@gmail.com
//
/////////////////////////////////////////////////////////////////
	import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.TrimmedButton;
import ij.gui.YesNoCancelDialog;
import ij.measure.Calibration;
import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

//	This plugin implements the Brightness/Contrast, Window/level and
//		Color Balance commands, all in the Image/Adjust sub-menu. It 
//		allows the user to interactively adjust the brightness  and
//		contrast of the active image. It is multi-threaded to 
//		provide a more  responsive user interface. 
//	
public class LivelloImmagine extends Panel implements Runnable,
		ActionListener, AdjustmentListener,MouseWheelListener{

		public static final String LOC_KEY = "b&c.loc";
		static final int AUTO_THRESHOLD = 5000;
		static final String[] channelLabels = {"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "All"};
		static final String[] altChannelLabels = {"Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5", "Channel 6", "All"};
		static final int[] channelConstants = {4, 2, 1, 3, 5, 6, 7};
		
		ContrastPlot plot = new ContrastPlot();
		Thread thread;
//		private static Frame instance;
			
		int minSliderValue=-1, maxSliderValue=-1, brightnessValue=-1, contrastValue=-1;
		int sliderRange = 256;
		boolean doAutoAdjust,doReset,doSet,doApplyLut,doPlotUpdate;
		
		Panel panel, tPanel;
		Button autoB, resetB, setB, applyB;
		int previousImageID;
		int previousType;
		int previousSlice = 1;
		Object previousSnapshot;
		ImageJ ij;
		double min, max;
		double previousMin, previousMax;
		double defaultMin, defaultMax;
		int contrast, brightness;
		boolean RGBImage;
		Scrollbar minSlider, maxSlider, contrastSlider, brightnessSlider;
		Label minLabel, maxLabel, windowLabel, levelLabel;
		boolean done;
		int autoThreshold;
		GridBagLayout gridbag;
		GridBagConstraints c;
		int y = 0;
		boolean windowLevel;
		Font monoFont = new Font("Monospaced", Font.PLAIN, 12);
		Font sanFont = new Font("SansSerif", Font.PLAIN, 12);
		int channels = 7; // RGBt
		Choice choice;
		boolean updatingRGBStack;
		Yawi_3D Frame;
		//private ImagePlus impSrc;
	 public LivelloImmagine() {
		 run("wl");
		}
		public void run(String arg) {
			windowLevel = arg.equals("wl");
			
			gridbag = new GridBagLayout();
			c = new GridBagConstraints();
			setLayout(gridbag);
			
			// plot
			c.gridx = 0;
			y = 0;
			c.gridy = y++;
			c.fill = GridBagConstraints.BOTH;
			c.anchor = GridBagConstraints.CENTER;
		//	c.insets = new Insets(10, 10, 0, 10);
			gridbag.setConstraints(plot, c);
			add(plot);		
			// min and max labels
			

			// min slider
	
			// max slider
			
			// brightness slider
			brightnessSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
			c.gridy = y++;
			c.insets = new Insets(windowLevel?12:2, 10, 0, 10);
			gridbag.setConstraints(brightnessSlider, c);
			add(brightnessSlider);
			brightnessSlider.addAdjustmentListener(this);
			brightnessSlider.addKeyListener(ij);		
			brightnessSlider.setUnitIncrement(1);
			brightnessSlider.setFocusable(false);
				addLabel("Level: ", levelLabel=new TrimmedLabel("        "));
				
			// contrast slider
				contrastSlider = new Scrollbar(Scrollbar.HORIZONTAL, sliderRange/2, 1, 0, sliderRange);
				c.gridy = y++;
				c.insets = new Insets(2, 10, 0, 10);
				gridbag.setConstraints(contrastSlider, c);
				add(contrastSlider);
				contrastSlider.addAdjustmentListener(this);
				contrastSlider.addKeyListener(ij);		
				contrastSlider.setUnitIncrement(1);
				contrastSlider.setFocusable(false);
			addLabel("Window: ", windowLabel=new TrimmedLabel("        "));
					// color channel popup menu
			// buttons
			int trim = IJ.isMacOSX()?20:0;
			panel = new Panel();
			panel.setLayout(new GridLayout(0,3, 0, 0));
			autoB = new TrimmedButton("Auto",trim);
			autoB.addActionListener(this);
		    autoB.addKeyListener(ij);
			panel.add(autoB);
			resetB = new TrimmedButton("Reset",trim);
			resetB.addActionListener(this);
			resetB.addKeyListener(ij);
			panel.add(resetB);
		    applyB = new TrimmedButton("Apply",trim);
			applyB.addActionListener(this);
			applyB.addKeyListener(ij);
			panel.add(applyB);
			/*setB = new TrimmedButton("Set",trim);
			setB.addActionListener(this);
			setB.addKeyListener(ij);
			panel.add(setB);*/
			c.gridy = y++;
			c.insets = new Insets(8, 5, 10, 5);
			gridbag.setConstraints(panel, c);
			add(panel);	
	 		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
			thread = new Thread(this, "wl");
			//thread.setPriority(thread.getPriority()-1);
			thread.start();
			setup();
		}
			

		void addLabel(String text, Label label2) {
			if (label2==null&&IJ.isMacOSX()) text += "    ";
			panel = new Panel();
			c.gridy = y++;
			int bottomInset = IJ.isMacOSX()?4:0;
			c.insets = new Insets(0, 10, bottomInset, 0);
			gridbag.setConstraints(panel, c);
	        panel.setLayout(new FlowLayout(label2==null?FlowLayout.CENTER:FlowLayout.LEFT, 0, 0));
			Label label= new TrimmedLabel(text);
			label.setFont(sanFont);
			panel.add(label);
			if (label2!=null) {
				label2.setFont(monoFont);
				label2.setAlignment(Label.LEFT);
				panel.add(label2);
			}
			add(panel);
		}

		void setup() {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) {
				setup(imp);
				updatePlot();
				updateLabels(imp);
				imp.updateAndDraw();
			}
		}
		
		public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
			Object source = e.getSource();
			if (source==minSlider)
				minSliderValue = minSlider.getValue();
			else if (source==maxSlider)
				maxSliderValue = maxSlider.getValue();
			else if (source==contrastSlider)
				contrastValue = contrastSlider.getValue();
			else
				brightnessValue = brightnessSlider.getValue();
			notify();
		}

		public synchronized  void actionPerformed(ActionEvent e) {//Evento Bottoni!!
			Button b = (Button)e.getSource();
			if (b==null) return;
			if (b==resetB)
				doReset = true;
			else if (b==autoB)
				doAutoAdjust = true;
			else if(b==applyB)
				doApplyLut=true;
			notify();
		}
		
		ImageProcessor setup(ImagePlus imp) {
			Roi roi = imp.getRoi();
			if (roi!=null)
				roi.endPaste();
			ImageProcessor ip = imp.getProcessor();
			int type = imp.getType();
			int slice = imp.getCurrentSlice();
			RGBImage = type==ImagePlus.COLOR_RGB;
			boolean snapshotChanged = RGBImage && previousSnapshot!=null && ((ColorProcessor)ip).getSnapshotPixels()!=previousSnapshot;
			if (imp.getID()!=previousImageID || snapshotChanged || type!=previousType || slice!=previousSlice)
				setupNewImage(imp, ip);
			previousImageID = imp.getID();
		 	previousType = type;
		 	previousSlice = slice;
		 	return ip;
		}

		void setupNewImage(ImagePlus imp, ImageProcessor ip)  {
			//IJ.write("setupNewImage");
			previousMin = min;
			previousMax = max;
		 	if (RGBImage) {
		 		ip.snapshot();
		 		previousSnapshot = ((ColorProcessor)ip).getSnapshotPixels();
		 	} else
				previousSnapshot = null;
//			double min2 = imp.getDisplayRangeMin();
//			double max2 = imp.getDisplayRangeMax();
//			if (imp.getType()==ImagePlus.COLOR_RGB)
//				{min2=0.0; max2=255.0;}
			if ((ip instanceof ShortProcessor) || (ip instanceof FloatProcessor)) {
				imp.resetDisplayRange();
				defaultMin = imp.getDisplayRangeMin();
				defaultMax = imp.getDisplayRangeMax();
			} else {
				defaultMin = 0;
				defaultMax = 255;
			}
			setMinAndMax(imp, defaultMin, defaultMax);
			min = imp.getDisplayRangeMin();
			max = imp.getDisplayRangeMax();
			if (IJ.debugMode) {
				IJ.log("min: " + min);
				IJ.log("max: " + max);
				IJ.log("defaultMin: " + defaultMin);
				IJ.log("defaultMax: " + defaultMax);
			}
			plot.defaultMin = defaultMin;
			plot.defaultMax = defaultMax;
			//plot.histogram = null;
			int valueRange = (int)(defaultMax-defaultMin);
			int newSliderRange = valueRange;
			if (newSliderRange>640 && newSliderRange<1280)
				newSliderRange /= 2;
			else if (newSliderRange>=1280)
				newSliderRange /= 5;
			if (newSliderRange<256) newSliderRange = 256;
			if (newSliderRange>1024) newSliderRange = 1024;
			double displayRange = max-min;
			if (valueRange>=1280 && valueRange!=0 && displayRange/valueRange<0.25)
				newSliderRange *= 1.6666;
			//IJ.log(valueRange+" "+displayRange+" "+newSliderRange);
			if (newSliderRange!=sliderRange) {
				sliderRange = newSliderRange;
				updateScrollBars(null, true);
			} else
				updateScrollBars(null, false);
			if (!doReset)
				plotHistogram(imp);
			autoThreshold = 0;
			if (imp.isComposite())
				IJ.setKeyUp(KeyEvent.VK_SHIFT);
		}
		
		void setMinAndMax(ImagePlus imp, double min, double max) {
				imp.setDisplayRange(min, max);
		}

		void updatePlot() {
			plot.min = min;
			plot.max = max;
			plot.repaint();
		}
		
		void updateLabels(ImagePlus imp) {
			double min = imp.getDisplayRangeMin();
			double max = imp.getDisplayRangeMax();;
			int type = imp.getType();
			Calibration cal = imp.getCalibration();
			boolean realValue = type==ImagePlus.GRAY32;
			if (cal.calibrated()) {
				min = cal.getCValue((int)min);
				max = cal.getCValue((int)max);
				if (type!=ImagePlus.GRAY16)
					realValue = true;
			}
			int digits = realValue?2:0;
			if (windowLevel) {
				//IJ.log(min+" "+max);
				double window = max-min;
				double level = min+(window)/2.0;
				windowLabel.setText(IJ.d2s(window, digits));
				levelLabel.setText(IJ.d2s(level, digits));
			} else {
				minLabel.setText(IJ.d2s(min, digits));
				maxLabel.setText(IJ.d2s(max, digits));
			}
		}

		void updateScrollBars(Scrollbar sb, boolean newRange) {// Posizionamento base o di aggiornamento ScrollBar
			if (sb==null || sb!=contrastSlider) {
				double mid = sliderRange/2;
				double c = ((defaultMax-defaultMin)/(max-min))*mid;
				if (c>mid)
					c = sliderRange - ((max-min)/(defaultMax-defaultMin))*mid;
				contrast = (int)c;
				if (contrastSlider!=null) {
					if (newRange)
						contrastSlider.setValues(contrast, 1, 0,  sliderRange);
					else
						contrastSlider.setValue(contrast);
				}
			}
			if (sb==null || sb!=brightnessSlider) {
				double level = min + (max-min)/2.0;
				double normalizedLevel = 1.0 - (level - defaultMin)/(defaultMax-defaultMin);
				brightness = (int)(normalizedLevel*sliderRange);
				if (newRange)
					brightnessSlider.setValues(brightness, 1, 0,  sliderRange);
				else
					brightnessSlider.setValue(brightness);
			}
		}
		
		int scaleDown(double v) {
			if (v<defaultMin) v = defaultMin;
			if (v>defaultMax) v = defaultMax;
			return (int)((v-defaultMin)*(sliderRange-1.0)/(defaultMax-defaultMin));
		}
		
		/** Restore image outside non-rectangular roi. */
	  	void doMasking(ImagePlus imp, ImageProcessor ip) {
			ImageProcessor mask = imp.getMask();
			if (mask!=null)
				ip.reset(mask);
		}

		void adjustMin(ImagePlus imp, ImageProcessor ip, double minvalue) {
			min = defaultMin + minvalue*(defaultMax-defaultMin)/(sliderRange-1.0);
			if (max>defaultMax)
				max = defaultMax;
			if (min>max)
				max = min;
			setMinAndMax(imp, min, max);
			if (min==max)
				setThreshold(ip);
			if (RGBImage) doMasking(imp, ip);
			updateScrollBars(minSlider, false);
		}

		void adjustMax(ImagePlus imp, ImageProcessor ip, double maxvalue) {
			max = defaultMin + maxvalue*(defaultMax-defaultMin)/(sliderRange-1.0);
			//IJ.log("adjustMax: "+maxvalue+"  "+max);
			if (min<defaultMin)
				min = defaultMin;
			if (max<min)
				min = max;
			setMinAndMax(imp, min, max);
			if (min==max)
				setThreshold(ip);
			if (RGBImage) doMasking(imp, ip);
			updateScrollBars(maxSlider, false);
		}

		void adjustBrightness(ImagePlus imp, ImageProcessor ip, double bvalue) {
			double center = defaultMin + (defaultMax-defaultMin)*((sliderRange-bvalue)/sliderRange);
			double width = max-min;
			min = center - width/2.0;
			max = center + width/2.0;
			setMinAndMax(imp, min, max);
			if (min==max)
				setThreshold(ip);
			if (RGBImage) doMasking(imp, ip);
			updateScrollBars(brightnessSlider, false);
		}

		void adjustContrast(ImagePlus imp, ImageProcessor ip, int cvalue) { //Da ScrollBar di Window..Contrasto!!
			double slope;
			double center = min + (max-min)/2.0;
			double range = defaultMax-defaultMin;
			double mid = sliderRange/2;
			if (cvalue<=mid)
				slope = cvalue/mid;
			else
				slope = mid/(sliderRange-cvalue);
			if (slope>0.0) {
				min = center-(0.5*range)/slope;
				max = center+(0.5*range)/slope;
			}
			setMinAndMax(imp, min, max);
			if (RGBImage) doMasking(imp, ip);
			updateScrollBars(contrastSlider, false);
		}

		void reset(ImagePlus imp, ImageProcessor ip) { //Button Reset dell'immagine
			if (!doPlotUpdate) {
			if ((ip instanceof ShortProcessor) || (ip instanceof FloatProcessor)) {
				imp.resetDisplayRange();
				defaultMin = imp.getDisplayRangeMin();
				defaultMax = imp.getDisplayRangeMax();
				plot.defaultMin = defaultMin;
				plot.defaultMax = defaultMax;
			}
			min = defaultMin;
			max = defaultMax;
			setMinAndMax(imp, min, max);
			updateScrollBars(null, false);
			}else
				doPlotUpdate=false;
			plotHistogram(imp);
			autoThreshold = 0;
		}

		void plotHistogram(ImagePlus imp) {
			ImageStatistics stats;
				stats = imp.getStatistics();
			Color color = Color.gray;
			plot.setHistogram(stats, color);
		}

		void apply(ImagePlus imp, ImageProcessor ip) {
			String option = null;
			if (RGBImage)
				imp.unlock();
			if (!imp.lock())
				return;
			if (imp.getType()==ImagePlus.COLOR_RGB) {
				if (imp.getStackSize()>1)
					applyRGBStack(imp);
				else {
					ip.snapshot();
					reset(imp, ip);
					imp.changes = true;
					if (Recorder.record) Recorder.record("run", "Apply LUT");
				}
				imp.unlock();
				return;
			}
			if (imp.isComposite()) {
				imp.unlock();
				((CompositeImage)imp).updateAllChannelsAndDraw();
				return;
			}
			if (imp.getType()!=ImagePlus.GRAY8) {
				IJ.beep();
				IJ.showStatus("Apply requires an 8-bit grayscale image or an RGB stack");
				imp.unlock();
				return;
			}
			int[] table = new int[256];
			int min = (int)imp.getDisplayRangeMin();
			int max = (int)imp.getDisplayRangeMax();
			for (int i=0; i<256; i++) {
				if (i<=min)
					table[i] = 0;
				else if (i>=max)
					table[i] = 255;
				else
					table[i] = (int)(((double)(i-min)/(max-min))*255);
			}
			ip.setRoi(imp.getRoi());
			if (imp.getStackSize()>1) {
				ImageStack stack = imp.getStack();
				YesNoCancelDialog d = new YesNoCancelDialog(ij,"Entire Stack?", "Apply LUT to all "+stack.getSize()+" slices in the stack?");
				if (d.cancelPressed())
					{imp.unlock(); return;}
				if (d.yesPressed()) {
					int current = imp.getCurrentSlice();
					ImageProcessor mask = imp.getMask();
					for (int i=1; i<=imp.getStackSize(); i++) {
						imp.setSlice(i);
						ip = imp.getProcessor();
						if (mask!=null) ip.snapshot();
						ip.applyTable(table);
						ip.reset(mask);
					}
					imp.setSlice(current);
					option = "stack";
				} else {
					if (ip.getMask()!=null) ip.snapshot();
					ip.applyTable(table);
					ip.reset(ip.getMask());
					option = "slice";
				}
			} else {
				if (ip.getMask()!=null) ip.snapshot();
				ip.applyTable(table);
				ip.reset(ip.getMask());
			}
			reset(imp, ip);
			imp.changes = true;
			imp.unlock();
		}

		void applyRGBStack(ImagePlus imp) {
			int current = imp.getCurrentSlice();
			int n = imp.getStackSize();
			if (!IJ.showMessageWithCancel("Update Entire Stack?",
			"Apply brightness and contrast settings\n"+
			"to all "+n+" slices in the stack?\n \n"+
			"NOTE: There is no Undo for this operation."))
				return;
	 		ImageProcessor mask = imp.getMask();
	 		updatingRGBStack = true;
			for (int i=1; i<=n; i++) {
				if (i!=current) {
					imp.setSlice(i);
					ImageProcessor ip = imp.getProcessor();
					if (mask!=null) ip.snapshot();
					setMinAndMax(imp, min, max);
					ip.reset(mask);
					IJ.showProgress((double)i/n);
				}
			}
			imp.setSlice(current);
	 		updatingRGBStack = false;
			imp.changes = true;
			if (Recorder.record)
				Recorder.record("run", "Apply LUT", "stack");
		}

		void setThreshold(ImageProcessor ip) {
			if (!(ip instanceof ByteProcessor))
				return;
			if (((ByteProcessor)ip).isInvertedLut())
				ip.setThreshold(max, 255, ImageProcessor.NO_LUT_UPDATE);
			else
				ip.setThreshold(0, max, ImageProcessor.NO_LUT_UPDATE);
		}

		void autoAdjust(ImagePlus imp, ImageProcessor ip) { //Button Auto dell'immagine!
	 		if (RGBImage)
				ip.reset();
			Calibration cal = imp.getCalibration();
			imp.setCalibration(null);
			ImageStatistics stats = imp.getStatistics(); // get uncalibrated stats
			imp.setCalibration(cal);
			int limit = stats.pixelCount/10;
			int[] histogram = stats.histogram;
			if (autoThreshold<10)
				autoThreshold = AUTO_THRESHOLD;
			else
				autoThreshold /= 2;
			int threshold = stats.pixelCount/autoThreshold;
			int i = -1;
			boolean found = false;
			int count;
			do {
				i++;
				count = histogram[i];
				if (count>limit) count = 0;
				found = count> threshold;
			} while (!found && i<255);
			int hmin = i;
			i = 256;
			do {
				i--;
				count = histogram[i];
				if (count>limit) count = 0;
				found = count > threshold;
			} while (!found && i>0);
			int hmax = i;
			Roi roi = imp.getRoi();
			if (hmax>=hmin) {
				if (RGBImage) imp.killRoi();
				min = stats.histMin+hmin*stats.binSize;
				max = stats.histMin+hmax*stats.binSize;
				if (min==max)
					{min=stats.min; max=stats.max;}
				setMinAndMax(imp, min, max);
				if (RGBImage && roi!=null) imp.setRoi(roi);
			} else {
				reset(imp, ip);
				return;
			}
			updateScrollBars(null, false);
			//if (roi!=null) { ???
			//	ImageProcessor mask = roi.getMask();
			//	if (mask!=null)
			//		ip.reset(mask);
			//}
			if (Recorder.record)
				Recorder.record("run", "Enhance Contrast", "saturated=0.5");
		}
		
		void setMinAndMax(ImagePlus imp, ImageProcessor ip) {
			min = imp.getDisplayRangeMin();
			max = imp.getDisplayRangeMax();
			Calibration cal = imp.getCalibration();
			int digits = (ip instanceof FloatProcessor)||cal.calibrated()?2:0;
			double minValue = cal.getCValue(min);
			double maxValue = cal.getCValue(max);
			int channels = imp.getNChannels();
			GenericDialog gd = new GenericDialog("Set Display Range");
			gd.addNumericField("Minimum Displayed Value: ", minValue, digits);
			gd.addNumericField("Maximum Displayed Value: ", maxValue, digits);
			gd.addCheckbox("Propagate to all open images", false);
			if (imp.isComposite())
				gd.addCheckbox("Propagate to all "+channels+" channels", false);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			minValue = gd.getNextNumber();
			maxValue = gd.getNextNumber();
			minValue = cal.getRawValue(minValue);
			maxValue = cal.getRawValue(maxValue);
			boolean propagate = gd.getNextBoolean();
			boolean allChannels = imp.isComposite()&&gd.getNextBoolean();
			if (maxValue>=minValue) {
				min = minValue;
				max = maxValue;
				setMinAndMax(imp, min, max);
				updateScrollBars(null, false);
				if (RGBImage) doMasking(imp, ip);
				if (propagate)
					IJ.runMacroFile("ij.jar:PropagateMinAndMax");
				if (allChannels) {
					int channel = imp.getChannel();
					for (int c=1; c<=channels; c++) {
						imp.setPositionWithoutUpdate(c, imp.getSlice(), imp.getFrame());
						imp.setDisplayRange(min, max);
					}
					((CompositeImage)imp).reset();
					imp.setPosition(channel, imp.getSlice(), imp.getFrame());
				}
				if (Recorder.record) {
					if (imp.getBitDepth()==32)
						Recorder.record("setMinAndMax", min, max);
					else {
						int imin = (int)min;
						int imax = (int)max;
						if (cal.isSigned16Bit()) {
							imin = (int)cal.getCValue(imin);
							imax = (int)cal.getCValue(imax);
						}
						Recorder.record("setMinAndMax", imin, imax);
					}
				}
			}
		}

		void setWindowLevel(ImagePlus imp, ImageProcessor ip) {
			min = imp.getDisplayRangeMin();
			max = imp.getDisplayRangeMax();
			Calibration cal = imp.getCalibration();
			int digits = (ip instanceof FloatProcessor)||cal.calibrated()?2:0;
			double minValue = cal.getCValue(min);
			double maxValue = cal.getCValue(max);
			//IJ.log("setWindowLevel: "+min+" "+max);
			double windowValue = maxValue - minValue;
			double levelValue = minValue + windowValue/2.0;		
			minValue = levelValue-(windowValue/2.0);
			maxValue = levelValue+(windowValue/2.0);
			minValue = cal.getRawValue(minValue);
			maxValue = cal.getRawValue(maxValue);
			if (maxValue>=minValue) {
				min = minValue;
				max = maxValue;
				setMinAndMax(imp, minValue, maxValue);
				updateScrollBars(null, false);							
			}
		}

		static final int RESET=0, AUTO=1, SET=2, APPLY=3, THRESHOLD=4, MIN=5, MAX=6, 
			BRIGHTNESS=7, CONTRAST=8, UPDATE=9;

		// Separate thread that does the potentially time-consuming processing 
		public void run() {
			while (!done) {
				synchronized(this) {
					try {wait();}
					catch(InterruptedException e) {}
				}
				doUpdate();
			}
		}

	public void doUpdate() {
			ImagePlus imp;
			ImageProcessor ip;
			int action;
			int minvalue = minSliderValue;
			int maxvalue = maxSliderValue;
			int bvalue = brightnessValue;
			int cvalue = contrastValue;
			if (doReset) action = RESET;
			else if (doAutoAdjust) action = AUTO;
			else if (doSet) action = SET;
			else if (doApplyLut) action = APPLY;
//			else if (minSliderValue>=0) action = MIN;
//			else if (maxSliderValue>=0) action = MAX;
			else if (brightnessValue>=0) action = BRIGHTNESS;
			else if (contrastValue>=0) action = CONTRAST;
			else if (doPlotUpdate) action=RESET;
			else return;
			minSliderValue = maxSliderValue = brightnessValue = contrastValue = -1;
			doReset = doAutoAdjust = doSet = doApplyLut = false;
			imp = WindowManager.getCurrentImage();
			ip = imp.getProcessor();
			//IJ.write("setup: "+(imp==null?"null":imp.getTitle()));
			switch (action) {
				case RESET:
					reset(imp, ip);
					if (Recorder.record) Recorder.record("resetMinAndMax");
					break;
				case AUTO: autoAdjust(imp, ip); break;
				case SET: if (windowLevel) setWindowLevel(imp, ip); else setMinAndMax(imp, ip); break;
				case APPLY: apply(imp, ip); break;
				case MIN: adjustMin(imp, ip, minvalue); break;
				case MAX: adjustMax(imp, ip, maxvalue); break;
				case BRIGHTNESS: adjustBrightness(imp, ip, bvalue); break;
				case CONTRAST: adjustContrast(imp, ip, cvalue); break;
			}
			updatePlot();
			updateLabels(imp);
				imp.updateChannelAndDraw();
			if (RGBImage)
				imp.unlock();
		}

		public synchronized  void itemStateChanged(ItemEvent e) {
			int index = choice.getSelectedIndex();
			channels = channelConstants[index];
			ImagePlus imp = WindowManager.getCurrentImage();			
			if (imp!=null && imp.isComposite()) {
				if (index+1<=imp.getNChannels()) 
					imp.setPosition(index+1, imp.getSlice(), imp.getFrame());
				else {
					choice.select(channelLabels.length-1);
					channels = 7;
				}
			} else
				doReset = true;
			notify();
		}

	    /** Resets this ContrastAdjuster and brings it to the front. */
	    public void updateAndDraw() {
	        previousImageID = 0;
	    }
		public synchronized void mouseWheelMoved(MouseWheelEvent e) {
			doPlotUpdate=true;
			notify();
		}

	    
	        
	} // ContrastAdjuster class


	 class ContrastPlot extends Canvas {
		
		static final int WIDTH = 256, HEIGHT=128;
		double defaultMin = 0;
		double defaultMax = 255;
		double min = 0;
		double max = 255;
		int[] histogram;
		int hmax;
		Image os;
		Graphics osg;
		Color color = Color.gray;
		
		public ContrastPlot() {
			setSize(WIDTH, HEIGHT);
		}

	    /** Overrides Component getPreferredSize(). Added to work 
	    	around a bug in Java 1.4.1 on Mac OS X.*/
	    public Dimension getPreferredSize() {
	        return new Dimension(WIDTH+1, HEIGHT+1);
	    }

		void setHistogram(ImageStatistics stats, Color color) {
			this.color = color;
			histogram = stats.histogram;
			if (histogram.length!=256)
				{histogram=null; return;}
//		for (int i=0; i<128; i++)
//				histogram[i] = (histogram[2*i]+histogram[2*i+1])/2;
			int maxCount = 0;
			int mode = 0;
			for (int i=0; i<256; i++) {
				if (histogram[i]>maxCount) {
					maxCount = histogram[i];
					mode = i;
				}
			}
			int maxCount2 = 0;
			for (int i=0; i<256; i++) {
				if ((histogram[i]>maxCount2) && (i!=mode))
					maxCount2 = histogram[i];
			}
			hmax = stats.maxCount;
			if ((hmax>(maxCount2*2)) && (maxCount2!=0)) {
				hmax = (int)(maxCount2*1.5);
				histogram[mode] = hmax;
			}
			os = null;
		}

		public void update(Graphics g) {
			paint(g);
		}

		public void paint(Graphics g) {
			int x1, y1, x2, y2;
			double scale = (double)WIDTH/(defaultMax-defaultMin);
			double slope = 0.0;
			if (max!=min)
				slope = HEIGHT/(max-min);
			if (min>=defaultMin) {
				x1 = (int)(scale*(min-defaultMin));
				y1 = HEIGHT;
			} else {
				x1 = 0;
				if (max>min)
					y1 = HEIGHT-(int)((defaultMin-min)*slope);
				else
					y1 = HEIGHT;
			}
			if (max<=defaultMax) {
				x2 = (int)(scale*(max-defaultMin));
				y2 = 0;
			} else {
				x2 = WIDTH;
				if (max>min)
					y2 = HEIGHT-(int)((defaultMax-min)*slope);
				else
					y2 = 0;
			}
			if (histogram!=null) {
				if (os==null && hmax!=0) {
					os = createImage(WIDTH,HEIGHT);
					osg = os.getGraphics();
					osg.setColor(Color.white);
					osg.fillRect(0, 0, WIDTH, HEIGHT);
					osg.setColor(Color.BLUE);
					for (int i = 0; i < WIDTH; i++)
			osg.drawLine(i, HEIGHT, i, HEIGHT - ((int)(HEIGHT * histogram[i])/hmax));

					osg.dispose();
				}
				if (os!=null) g.drawImage(os, 0, 0, this);
			} else {
				g.setColor(Color.white);
				g.fillRect(0, 0, WIDTH, HEIGHT);
			}
			g.setColor(Color.RED);
	 		g.drawLine(x1, y1, x2, y2);
	 		//g.drawLine(x2, HEIGHT-5, x2, HEIGHT);
	 		//g.drawRect(0, 0, WIDTH, HEIGHT);
	     }

	} // ContrastPlot class


	class TrimmedLabel extends Label {
		int trim = IJ.isMacOSX()?0:6;

	    public TrimmedLabel(String title) {
	        super(title);
	    }

	    public Dimension getMinimumSize() {
	        return new Dimension(super.getMinimumSize().width, super.getMinimumSize().height-trim);
	    }

	    public Dimension getPreferredSize() {
	        return getMinimumSize();
	    }

	} // TrimmedLabel class
