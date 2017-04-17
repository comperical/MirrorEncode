
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
			
	public static class EncodeAdaptiveExpo extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			String expolabel = _argMap.getStr("label", "A");
			
			String fpath = Util.sprintf("/userdata/external/mirrenc/data/expon/lambda%s.txt", expolabel);
			
			List<Integer> reclist = Util.readLineList(fpath, s -> Integer.valueOf(s));
			
			Util.pf("Read %d records from path %s\n", reclist.size(), fpath);
			
			double startup = Util.curtime();
			double lambda = _argMap.getDbl("lambda", .005);
			
			AdaptiveExpoModeler encmod = new AdaptiveExpoModeler(reclist.size(), 10000);
			AdaptiveExpoModeler decmod = new AdaptiveExpoModeler(reclist.size(), 10000);
			
			encmod.setOriginal(reclist);
			
			byte[] encdata = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encdata);
			
			Util.massert(decmod.getResult().equals(reclist),
				  	"Decoded data fails to match original");
			
			double netbitlen = encdata.length*8;
			int bswancount = encmod.getBlackSwanCount();
			
			Util.pf("Encoding correct, required %.03f bits, %.03f bit per item, %d Black Swans, took %.03f sec\n",
					netbitlen, netbitlen/reclist.size(), bswancount, (Util.curtime()-startup)/1000);
		}
	}		
	
	
	public static class EncodeExpoData extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{
			String expolabel = _argMap.getStr("label", "A");
			
			String fpath = Util.sprintf("/userdata/external/mirrenc/data/expon/lambda%s.txt", expolabel);
			
			List<Integer> reclist = Util.readLineList(fpath, s -> Integer.valueOf(s));
			
			Util.pf("Read %d records from path %s\n", reclist.size(), fpath);
			
			double startup = Util.curtime();
			double lambda = _argMap.getDbl("lambda", .005);
			
			ExpoDataModeler encmod = new ExpoDataModeler(reclist.size(), lambda, 10000);
			ExpoDataModeler decmod = new ExpoDataModeler(reclist.size(), lambda, 10000);
			
			encmod.setOriginal(reclist);
			
			byte[] encdata = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encdata);
			
			Util.massert(decmod.getResult().equals(reclist),
				  	"Decoded data fails to match original");
			
			double netbitlen = encdata.length*8;
			int bswancount = encmod.getBlackSwanCount();
			
			Util.pf("Encoding correct, required %.03f bits, %.03f bit per item, %d Black Swans, took %.03f sec\n",
					netbitlen, netbitlen/reclist.size(), bswancount, (Util.curtime()-startup)/1000);
		}
	}	
	
	
	public static class GenerateExponData extends ArgMapRunnable
	{
		public void runOp() throws Exception
		{
			Map<String, Double> lmap = getLambdaMap();
			Random rng = new Random(10000);
			int numsamp = _argMap.getInt("numsamp", 10000);
			
			for(String lkey : lmap.keySet())
			{
				List<Integer> datalist = Util.vector();
				
				double lambda = lmap.get(lkey);
				
				for(int i : Util.range(numsamp))
				{
					double u = rng.nextDouble();
					double x = Math.log(1-u)/(-lambda);
					
					// Util.pf("Generated u=%.03f, x=%.03f\n", u, x);
					
					datalist.add((int) Math.floor(x));
				}
				
				String fpath = Util.sprintf("/userdata/external/mirrenc/data/expon/lambda%s.txt", lkey);
				
				Util.writeData2Path(datalist, fpath);				
				
				Util.pf("Wrote %d records to %s for lambda=%.03f\n", datalist.size(), fpath, lambda);
			}
		}
		
		private Map<String, Double> getLambdaMap()
		{
			Map<String, Double> lmap = Util.treemap();
			lmap.put("A", .1);
			lmap.put("B", .05);
			lmap.put("C", .01);
			lmap.put("D", .005);
			return lmap;
		}
	}
	
	public static class RunStrEncDemo extends ArgMapRunnable
	{
		public void runOp() throws Exception
		{
			String modeltype = _argMap.getStr("modeltype");
			
			int trunclen = _argMap.getInt("trunc2len", Integer.MAX_VALUE);
			
			String datastr = StringEncDemo.getBookData("A", trunclen);
						
			Util.pf("Got data string length %d\n", datastr.length());
			
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
