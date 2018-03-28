package net.danburfoot.encoder;


import java.io.*;
import java.util.*;

import java.util.function.*;
import java.util.stream.Collectors;

import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

/**
*/
public abstract class EncoderUtil
{
	public static final int MAX_BITS = 50;
	
	public static final double LOG2 = Math.log(2.0);
	public static final double INV_LOG2 = 1/LOG2;
	public static final double LOG_26 = Math.log(26)/LOG2;	
	
	public static double prob2clen(double prob)
	{
		Util.massert(prob > 1e-30, "Prob value is too small");
		return -Math.log(prob)/LOG2;	
	}
	
	public static double clen4uniform(int M)
	{
		double P = 1.0D;
		P /= M;
		return -Math.log(P)/LOG2;
	}
			
	public static <K> double KL_divergence(Map<K, Integer> P, Map<K, Integer> Q)
	{
		Util.massert(P.size() == Q.size(), 
			"Size mismatch in probability distributions");
		
		double dtotal = 0D;
		
		int ptotal = Util.reduce(P.values(), 0, pr -> pr._1 + pr._2);
		int qtotal = Util.reduce(Q.values(), 0, pr -> pr._1 + pr._2);
		
		for(K item : P.keySet())
		{
			Util.massert(Q.containsKey(item),
				"Item %s is not available in model Q", item);
			
			double pi = P.get(item);
			double qi = Q.get(item);
			
			pi /= ptotal;
			qi /= qtotal;
			
			double logratio = Math.log((pi / qi));
			dtotal += pi * logratio;
		}
		
		return dtotal / LOG2;
	}
	
	/** 
	 * This value is the maximum scale size that can be safely
	 * used for encoding cdfs. 
	 * This value is determined by the number of bits used in the Encoder.
	 * The arithmetic encoder guarantees that the range value at each step 
	 * is at least 2^14. If the scale is greater than this, and a probability 
	 * with value 1 is encountered, we will lose precision. 
	 */ 
	public static int MAX_SCALE = (1 << 20);

	public interface EventLogger
	{
		public <T extends Comparable<T>> void logEvent(EventModeler<T> evmod, T outc, Symbol symtarg, int depth);
		
		// These don't do anything by default, use RegionCodeLengthLogger
		// To turn xthem on.
		public default void startRegion(Enum e) { }
		
		public default void endRegion(Enum e) { }
		
		// Pop the most recent region
		public default void endRegion() {}
		
	}		
	
	// No functionality 
	public interface EncOrDec {} 
	
	public interface AcEncoder extends EncOrDec
	{
		public abstract void encodeSymbol(Symbol s) throws IOException;
		public abstract void flush() throws IOException;
	}	
	
	public interface AcDecoder extends EncOrDec
	{
		public abstract void removeSymbol(Symbol s) throws IOException;
		public abstract long getCurrentCount(long scale) throws IOException;
	}		
	
	
	public static class EncoderHook 
	{
		public final EncOrDec encOrDec;
		
		public final EventLogger evLog;
		
		public final boolean isEncode;
		
		private EncoderHook(EncOrDec eod, EventLogger evlog)
		{
			encOrDec = eod;
			
			evLog = evlog;
			
			isEncode = (eod instanceof AcEncoder);
		}
		
		private EncoderHook(EventLogger evlog)
		{
			encOrDec = null;
			
			evLog = evlog;
			
			isEncode = true;
		}
	} 
	
	


	
	public static <T> SortedMap<T, Integer> buildUniformCountMap(Collection<T> keyset)
	{
		SortedMap<T, Integer> countmap = Util.treemap();

		for(T onekey : keyset)
			{ countmap.put(onekey, 10); }
		
		return countmap;		
	}
	

	public static SortedMap<Boolean, Integer> buildBinaryMap(int trueprob, int falseprob)
	{
		SortedMap<Boolean, Integer> binmap = Util.treemap();
		
		if(trueprob > 0)
			{ binmap.put(true, trueprob); }
		
		if(falseprob > 0)
			{ binmap.put(false, falseprob); }
		
		return binmap;
	}
	

	
	public static <T> SortedMap<T, Integer> filterCountByWeight(Map<T, Integer> basemap, Map<T, Pair<Long, Long>> filtmap)
	{
		SortedMap<T, Integer> resmap = Util.treemap();
		
		for(T basekey : basemap.keySet())
		{
			Pair<Long, Long> filtpair = filtmap.get(basekey);
			
			if(filtpair._1 == 0)
				{ continue; }
			
			if(filtpair._1 == filtpair._2)
			{
				resmap.put(basekey, basemap.get(basekey));
				continue;
			}
			
			double VP = basemap.get(basekey);
			VP *= filtpair._1;
			VP /= filtpair._2;

			int vp = (int) Math.round(VP);
			vp = (vp == 0 ? 1 : vp);
			resmap.put(basekey, vp);
		}
		
		return resmap;
	}
	
	
	

	

	
	public interface LookupMap<T>
	{
		public boolean hasOutcome(T otc);
		
		public Symbol lookupSymbol(T otc);
		
		public T decodeOutcome(long count);
		
		public long getScale();		
		
		public Symbol getFastProbSymbol(T otc);
		
		
		public default void debugShowMap()
		{
			Util.massert(false, "This is an optional method, subclasses must implement if desired");	
			
		}
	}
	
	public static <R extends Comparable<R>> CachedSumLookup<R> helperMap(SortedMap<R, Integer> cmap)
	{
		return new CachedSumLookup<R>(cmap);	
	}			
	
	// This is the standard, not-very-smart way of transforming a CountMap into a LookupMap.
	// PRNZ::LazyLookupMap::Lookup maps should only construct CDFs when required
	public static  class CachedSumLookup<T extends Comparable<T>> implements LookupMap<T>
	{
		public final Pair<Integer, Long> divScalePair; 
		
		private SortedMap<T, Integer> _baseCountMap;
		
		SortedMap<T, Pair<Long, Long>> _lowHghMap;
		
		TreeMap<Long, T> _lookupMap;
		

		public CachedSumLookup(SortedMap<T, Integer> countmap)
		{
			Util.massert(!countmap.isEmpty(),
				"Attempt to build helper map with empty countmap");
			
			_baseCountMap = countmap;
				
			divScalePair = getDivisorNScale(countmap);
			
			Util.massert(divScalePair != null,
				"Unable to find good divisor for countmap");
		}
		
		public static <R extends Comparable<R>> CachedSumLookup<R> build(SortedMap<R, Integer> cmap)
		{
			return new CachedSumLookup<R>(cmap);
		}
		
		private void maybeBuildHelperMap()
		{
			if(_lowHghMap != null)
				{ return; }
				
			
			_lowHghMap = Util.treemap();
			_lookupMap = Util.treemap();
				
			long low = 0;
			
			for(Map.Entry<T, Integer> keypair : _baseCountMap.entrySet())
			{
				Util.massert(keypair.getValue() > 0, "Found zero value entry for key %s", keypair.getKey());
				
				long newval = keypair.getValue();
				
				newval = newval > divScalePair._1 ? newval / divScalePair._1 : 1;
				
				long hgh = low + newval;				

				_lowHghMap.put(keypair.getKey(), Pair.build(low, hgh));
				_lookupMap.put(hgh, keypair.getKey());

				low = hgh;
			}
			
			Util.massert(low == divScalePair._2,
				"Mismatch Low is %d, but scale is %d", low, divScalePair._2);
		}
		
		public boolean hasOutcome(T otc)
		{
			return _baseCountMap.containsKey(otc);
		}
		
		int getDivisor()
		{
			return divScalePair._1;	
		}
		
		public long getScale()
		{
			return divScalePair._2;
		}		
		
		boolean isInit()
		{
			return _lowHghMap != null;
		}
		
		public Symbol lookupSymbol(T otc)
		{
			maybeBuildHelperMap();
			
			Pair<Long, Long> lowhgh = _lowHghMap.get(otc);
			
			Util.massert(lowhgh != null, "Outcome %s not found in lowHgh Map", otc);
			
			return new Symbol(lowhgh._1, lowhgh._2, divScalePair._2);
		}
		
		public T decodeOutcome(long count)
		{
			maybeBuildHelperMap();
			
			Util.massert(0 <= count && count < _lookupMap.lastKey(),
				"Argument count %d out of bounds", count);
			
			return _lookupMap.higherEntry(count).getValue();
		}

		@Override
		public Symbol getFastProbSymbol(T outc)
		{
			int p = _baseCountMap.get(outc) / divScalePair._1;
			p = (p < 1 ? 1 : p);
			
			return new Symbol(0, p, divScalePair._2);
		}
		
		@Override 
		public void debugShowMap()
		{
			Util.pf("CountMap has %d entries:\n", _baseCountMap.size());
			
			for(T key : _baseCountMap.keySet())
			{
				Util.pf("\t%s\t%d\n", key, _baseCountMap.get(key));	
			}
			
		}

	}
	
	static <T> Pair<Integer, Long> getDivisorNScale(SortedMap<T, Integer> cmap)
	{
		return getDivisorNScale(cmap, x -> true);	
	}
	
	
	// Okay, want a good divisor we can use that ensures:
	// - Total is less than MAX_SCALE under division* by div,
	// where division* means divide but with minimum of 1.	
	static <T> Pair<Integer, Long> getDivisorNScale(SortedMap<T, Integer> cmap, Predicate<T> predfunc)
	{
		List<Integer> okaycountlist = cmap.entrySet()
							.stream()
							.filter(me -> predfunc.test(me.getKey()))
							.map(me -> me.getValue())
							.collect(Collectors.toList());
		
		for(int div = 1; div < Short.MAX_VALUE; div *= 2)
		{
			// long scale4div = sumWithDivide(cmap, predfunc, div);
			
			long scale4div = sumWithDivide(okaycountlist, div);
			
			// Success, we found the minimum divisor that gives a sum less than MAX_SCALE
			if(scale4div < EncoderUtil.MAX_SCALE)
			{
				return Pair.build(div, scale4div);
			}			
		}
		
		return null;
	}
	
	private static long sumWithDivide(List<Integer> okaylist, int curdiv)
	{
		long t = 0L;
		
		for(int oneokay : okaylist)
		{
			t += (oneokay > curdiv ? (oneokay / curdiv) : 1);
		}
		
		return t;
	}		
	
	public static  class BufferedLookupTool<T extends Comparable<T>>
	{
		private SortedMap<T, Integer> _countMap = Util.treemap();
		
		private SortedMap<T, Integer> _extraMap = Util.treemap();
		
		private CachedSumLookup<T> _lookupTool;
		
		public BufferedLookupTool(Map<T, Integer> initmap)
		{
			_countMap.putAll(initmap);	
			
			_lookupTool = CachedSumLookup.build(_countMap);
		}
		
		public static <R extends Comparable<R>> BufferedLookupTool<R> build(Map<R, Integer> initmap)
		{
			return new BufferedLookupTool<R>(initmap);	
		}
		
		public void reportResult(T item)
		{
			Util.incHitMap(_extraMap, item);
		}
		
		public CachedSumLookup<T> getCurLookupTool()
		{
			return _lookupTool;
		}
		
		public void flush()
		{
			for(Map.Entry<T, Integer> me : _extraMap.entrySet())
			{
				Util.incHitMap(_countMap, me.getKey(), me.getValue());	
			}
			
			_extraMap.clear();
			
			_lookupTool = CachedSumLookup.build(_countMap);
		}
		
		public boolean haveBaseItem(T item)
		{
			return _countMap.containsKey(item);
		}	
		
		public boolean haveExtraItem(T item)
		{
			return _extraMap.containsKey(item);
		}
	}
	
	
	
	public static <T>  SortedMap<T, Integer> prob2CountMap(SortedMap<T, Double> doublemap)
	{
		SortedMap<T, Integer> resmap = Util.treemap();
		
		for(T key : doublemap.keySet())
		{
			int resval = (int) (doublemap.get(key) * 20000);
			resval = (resval > 0 ? resval : 1);
			resmap.put(key, resval);
		}
		
		return resmap;
	}
	
	

	

	
	
	
	public interface ContextInfo
	{
		public String getEventNameKey() ;
		
		public String getExtraInfo() ;
	}		
	
	public static double getCodeLen(ModelerTree encmod)
	{
		try { return getCodeLenT(encmod);  }
		catch (ImpossibleOutcomeException ioutex) {
			return 1e6;	
		}
	}
		
	
	public static double getCodeLenT(ModelerTree encmod) throws ImpossibleOutcomeException
	{
		CodeLengthLogger clog = new CodeLengthLogger();
		
		encmod.recModel(new EncoderHook(clog));
		
		return clog.getCodeLength();		
	}		
	
	
	public static byte[] shrink(ModelerTree encmod)
	{
		return shrink(encmod, new EmptyEventLogger());		
	}
	
	public static byte[] shrink(ModelerTree encmod, EventLogger evlog)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		HighPrecCoder.Encoder coder = new HighPrecCoder.Encoder(baos);
		
		encmod.recModel(new EncoderHook(coder, evlog));
		
		coder.flush();
		
		return baos.toByteArray();		
	}	
	
	
	public static void expand(ModelerTree decmod, byte[] bytedata)
	{
		expand(decmod, bytedata, new EmptyEventLogger());
	}
	
	public static void expand(ModelerTree decmod, byte[] bytedata, EventLogger evlog)
	{
		ByteArrayInputStream in = new ByteArrayInputStream(bytedata);	
		
		HighPrecCoder.Decoder decoder = new HighPrecCoder.Decoder(in);
		
		decmod.recModel(new EncoderHook(decoder, evlog));
		
		// Arrgh!!		
		try { in.close(); }
		catch (IOException ioex) { }		
	}	
	
	
	// This is the key utility method that transforms the data in encmod into decmod.
	public static void shrinkExpand(ModelerTree encmod, ModelerTree decmod)
	{
		shrinkExpand(encmod, decmod, new EmptyEventLogger());
	}		
	
	// This is the key utility method that transforms the data in encmod into decmod.
	public static void shrinkExpand(ModelerTree encmod, ModelerTree decmod, EventLogger evlog)
	{
		Util.massert(encmod.isEncode() && !decmod.isEncode(),
			"Wrong argument order for encoder/decoder");
		
		// ahhh
		expand(decmod, shrink(encmod, evlog));
	}	
	
	public static void sample(ModelerTree smpmod, Random jr, EventLogger samplelog)
	{
		// Don't think any sentences should be bigger than this...
		byte[] bigbuf = new byte[10000];
		jr.nextBytes(bigbuf);
		
		expand(smpmod, bigbuf, samplelog);		
	}	
	
	
	public static void sample(ModelerTree smpmod, Random jr)
	{
		// Don't think any sentences should be bigger than this...
		byte[] bigbuf = new byte[10000];
		jr.nextBytes(bigbuf);
		
		expand(smpmod, bigbuf);		
	}
	
	public static void sample(ModelerTree smpmod)
	{
		sample(smpmod, new Random());	
	}
	
	public static class FullEventRecord
	{
		public final String outcString;
		
		public final Symbol symTarg;
		
		public final int eventDepth;
		
		public final ContextInfo contextInfo;
		
		public FullEventRecord(String otcstr, Symbol symtarg, int depth, EventModeler<?> evmod)
		{
			outcString = otcstr;
			
			symTarg = symtarg;
			
			eventDepth = depth;
			
			contextInfo = evmod.getContextInfo();
		}
	}
		
	
	
	// This helps with logging 
	// Callers attach this to EventModelers, so the logging
	// system can look at the context information attached to an event.
	public static abstract class ContextInfoImpl implements ContextInfo
	{
		public String getEventNameKey()
		{
			String basestr = getClass().getSimpleName();
			
			Util.massert(basestr.endsWith("ContextInfo"), 
				"Naming convention requires these objects to have names ending with -ContextInfo, found %s", basestr);
			
			return basestr.substring(0, basestr.length()-"ContextInfo".length());
		}
		
		// subclasses override
		public String getExtraInfo()
		{ return "extrainfo"; }
	}
	

	public static class ImpossibleOutcomeException extends RuntimeException
	{
		Object _badOutcome;
		
		ContextInfo _contextInfo;
		
		Class _modClass;
		
		public ImpossibleOutcomeException(Object outc, ContextInfo cinfo, Class modclass)
		{
			_badOutcome = outc;
			_contextInfo = cinfo;
			_modClass = modclass;
		}
		
		public String getMessage()
		{
			String errmssg = Util.sprintf("Outcome %s::%s not found in in modeler PDF", 
							_badOutcome.getClass().getSimpleName(), _badOutcome);
			
			if(_contextInfo != null)
				{ errmssg += Util.sprintf(" Context is %s :: %s ", _contextInfo.getEventNameKey(), _contextInfo.getExtraInfo()); }
			else
				{ errmssg += " no context info set "; }
			
			return errmssg;
		}
	}
	
	public static class GenericContextInfo implements ContextInfo
	{
		private String _evKey;
		// private String _exInfo;
		
		private List<Object> _extraList;
		
		public GenericContextInfo(String evkey, Object... varglist)
		{
			_evKey = evkey;
			_extraList = Util.listify(varglist);
		}		
		
		
		public String getEventNameKey()
		{
			return _evKey;
		}	
		
		public String getExtraInfo()
		{
			return Util.join(_extraList, "::");
		}
	}	
	
	
	public static class EmptyEventLogger implements EventLogger
	{
		public <T extends Comparable<T>> void logEvent(EventModeler<T> evmod, T outcome, Symbol symtarg, int depth) {} 
	}
	
	public static class CodeLengthLogger implements EventLogger
	{
		private double _codeLen = 0D;
		
		public <T extends Comparable<T>> void logEvent(EventModeler<T> evmod, T outcome, Symbol symtarg, int depth)
		{
			// Util.pf("EvMod is %s, outcome is %s, symtarg is %s, code is %.03f\n",
			//	evmod.getClass().getSimpleName(), outcome, symtarg, symtarg.getCode());
			
			_codeLen += symtarg.getCode();	
			
		}
		
		public double getCodeLength()
		{
			return _codeLen;	
		}
	}	
}
