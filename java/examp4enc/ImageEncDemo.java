
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

import net.danburfoot.examp4enc.ExampleUtil.*;

public class ImageEncDemo
{
	
	public static abstract class BlobImageModeler extends ModelerTree<DumbPixelBlob>
	{
		public int getExtraModelCost()
		{
			return 0;	
		}
	}
	
	// Dumbest form of encoding, just use a uniform distribution.
	// Good as a sanity check
	public static class UniformImageModeler extends BlobImageModeler
	{
		// {{{ 
		private DumbPixelBlob _mirrBlob;
		
		private CachedSumLookup<Short> _pixelModMap;
		
		private final int _imWidth;
		private final int _imHight;
		
		UniformImageModeler(int wid, int hit)
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
		
		// }}}
	}	

	
	public static class AdaptiveBasicModeler extends BlobImageModeler
	{
		// {{{
		
		private DumbPixelBlob _mirrBlob;
		
		private final int _imWidth;
		private final int _imHight;
		
		private SortedMap<Short, Integer> _pixelModMap = Util.treemap();
		
		private CachedSumLookup<Short> _lookupMap = null;
		
		AdaptiveBasicModeler(int wid, int hit)
		{
			_imWidth = wid;
			_imHight = hit;
			
			for(int pix : Util.range(256))
				{ _pixelModMap.put((short) pix, 1); }
		}
		
		public void recModel(EncoderHook enchook) 
		{
			int pixcount = 0;
			_mirrBlob = new DumbPixelBlob(_imWidth, _imHight);
			
			for(int x : Util.range(_imWidth))
			{
				for(int y : Util.range(_imHight))
				{
					for(int i : Util.range(3))
					{
						int predictor = (x == 0 ? -1 : _mirrBlob.pixBlob[x-1][y][i]);
						
						EventModeler<Short> pixmod = EventModeler.build(getLookupMap());
						
						if(isEncode())
						{
							Short origpix = _origOutcome.pixBlob[x][y][i];
							pixmod.setOriginal(origpix);
						}
						
						short pixelresult = pixmod.decodeResult(enchook);
						
						_mirrBlob.pixBlob[x][y][i] = pixelresult;
						
						Util.incHitMap(_pixelModMap, pixelresult);
						
						pixcount++;
						
						if((pixcount % 1000) == 0)
							{ _lookupMap = null; }
					}
				}
			}
		}
		
		public CachedSumLookup<Short> getLookupMap()
		{
			if(_lookupMap == null)
				{ _lookupMap = CachedSumLookup.build(_pixelModMap); }
			
			return _lookupMap;
		}
		
		public DumbPixelBlob getResult()
		{
			return _mirrBlob;	
		}
		
		// }}}			
	}
	
	public static class OfflinePredictionModeler extends BlobImageModeler
	{
		// {{{
		
		private DumbPixelBlob _mirrBlob;
		
		private Map<Integer, CachedSumLookup<Short>> _pred2ModMap = Util.treemap();
				
		private final int _imWidth;
		private final int _imHight;
		
		OfflinePredictionModeler(int wid, int hit)
		{
			_imWidth = wid;
			_imHight = hit;
		}
		
		public void recModel(EncoderHook enchook)
		{
			initPixelModMap();
			
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
		
		@Override
		public int getExtraModelCost()
		{
			File statfile = new File(getStatDataPath());
			return (int) statfile.length();
		}		
		
		
		@Override
		public void setOriginalSub()
		{
			try { writeDeltaFile(_origOutcome); }
			catch (IOException ioex) { throw new RuntimeException(ioex); }
		}
		
		static void writeDeltaFile(DumbPixelBlob dpblob) throws IOException
		{
			SortedMap<Integer, SortedMap<Integer, Integer>> deltamap = Util.treemap();
			
			for(int i : Util.range(256))
			{ 
				deltamap.put(i, Util.treemap());
				
				for(int j : Util.range(256))
					{ deltamap.get(i).put(j, 1); }
			}			
			
			
			for(int x : Util.range(1, dpblob.getWidth()))
			{
				for(int y : Util.range(dpblob.getHight()))
				{
					for(int i : Util.range(3))
					{
						int prvpix = dpblob.pixBlob[x-1][y][i];
						int nxtpix = dpblob.pixBlob[x  ][y][i];
						
						Util.incHitMap(deltamap.get(prvpix), nxtpix);
					}
				}
			}
			
			List<String> reclist = Util.vector();
			
			for(int i : Util.range(256))
			{
				// Record break line
				reclist.add("------------------ // value " + i);
				
				for(int j : Util.range(256))
					{ reclist.add(deltamap.get(i).get(j)+""); }
			}
			
			Util.writeLineList(new File(getStatDataPath()), reclist, rec -> rec);
			Util.pf("Wrote %d lines to %s\n", reclist.size(), getStatDataPath()); 
		}
		
		
		public DumbPixelBlob getResult()
		{
			return _mirrBlob;	
		}
		
		private static String getStatDataPath()
		{
			return ExampleUtil.getDataFile(EncodeDataType.image, "IMAGE_STAT_DATA.txt.gz").getAbsolutePath();
		}
		
		void initPixelModMap() 
		{
			List<String> reclist; 
			
			try { reclist = Util.readLineList(getStatDataPath()); }
			catch (IOException ioex) { throw new RuntimeException(ioex); }
			
			Util.pf("Read %d stat lines from %s\n", reclist.size(), getStatDataPath());
			
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
		
		// }}}	
	}	
	

	public static class AdaptivePredictionModeler extends BlobImageModeler
	{
		// {{{
		
		private DumbPixelBlob _mirrBlob;
		
		// These are the actual underlying statistics. 
		private Map<Integer, SortedMap<Short, Integer>> _statMap = Util.treemap();

		// This is a Cached Cum Sum lookup, which we will rebuild every N pixels.
		private Map<Integer, CachedSumLookup<Short>> _pred2ModMap = Util.treemap();
				
		public final int pixPerFlush;
		
		private final int _imWidth;
		private final int _imHight;
		
		private int _totalPix = 0;
		
		AdaptivePredictionModeler(int wid, int hit, int ppflush)
		{
			_imWidth = wid;
			_imHight = hit;
			
			pixPerFlush = ppflush;
			
			for(int i : Util.range(-1, 256))
			{
				SortedMap<Short, Integer> defmap = Util.treemap();
				
				for(int j : Util.range(256))
					{ defmap.put((short) j, 1); }
				
				_statMap.put(i, defmap);
			}
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
						
						CachedSumLookup<Short> cclm = getLookup4Predictor(predictor);
						
						EventModeler<Short> pixmod = EventModeler.build(cclm);
						
						if(isEncode())
						{
							Short origpix = _origOutcome.pixBlob[x][y][i];
							pixmod.setOriginal(origpix);
						}
						
						short result = pixmod.decodeResult(enchook);
						
						_mirrBlob.pixBlob[x][y][i] = result;
						
						Util.incHitMap(_statMap.get(predictor), result);
					}
					
					_totalPix++;
					
					if((_totalPix % pixPerFlush) == 0)
						{ _pred2ModMap.clear(); }
				}
			}
		}
		
		public DumbPixelBlob getResult()
		{
			return _mirrBlob;	
		}
		
		private CachedSumLookup<Short> getLookup4Predictor(int predictor)
		{
			if(_pred2ModMap.isEmpty())
			{
				// Util.pf("Rebuilding cache maps from stats..., pixcount is %d \n", _totalPix);
				
				for(int pred : _statMap.keySet())
					{ _pred2ModMap.put(pred, CachedSumLookup.build(_statMap.get(pred))); }
			}
			
			Util.massert(_pred2ModMap.containsKey(predictor),
				"Don't have a cached map for predictor %d", predictor);
			
			return _pred2ModMap.get(predictor);
		}
		
		// }}}
	}		
	
	// Just a dumb package for holding pixels.
	public static class DumbPixelBlob
	{
		// {{{
		
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
		
		static DumbPixelBlob readFromFile(File bmpfile)
		{
			return readFromFile(bmpfile.getAbsolutePath());			
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
		
		// }}}
	}
}	
