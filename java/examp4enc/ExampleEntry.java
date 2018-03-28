
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
import net.danburfoot.examp4enc.ExampleUtil.*;
import net.danburfoot.examp4enc.BiasedDiceExample.*;

// Entry point for all code in the Example package.
public class ExampleEntry
{
	public static class PrepareImageFile extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			String imagepath = _argMap.getStr("imagepath");
			
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
			
			String targetpath = ExampleUtil.getDataFilePath(EncodeDataType.image, getBitMapFilePath(new File(imagepath)));
			
			ImageIO.write(backim, "bmp", new File(targetpath));
			
			Util.pf("Wrote bit map file to %s\n", targetpath);
		}
		
		private String getBitMapFilePath(File imagefile)
		{	
			String bname = imagefile.getName();
			String[] base_ext = bname.split("\\.");

			Util.massert(base_ext.length == 2, "Expected two tokens here, got %d, for string %s", base_ext.length, bname);
			return base_ext[0] + ".bmp";
		}
	}	
	
	public static class TestImageEncode extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			String bitmapname = _argMap.getStr("bitmapname") + ".bmp";
			
			DumbPixelBlob dpblob = DumbPixelBlob.readFromFile(ExampleUtil.getImageFile(bitmapname));
			
			BlobImageModeler encmod = getModeler(_argMap, dpblob);
			BlobImageModeler decmod = getModeler(_argMap, dpblob);
			
			Util.pf("Using modeler class %s\n", encmod.getClass().getSimpleName());
			
			encmod.setOriginal(dpblob);
			
			double startup = Util.curtime();
			byte[] encim = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encim);
			
			int extramodsize = encmod.getExtraModelCost();
			double bitpixrate = (encim.length + extramodsize) * 8;
			bitpixrate /= encmod.getResult().getPixelCount();
			
			Util.pf("Encoded byte size is %d, extra size is %d, pixel count is %d,  %.03f bit/pixel, took %.03f sec\n", 
						encim.length, extramodsize, encmod.getResult().getPixelCount(), bitpixrate, (Util.curtime()-startup)/1000);
			
			Util.massert(encmod.getResult().equals(decmod.getResult()),
				"Encoded version does not match decoded version");
		}
		
		static BlobImageModeler getModeler(ArgMap themap, DumbPixelBlob dpblob) throws IOException
		{
			String modelername = themap.getStr("modelername");
			
			if(modelername.equals("uniform"))
				{ return new UniformImageModeler(dpblob.getWidth(), dpblob.getHight()); }
					
			if(modelername.equals("adaptivebasic"))
				{ return new AdaptiveBasicModeler(dpblob.getWidth(), dpblob.getHight()); }
			
			if(modelername.equals("offlinepredict"))
				{ return new OfflinePredictionModeler(dpblob.getWidth(), dpblob.getHight()); } 
			
			if(modelername.equals("adaptivepredict"))
			{
				int ppflush = themap.getInt("ppflush", 1000);
				return new AdaptivePredictionModeler(dpblob.getWidth(), dpblob.getHight(), ppflush); 
			}
			
			Util.massert(false, "No modelername found for string %s, please check spelling", modelername);
			return null;
		}
		
	}	
		
	
	public static class RunStrEncDemo extends ArgMapRunnable
	{
		public void runOp() throws Exception
		{
			String bookname = _argMap.getStr("bookname", "Sherlock");
			String modeltype = _argMap.getStr("modeltype");
			int trunclen = _argMap.getInt("trunc2len", Integer.MAX_VALUE);
			
			String datastr = StringEncDemo.getBookData(bookname, trunclen);
						
			Util.pf("Got string length %d for book %s\n", datastr.length(), bookname);
			
			double startup = Util.curtime();
			
			ModelerTree<String> encmod = StringEncDemo.getTextModeler(modeltype, datastr.length());
			ModelerTree<String> decmod = StringEncDemo.getTextModeler(modeltype, datastr.length());
			
			encmod.setOriginal(datastr);
			
			byte[] bytebuf = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, bytebuf);
			String decresult = decmod.getResult();
			
			Util.massert(datastr.equals(decresult),
				"Discrepancy between encoded data and original");
			
			Util.pf("Encode success confirmed, required %d bytes, %.03f byte/char, took %.03f sec\n", 
				bytebuf.length, ((double) bytebuf.length)/datastr.length(), (Util.curtime()-startup)/1000); 
		}
	}	
	
	
	public static class BiasedDieDemo extends ArgMapRunnable
	{
		public void runOp()
		{
			int N = _argMap.getInt("numsamp", 100);
			
			SortedMap<Integer, Integer> unifmap = BiasedDiceExample.getUniform6Model();
			SortedMap<Integer, Integer> biasmap = BiasedDiceExample.getBiased6Model();
			
			List<Integer> data = ExampleUtil.generateDataFromModel(biasmap, N, new Random());
			
			double unifcost = BiasedDiceExample.encodeAndCheck(unifmap, data);
			double biascost = BiasedDiceExample.encodeAndCheck(biasmap, data);
			
			Util.pf("Encode check passed, uniform cost is %.03f, bias model cost is %.03f\n",
					unifcost, biascost);
			
			Util.pf("Penalty is %.03f total, %.03f/sample, N=%d samples\n",
				unifcost - biascost, (unifcost - biascost)/N, N);
			
			double kldiverge = EncoderUtil.KL_divergence(biasmap, unifmap);
			Util.pf("Theoretically predicted penalty is %.03f\n", kldiverge);
			
		}
		

	}
	
	public static class AdaptiveDieDemo extends ArgMapRunnable
	{
		public void runOp()
		{
			int N = _argMap.getInt("numsamp", 100);
			
			SortedMap<Integer, Integer> realprob = BiasedDiceExample.getBiased6Model();
			List<Integer> data = ExampleUtil.generateDataFromModel(realprob, N, new Random());
			
			double bestcost;
			double adptcost;
			{
				Flat6SideModel encmod = new Flat6SideModel(realprob, data.size());
				Flat6SideModel decmod = new Flat6SideModel(realprob, data.size());
				bestcost = BiasedDiceExample.encodeAndCheck(encmod, decmod, data);
			}
			
			{
				Adaptive6SideModel encmod = new Adaptive6SideModel(data.size());
				Adaptive6SideModel decmod = new Adaptive6SideModel(data.size());
				adptcost = BiasedDiceExample.encodeAndCheck(encmod, decmod, data);
			}			
						
			Util.pf("Encode check passed, real model cost is %.03f, adaptive model cost is %.03f\n",
					bestcost, adptcost);
			
			Util.pf("Penalty is %.03f total, %.03f/sample, N=%d samples\n",
				adptcost - bestcost, (adptcost - bestcost)/N, N);
		}
	}	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Util.massert(args.length > 0,
			"Must include at least one argument!!");
		
		String fullclass = Util.sprintf("%s$%s", ExampleEntry.class.getName(), args[0]);
		
		ArgMap themap = ArgMap.getClArgMap(args);
		ArgMapRunnable amr;
		
		String installdir = themap.getStr("installdir");
		ExampleUtil.setInstallDir(installdir);
		
		
		
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
