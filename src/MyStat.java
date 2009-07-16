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
import ij.measure.Calibration;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;

public class MyStat extends ImageStatistics{

	private RoiManager rm;
	
	public MyStat(byte[]pixels,int[]hist){
		super();
		histogram=hist;
		int minThreshold=0,maxThreshold=255;		
			getRawStatistics(minThreshold,maxThreshold);
			getRawMinAndMax(minThreshold,maxThreshold);
			calculateMoments(pixels, minThreshold, maxThreshold);		
	}
public MyStat(ImagePlus imp,RoiManager rm){
	this.rm=rm;
	if(imp.getType()==imp.GRAY8)
		doCalcutions8(imp);
	else
		doCalculations(imp,256,0,0);	
}
	
void doCalcutions8(ImagePlus imp){	
	int Dim=256;
	histogram = new int[Dim];
	for(int i=0;i<Dim;i++)
		histogram[i]=0;
		float v;
	double dv, dv2, sum1=0.0, sum2=0.0, sum3=0.0, sum4=0.0;
	if(rm!=null){		
		   ImageProcessor ip = imp.getProcessor();		
		   Analyzer.setUnsavedMeasurements(false);
		   Analyzer.resetCounter();
		for(int index=0;index<rm.getCount();index++){
			Roi roiCurr =rm.getRoisAsArray()[index];
			int indexImg=rm.getSliceNumber(rm.getList().getItem(index));
			imp.setSlice(indexImg);
			imp.setRoi(roiCurr);			
			IJ.run("Measure");
	        byte[] mask = ip.getMaskArray();	        	        	        
	        int width, height;
	        int rx, ry, rw, rh;
	        double pw, ph;	        
	        width = ip.getWidth();
	        height = ip.getHeight();
	        Rectangle roi = ip.getRoi();
	        if (roi != null) {
	            rx = roi.x;
	            ry = roi.y;
	            rw = roi.width;
	            rh = roi.height;
	        } else {
	            rx = 0;
	            ry = 0;
	            rw = width;
	            rh = height;
	        }
	        
	        pw = 1.0;
	        ph = 1.0;
	        roiX = rx*pw;
	        roiY = ry*ph;
	        roiWidth = rw*pw;
	        roiHeight = rh*ph;	
	        for (int y=ry, my=0; y<(ry+rh); y++, my++) {
				int i = y * width + rx;
				int mi = my * rw;
				for (int x=rx; x<(rx+rw); x++) {
					if (mask==null || mask[mi++]!=0) {
						if(imp.getRoi().contains(x,y)){
						v = ip.getPixelValue(x,y);
						dv = v+Double.MIN_VALUE;
						dv2 = dv*dv;
						sum1 += dv;
						sum2 += dv2;
						sum3 += dv*dv2;
						sum4 += dv2*dv2;						
						histogram[(int) v]++;
						if (v<min) min= v;
						if (v>max) max= v;
						}
					}
					i++;
				}
			}	
	       // ip.reset(ip.getMask());
	        IJ.showProgress((double)index/rm.getCount());
			IJ.showStatus("Finding Min/Max 16 bits: "+index+"/"+rm.getCount());
		}
		int count;		
		double sum = 0.0;	
		for (int i=0; i<Dim; i++) {
			count = histogram[i];
			pixelCount += count;
			sum += (double)i*count;				
			if (count>maxCount) {
				maxCount = count;
				mode = i;
			}
		}
		area = pixelCount;
		mean = sum/pixelCount;
		umean = mean;
		dmode = mode;	
		histMin = 0.0;
		histMax = 255.0;
		double mean2 = mean*mean;
		double variance = sum2/pixelCount - mean2;		
		stdDev=Math.sqrt(variance);
		skewness = ((sum3 - 3.0*mean*sum2)/pixelCount + 2.0*mean*mean2)/(variance*stdDev);
		kurtosis = (((sum4 - 4.0*mean*sum3 + 6.0*mean2*sum2)/pixelCount - 3.0*mean2*mean2)/(variance*variance)-3.0);
	}
}
void doCalculations(ImagePlus imp,  int bins, double histogramMin, double histogramMax) {
    ImageProcessor ip = imp.getProcessor();
	boolean limitToThreshold = (Analyzer.getMeasurements()&LIMIT)!=0;
	double minThreshold = -Float.MAX_VALUE;
	double maxThreshold = Float.MAX_VALUE;
    Calibration cal = imp.getCalibration();
	if (limitToThreshold && ip.getMinThreshold()!=ImageProcessor.NO_THRESHOLD) {
		minThreshold=cal.getCValue(ip.getMinThreshold());
		maxThreshold=cal.getCValue(ip.getMaxThreshold());
	}
	nBins = bins;
	histMin = histogramMin;
	histMax = histogramMax;
    //float[] cTable = imp.getCalibration().getCTable();
    histogram = new int[nBins];
    double  dv, dv2, v ,sum1 = 0 ,sum2=0, sum3=0 , sum4=0;
    int width, height;
    int rx, ry, rw, rh;
    double pw=1.0, ph=1.0;
    double roiMin = Double.MAX_VALUE;
	double roiMax = -Double.MAX_VALUE;
	 Analyzer.setUnsavedMeasurements(false);
	   Analyzer.resetCounter();
    for(int index=0;index<rm.getCount();index++){    	
		Roi roiCurr =rm.getRoisAsArray()[index];
		int indexImg=rm.getSliceNumber(rm.getList().getItem(index));
		imp.setSlice(indexImg);
		imp.setRoi(roiCurr);
		IJ.run("Measure");
		byte[] mask = ip.getMaskArray();
		width = ip.getWidth();
		height = ip.getHeight();
		Rectangle roi = ip.getRoi();
		if (roi != null) {
			rx = roi.x;
			ry = roi.y;
			rw = roi.width;
			rh = roi.height;
		} else {
			rx = 0;
			ry = 0;
			rw = width;
			rh = height;
		}
		roiX = rx*pw;
		roiY = ry*ph;
		roiWidth = rw*pw;
		roiHeight = rh*ph;
		boolean fixedRange = histMin!=0 || histMax!=0.0;
		// calculate min and max
		for (int y=ry, my=0; y<(ry+rh); y++, my++) {
			int i = y * width + rx;
			int mi = my * rw;
			for (int x=rx; x<(rx+rw); x++) {
				if (mask==null || mask[mi++]!=0) {			
					if(imp.getRoi().contains(x,y)){						
						v = ip.getPixelValue(x,y);
						if (v>=minThreshold && v<=maxThreshold) {
							if (v<roiMin) roiMin = v;
							if (v>roiMax) roiMax = v;
						}
					}
				}
				i++;		
			}
		}
		if(roiMin<min) min = roiMin;
		if(roiMax>max) max = roiMax;
//		if (fixedRange) {
//			if (min<histMin) min = histMin;
//			if (max>histMax) max = histMax;
//		} else {
//			histMin = min; 
//			histMax =  max;
//		}	
		IJ.showStatus(" Finding 16 bits Min/Max: "+index+"/"+rm.getCount());
    }
	histMin = min; 
    histMax =  max;
    // Generate histogram
    double scale = nBins/( histMax-histMin);
    pixelCount = 0;
    int index;
    for(int indexR=0;indexR<rm.getCount();indexR++){
    	IJ.showStatus(" Calculate Histogram: "+indexR+"/"+rm.getCount());
		Roi roiCurr =rm.getRoisAsArray()[indexR];
		int indexImg=rm.getSliceNumber(rm.getList().getItem(indexR));
		imp.setSlice(indexImg);
		imp.setRoi(roiCurr);	
        //ip.setCalibrationTable(cTable);
        byte[] mask = ip.getMaskArray();
		width = ip.getWidth();
		height = ip.getHeight();
		Rectangle roi = ip.getRoi();
		if (roi != null) {
			rx = roi.x;
			ry = roi.y;
			rw = roi.width;
			rh = roi.height;
		} else {
			rx = 0;
			ry = 0;
			rw = width;
			rh = height;
		}
		roiX = rx*pw;
		roiY = ry*ph;
		roiWidth = rw*pw;
		roiHeight = rh*ph;
        for (int y=ry, my=0; y<(ry+rh); y++, my++) {
            int i = y * width + rx;
            int mi = my * rw;
            for (int x=rx; x<(rx+rw); x++) {
                if (mask==null || mask[mi++]!=0) {                 	
                	if(imp.getRoi().contains(x,y)){
                		v = ip.getPixelValue(x,y);
                		if (v>=minThreshold && v<=maxThreshold && v>=histMin && v<=histMax) {
                			pixelCount++;                	
                			dv = v+Double.MIN_VALUE;
                			dv2 = dv*dv;
                			sum1 += dv;
                			sum2 += dv2;
                			sum3 += dv*dv2;
                			sum4 += dv2*dv2;
                			index = (int)(scale*(v-histMin));
                			if (index>=nBins)
							index = nBins-1;
                			histogram[index]++;
                			}
                	}
                	i++;
                }
            }
        }
    }
    area = pixelCount*pw*ph;
    mean = sum1/pixelCount;
    //calculateStdDev(pixelCount, sum1, sum2);
    histMin = cal.getRawValue(histMin); 
    histMax =  cal.getRawValue(histMax);
    binSize = (histMax-histMin)/nBins;
    int bits = imp.getBitDepth();
    if (histMin==0.0 && histMax==256.0 && (bits==8||bits==24))
    	histMax = 255.0;
   dmode = getMode(cal);
	umean = mean;	
	double mean2 = mean*mean;
	double variance = sum2/pixelCount - mean2;		
	stdDev=Math.sqrt(variance);
	skewness = ((sum3 - 3.0*mean*sum2)/pixelCount + 2.0*mean*mean2)/(variance*stdDev);
	kurtosis = (((sum4 - 4.0*mean*sum3 + 6.0*mean2*sum2)/pixelCount - 3.0*mean2*mean2)/(variance*variance)-3.0);
}


double getMode(Calibration cal) {
    int count;
    maxCount = 0;
    for (int i=0; i<nBins; i++) {
        count = histogram[i];
        if (count > maxCount) {
            maxCount = count;
            mode = i;
        }
    }
    double tmode = histMin+mode*binSize;
    if (cal!=null) tmode = cal.getCValue(tmode);
   return tmode;
}

void getRawStatistics(int minThreshold, int maxThreshold) {
	int count;
	double value;
	double sum = 0.0;
	double sum2 = 0.0;
	
	for (int i=minThreshold; i<=maxThreshold; i++) {
		count = histogram[i];
		pixelCount += count;
		sum += (double)i*count;
		value = i;
		sum2 += (value*value)*count;
		if (count>maxCount) {
			maxCount = count;
			mode = i;
		}
	}
	area = pixelCount*pw*ph;
	mean = sum/pixelCount;
	umean = mean;
	dmode = mode;
	calculateStdDev(pixelCount, sum, sum2);
	histMin = 0.0;
	histMax = 255.0;
}
	
	void getRawMinAndMax(int minThreshold, int maxThreshold) {
		int min = minThreshold;
		while ((histogram[min] == 0) && (min < 255))
			min++;
		this.min = min;
		int max = maxThreshold;
		while ((histogram[max] == 0) && (max > 0))
			max--;
		this.max = max;
	}
	
	void calculateMoments(byte[] pixels,  int minThreshold, int maxThreshold) {
		int v, i;
		double dv, dv2, sum1=0.0, sum2=0.0, sum3=0.0, sum4=0.0;
		for (i=0;i<pixels.length;i++){
					v = pixels[i]&255;
					if (v>=minThreshold&&v<=maxThreshold) {
						dv = v+Double.MIN_VALUE;
						dv2 = dv*dv;
						sum1 += dv;
						sum2 += dv2;
						sum3 += dv*dv2;
						sum4 += dv2*dv2;					
			}
	    double mean2 = mean*mean;
	    double variance = sum2/pixelCount - mean2;
	    double sDeviation = Math.sqrt(variance);
	    skewness = ((sum3 - 3.0*mean*sum2)/pixelCount + 2.0*mean*mean2)/(variance*sDeviation);
	    kurtosis = (((sum4 - 4.0*mean*sum3 + 6.0*mean2*sum2)/pixelCount - 3.0*mean2*mean2)/(variance*variance)-3.0);
		}
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
