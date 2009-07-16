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
import ij.ImageStack;
import ij.gui.NewImage;
import ij.gui.YesNoCancelDialog;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.font.ImageGraphicAttribute;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Stack;

public class RoiFrom_Seed implements PlugInFilter{
	private ImagePlus imp;
	private ImageProcessor ip;
	private ImageStack stack;
	private ImagePlus GrowingStack;
//	private Stack<Integer> Pixels_Stack = new Stack<Integer>();
	private int  [] pixels_rgb;
	byte [] pixels;
	private int minThreshold=0;
	private int maxThreshold=255;
	public static int w;
	public static int  h; 
	private int currSlice;
	int indexSlice=0;
	//Testing
	private int MedCurrSlice;
	static final int LEAVING_PIXEL=6;
	private static int ITERATOR=3;
	//Attributi per l'Overlay 
	private  ArrayList<Integer>[] alPoint;
	String arg;
	
   public synchronized boolean exec() {
	   synchronized (this) {				
		boolean [][] bVisitedPixel;
		boolean bRegionGrowing=false;
		byte[]temp_ip;
		w=ip.getWidth();
		h=ip.getHeight();
		if (imp.getRoi()!= null){
		 ImageProcessor mask = imp.getRoi().getMask();
		if (mask!=null){
			try{
			if(Threshold()){
				stack= imp.getStack();
			 //inv_stack = new ImageStack(stack.getWidth(),stack.getHeight());	 
				alPoint = new ArrayList [imp.getStackSize()+1];
				bVisitedPixel=new boolean[stack.getSize()][w*h];
				MedCurrSlice=imp.getCurrentSlice();
				alPoint[MedCurrSlice]= new ArrayList<Integer>();			 	 			 
				for(int k=0;k<w*h;k++){
					int x=k%w;
					int y=(k-x)/h;
					if (imp.getRoi().contains(x, y)){						
						temp_ip=(byte[])stack.getProcessor(MedCurrSlice).getPixels();							
					 	Convert(k,temp_ip,alPoint[MedCurrSlice],bVisitedPixel[MedCurrSlice-1]);
					}					
			 }
			 IJ.showProgress(1/stack.getSize());
			 for(currSlice=MedCurrSlice+1;currSlice<=stack.getSize();++currSlice){
					 alPoint[currSlice]= new ArrayList<Integer>();					
					 temp_ip=(byte[])stack.getProcessor(currSlice).getPixels();
					 //temp_ip.smooth();					 					 
					 if(alPoint[currSlice-1]!=null)
					 for(int k: alPoint[currSlice-1]){//[0]null						 
						 if(k!=0 && !bVisitedPixel[currSlice-1][k])
							 Convert(k,temp_ip,alPoint[currSlice],bVisitedPixel[currSlice-1]);
						 //IJ.showProgress(0.5);
					 }
					 stack.getProcessor(currSlice).reset();
					 IJ.showProgress(indexSlice/stack.getSize());
			 }			 
		    for(currSlice=MedCurrSlice-1;currSlice>=1;--currSlice){
						 alPoint[currSlice]= new ArrayList<Integer>();					
							 temp_ip=(byte[])stack.getProcessor(currSlice).getPixels();
						 //temp_ip.smooth();
						 if(alPoint[currSlice+1]!=null)
						 for(int k: alPoint[currSlice+1]){//[0]null
							 if(k!=0 && !bVisitedPixel[currSlice-1][k])
								 Convert(k,temp_ip,alPoint[currSlice],bVisitedPixel[currSlice-1]);					
						 } 
						 stack.getProcessor(currSlice).reset();
						 IJ.showProgress(indexSlice/stack.getSize());
			}
		    CreateMask();
		    IJ.hideProcessStackDialog=true;
		    RoiManager rm=RoiManager.getInstance();		    		    
			if (rm==null) 
		     rm=(RoiManager)IJ.runPlugIn("ij.plugin.frame.RoiManager","");
			else
				rm.getList().clear();		
			rm.setVisible(true);
		    for(int i=1;i<=GrowingStack.getStackSize();i++){		    	
		    		for (int j=0; j<ITERATOR; j++)
		    			((ByteProcessor)GrowingStack.getStack().getProcessor(i)).dilate(1,0);
		    		for (int j=0; j<ITERATOR; j++)
		    			((ByteProcessor)GrowingStack.getStack().getProcessor(i)).erode(1,0);
		    		fill(GrowingStack.getStack().getProcessor(i),255,0);
		    		GrowingStack.setSlice(i);
		    		IJ.runPlugIn(GrowingStack,"ij.plugin.Selection","from");
		    		IJ.runPlugIn(GrowingStack,"ij.plugin.Selection","inverse");
		    		if(GrowingStack.getRoi()!=null){		    			
		    			rm.add(GrowingStack,GrowingStack.getRoi(),-1);
		    			//GrowingStack.setColor(Color.GREEN);		    
		    			//GrowingStack.getRoi().drawPixels(imp.getStack().getProcessor(i));
		    			//imp.getStack().getProcessor(i).setRoi(GrowingStack.getRoi());		    			
//		    			imp.getStack().getProcessor(i).fill()
		    			bRegionGrowing=true;
		    		}
		    }		  		    
		    //IJ.runPlugIn(GrowingStack,"ij.plugin.Selection","from");
		    //rm.add(GrowingStack,GrowingStack.getRoi(),0);
//		    for(int i=1;i<GrowingStack.getStackSize();i++){
//		    	for(int j=0;j<5;j++)
//		    }		    			    	
//             GrowingStack.show();
//			GrowingStack.updateAndDraw();
//			imp.show();
//			imp.updateAndDraw();
			//GrowingStack.show();
			GrowingStack.close();		
			return true;
			}
		}catch (OutOfMemoryError e) {
			IJ.showMessage("Out of Memory \n Selezionare un nuovo Seed");
		}		
		}else
			IJ.showMessage("Creare una selezione Ovale");
		}else{
			IJ.showMessage("Selezionare Area di Interesse!!");		
			}			
	   }
	   return false;
	}

	private boolean Threshold(){
		ImageStatistics stats = ImageStatistics.getStatistics(ip,ImagePlus.MEAN+ImagePlus.STD_DEV, null);	
		if(stats.stdDev<50){
			minThreshold= (int)(stats.mean-1.5*stats.stdDev);
			maxThreshold=(int)(stats.mean+1.5*stats.stdDev);
		}
			else{				
				YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(),
						"WrongSelection", "\tSelezione Ambigua \n Annullare per Impostare un nuovo Seed \n \t Yes per Continuare ");
				if (!d.yesPressed() )
					return false;				
			}
		return true;
	}		
//		OtsuThreshold otsu = new OtsuThreshold();
//		otsu.run(ip, stats.histogram);		
//		int iotsuThr=otsu.getThreshold();
//		IJ.write("Chebischev : MinTh "+ minThreshold + "MaxTh " + maxThreshold);
//		IJ.write("Otsu : "+otsu.getThreshold()+"Errore "+otsu.getSigma());
		
		
		
	
	// Binary fill by Gabriel Landini, G.Landini at bham.ac.uk
	// 21/May/2008
	void fill(ImageProcessor ip, int foreground, int background) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		FloodFiller ff = new FloodFiller(ip);
		ip.setColor(127);
		for (int y=0; y<height; y++) {
			if (ip.getPixel(0,y)==background) ff.fill(0, y);
			if (ip.getPixel(width-1,y)==background) ff.fill(width-1, y);
		}
		for (int x=0; x<width; x++){
			if (ip.getPixel(x,0)==background) ff.fill(x, 0);
			if (ip.getPixel(x,height-1)==background) ff.fill(x, height-1);
		}
		byte[] pixels = (byte[])ip.getPixels();
		int n = width*height;
		for (int i=0; i<n; i++) {
		if (pixels[i]==127)
			pixels[i] = (byte)background;
		else
			pixels[i] = (byte)foreground;
		}
	}
	
	private byte[] smoothImageBackup(ImageProcessor ip){
		ImageProcessor bkup=new ByteProcessor(ip.getWidth(),ip.getHeight(),(byte[])ip.getPixelsCopy(),null);
		bkup.smooth();
		return (byte[])bkup.getPixels();
	}
	
	public int Ycoord(int pos){
		if(pos<imp.getWidth()*imp.getHeight())
			return pos/imp.getHeight();
		else	
		return -1;
	}
	
	public int Xcoord(int pos){
		if(pos<imp.getWidth()*imp.getHeight())
			return pos%imp.getWidth();
		else	
		return -1;
	}
	
	public GeneralPath getPath(int indx){
		 GeneralPath path=null;
		 if(alPoint!=null && indx<=alPoint.length)
		 {
			 path=new GeneralPath();
			 for(int px : alPoint[indx]){
				 int X=Xcoord(px);
				 int Y=Ycoord(px);
				 if(X>=0 && Y >=0)
				 {
					 path.moveTo(X,Y);
					 path.lineTo(X,Y);
			 }
			 }	 
		 }
		return path; 
	}
	public void CreateMask(){
		ImageStack ImStack = imp.getStack();
		GrowingStack= NewImage.createByteImage("GrowingMask", imp.getWidth(),imp.getHeight(),imp.getStackSize(),NewImage.FILL_BLACK);
		for(int k=1;k<=ImStack.getSize();k++){
			 pixels = (byte[]) ImStack.getPixels(k);
		if(alPoint[k]!=null)
			for (int val : alPoint[k]){
			   ((byte[]) GrowingStack.getStack().getPixels(k))[val]=(byte) 255;
				}
		}
	}
	
	public int getTotPoint(){
		return alPoint.length;
	}
	public int getnPoint(int indx){
		int np=0;
		if (indx<alPoint.length)
			if(alPoint[indx]!=null)
				np=alPoint[indx].size();
		return np;		
	}
	public ArrayList<Integer>getList(int indx){
		 if(indx<=alPoint.length)
			 return alPoint[indx];
		 else
			 return null;
	}
	
	
//		private ImageProcessor ProcessImage(ImageProcessor ip,boolean ColorImage ){
//			ImageProcessor inv_ip= ip.convertToRGB();
//			inv_ip.copyBits(ip, 0, 0, Blitter.COPY);
//			pixels_rgb =(int[]) inv_ip.getPixels();
//			pixels = (byte[]) ip.getPixels();
//			if (ColorImage) 
//				setImage();
//			return inv_ip;	
//		}
		
		private void setImage(){
			for(int i=0;i<w;i++){
				int offset=i*w;
				for(int j=0;j<h;j++){
					int pos = offset+j;
					int r=(pixels_rgb[pos] & 0xff0000)>>16;
					int g=(pixels_rgb[pos] & 0x00ff00)>>8;
					int b=(pixels_rgb[pos] & 0x0000ff);
					int srcByte = pixels[pos] & 0xff;
					if (srcByte>=minThreshold && srcByte <=maxThreshold){
						g=0;
						}
					else{
						r=0;
						}
					b=0;
					pixels_rgb[pos]=((r & 0xff)<<16)+( (g&0xff)<<8) +(b&0xff);	
					}
			}			
		}
//		private void Convert(int pos ,int[]M_Src,int[]M_Copy,boolean ZBar,ArrayList<Integer> Al){
//			Pixels_Stack.clear();
//			Pixels_Stack.push(pos);			
//			int LengthLeavingPixel=0;
//			while(! Pixels_Stack.empty()){
//				int pxSrc = Pixels_Stack.pop();
//				ArrayList<Integer> Ad=Adiacenze(pxSrc,'8');
//				for (int px : Ad){
//					if((px!=pxSrc && px>=0 && px<w*h)){  //Primo controllo da eliminare in function Adiacenze
//						int [] pixRgbCopy=GetRgb(M_Copy[px]);
//						if(pixRgbCopy[0]==pixRgbCopy[1]){
//						 int [] pixRgbSrc=GetRgb(M_Src[px]);
//						if( pixRgbSrc[1]==0 && pixRgbSrc[0]!=0 /*&& !CheckAdiacenze(M2,Adiacenze(px,'4'),0)*/){	
//							if (LengthLeavingPixel>LEAVING_PIXEL){
//								ZBar=false;
//							}
//							else
//								LengthLeavingPixel++;
//							Pixels_Stack.push(px);
//							M_Copy[px]=M_Src[px];					
//							Al.add(px);
//						}else
//						if (pixRgbSrc[1]!=0 ){
//							if(!ZBar){
//								M_Copy[px]=((0 & 0xff)<<16)+( (0 & 0xff)<<8) +( 255 &0xff);								
//							}
//						}
//						}					
//					}
//				}
//				Ad.clear();
//			}		
//		}
		private void Convert(int pos ,byte[] srcImage,ArrayList<Integer> Al,boolean []bVisitedPixel){
		  Stack<Integer> stckPixel = new Stack<Integer>();
		  int [] stckZBarPixel = new int[LEAVING_PIXEL];
		  boolean bLeaving=false;
			stckPixel.push(pos);	
			bVisitedPixel[pos]=true;
			int LengthLeavingPixel=0;
			while(! stckPixel.empty()){
				int pxSrc = stckPixel.pop();
				ArrayList<Integer> Ad=Adiacenze(pxSrc,'8');
			//	IJ.write(" threshold "+minThreshold+"||"+maxThreshold);
				for (int px : Ad){
					if((px!=pxSrc && px>=0 && px<w*h)){
						int srcByte = srcImage[px] & 0xff;
						if( !bVisitedPixel[px] && ( srcByte>=minThreshold && srcByte<=maxThreshold) /*&& !CheckAdiacenze(M2,Adiacenze(px,'4'),0)*/){	
							if (LengthLeavingPixel>=LEAVING_PIXEL-1)
								bLeaving=true;
							else if(!bLeaving)
								LengthLeavingPixel++;						
							//IJ.write("Nel threshold"+px+"="+srcByte);
							stckPixel.push(px);
							if(bLeaving)
								Al.add(px);
							else
								stckZBarPixel[LengthLeavingPixel]=px;
							}
//							else
//							 IJ.write("Escluso"+px+"="+srcByte);
						bVisitedPixel[px]=true;
						
						}
					}
				}
			if(bLeaving)
				for(int i=0;i<=LengthLeavingPixel;i++)
					Al.add(stckZBarPixel[i]);
			indexSlice++;
		}
		private ArrayList<Integer> Adiacenze(int pos,char Nad){
		ArrayList<Integer> list = new ArrayList<Integer>();
		int MonoPos;
		int j=pos%w; //x
		int i=(pos-j)/h; //y 	
		if(Nad=='8')
		for(int k=-1;k<=1;k++){
			for(int v=-1;v<=1;v++){
				if(i + k >= 0 && i + k < h)
					if(j+v>=0 && j+v<w){
						 MonoPos=((i+k)*h+(j+v));
						list.add(MonoPos);
						}
				}
			}
		else if(Nad=='4'){
			for(int k=-1;k<=1;k++)
				if(k!=0){
					if(i + k >= 0 && i + k < h){
						MonoPos=((i+k)*h+j);
						list.add(MonoPos);
					}
					if(j + k >= 0 && j + k < w){
							 MonoPos=((i*k+j));
							list.add(MonoPos);
					}					
				}											
		}
		return list;
			}		
		private int[] GetRgb(int Pixel){
			int [] rgb={(Pixel & 0xff0000)>>16,(Pixel & 0x00ff00)>>8,Pixel & 0xff}; 			
			return rgb;
		}
		private boolean CheckAdiacenze(int[]Mrgb,ArrayList<Integer> listAdiacenze,int index){			
			if(index<listAdiacenze.size()){
				int pos= listAdiacenze.get(index);
				int [] pixRgb=GetRgb(Mrgb[pos]);
				if(pixRgb[1]==0 && pixRgb[0]!=0)
					return CheckAdiacenze(Mrgb, listAdiacenze, index+1);
				else
					return false;
			}
			return true;
		}
		public void run(ImageProcessor ip){
			this.ip = ip;
			if(!arg.equals("Back"))		
				exec();
		}
		public int setup(String arg, ImagePlus imp) {
			this.arg=arg;
			this.imp=imp;
			return DOES_8G+STACK_REQUIRED;
		}		
}

