
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;
import java.util.function.*;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;


public class ExponentialDataDemo
{
	public static class AdaptiveExpoModeler extends ModelerTree<List<Integer>>
	{
		private static int RECALC_INTERVAL = 10000;
		
		public final int num2Send;
		
		public final int topRange;
		
		private List<Integer> _mirrList = Util.vector();
		
		private CachedSumLookup<Integer> _expoLookup;
		private CachedSumLookup<Integer> _flatLookup;
		private CachedSumLookup<Boolean> _swanLookup;	
			
		private int lastOkayValue = -1;
		
		private int _blackSwanCount = 0;
		
		public AdaptiveExpoModeler(int n2s, int trange)
		{
			num2Send = n2s;
			
			topRange = trange;
			
			reBuildLookup();
		}
		
		public void setOriginalSub()
		{
			Util.massert(_origOutcome.size() == num2Send,
				"Expected to see %d outcomes, but got %d", num2Send, _origOutcome.size());
		}
		
		public void recModel(EncoderHook enchook)
		{
			while(_mirrList.size() < num2Send)
			{
				// Encode T/F, depending on whether the outcome is a "black swan"
				boolean mirrblack;
				{
					EventModeler<Boolean> bswanmod = EventModeler.build(_swanLookup);
					
					if(isEncode())
					{
						boolean origblack = _origOutcome.get(_mirrList.size()) > lastOkayValue;
						bswanmod.setOriginal(origblack);
					}
					
					mirrblack = bswanmod.decodeResult(enchook);
					
					_blackSwanCount += (mirrblack ? 1 : 0);
				}
				
				
				{
					// Okay, use either the flat lookup map, or the expo map, depending on whether
					// value is a BlackSwan or not
					EventModeler<Integer> nextvalmod = mirrblack ? 
										EventModeler.build(_flatLookup) : 
										EventModeler.build(_expoLookup);
					
					if(isEncode())
					{
						int origval = _origOutcome.get(_mirrList.size());
						nextvalmod.setOriginal(origval);
					}
					
					int mirrnext = nextvalmod.decodeResult(enchook);
					
					_mirrList.add(mirrnext);
				}
				
				if((_mirrList.size() % RECALC_INTERVAL) == 0)
					{ reBuildLookup(); }
					
				
				// Util.pf("Sent value %d\n", mirrnext);
			}
		}
		
		public List<Integer> getResult()
		{
			Util.massert(_mirrList.size() == num2Send,
				"Attempt to get result before encoding is finished");
			
			return _mirrList;	
		}
		
		public int getBlackSwanCount()
		{
			return _blackSwanCount;	
		}
		
		private void reBuildLookup()
		{	
			double lambda = getLambdaEstimate();
			
			// Util.pf("Lambda est is %.03f\n", lambda);
			
			SortedMap<Integer, Integer> dmap = Util.treemap();
			
			double factor = Math.exp(-lambda);
			double w = 1_000_000;
			
			for(int i = 0; i < topRange; i++)
			{
				int floorw = (int) Math.floor(w);
				
				if(floorw == 0)
				{
					lastOkayValue = i-1;
					// Util.pf("Got floorW=0, last non-zero was %d\n", lastOkayValue);	
					break;
				}
				
				dmap.put(i, floorw);
				
				w *= factor;
				
				//if(i < 10)
				//	{ Util.pf("%s\n", dmap); }
			}
			
			_expoLookup = CachedSumLookup.build(dmap);
			
			{
				SortedMap<Boolean, Integer> swanmap = Util.treemap();
				swanmap.put(true, 1);
				swanmap.put(false, 100_000);
				_swanLookup = CachedSumLookup.build(swanmap);
			}
			
			{
				SortedMap<Integer, Integer> flatmap = Util.treemap();
				
				for(int x : Util.range(lastOkayValue+1, topRange))
					{ flatmap.put(x, 1); }
					
				_flatLookup = CachedSumLookup.build(flatmap);				
			}
			
			Util.pf(".");
			
			
			// Util.pf("Built cached lookup, total size is %d, endSequence is %d\n", cmap.size(), endSequence);
		}
		
		private double getLambdaEstimate()
		{
			// If we don't have any data yet, just guess!!
			if(_mirrList.isEmpty())
				{ return .001; }
			
			double m = 0D;
			
			for(int result : _mirrList)
				{ m += result; }
			
			m /= _mirrList.size();
			
			// Lambda = 1/mean
			return 1/m;
		}
	}	
	
	
	public static class ExpoDataModeler extends ModelerTree<List<Integer>>
	{
		public final int num2Send;
		
		public final int topRange;
		
		private List<Integer> _mirrList = Util.vector();
		
		private CachedSumLookup<Integer> _expoLookup;
		private CachedSumLookup<Integer> _flatLookup;
		private CachedSumLookup<Boolean> _swanLookup;
		
		
		private int endSequence = -1;
		
		private int lastOkayValue = -1;
		
		private int _blackSwanCount = 0;
		
		public ExpoDataModeler(int n2s, double lambda, int trange)
		{
			num2Send = n2s;
			
			topRange = trange;
			
			buildCachedLookup(lambda);
		}
		
		public void setOriginalSub()
		{
			Util.massert(_origOutcome.size() == num2Send,
				"Expected to see %d outcomes, but got %d", num2Send, _origOutcome.size());
		}
		
		public void recModel(EncoderHook enchook)
		{
			while(_mirrList.size() < num2Send)
			{
				// Encode T/F, depending on whether the outcome is a "black swan"
				boolean mirrblack;
				{
					EventModeler<Boolean> bswanmod = EventModeler.build(_swanLookup);
					
					if(isEncode())
					{
						boolean origblack = _origOutcome.get(_mirrList.size()) > lastOkayValue;
						bswanmod.setOriginal(origblack);
					}
					
					mirrblack = bswanmod.decodeResult(enchook);
					
					_blackSwanCount += (mirrblack ? 1 : 0);
				}
				
				
				{
					// Okay, use either the flat lookup map, or the expo map, depending on whether
					// value is a BlackSwan or not
					EventModeler<Integer> nextvalmod = mirrblack ? 
										EventModeler.build(_flatLookup) : 
										EventModeler.build(_expoLookup);
					
					if(isEncode())
					{
						int origval = _origOutcome.get(_mirrList.size());
						nextvalmod.setOriginal(origval);
					}
					
					int mirrnext = nextvalmod.decodeResult(enchook);
					
					_mirrList.add(mirrnext);
				}
				
				// Util.pf("Sent value %d\n", mirrnext);
			}
		}
		
		public List<Integer> getResult()
		{
			Util.massert(_mirrList.size() == num2Send,
				"Attempt to get result before encoding is finished");
			
			return _mirrList;	
		}
		
		public int getBlackSwanCount()
		{
			return _blackSwanCount;	
		}
		
		private void buildCachedLookup(double lambda)
		{	
			SortedMap<Integer, Integer> dmap = Util.treemap();
			
			double factor = Math.exp(-lambda);
			double w = 1_000_000;
			
			for(int i = 0; i < topRange; i++)
			{
				int floorw = (int) Math.floor(w);
				
				if(floorw == 0)
				{
					lastOkayValue = i-1;
					Util.pf("Got floorW=0, last non-zero was %d\n", lastOkayValue);	
					break;
				}
				
				dmap.put(i, floorw);
				
				w *= factor;
				
				//if(i < 10)
				//	{ Util.pf("%s\n", dmap); }
			}
			
			_expoLookup = CachedSumLookup.build(dmap);
			
			{
				SortedMap<Boolean, Integer> swanmap = Util.treemap();
				swanmap.put(true, 1);
				swanmap.put(false, 100_000);
				_swanLookup = CachedSumLookup.build(swanmap);
			}
			
			{
				SortedMap<Integer, Integer> flatmap = Util.treemap();
				
				for(int x : Util.range(lastOkayValue+1, topRange))
					{ flatmap.put(x, 1); }
					
				_flatLookup = CachedSumLookup.build(flatmap);				
			}
			
			
			// Util.pf("Built cached lookup, total size is %d, endSequence is %d\n", cmap.size(), endSequence);
		}
	}
	
	public static class ExpoDistInfo
	{
		public final double prob2Stop;
		
		private double[] _explicitProb;
		
		private ExpoDistInfo(double p)
		{
			prob2Stop = p;	
			
		}
		
		public double getLambda()
		{
			return 1/getMean();
		}
		
		public double getMean()
		{
			return (1/prob2Stop) - 1;	
		}
		
		private void buildExplicitProb()
		{
			if(_explicitProb != null)
				{ return; }
					
			_explicitProb = new double[1_000_000];
			
			double remprob = 1D;
			
			for(int i = 0; i < _explicitProb.length; i++)
			{
				double probstopnow = prob2Stop * remprob;
				
				remprob -= probstopnow;
				
				_explicitProb[i] = probstopnow;
			}
		}
		
		public void showProbCalc()
		{
			buildExplicitProb();
			
			double lambda = getLambda();
			
			for(int k = 0; k < 20; k++)
			{
				double lc = lambda * Math.exp(-lambda * k);
				double ep = _explicitProb[k];
				
				Util.pf("\tk=%d\tL=%.03f\tE=%.03f\n", k, lc, ep);
			}
			
		}
		
		public double explicitMeanCalc()
		{
			buildExplicitProb();
			
			/*
			double mu = 0D;
			
			for(int i = 0; i < _explicitProb.length; i++)
				{ mu += i * _explicitProb[i]; }
			
			return mu;
			*/
			
			return explicitCalc(pr -> pr._1*pr._2);
		}
		
		public static double oneItemEntropy(double d)
		{
			if(d < 1e-24)
				{ return 0; }
			
			return -Math.log(d) * d;
			
		}
		
		public double getEntropy()
		{
			return 1 - Math.log(getLambda());
		}
		
		public double getVariance()
		{
			// return 
			
			return -1D;
		}
		
		public double explicitVarianceCalc()
		{
			double expmean = explicitMeanCalc();
			
			return explicitCalc(pr -> (pr._1 * pr._1)*pr._2) - (expmean * expmean);	
		}
		
		public double explicitNormCalc()
		{
			return explicitCalc(pr -> pr._2);	
		}
		
		public double explicitEntropyCalc()
		{
			return explicitCalc(pr -> oneItemEntropy(pr._2));			
		}
		
		public double explicitCalc(Function<Pair<Integer, Double>, Double> myfunc)
		{
			buildExplicitProb();
			
			double x = 0D;
			
			for(int i = 0; i < _explicitProb.length; i++)
			{
				x += myfunc.apply(Pair.build(i, _explicitProb[i]));	
			}
			
			return x;
			
		}		
		
	}
	
	public static int sampleExpoData(Random myrand, double prob2stop)
	{
		double nextr = myrand.nextDouble();
		
		if(nextr < prob2stop)
			{ return 0; }
		
		return sampleExpoData(myrand, prob2stop)+1;
	}
	
	public static List<Integer> readDataList(String fname)
	{
		/*
		List<String> reclist = FileUtils.getReaderUtil()
							.setFile(fname)
							.readLineListE();
							
		return Util.map2list(reclist, s -> Integer.valueOf(s.trim()));	
		*/
		
		Util.massert(false, "need to re-implement");
		return null;
	}
	
	

	
	public static class ShowExpoDistInfo extends ArgMapRunnable
	{
		public void runOp()
		{
			double prob2stop = _argMap.getDbl("prob2stop");
			
			ExpoDistInfo edi = new ExpoDistInfo(prob2stop);
			
			Util.pf("Explicit mean calc is %.03f\n", edi.explicitMeanCalc());
			Util.pf("Fast mean calc is %.03f, lambda=%.03f\n", edi.getMean(), edi.getLambda());
			Util.pf("Explicit norm calc is %.03f\n", edi.explicitNormCalc());
			Util.pf("Explicit entropy calc is %.03f, fast is %.03f\n", edi.explicitEntropyCalc(), edi.getEntropy());
			Util.pf("Explicit variance calc is %.03f, fast is %.03f\n", edi.explicitVarianceCalc(), edi.getVariance());
			
			edi.showProbCalc();
		}
	}
	
	/*
	
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
	
	*/	
	
	
}	
