
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

public class ImageEncDemo
{
	public static class SimplePredictionModeler extends ModelerTree<DumbPixelBlob>
	{
		private DumbPixelBlob _mirrBlob;
		
		private Map<Integer, CachedSumLookup<Short>> _pred2ModMap = Util.treemap();
				
		private final int _imWidth;
		private final int _imHight;
		
		SimplePredictionModeler(int wid, int hit)
		{
			_imWidth = wid;
			_imHight = hit;
			
			initPixelModMap();
		}
		
		public void recModel(EncoderHook enchook)
		{
			_mirrBlob = new DumbPixelBlob(_imWidth, _imHight);
			
			for(int x : Util.range(_imWidth))
			{
				for(int y : Util.range(_imHight))
				{
					for(int i : Util.range(3))
					{
						int predictor = (x == 0 ? -1 : _mirrBlob.pixBlob[x-1][y][i]);
						
						CachedSumLookup<Short> cclm = _pred2ModMap.get(predictor);
						
						EventModeler<Short> pixmod = EventModeler.build(cclm);
						
						if(isEncode())
						{
							Short origpix = _origOutcome.pixBlob[x][y][i];
							pixmod.setOriginal(origpix);
						}
						
						_mirrBlob.pixBlob[x][y][i] = pixmod.decodeResult(enchook);
					}
				}
			}
		}
		
		public DumbPixelBlob getResult()
		{
			return _mirrBlob;	
			
		}
		
		void initPixelModMap()
		{
			/*
			List<String> reclist = FileUtils.getReaderUtil()
								.setFile("/userdata/lifecode/datadir/ENC_DATA_MAP.txt")
								.readLineListE();
								
			*/
			
			List<String> reclist = Util.vector();
			
			LinkedList<String> gimplist = new LinkedList<String>(reclist);
			
			SortedMap<Short, Integer> globalmap = Util.treemap();
			
			while(!gimplist.isEmpty())
			{
				String breakline = gimplist.poll();
				
				Util.massert(breakline.startsWith("-----"), 
					"Break line should start with ---- , found %d", breakline);
				
				SortedMap<Short, Integer> curmap = Util.treemap();
				
				for(int i : Util.range(256))
				{
					String nextrec = gimplist.poll();
					int weight = Integer.valueOf(nextrec);
					curmap.put((short) i, weight);
					
					Util.incHitMap(globalmap, ((short) i), weight);
				}
				
				CachedSumLookup<Short> nextcalc = CachedSumLookup.build(curmap);
				
				_pred2ModMap.put(_pred2ModMap.size(), nextcalc);
			}
			
			_pred2ModMap.put(-1, CachedSumLookup.build(globalmap));
		}
		
		
	}	
	
	public static class BasicImageModeler extends ModelerTree<DumbPixelBlob>
	{
		private DumbPixelBlob _mirrBlob;
		
		private CachedSumLookup<Short> _pixelModMap;
		
		private final int _imWidth;
		private final int _imHight;
		
		BasicImageModeler(int wid, int hit)
		{
			_imWidth = wid;
			_imHight = hit;
			
			initPixelModMap();
		}
		
		public void recModel(EncoderHook enchook)
		{
			_mirrBlob = new DumbPixelBlob(_imWidth, _imHight);
			
			for(int x : Util.range(_imWidth))
			{
				for(int y : Util.range(_imHight))
				{
					for(int i : Util.range(3))
					{
						EventModeler<Short> pixmod = EventModeler.build(_pixelModMap);
						
						if(isEncode())
						{
							Short origpix = _origOutcome.pixBlob[x][y][i];
							pixmod.setOriginal(origpix);
						}
						
						_mirrBlob.pixBlob[x][y][i] = pixmod.decodeResult(enchook);
					}
				}
			}
		}
		
		public DumbPixelBlob getResult()
		{
			return _mirrBlob;	
		}
		
		void initPixelModMap()
		{
			List<Short> slist = Util.vector();
			
			for(int i : Util.range(256))
				{ slist.add((short) i); }
			
			SortedMap<Short, Integer> unimap = EncoderUtil.buildUniformCountMap(slist);
			
			_pixelModMap = CachedSumLookup.build(unimap);
		}
	}
	
	
	
	public static class DumbPixelBlob
	{
		short[][][] pixBlob;
		
		DumbPixelBlob(int w, int h)
		{
			pixBlob = new short[w][h][3];	
		}
		
		DumbPixelBlob(BufferedImage img)
		{
			pixBlob = new short[img.getWidth()][img.getHeight()][3];
			
			for(int x : Util.range(img.getWidth()))
			{
				for(int y : Util.range(img.getHeight()))
				{
					int rgb = img.getRGB(x, y);
					
					int r = (rgb >> 16 ) & 0x000000FF;
					int g = (rgb >>  8 ) & 0x000000FF;
					int b = (rgb >>  0 ) & 0x000000FF;
					
					pixBlob[x][y][0] = (short) r;
					pixBlob[x][y][1] = (short) g;
					pixBlob[x][y][2] = (short) b;
				}
			}
		}
		
		public int getWidth() 	{ return pixBlob.length; }
		public int getHight() 	{ return pixBlob[0].length; }
		
		static DumbPixelBlob readFromFile(String bmpfile)
		{
			try 
				{ return new DumbPixelBlob(ImageIO.read(new File(bmpfile))); }
			catch (IOException ioex) 
				{ throw new RuntimeException(ioex); }			
		}
		
		public BufferedImage composeImage()
		{
			BufferedImage bim = new BufferedImage(pixBlob.length, pixBlob[0].length, BufferedImage.TYPE_INT_RGB);
			
			for(int x : Util.range(pixBlob.length))
			{
				for(int y : Util.range(pixBlob[0].length))
				{
					int rgb = 0;
					
					rgb += (pixBlob[x][y][0] << 16);
					rgb += (pixBlob[x][y][1] <<  8);
					rgb += (pixBlob[x][y][1] <<  0);
					
					bim.setRGB(x, y, rgb);
				}
			}
			
			return bim;
		}
		
		public int getPointCount()
		{
			return getWidth()*getHight();	
		}
		
		public int getPixelCount()
		{
			return getPointCount()*3;
		}
		
		public boolean equals(DumbPixelBlob that)
		{
			if(getWidth() != that.getWidth() || getHight() != that.getHight())
				{ return false; }
			
			for(int x : Util.range(getWidth()))
			{
				for(int y : Util.range(getHight()))
				{
					for(int i : Util.range(3))
					{
						if(this.pixBlob[x][y][i] != this.pixBlob[x][y][i])
							{ return false; }
					}
				}
			}
			
			return true;
		}
	}
	
	
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
	
	public static class BuildPredictDeltaData extends ArgMapRunnable
	{
		SortedMap<Integer, SortedMap<Integer, Integer>> _dataMap = Util.treemap();
		
		public void runOp()
		{
			initDataMap();
			
			DumbPixelBlob dpblob = DumbPixelBlob.readFromFile("/Users/burfoot/Desktop/OrigImage.bmp");
			
			for(int x : Util.range(1, dpblob.getWidth()))
			{
				for(int y : Util.range(dpblob.getHight()))
				{
					for(int i : Util.range(3))
					{
						int prvpix = dpblob.pixBlob[x-1][y][i];
						int nxtpix = dpblob.pixBlob[x  ][y][i];
						
						_dataMap.putIfAbsent(prvpix, Util.treemap());
						
						Util.incHitMap(_dataMap.get(prvpix), nxtpix);
						
						// short a = dpblob.pixBlob[x][y][
					
					}
				}
			}
			
			List<String> reclist = Util.vector();
			
			for(int i : Util.range(256))
			{
				reclist.add("------------------ // value " + i);
				
				for(int j : Util.range(256))
					{ reclist.add(_dataMap.get(i).get(j)+""); }
			}
			
			Util.massert(false, "Need to re-implement");
			
			/*
			FileUtils.getWriterUtil()
					.setFile("/userdata/lifecode/datadir/ENC_DATA_MAP.txt")
					.writeLineListE(reclist);
					
			*/
			
			// for(int
		}
		
		private void initDataMap()
		{
			for(int i : Util.range(256))
			{ 
				_dataMap.put(i, Util.treemap());
				
				for(int j : Util.range(256))
					{ _dataMap.get(i).put(j, 1); }
					
			}
		}
	}
	
	
	public static class TestBasicEncode extends ArgMapRunnable
	{
		public void runOp() throws IOException
		{

			DumbPixelBlob dpblob = DumbPixelBlob.readFromFile("/Users/burfoot/Desktop/OrigImage.bmp");
			
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
	
	
}	
