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
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;

public class MyStat16 extends ImageStatistics {
	
public MyStat16(ImagePlus imp,RoiManager rm){
	super();		
	if(rm!=null){		
		   ImageProcessor ip = imp.getProcessor();		
		   Analyzer.setUnsavedMeasurements(false);
		   Analyzer.resetCounter();
		   nBins = 256;
		   int minThreshold=0,maxThreshold=65535;
		   int [] Stackhistogram= new int[65536];
//		   for(int i=0;i<Stackhistogram.length;i++)
//			   Stackhistogram[i]=0;
		for(int index=0;index<rm.getCount();index++){
			Roi roiCurr =rm.getRoisAsArray()[index];
			int indexImg=rm.getSliceNumber(rm.getList().getItem(index));
			imp.setSlice(indexImg);
			imp.setRoi(roiCurr);			
			IJ.run("Measure");
			this.width = ip.getWidth();
			this.height = ip.getHeight();
			setup(ip);											
			int[] hist = ip.getHistogram(); 			
			for(int i=0;i<Stackhistogram.length;i++)
				Stackhistogram[i]+=hist[i];				
		}
		getRawMinAndMax(Stackhistogram, minThreshold, maxThreshold);
		histMin = min;
		histMax = max;
			getStatistics(Stackhistogram, (int)min, (int)max, null);
			getMode();
			calculateMoments(imp, minThreshold, maxThreshold, rm);					
	}
}
void setup(ImageProcessor ip) {
	width = ip.getWidth();
	height = ip.getHeight();
	Rectangle roi = ip.getRoi();
	if (roi != null) {
		rx = roi.x;
		ry = roi.y;
		rw = roi.width;
		rh = roi.height;
	}
	else {
		roiX=rx = 0;
		roiY=ry = 0;
		rw = width;
		rh = height;
	}
		
		pw = 1.0;
		ph = 1.0;		 
	roiWidth = rw*pw;
	roiHeight = rh*ph;
}

void getRawMinAndMax(int[] hist, int minThreshold, int maxThreshold) {
	int min = minThreshold;
	while ((hist[min]==0) && (min<65535))
		min++;
	this.min = min;
	int max = maxThreshold;
	while ((hist[max]==0) && (max>0))
		max--;
	this.max = max;
}
void getStatistics( int[] hist, int min, int max, float[] cTable) {
	int count;
	double value;
	double sum = 0.0;
	double sum2 = 0.0;
	nBins = 256;
	if (histMin==0.0 && histMax==0.0) {
		histMin = min; 
		histMax = max;
	} else {
		if (min<histMin) min = (int)histMin;
		if (max>histMax) max = (int)histMax;
	}
	binSize = (histMax-histMin)/nBins;
	double scale = 1.0/binSize;
	int hMin = (int)histMin;
	histogram = new int[nBins]; // 256 bin histogram
	int index;
    int maxCount = 0;
			
	for (int i=min; i<=max; i++) {
		count = hist[i];
        if (count>maxCount) {
            maxCount = count;
            dmode = i;
        }
		pixelCount += count;
		value = i;
		sum += value*count;
		sum2 += (value*value)*count;
		index = (int)(scale*(i-hMin));
		if (index>=nBins)
			index = nBins-1;
		histogram[index] += count;
	}
	area = pixelCount*pw*ph;
	mean = sum/pixelCount;
	umean = mean;
	calculateStdDev(pixelCount, sum, sum2);
    if (cTable!=null)
    	dmode = cTable[(int)dmode];
}

void getMode() {
    int count;
    maxCount = 0;
    for (int i=0; i<nBins; i++) {
    	count = histogram[i];
        if (count > maxCount) {
            maxCount = count;
            mode = i;
        }
    }
	//ij.IJ.write("mode2: "+mode+" "+dmode+" "+maxCount);
}

void calculateMoments(ImagePlus imp,  int minThreshold, int maxThreshold, RoiManager rm) {	
	int i, mi, iv;
	double v, v2, sum1=0.0, sum2=0.0, sum3=0.0, sum4=0.0, xsum=0.0, ysum=0.0;	 
	ImageProcessor ip = imp.getProcessor();				
		for(int index=0;index<rm.getCount();index++){
			Roi roiCurr =rm.getRoisAsArray()[index];
			int indexImg=rm.getSliceNumber(rm.getList().getItem(index));
			imp.setSlice(indexImg);
			imp.setRoi(roiCurr);			
	short[] pixels = (short[])ip.getPixels();
	byte[] mask = ip.getMaskArray();	
	for (int y=ry,my=0; y<(ry+rh); y++,my++) {
		i = y*width + rx;
		mi = my*rw;
		for (int x=rx; x<(rx+rw); x++) {
			if (mask==null || mask[mi++]!=0) {
				iv = pixels[i]&0xffff;
				if (iv>=minThreshold&&iv<=maxThreshold) {
					v = iv;
					v2 = v*v;
					sum1 += v;
					sum2 += v2;
					sum3 += v*v2;
					sum4 += v2*v2;
					xsum += x*v;
					ysum += y*v;
				}
			}
			i++;
		}
	}
}
    double mean2 = mean*mean;
    double variance = sum2/pixelCount - mean2;
    double sDeviation = Math.sqrt(variance);
    skewness = ((sum3 - 3.0*mean*sum2)/pixelCount + 2.0*mean*mean2)/(variance*sDeviation);
    kurtosis = (((sum4 - 4.0*mean*sum3 + 6.0*mean2*sum2)/pixelCount - 3.0*mean2*mean2)/(variance*variance)-3.0);
	xCenterOfMass = xsum/sum1+0.5;
	yCenterOfMass = ysum/sum1+0.5;
//	if (cal!=null) {
//		xCenterOfMass = cal.getX(xCenterOfMass);
//		yCenterOfMass = cal.getY(yCenterOfMass, height);
	//}
}

void calculateStdDev(int n, double sum, double sum2) {
	//ij.IJ.write("calculateStdDev: "+n+" "+sum+" "+sum2);
	if (n>0) {
		stdDev = (n*sum2-sum*sum)/n;
		if (stdDev>0.0)
			stdDev = Math.sqrt(stdDev/(n-1.0));
		else
			stdDev = 0.0;
	}
	else
		stdDev = 0.0;
}
	

		
	
	public String toString() {
		return "stats[count="+pixelCount+", mean="+mean+", skwness="+skewness+", kurtosis="+kurtosis+"pixelcount="+pixelCount+"]";
	}
}