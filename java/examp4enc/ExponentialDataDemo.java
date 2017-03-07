
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
	public static class ExpoDataModeler extends ModelerTree<List<Integer>>
	{
		public final int num2Send;
		
		private List<Integer> _mirrList = Util.vector();
		
		private CachedSumLookup<Integer> _cachedLookup;
		
		private int endSequence = -1;
		
		public ExpoDataModeler(int n2s, double prob2stop)
		{
			num2Send = n2s;
			
			Util.massert(0.0001 < prob2stop && prob2stop < .9999,
				"Stop probability must be in (.0001, .9999), found %.06f", prob2stop);
			
			buildCachedLookup(prob2stop);
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
				int mirrtotal = 0;
				
				while(true)
				{
					EventModeler<Integer> nextvalmod = EventModeler.build(_cachedLookup);
					
					if(isEncode())
					{
						int origval = _origOutcome.get(_mirrList.size());
						int val2send = origval - mirrtotal;
						
						val2send = (val2send > endSequence ? endSequence : val2send);
						
						nextvalmod.setOriginal(val2send);
					}
					
					int mirrnext = nextvalmod.decodeResult(enchook);
					
					mirrtotal += mirrnext;
					
					if(mirrnext < endSequence)
						{ break; }
				}
				
				_mirrList.add(mirrtotal);
			}
		}
		
		public List<Integer> getResult()
		{
			Util.massert(_mirrList.size() == num2Send,
				"Attempt to get result before encoding is finished");
			
			return _mirrList;	
		}
		
		private void buildCachedLookup(double prob2stop)
		{	
			int remprobmass = 1_000_000;
			
			SortedMap<Integer, Integer> cmap = Util.treemap();
			
			while(remprobmass > 1_000)
			{
				int nextprob = (int) Math.floor(prob2stop * remprobmass);
				cmap.put(cmap.size(), nextprob);
				
				remprobmass -= nextprob;
			}
			
			endSequence = cmap.size();
			
			cmap.put(endSequence, remprobmass);
			
			_cachedLookup = CachedSumLookup.build(cmap);
			
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
	
	public static class CreateExpoData extends ArgMapRunnable
	{
		
		public void runOp()
		{
			Random r = new Random();
			double p = _argMap.getDbl("prob2stop", .1);
			int numsamp = _argMap.getInt("numsamp", 1_000_000);
			String fname = _argMap.getStr("filename");
			
			List<String> reclist = Util.vector();
			
			for(int i : Util.range(numsamp))
			{
				int exposample = sampleExpoData(r, p);
				reclist.add(exposample+"");
			}
			
			String fullpath = Util.sprintf("/userdata/lifecode/datadir/encoderdemo/%s.txt", fname);
			
			Util.massert(false, "need to re-implement");
			
			/*
			FileUtils.getWriterUtil()
					.setFile(fullpath)
					.writeLineListE(reclist);
					
			*/
			Util.pf("Wrote %d records to path %s\n", reclist.size(), fullpath);
		}
	}
	
	public static class EncodeExpoData extends ArgMapRunnable
	{
		
		public void runOp()
		{
			double modelprob = _argMap.getDbl("modelprob", .01);
			String fname = _argMap.getStr("filename");
			
			String fullpath = Util.sprintf("/userdata/lifecode/datadir/encoderdemo/%s.txt", fname);
			
			List<Integer> datalist = readDataList(fullpath);
			
			Util.pf("Read %d data items from path %s\n", datalist.size(), fullpath);
			
			double startup = Util.curtime();
			
			ExpoDataModeler encmod = new ExpoDataModeler(datalist.size(), modelprob);
			ExpoDataModeler decmod = new ExpoDataModeler(datalist.size(), modelprob);
			
			encmod.setOriginal(datalist);
			
			byte[] encdata = EncoderUtil.shrink(encmod);
			EncoderUtil.expand(decmod, encdata);
			
			Util.massert(decmod.getResult().equals(datalist),
				  	"Decoded data fails to match original");
			
			double netbitlen = encdata.length*8;
			
			Util.pf("Encoding correct, required %.03f bits, %.03f bit per item, took %.03f sec\n",
					netbitlen, netbitlen/datalist.size(), (Util.curtime()-startup)/1000);
			
			
			
			
		}
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
	
	
}	
