/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer. 

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution. 

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of 
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration 
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/. 

*/

package com.amd.aparapi.sample.extension;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javax.swing.JComponent;
import javax.swing.JFrame;

import com.amd.aparapi.Range;
import com.amd.opencl.Device;
import com.amd.opencl.OpenCL;
import com.amd.opencl.OpenCLAdaptor;

/**
 * An example Aparapi application which displays a view of the Mandelbrot set and lets the user zoom in to a particular point. 
 * 
 * When the user clicks on the view, this example application will zoom in to the clicked point and zoom out there after.
 * On GPU, additional computing units will offer a better viewing experience. On the other hand on CPU, this example 
 * application might suffer with sub-optimal frame refresh rate as compared to GPU. 
 *  
 * @author gfrost
 *
 */

@OpenCL.Resource("com/amd/aparapi/sample/extension/mandel.cl")
interface MandelBrot extends OpenCL<MandelBrot>{
   MandelBrot createMandleBrot(//
         Range range,//
         @Arg("scale") float scale, //
         @Arg("offsetx") float offsetx, //
         @Arg("offsety") float offsety, //
         @GlobalWriteOnly("rgb") int[] rgb,//
         @GlobalReadOnly("pallette") int[] pallette//
   );
}

class JavaMandelBrot extends OpenCLAdaptor<MandelBrot> implements MandelBrot{

   @Override
   public MandelBrot createMandleBrot(Range range, float scale, float offsetx, float offsety, int[] rgb, int[] pallette) {
      final int MAX_ITERATIONS = 64;

      int width = range.getGlobalSize(0);
      int height = range.getGlobalSize(1);
      for (int gridy = 0; gridy < height; gridy++) {
         for (int gridx = 0; gridx < width; gridx++) {
            float x = ((((float) (gridx) * scale) - ((scale / 2.0f) * (float) width)) / (float) width) + offsetx;
            float y = ((((float) (gridy) * scale) - ((scale / 2.0f) * (float) height)) / (float) height) + offsety;
            int count = 0;
            float zx = x;
            float zy = y;
            float new_zx = 0.0f;
            for (; count < MAX_ITERATIONS && ((zx * zx) + (zy * zy)) < 8.0f; count++) {
               new_zx = ((zx * zx) - (zy * zy)) + x;
               zy = ((2.0f * zx) * zy) + y;
               zx = new_zx;
            }
            rgb[gridx + gridy * width] = pallette[count];

         }
      }
      return (this);
   }

}

class JavaMandelBrotMultiThread extends OpenCLAdaptor<MandelBrot> implements MandelBrot{

   @Override
   public MandelBrot createMandleBrot(final Range range, final float scale, final float offsetx, final float offsety,
         final int[] rgb, final int[] pallette) {
      final int MAX_ITERATIONS = 64;

      final int width = range.getGlobalSize(0);
      final int height = range.getGlobalSize(1);
      final int threadCount = 8;
      Thread[] threads = new Thread[threadCount];
      final CyclicBarrier barrier = new CyclicBarrier(threadCount+1);
      for (int thread = 0; thread < threadCount; thread++) {
         final int threadId = thread;
         final int groupHeight = height / threadCount;
         (threads[threadId] = new Thread(new Runnable(){
            public void run() {
               for (int gridy = threadId * groupHeight; gridy < (threadId + 1) * groupHeight; gridy++) {
                  for (int gridx = 0; gridx < width; gridx++) {
                     float x = ((((float) (gridx) * scale) - ((scale / 2.0f) * (float) width)) / (float) width) + offsetx;
                     float y = ((((float) (gridy) * scale) - ((scale / 2.0f) * (float) height)) / (float) height) + offsety;
                     int count = 0;
                     float zx = x;
                     float zy = y;
                     float new_zx = 0.0f;
                     for (; count < MAX_ITERATIONS && ((zx * zx) + (zy * zy)) < 8.0f; count++) {
                        new_zx = ((zx * zx) - (zy * zy)) + x;
                        zy = ((2.0f * zx) * zy) + y;
                        zx = new_zx;
                     }
                     rgb[gridx + gridy * width] = pallette[count];
                  }
               }
            try {
               barrier.await();
            } catch (InterruptedException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            } catch (BrokenBarrierException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }   
            }
         })).start();
      }
      try {
         barrier.await();
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (BrokenBarrierException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }   
      return (this);
   }

}

public class MandelExample{

   /** User selected zoom-in point on the Mandelbrot view. */
   public static volatile Point to = null;

   @SuppressWarnings("serial")
   public static void main(String[] _args) {

      JFrame frame = new JFrame("MandelBrot");

      /** Width of Mandelbrot view. */
      final int width = 768;

      /** Height of Mandelbrot view. */
      final int height = 768;

      /** Mandelbrot image height. */
      final Range range = Range.create2D(width, height);

      /** Image for Mandelbrot view. */
      final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      final BufferedImage offscreen = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      // Draw Mandelbrot image
      JComponent viewer = new JComponent(){
         @Override
         public void paintComponent(Graphics g) {

            g.drawImage(image, 0, 0, width, height, this);
         }
      };

      // Set the size of JComponent which displays Mandelbrot image
      viewer.setPreferredSize(new Dimension(width, height));

      final Object doorBell = new Object();

      // Mouse listener which reads the user clicked zoom-in point on the Mandelbrot view 
      viewer.addMouseListener(new MouseAdapter(){
         @Override
         public void mouseClicked(MouseEvent e) {
            to = e.getPoint();
            synchronized (doorBell) {
               doorBell.notify();
            }
         }
      });

      // Swing housework to create the frame
      frame.getContentPane().add(viewer);
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);

      // Extract the underlying RGB buffer from the image.
      // Pass this to the kernel so it operates directly on the RGB buffer of the image
      final int[] rgb = ((DataBufferInt) offscreen.getRaster().getDataBuffer()).getData();
      final int[] imageRgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

      /** Maximum iterations for Mandelbrot. */
      final int maxIterations = 64;

      /** Palette which maps iteration values to RGB values. */
      final int pallette[] = new int[maxIterations + 1];

      /** Mutable values of scale, offsetx and offsety so that we can modify the zoom level and position of a view. */
      float scale = .0f;

      float offsetx = .0f;

      float offsety = .0f;

      for (int i = 0; i < maxIterations; i++) {
         float h = i / (float) maxIterations;
         float b = 1.0f - h * h;
         pallette[i] = Color.HSBtoRGB(h, 1f, b);
      }

      MandelBrot mandelBrot = // Device.firstGPU(MandelBrot.class);
      // new JavaMandelBrot();
      new JavaMandelBrotMultiThread();
      float defaultScale = 3f;
      scale = defaultScale;
      offsetx = -1f;
      offsety = 0f;
      mandelBrot.createMandleBrot(range, scale, offsetx, offsety, rgb, pallette);

      System.arraycopy(rgb, 0, imageRgb, 0, rgb.length);
      viewer.repaint();

      // Window listener to dispose Kernel resources on user exit.
      frame.addWindowListener(new WindowAdapter(){
         public void windowClosing(WindowEvent _windowEvent) {
            // mandelBrot.dispose();
            System.exit(0);
         }
      });

      // Wait until the user selects a zoom-in point on the Mandelbrot view.
      while (true) {
         // Wait for the user to click somewhere
         while (to == null) {
            synchronized (doorBell) {
               try {
                  doorBell.wait();
               } catch (InterruptedException ie) {
                  ie.getStackTrace();
               }
            }
         }

         float x = -1f;
         float y = 0f;
         float tox = (float) (to.x - width / 2) / width * scale;
         float toy = (float) (to.y - height / 2) / height * scale;

         // This is how many frames we will display as we zoom in and out.
         int frames = 128;
         long startMillis = System.currentTimeMillis();
         for (int sign = -1; sign < 2; sign += 2) {
            for (int i = 0; i < frames - 4; i++) {
               scale = scale + sign * defaultScale / frames;
               x = x - sign * (tox / frames);
               y = y - sign * (toy / frames);
               offsetx = x;
               offsety = y;

               mandelBrot.createMandleBrot(range, scale, offsetx, offsety, rgb, pallette);

               System.arraycopy(rgb, 0, imageRgb, 0, rgb.length);
               viewer.repaint();
            }
         }

         long elapsedMillis = System.currentTimeMillis() - startMillis;
         System.out.println("FPS = " + frames * 1000 / elapsedMillis);

         // Reset zoom-in point.
         to = null;

      }

   }

}
