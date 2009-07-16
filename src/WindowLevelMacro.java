/**
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This software is licensed under the Apache License:
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

import ij.WindowManager;
import ij.ImagePlus;
import ij.IJ;
import ij.process.ImageProcessor;
import ij.gui.ImageCanvas;
import ij.gui.Toolbar;

import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;


/**
 * This class will change the window/level by dragging the mouse.  The window is changed
 * by dragging the mouse over the image on the x axis.  The level is changed
 * by dragging the mouse on the y axis.
 */
public final class WindowLevelMacro
{
    /** The toolID of this tool */
    private static int toolID;

    private static boolean startedThread = false;

    /**
     * This is called by the macro.  This will start a thread that every second makes
     * sure that the all the open image windows have the mouse listener for this macro.
     */
    public static void initialize()
    {
        toolID = Toolbar.getToolId();

        //a thread isn't pretty but its the only way that guarntees that all the image
        //windows get a mouse listener
        if( !startedThread )
        {
            startedThread = true;
            Thread t = new Thread( new Runnable()
            {
                public void run()
                {
                    while( true )
                    {
                        addMouseListeners();
                        try
                        {
                            Thread.sleep( 1000 );
                        }
                        catch( InterruptedException ie ) {}
                    }
                }
            });
            t.setDaemon( true );
            t.start();
        }
    }

    /**
     * Will add a mouse listener that will change the wl when the tool is selected
     * and the mouse is dragged.
     */
    private static void addMouseListeners()
    {
        int[] imageIDs = WindowManager.getIDList();
        if( imageIDs != null )
        {
            for( int i=0; i<imageIDs.length; i++ )
            {
                ImagePlus imagePlus = WindowManager.getImage( imageIDs[i] );
                ImageCanvas canvas = imagePlus.getCanvas();
                MouseListener[] currentListeners = imagePlus.getCanvas().getMouseListeners();
                boolean addListeners = true;
                for( int j=0; j<currentListeners.length; j++ )
                    if( currentListeners[j] instanceof WindowLevelMouseListener )
                        addListeners = false;

                if( addListeners )
                {
                    WindowLevelMouseListener mouseListeners = new WindowLevelMouseListener( imagePlus );
                    canvas.addMouseListener( mouseListeners );
                    canvas.addMouseMotionListener( mouseListeners );
                }
            }
        }
    }

    /**
     * Mouse listener.
     */
    private static final class WindowLevelMouseListener implements MouseListener, MouseMotionListener
    {
        private final ImagePlus imagePlus;

        private double currentMin = 0;
        private double currentMax = 0;
        private int lastX = -1;
        private int lastY = -1;

        public WindowLevelMouseListener( ImagePlus imagePlus )
        {
            this.imagePlus = imagePlus;
        }

        public void mouseClicked( MouseEvent e )
        {}

        public void mousePressed( MouseEvent e )
        {
            //make sure the tool is selected
            if( toolID != Toolbar.getToolId() )
                return;

            lastX = e.getX();
            lastY = e.getY();
            currentMin = imagePlus.getProcessor().getMin();
            currentMax = imagePlus.getProcessor().getMax();
        }

        public void mouseReleased( MouseEvent e )
        {}

        public void mouseEntered( MouseEvent e )
        {
            if( toolID == Toolbar.getToolId() )
                addMouseListeners();
        }

        public void mouseExited( MouseEvent e )
        {}

        public void mouseDragged( MouseEvent e )
        {
            //make sure our toolID is selected
            if( toolID != Toolbar.getToolId() )
                return;

            double minMaxDifference = currentMax - currentMin;

            int x = e.getX();
            int y = e.getY();

            int xDiff = x - lastX;
            int yDiff = y - lastY;

            int totalWidth  = (int) (imagePlus.getWidth() * imagePlus.getCanvas().getMagnification() );
            int totalHeight = (int) (imagePlus.getHeight() * imagePlus.getCanvas().getMagnification() );

            double xRatio = ((double)xDiff)/((double)totalWidth);
            double yRatio = ((double)yDiff)/((double)totalHeight);

            //scale to our image range
            double xScaledValue = minMaxDifference*xRatio;
            double yScaledValue = minMaxDifference*yRatio;

            //invert x
            xScaledValue = xScaledValue * -1;

            adjustWindowLevel( xScaledValue, yScaledValue );
        }

        public void mouseMoved( MouseEvent e )
        {}

        public void adjustWindowLevel( double xDifference, double yDifference )
        {
            ImageProcessor processor = imagePlus.getProcessor();

            //current settings
            double currentWindow = currentMax - currentMin;
            double currentLevel = currentMin + (.5*currentWindow);

            //change
            double newWindow = currentWindow + xDifference;
            double newLevel = currentLevel + yDifference;

            if( newWindow < 0 )
                newWindow = 0;
            if( newLevel < 0 )
                newLevel = 0;

            IJ.showStatus( "Window: " + IJ.d2s(newWindow) + ", Level: " + IJ.d2s(newLevel) );

            //convert to min/max
            double newMin = newLevel - (.5*newWindow);
            double newMax = newLevel + (.5*newWindow);

            processor.setMinAndMax( newMin, newMax );
            imagePlus.updateAndDraw();
        }
    }
}
