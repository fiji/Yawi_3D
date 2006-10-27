/////////////////////////////////////////////////////////////////
//
// Yawi2D (Yet Another Wand for ImageJ) 2D tool - version 1.3.2
//
// This is the selection tool (magic wand) used on 2D slices 
// to select ROIs. It uses an adaptive algorithm based on cromatic 
// composition of areas that segments regions with cromatic 
// similarities.
// The pluging provides a powerful implementation of the Wand 
// selection tool and it can be applied, with respect to ImageJ 
// wand, to a wider spectrum of problems.
//
// This software is released under GPL license, you can find a 
// copy of this license at http://www.gnu.org/copyleft/gpl.html
//
//
// Start date: 
// 	2004-05-05
// Last update date: 
// 	2004-12-27 
//
// Authors:
// 	Davide Coppola - dmc@dev-labs.net
//	Mario Rosario Guarracino - mario.guarracino@na.icar.cnr.it 
//	
/////////////////////////////////////////////////////////////////

import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.io.FileSaver;
import ij.measure.ResultsTable;
import ij.text.TextPanel;

import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;

import java.awt.*;
//import java.awt.Point;
import java.awt.image.*;
import java.awt.event.*;

//import java.util.Vector;
import java.util.Comparator;
import java.util.Arrays;

public class Yawi_2D implements PlugInFilter
{
	private ImagePlus img;
	private LocalCanvas canvas;
	private ImageProcessor ip;
	private ImageStack stack;
	
	//image data
	private byte[] pixels;
	private int ip_width, ip_height;
	//Threshold values
	private int lowerThreshold;
	private int upperThreshold;
	//radius of Threshold rectangle
	private int rInside;
	//edge points
	private int edgeX, edgeY;
	//initial direction of edge
	private int startDir;

	private boolean shift = false;
	private boolean alt = false;
	
	private static final int UP = 0, DOWN = 1, UP_OR_DOWN = 2, LEFT = 3, RIGHT = 4, LEFT_OR_RIGHT = 5, NA = 6;
	
	// will be increased if necessary
	private int maxPoints = 1000; 
	private int maxBROIPoints = 2000;

	// The x-coordinates of the points in the outline. 
	private int[] xpoints = new int[maxPoints];
	// The y-coordinates of the points in the outline. 
	private int[] ypoints = new int[maxPoints];

	//backup arrays
	private int[] xpoints_b;
	private int[] ypoints_b;

	// all the points that make the ROI
	private Point[] broi_points = new Point[maxBROIPoints];
	
	// The number of points in the generated outline.
	private int npoints = 0;
	private int npoints_broi = 0;

	//flag that rapresents the status of the plugin (working or paused)
	private boolean work = false;

	//flag that checks if the user click on the image when, on the beginning,  
	//the plugin is stopped 
	private boolean first_click = true;

	//obtained ROI
	private	Roi roi = null;

	private String img_name = null;	
	
	//slice indexes
	private int cur_slice = 1;
	private int prev_slice = 1;

	private boolean show_roi = false;
	private boolean show_broi = false;
	private boolean show_roi_fill = false;

	// -- compareROIs constants --
	// threshold levels
	private static int comp_tresh1 = 5;
	private static int comp_tresh2 = 10;
	private static int comp_tresh3 = 15;
	private static int comp_tresh4 = 20;
	// max points 2 ROIs can get
	private static int comp_max_points = 12;
	// points scale
	private static int comp_points_scale = 10;
	// points 
	private static int comp_p1 = 4;
	private static int comp_p2 = 3;
	private static int comp_p3 = 2;
	private static int comp_p4 = 1;

	//About and image requirements
	public int setup(String arg, ImagePlus img) 
	{
		if(arg.equals("about"))
		{
			showAbout();
			return DONE;
		}

		this.img = img;
		this.stack = img.getStack();
	
		return DOES_8G+NO_CHANGES;
	}

	//the plugin is executed
	public void run(ImageProcessor ip)
	{
		this.ip = ip;

		pixels = (byte[])ip.getPixels();
		
		//img dimensions
		ip_width = ip.getWidth();
		ip_height = ip.getHeight();

		//new canvas
		canvas = new LocalCanvas(img);

		//user have loaded a stack of images
        if (img.getStackSize()>1)
		{
			stack = img.getStack();
			new MainStackWindow(img, canvas);
			img_name = "Stack";
		}
		//single image
		else	
		{
			new MainWindow(img, canvas);
			img_name = img.getTitle();
		}

		//setting window title
		img.setTitle( img_name + " - Yawi2D - http://yawi3d.sf.net");
	}
	
	//ImageJ inside, checks just 1 pixel
	private boolean inside(int x, int y)
	{
		int value = pixels[(ip_width*y)+x] & 0xff;
		
		return value >= lowerThreshold && value <= upperThreshold;
	}

	//Yawi2D inside, checks a square area
	private boolean inside(int x, int y, int direction)
	{
		int x_a, x_b;
		int y_a, y_b;

		if((upperThreshold-lowerThreshold) < 10)
			rInside = 3;
		else
			rInside = 4;

		x_a = x_b = y_a = y_b = 0;

		//we're moving UP
		if(direction == UP) 
		{
			if(x-rInside > 0)
				x_a = x-rInside;
			else
				x_a = 0;
				
			if(x+rInside < ip_width)
				x_b = x+rInside;
			else
				x_b = ip_width-1;
				
			if(y-(rInside*2) > 0)
				y_a = y-(rInside*2);
			else
				y_a = 0;

			y_b = y;
		}

		//we're moving DOWN
		if(direction == DOWN) 
		{
			if(x-rInside > 0)
				x_a = x-rInside;
			else
				x_a = 0;

			if(x+rInside < ip_width)
		 		x_b = x+rInside;
			else
				x_b = ip_width-1;
			
			y_a = y;

			if(y+(rInside*2) < ip_height)
				y_b = y+(rInside*2);
			else
				y_b = ip_height-1;
		}

		//we're moving LEFT
		if(direction == LEFT) 
		{
			if(x-(2*rInside) > 0)
				x_a = x-(2*rInside);
			else
				x_a = 0;
				
			x_b = x;
				
			if(y-rInside > 0)
				y_a = y-rInside;
			else
				y_a = 0;

			if(y+rInside < ip_height)
				y_b = y+rInside;
			else
				y_b = ip_height-1;
		}

		//we're moving RIGHT
		if(direction == RIGHT) 
		{
			x_a = x;
		
			if(x+(2*rInside) < ip_width)
				x_b = x+(2*rInside);
			else
				x_b = ip_width-1;
				
			if(y-rInside > 0)
				y_a = y-rInside;
			else
				y_a = 0;

			if(y+rInside < ip_height)
				y_b = y+rInside;
			else
				y_b = ip_height-1;
		}
			
		int area = ((rInside*2)+1)*((rInside*2)+1);
		int insideCount = 0;
		int xp,yp;
		
		for(xp = x_a; xp <= x_b; xp++)
		{
			for(yp = y_a; yp <= y_b; yp++) 
			{
					if(inside(xp,yp))
						insideCount++;
			}
		}
			
		double min_perc;

		//small DELTAthreshold
		if((upperThreshold-lowerThreshold) < 10)
			min_perc = 0.3;	
		else	
			min_perc = 0.4;

		return ((double)insideCount)/area >= min_perc;
	}


	//return the color of a pixel located at (x,y)
	private int get_color(int x, int y)
	{
		if(x >= 0 && y >= 0 && x < ip_width && y < ip_height)
			return pixels[(ip_width*y)+x] & 0xff;
		else
			return 0;
	}

	//set the threshold of the ROI
	private void setThreshold(int x, int y)
	{
		//must be odd
		int side = 5;
		int dist = side/2;
		int color;

		int i,k;

		lowerThreshold = 255;
		upperThreshold = 0;

		for(i = (y-dist);i <= (y+dist);i++)
		{
			for(k = (x-dist);k <= (x+dist);k++)
			{
				color = get_color(k,i);

				if(color > upperThreshold)
					upperThreshold = color;
				else if(color < lowerThreshold)
					lowerThreshold = color;
			}
		}
	}

	//find ROI border
	private void autoOutline(int startX, int startY) 
	{
		edgeX = startX;
		edgeY = startY;

		int direction = 0;

		if(inside(edgeX,edgeY,RIGHT)) 
		{
			//if DELTAthreshold is very small we use the ImageJ inside 
			if((upperThreshold-lowerThreshold) < 5)
				do { edgeX++; } while(inside(edgeX,edgeY) && edgeX < ip_width);
			else
			{
				do { edgeX++; } while(inside(edgeX,edgeY,RIGHT) && edgeX < ip_width);
				//we are still into the threshold area
				if(inside(edgeX,edgeY))
					do { edgeX++; } while(inside(edgeX,edgeY) && edgeX < ip_width);
				//we are out the threshold area more than 1 pixel
				else if(!inside(edgeX-1,edgeY))
					do { edgeX--; } while(!inside(edgeX,edgeY,LEFT) && edgeX > 0);
			}
					
			//initial direction choice
			if (!inside(edgeX-1,edgeY-1))
				direction = RIGHT;
			else if (inside(edgeX,edgeY-1))
		 		direction = LEFT;
			else
		 		direction = DOWN;
		} 
		else 
		{ 
			//By now this case is not managed
		}

		//start direction setted for traceEdge
		startDir = direction; 
	}

	// Traces an object defined by lower and upper threshold values. The
	// boundary points are stored in the public xpoints and ypoints fields
	private boolean traceEdge()
	{
		int secure = 0;

		int[] table = 
		{
							// 1234 1=upper left pixel,  2=upper right, 3=lower left, 4=lower right
			NA, 			// 0000 should never happen
			RIGHT,			// 000X
			DOWN,			// 00X0
			RIGHT,			// 00XX
			UP,				// 0X00
			UP,				// 0X0X
			UP_OR_DOWN,		// 0XX0 Go up or down depending on current direction
			UP,				// 0XXX
			LEFT,			// X000
			LEFT_OR_RIGHT,  // X00X Go left or right depending on current direction
			DOWN,			// X0X0
			RIGHT,			// X0XX
			LEFT,			// XX00
			LEFT,			// XX0X
			DOWN,			// XXX0
			NA,				// XXXX Should never happen
		};

		int index;
		int newDirection;
		int x = edgeX;
		int y = edgeY;
		int direction = startDir;

		// upper left
		boolean UL = inside(x-1, y-1);	
		// upper right
		boolean UR = inside(x, y-1);	
		// lower left
		boolean LL = inside(x-1, y);	
		// lower right
		boolean LR = inside(x, y);		

		int count = 0;
		int broi_count = 0;
			
		do
		{
			index = 0;

			if(LR) index |= 1;
			if(LL) index |= 2;
			if(UR) index |= 4;
			if(UL) index |= 8;
				
			newDirection = table[index];

			//indetermination, up or down
			if(newDirection == UP_OR_DOWN) 
			{
				if(direction == RIGHT)
					newDirection = UP;
				else
					newDirection = DOWN;
			}
			
			//indetermination, left or right
			if(newDirection == LEFT_OR_RIGHT) 
			{
				if(direction == UP)
			   		newDirection = LEFT;
			 	else
				 	newDirection = RIGHT;
			}

			//error
		   	if(newDirection == NA) 
				 return false;

			// broi_points requires more Points
			if(broi_count == broi_points.length)
			{
				Point[] points_temp = new Point[maxBROIPoints * 2];

				System.arraycopy(broi_points, 0, points_temp, 0, maxBROIPoints);
				broi_points = points_temp;

				broi_points = points_temp;

				maxBROIPoints *= 2;
			}

			broi_points[broi_count] = new Point(x, y);
			broi_count++;

			//a new direction means a new selection's point
			if(newDirection != direction) 
		 	{
				xpoints[count] = x;
			 	ypoints[count] = y;
			 	count++;

				//IJ.write("TraceEdge - ADD: " + x + "," + y + "\n");

				//xpoints and ypoints need more memory
			 	if(count == xpoints.length) 
			 	{
					int[] xtemp = new int[maxPoints*2];
				 	int[] ytemp = new int[maxPoints*2];

				 	System.arraycopy(xpoints, 0, xtemp, 0, maxPoints);
				 	System.arraycopy(ypoints, 0, ytemp, 0, maxPoints);

				 	xpoints = xtemp;
				 	ypoints = ytemp;

				 	maxPoints *= 2;
				}
			}
			 
			//moving along the selected direction
		  	switch(newDirection) 
			{
				case UP:
		 	    	y = y-1;
				 	LL = UL;
				 	LR = UR;
				 	UL = inside(x-1, y-1);
				 	UR = inside(x, y-1);
				 	break;
					 
			 	case DOWN:
				 	y = y + 1;
				 	UL = LL;
				 	UR = LR;
				 	LL = inside(x-1, y);
				 	LR = inside(x, y);
				 	break;
					 
 		 		case LEFT: 
					x = x-1;
				 	UR = UL;
				 	LR = LL;
				 	UL = inside(x-1, y-1);
				 	LL = inside(x-1, y);
				 	break;
					 
			 	case RIGHT:
				 	x = x + 1;
				 	UL = UR;
				 	LL = LR;
				 	UR = inside(x, y-1);
				 	LR = inside(x, y);
				 	break;
			}
			
		  	direction = newDirection;

		 	if(secure < 10000)
				secure++;
		 	else	//traceEdge OVERFLOW!!!
				return false;

		} while ((x!=edgeX || y!=edgeY || direction!=startDir));
	
		//number of ROI points
	 	npoints = count;
		npoints_broi = broi_count;
		
		//backup ROI point 
	 	xpoints_b = new int[npoints];
	 	ypoints_b = new int[npoints];

		for(int i = 0; i < npoints ; i++)
		{
				xpoints_b[i] = xpoints[i];
				ypoints_b[i] = ypoints[i];
		}

		return true;
	}

	public void showAbout() 
	{
		IJ.showMessage("About Yawi2D...",
			"Yawi2D (Yet Another Wand for ImageJ 2D) implements a selection tool for ImageJ\n" +
			"suitable for CT scanned images.\n" + 
			"It helps in the selection of a 2D Region Of Interest (ROI) containing a lymphoma\n" + 
			"(tumor mass).");
	}

	class LocalCanvas extends ImageCanvas
	{
		LocalCanvas(ImagePlus imp) 
		{
			//ImagePlus constructor call, it needs to use keyword super 
			//because constructors are no inherited
			super(imp);
		}

		public void paint(Graphics g)
		{
			super.paint(g);
			drawOverlay(g);
		}

        void drawOverlay(Graphics g)
		{
			if(npoints_broi > 0 && show_broi == true)
			{
				g.setColor(Color.blue);

				for(int i = 0; i < npoints_broi ; i++)
					g.drawRect(screenX((int)broi_points[i].getX()), screenY((int)broi_points[i].getY()), 1, 1);
			}

			if(npoints > 0 && show_roi == true)
			{
				g.setColor(Color.cyan);

				for(int i = 0; i < npoints ; i++)
					g.drawRect(screenX(xpoints_b[i]), screenY(ypoints_b[i]), 1, 1);
			}

			if(npoints_broi > 0 && show_roi_fill == true)
			{
				Rectangle roi_rect = roi.getBounds();

				for(int x = (int)roi_rect.getX(); x < (int) (roi_rect.getX() + roi_rect.getWidth()); x++)
				{
					for(int y = (int)roi_rect.getY(); y < (int) (roi_rect.getY() + roi_rect.getHeight()); y++)
					{
						// a point inside the roi
						if(roi.contains(x, y) == true)
						{
							int value = pixels[(ip_width * y) + x] & 0xff;

							// a point inside the treshold
							if(value >= lowerThreshold && value <= upperThreshold)
								g.setColor(Color.green);
							// a point not in the treshold
							else
								g.setColor(Color.red);

							g.drawRect(screenX(x), screenY(y), 1, 1);
						}
					}
				}
			}
		}

		public void makeROI(int x, int y)
		{
			//get current slice number
			cur_slice = img.getCurrentSlice();

			//user have changed slice
			if(cur_slice != prev_slice)
			{
				//update pixels data with current slice
				pixels = (byte[])stack.getPixels(cur_slice);

				prev_slice = cur_slice;
			}
			
			//user clicks on the image without starting the plugin
			if(first_click & !work)
					IJ.write("Yawi2D is OFF, you have to start it to work.\n");

			//the plugin is executed only if it's active <=> work=true
			if(work)
			{
				//int off_X = canvas.offScreenX(x);
				//int off_Y = canvas.offScreenY(y);
				int off_X = x;
				int off_Y = y;

				setThreshold(off_X,off_Y);
				autoOutline(off_X, off_Y);

				//there's a selection
				if(traceEdge())
				{
					roi = new PolygonRoi(xpoints, ypoints, npoints, Roi.TRACED_ROI);
					img.setRoi(roi);
//					roi.addOrSubtract();

					IJ.write("num points ROI: " + npoints + "\n");
					IJ.write("num points BROI: " + npoints_broi + "\n");
					IJ.write("ROI: " + roi + "\n");

					ResultsTable rt = ResultsTable.getResultsTable();
					rt.reset();
					for (int i = 0; i < npoints ; i++) 
					{
						rt.incrementCounter();
						rt.addValue("ROI_x", xpoints_b[i]);
						rt.addValue("ROI_y", ypoints_b[i]);
					}

					rt.show("Base ROI Points");

					Arrays.sort(broi_points, 0, npoints_broi, new PointYComparator());

					ResultsTable rt2 = ResultsTable.getResultsTable();
					rt2.reset();
					for (int i = 0; i < npoints_broi ; i++) 
					{
						rt2.incrementCounter();
						rt2.addValue("BROI_x", broi_points[i].getX());
						rt2.addValue("BROI_y", broi_points[i].getY());
					}
					rt2.show("Border ROI Points");
				}
				else	//no selection
				{
					img.killRoi();
					IJ.write("No selection avalaible, retry\n");
				}
			}
		}

		//mouse is clicked on the image
		public void mousePressed(MouseEvent e) 
		{
			//original mousePressed 	
			super.mousePressed(e);

			makeROI(offScreenX(e.getX()), offScreenY(e.getY()));
		}
	} 
	
	class MainWindow extends ImageWindow implements ActionListener 
	{
		//panel buttons
		private Button button1, button2, button3, button4;
		private Button button5, button6, button7;
		private Button button8, button9;
		private Button button10;

		private TextField x_field, y_field;

//		private TextPanel txt_panel;

		//constructor
		MainWindow(ImagePlus imp, ImageCanvas ic)
		{
			//ImageWindow constructor
			super(imp, ic);

			//the panel is created
			createPanel();

			//the window is located at top left corner
			this.setLocation(0,0);
		}

		void createPanel()
		{
			// -- FIRST ROW --
			Panel panel = new Panel();
			//add a button to the pannel
			button1 = new Button(" Start ");
			button1.addActionListener(this);
			panel.add(button1);

			//add a button to the pannel
			button2 = new Button(" Stop ");
			button2.addActionListener(this);
			panel.add(button2);

			//add a button to the pannel
			button3 = new Button(" Exit ");
			button3.addActionListener(this);
			panel.add(button3);

			//add a button to the pannel
			button4 = new Button(" Snapshot ");
			button4.addActionListener(this);
			panel.add(button4);

			//add the pannel to the window and show it
			add(panel);
			// -- END FIRST ROW --

			// -- SECOND ROW --
			panel = new Panel();
			//add a button to the pannel
			button5 = new Button(" ROI Points ");
			button5.addActionListener(this);
			panel.add(button5);

			//add a button to the pannel
			button6 = new Button(" BROI Points ");
			button6.addActionListener(this);
			panel.add(button6);

			//add a button to the pannel
			button7 = new Button(" Fill ROI ");
			button7.addActionListener(this);
			panel.add(button7);

			//add the pannel to the window and show it
			add(panel);
			// -- END SECOND ROW --

			// -- THIRD ROW --
			panel = new Panel();
			x_field = new TextField("", 3);
			panel.add(x_field);

			y_field = new TextField("", 3);
			panel.add(y_field);

			//add a button to the pannel
			button8 = new Button(" OK ");
			button8.addActionListener(this);
			panel.add(button8);

			//add a button to the pannel
			button9 = new Button(" Cancel ");
			button9.addActionListener(this);
			panel.add(button9);

			//add the pannel to the window and show it
			add(panel);
			// -- END THIRD ROW --

			// -- FOURTH ROW --
			panel = new Panel();

			//add a button to the pannel
			button10 = new Button(" Generate ROIs ");
			button10.addActionListener(this);
			panel.add(button10);

			//add the pannel to the window and show it
			add(panel);
			// -- END FOURTH ROW --

			// --  FIFTH ROW --
//			txt_panel = new TextPanel();
//			add(txt_panel);
			// -- END FIFTTH ROW --

			pack();
		}

		public void actionPerformed(ActionEvent e)
		{
			Object b = e.getSource();

			//start 
			if(b == button1) 
			{
				work = true;		

				//the plugin starts to work
				if(first_click)
						first_click = false;
			} 
			//stop
			else if(b == button2) 
				work = false;
			//exit
			else if(b == button3) 
				this.dispose();
			//snapshot
			else if(b == button4) 
			{
				//add _snap.jpg to image name
				//String exp_name = img_name.substring(0,img_name.indexOf(".jpg")) + "_snap.jpg";
				String exp_name = img_name.substring(0,img_name.indexOf(".jpg")) + "_snap.png";

				//creating new image and its processor
				ImagePlus exp_img = NewImage.createRGBImage(exp_name, ip_width, ip_height, 1, NewImage.FILL_BLACK);
				ImageProcessor exp_ip = exp_img.getProcessor();

				//copy original image into the new
				exp_ip.copyBits(ip, 0, 0, Blitter.COPY);

				//image saver
				FileSaver fs;
				//creating the filesaver
				fs = new FileSaver(exp_img);

				// draw fill if it's showed
				if(npoints_broi > 0 && show_roi_fill == true)
				{
					Rectangle roi_rect = roi.getBounds();

					for(int x = (int)roi_rect.getX(); x < (int) (roi_rect.getX() + roi_rect.getWidth()); x++)
					{
						for(int y = (int)roi_rect.getY(); y < (int) (roi_rect.getY() + roi_rect.getHeight()); y++)
						{
							// a point inside the roi
							if(roi.contains(x, y) == true)
							{
								int value = pixels[(ip_width * y) + x] & 0xff;

								// a point inside the treshold
								if(value >= lowerThreshold && value <= upperThreshold)
									exp_ip.setColor(Color.green);
								// a point not in the treshold
								else
									exp_ip.setColor(Color.red);

								exp_ip.drawPixel(x, y);
							}
						}
					}
				}

				//set lines color
				exp_ip.setColor(Color.yellow);

				//draw ROI lines
				for(int i = 0; i < npoints-1 ; i++)
						exp_ip.drawLine(xpoints_b[i], ypoints_b[i], xpoints_b[i+1], ypoints_b[i+1]);

				exp_ip.drawLine(xpoints_b[npoints - 1], ypoints_b[npoints - 1], xpoints_b[0], ypoints_b[0]);

				if(npoints_broi > 0 && show_broi == true)
				{
					exp_ip.setColor(Color.blue);

					for(int i = 0; i < npoints_broi ; i++)
						exp_ip.drawPixel((int)broi_points[i].getX(), (int)broi_points[i].getY());
				}

				if(npoints > 0 && show_roi == true)
				{
					exp_ip.setColor(Color.cyan);

					for(int i = 0; i < npoints ; i++)
						exp_ip.drawPixel(xpoints_b[i], ypoints_b[i]);
				}

				//save new image
				//fs.saveAsJpeg();
				fs.saveAsPng();
			}
			else if(b == button5)
			{
				if(show_roi == true)
					show_roi = false;
				else
					show_roi = true;

				getCanvas().update(getCanvas().getGraphics());
			}
			else if(b == button6)
			{
				if(show_broi == true)
					show_broi = false;
				else
					show_broi = true;

				getCanvas().update(getCanvas().getGraphics());
			}
			else if(b == button7)
			{
				if(show_roi_fill == true)
					show_roi_fill = false;
				else
					show_roi_fill = true;

				getCanvas().update(getCanvas().getGraphics());
			}
			else if(b == button8)
			{
				if(x_field.getText().length() > 0 && y_field.getText().length() > 0)
				{
					int x = Integer.parseInt(x_field.getText());
					int y = Integer.parseInt(y_field.getText());
					((LocalCanvas)getCanvas()).makeROI(x, y);
				}
			}
			else if(b == button9)
			{
				x_field.setText("");
				y_field.setText("");
			}
			else if(b == button10)
			{
				int[] xpoints_bk = new int[npoints];
				int[] ypoints_bk = new int[npoints];

				for(int i = 0; i < npoints; i++)
				{
					xpoints_bk[i] = xpoints_b[i];
					ypoints_bk[i] = ypoints_b[i];
				}

				Roi roi_bk = new PolygonRoi(xpoints_bk, ypoints_bk, npoints, Roi.TRACED_ROI);

				Rectangle roi_rect2;
				Rectangle roi_rect = roi_bk.getBounds();

				String img_name;

				ImagePlus exp_img;
				ImageProcessor exp_ip;

				int start_lowerThreshold = lowerThreshold;
				int start_upperThreshold = upperThreshold;

				int start_x = (int)roi_rect.getX();
				int start_y = (int)roi_rect.getY();
				int end_x = (int) (roi_rect.getX() + roi_rect.getWidth());
				int end_y = (int) (roi_rect.getY() + roi_rect.getHeight());

				for(int x_roi = start_x; x_roi < end_x; x_roi++)
				{
					for(int y_roi = start_y; y_roi < end_y; y_roi++)
					{
						if(roi_bk.contains(x_roi, y_roi) == true)
						{
							img_name = start_x + "_" + start_y + "-" + x_roi + "_" + y_roi + ".png";

							//creating new image and its processor
							exp_img = NewImage.createRGBImage(img_name, ip_width, ip_height, 1, NewImage.FILL_BLACK);
							exp_ip = exp_img.getProcessor();

							//copy original image into the new
							exp_ip.copyBits(ip, 0, 0, Blitter.COPY);

							setThreshold(x_roi, y_roi);
							autoOutline(x_roi, y_roi);

							//there's a selection
							if(traceEdge())
							{
								roi = new PolygonRoi(xpoints, ypoints, npoints, Roi.TRACED_ROI);

								// draw fill if it's showed
								if(npoints_broi > 0 && show_roi_fill == true)
								{
									roi_rect2 = roi.getBounds();

									for(int x = (int)roi_rect.getX(); x < (int) (roi_rect.getX() + roi_rect.getWidth()); x++)
									{
										for(int y = (int)roi_rect.getY(); y < (int) (roi_rect.getY() + roi_rect.getHeight()); y++)
										{
											// a point inside the roi
											if(roi.contains(x, y) == true)
											{
												int value = pixels[(ip_width * y) + x] & 0xff;

												// a point inside the treshold
												if(value >= lowerThreshold && value <= upperThreshold)
													exp_ip.setColor(Color.green);
												// a point not in the treshold
												else
													exp_ip.setColor(Color.red);

												exp_ip.drawPixel(x, y);
											}
										}
									}
								}

								//set lines color
								exp_ip.setColor(Color.yellow);

								//draw ROI lines
								for(int i = 0; i < npoints-1 ; i++)
									exp_ip.drawLine(xpoints_b[i], ypoints_b[i], xpoints_b[i+1], ypoints_b[i+1]);

								exp_ip.drawLine(xpoints_b[npoints - 1], ypoints_b[npoints - 1], xpoints_b[0], ypoints_b[0]);

								if(npoints_broi > 0 && show_broi == true)
								{
									exp_ip.setColor(Color.blue);

									for(int i = 0; i < npoints_broi ; i++)
										exp_ip.drawPixel((int)broi_points[i].getX(), (int)broi_points[i].getY());
								}

								if(npoints > 0 && show_roi == true)
								{
									exp_ip.setColor(Color.cyan);

									for(int i = 0; i < npoints ; i++)
										exp_ip.drawPixel(xpoints_b[i], ypoints_b[i]);
								}

							// et color for origin point according to its threshold
							int value = pixels[(ip_width * y_roi) + x_roi] & 0xff;
							// a point inside the treshold
							if(value >= start_lowerThreshold && value <= start_upperThreshold)
								exp_ip.setColor(Color.green);
							// a point not in the treshold
							else
								exp_ip.setColor(Color.red);

								exp_ip.drawString("ROI from " + x_roi + "," + y_roi , 15, 15);

								int comp_score = compareROIs(roi_bk, roi);

								if(comp_score < 4)
									exp_ip.setColor(Color.red);
								else if(comp_score < 7)
									exp_ip.setColor(Color.orange);
								else if(comp_score < 10)
									exp_ip.setColor(Color.yellow);
								// u win - 10!
								else
									exp_ip.setColor(Color.green);

								exp_ip.drawString("ROI score: " +  comp_score + "/10", 15, 30);
							}
							else
							{
								// et color for origin point according to its threshold
								int value = pixels[(ip_width * y_roi) + x_roi] & 0xff;
								// a point inside the treshold
								if(value >= start_lowerThreshold && value <= start_upperThreshold)
									exp_ip.setColor(Color.green);
								// a point not in the treshold
								else
									exp_ip.setColor(Color.red);

								exp_ip.drawString("NO ROI from " + x_roi + "," + y_roi  , 15, 15);

								exp_ip.setColor(Color.yellow);
								exp_ip.drawString("ROI score: NA", 15, 30);
							}

							exp_ip.setColor(Color.cyan);
							exp_ip.drawPixel(x_roi, y_roi);

							try
							{
								writeImage(exp_img, "./plugins/Yawi/tests/" + img_name);
							}
							catch (Exception exc) 
							{
								String msg = exc.getMessage();
								if (msg==null || msg.equals(""))
									msg = "" + exc;
								IJ.showMessage("Yawi_2D", "An error occured writing the file.\n \n" + msg);
							}
						}
					}
				}
			}
		}

		/// compare the second ROI to the first one and return a similarity value
		public int compareROIs(Roi r1, Roi r2)
		{
			int score = 0;

			Rectangle roi_rect = r1.getBounds();
			Rectangle roi_rect2 = r2.getBounds();

			int area1 = 0;
			int area2 = 0;

			// compute area 1st ROI
			int start_x = (int)roi_rect.getX();
			int start_y = (int)roi_rect.getY();
			int end_x = (int) (roi_rect.getX() + roi_rect.getWidth());
			int end_y = (int) (roi_rect.getY() + roi_rect.getHeight());

			for(int x_roi = start_x; x_roi < end_x; x_roi++)
				for(int y_roi = start_y; y_roi < end_y; y_roi++)
					if(r1.contains(x_roi, y_roi))
						area1++;

			// compute area 2nd ROI
			int start_x2 = (int)roi_rect2.getX();
			int start_y2 = (int)roi_rect2.getY();
			int end_x2 = (int) (roi_rect2.getX() + roi_rect2.getWidth());
			int end_y2 = (int) (roi_rect2.getY() + roi_rect2.getHeight());

			for(int x_roi = start_x2; x_roi < end_x2; x_roi++)
				for(int y_roi = start_y2; y_roi < end_y2; y_roi++)
					if(r2.contains(x_roi, y_roi))
						area2++;

			// compute areas difference
			int diff_areas;

			if(area1 > area2)
				diff_areas = 100 - (area2 * 100 / area1);
			else
				diff_areas = 100 - (area1 * 100 / area2);

			// assign score for areas diff
			if(diff_areas < comp_tresh1)
				score += comp_p1;
			else if(diff_areas < comp_tresh2)
				score += comp_p2;
			else if(diff_areas < comp_tresh3)
				score += comp_p3;
			else if(diff_areas < comp_tresh3)
				score += comp_p4;
	
			//compute perimeters difference
			int len1 = (int) r1.getLength();
			int len2 = (int) r2.getLength();

			int diff_len;

			if(len1 > area2)
				diff_len = 100 - (len2 * 100 / len1);
			else
				diff_len = 100 - (len1 * 100 / len2);

			// assign score for perimeters diff
			if(diff_len < comp_tresh1)
				score += comp_p1;
			else if(diff_len < comp_tresh2)
				score += comp_p2;
			else if(diff_len < comp_tresh3)
				score += comp_p3;
			else if(diff_len < comp_tresh3)
				score += comp_p4;

			//compute distance between ROIs
			int dist = (int) Point.distance(start_x, start_y, start_x2, start_y2);

			// assign score for ROIs distance
			if(dist < comp_tresh1)
				score += comp_p1;
			else if(dist < comp_tresh2)
				score += comp_p2;
			else if(dist < comp_tresh3)
				score += comp_p3;
			else if(dist < comp_tresh3)
				score += comp_p4;

			return Math.round(score * comp_points_scale / comp_max_points);
		}

		void writeImage(ImagePlus imp, String path) throws Exception
		{
			int width = imp.getWidth();
			int  height = imp.getHeight();
			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = (Graphics2D)bi.getGraphics();
			g.drawImage(imp.getImage(), 0, 0, null);
			File f = new File(path);
			ImageIO.write(bi, "png", f);
		}
	}

	//stack window
    class MainStackWindow extends StackWindow implements ActionListener
    {
		//panel buttons
		private Button button1, button2, button3, button4;

		//constructor
		MainStackWindow(ImagePlus imp, ImageCanvas ic)
		{
			//ImageWindow constructor
			super(imp, ic);

			//the panel is created
			createPanel();

			//the window is located at top left corner
			this.setLocation(0,0);

		}

        void createPanel() 
		{
			//FlowLayout: puts the components in a left-to-right, top-to-bottom order. 
			Panel panel = new Panel(new FlowLayout());
			
			//add a slice selector
            panel.add(sliceSelector);
			
			//add a button to the pannel
			button1 = new Button(" Start ");
			button1.addActionListener(this);
			panel.add(button1);

			//add a button to the pannel
			button2 = new Button(" Stop ");
			button2.addActionListener(this);
			panel.add(button2);

			//add a button to the pannel
			button3 = new Button(" Exit ");
			button3.addActionListener(this);
			panel.add(button3);

			//add a button to the pannel
			button4 = new Button(" Snapshot ");
			button4.addActionListener(this);
			panel.add(button4);
			
			//add the pannel to the window and show it
			add(panel);
			pack();
		}

		public void actionPerformed(ActionEvent e)
		{
			Object b = e.getSource();

			//start 
			if(b == button1) 
			{
				work = true;		

				//the plugin starts to work
				if(first_click)
						first_click = false;
			} 
			//stop
			else if(b == button2) 
				work = false;
			//exit
			else if(b == button3) 
				this.dispose();
			//snapshot
			else if(b == button4) 
			{
				//snapshot name
				String exp_name = "stack_" + cur_slice +"_snap.jpg";

				//creating new image and its processor
				ImagePlus exp_img = NewImage.createRGBImage(exp_name,ip_width,ip_height,1,NewImage.FILL_BLACK);
				ImageProcessor exp_ip = exp_img.getProcessor();

				//copy original image into the new
				exp_ip.copyBits(ip,0,0,Blitter.COPY);
				
				//image saver
				FileSaver fs;
				//creating the filesaver
				fs = new FileSaver(exp_img);
				
				//set lines color
				exp_ip.setColor(Color.yellow);
				
				//draw ROI lines
				for(int i = 0; i < npoints-1 ; i++)
						exp_ip.drawLine(xpoints_b[i], ypoints_b[i], xpoints_b[i+1], ypoints_b[i+1]); 

				//save new image
				fs.saveAsJpeg();
			}
		}
    } 

	public class PointYComparator implements Comparator
	{
		public int compare(Object o1, Object o2)
		{
			Point p1 = (Point)o1;
			Point p2 = (Point)o2;

			// p1.y < p2.y
			if((int)p1.getY() < (int)p2.getY())
				return -1;
			// p1.y > p2.y
			else if((int)p1.getY() > (int)p2.getY())
				return 1;
			// p1.y = p2.y AND p1.x < p2.x
			else if((int)p1.getX() < (int)p2.getX())
				return -1;
			// p1.y = p2.y AND p1.x > p2.x
			else if((int)p1.getX() > (int)p2.getX())
				return 1;
			// p1 = p2
			else
				return 0;
		}

		public boolean equals(Object o)
		{
			if(!(o instanceof PointYComparator))
				return false;
			else
				return true;
		}
	}
}
