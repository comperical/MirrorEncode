
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;


import net.danburfoot.examp4enc.ImageEncDemo.*; 
import net.danburfoot.examp4enc.ExponentialDataDemo.*;

// Entry point for all code in the Example package.
public class ExampleEntry
{
	public static class TestBitmapLoad extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			String imagepath = "/Users/burfoot/Desktop/Oak_Hill_1st_Hole.bmp";
			
			BufferedImage img = null;
			try {
				img = ImageIO.read(new File(imagepath));
			} catch (IOException ex) 
			{
				ex.printStackTrace();
				return;
			}
			int height = img.getHeight();
			int width = img.getWidth();
			
			int amountPixel = 0;
			int amountBlackPixel = 0;	
			
			DumbPixelBlob dpblob = new DumbPixelBlob(img);
			
			BufferedImage backim = dpblob.composeImage();
			
			ImageIO.write(backim, "bmp", new File("/Users/burfoot/Desktop/BackImage.bmp"));
		}
	}	
	
	public static class TestBasicEncode extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{

			DumbPixelBlob dpblob = DumbPixelBlob.readFromFile("/userdata/external/mirrenc/data/OrigImage.bmp");
			
			// BasicImageModeler encmod = new BasicImageModeler(dpblob.getWidth(), dpblob.getHight());
			// BasicImageModeler decmod = new BasicImageModeler(dpblob.getWidth(), dpblob.getHight());
			
			SimplePredictionModeler encmod = new SimplePredictionModeler(dpblob.getWidth(), dpblob.getHight());
			SimplePredictionModeler decmod = new SimplePredictionModeler(dpblob.getWidth(), dpblob.getHight());
			
			encmod.setOriginal(dpblob);
			
			byte[] encim = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encim);
			
			Util.pf("Encoded byte size is %d, pixel count is %d\n", 
						encim.length, encmod.getResult().getPixelCount());
			
			Util.massert(encmod.getResult().equals(decmod.getResult()),
				"Encoded version does not match decoded version");
		}
	}	
		
	public static class TestSmartFlush extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			int ppflush = _argMap.getInt("ppflush", 1000);
			
			DumbPixelBlob dpblob = DumbPixelBlob.readFromFile("/userdata/external/mirrenc/data/OrigImage.bmp");
			
			// BasicImageModeler encmod = new BasicImageModeler(dpblob.getWidth(), dpblob.getHight());
			// BasicImageModeler decmod = new BasicImageModeler(dpblob.getWidth(), dpblob.getHight());
			
			SmartFlushImageModeler encmod = new SmartFlushImageModeler(dpblob.getWidth(), dpblob.getHight(), ppflush);
			SmartFlushImageModeler decmod = new SmartFlushImageModeler(dpblob.getWidth(), dpblob.getHight(), ppflush);
			
			encmod.setOriginal(dpblob);
			
			byte[] encim = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encim);
			
			Util.pf("Encoded byte size is %d, pixel count is %d\n", 
						encim.length, encmod.getResult().getPixelCount());
			
			Util.massert(encmod.getResult().equals(decmod.getResult()),
				"Encoded version does not match decoded version");
		}
	}	
			
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Util.massert(args.length > 0,
			"Must include at least one argument!!");
		
		String fullclass = Util.sprintf("%s$%s", ExampleEntry.class.getName(), args[0]);
		
		ArgMap themap = ArgMap.getClArgMap(args);
		ArgMapRunnable amr;
		
		try {
			Class amrclass = Class.forName(fullclass);	
			amr = (ArgMapRunnable) amrclass.newInstance();
		} catch (Exception ex) {
			
			Util.pf("Failed to set up ArgMapRunnable properly\n");
			ex.printStackTrace();
			return;
		} 
		
		amr.initFromArgMap(themap);
		amr.runOp();
	}
	
}	
