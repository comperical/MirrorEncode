
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;

import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

public class StringEncDemo
{
	public static class DumbUniformModeler extends ModelerTree<String>
	{
		private StringBuffer _mirrBuf = new StringBuffer();
		
		public final int numChar;
		
		private CachedSumLookup<Character> _dumbLookup;
		
		private SortedMap<Character, Integer> simpleMap = Util.treemap();
		
		public DumbUniformModeler(int nc)
		{
			numChar = nc;
			
			buildLookup();
		}
		
		public String getResult()
		{
			return _mirrBuf.toString();	
		}
		
		public void recModel(EncoderHook enchook)
		{
			for(int pos = 0; pos < numChar; pos++)
			{
				EventModeler<Character> evmod = EventModeler.build(_dumbLookup);
				
				if(isEncode())
				{ 
					char tosend = _origOutcome.charAt(pos);
					evmod.setOriginal(tosend);
				}

				evmod.recModel(enchook);
				
				char reschar = evmod.getResult();
				
				_mirrBuf.append(reschar);
			}
		}		
		
		private void buildLookup()
		{
			SortedMap<Character, Integer> cmap = Util.treemap();
			
			for(int i : Util.range(256))
				{ cmap.put((char) i, 1); }	
		
			_dumbLookup = CachedSumLookup.build(cmap);
		}
	}
	
	
	public static class UnigramModeler extends ModelerTree<String>
	{
		static final int FLUSH_INTERVAL = 100;
		
		private StringBuffer _mirrBuf = new StringBuffer();
		
		public final int numChar;
		
		private SortedMap<Character, Integer> _statMap = Util.treemap();
		
		private CachedSumLookup<Character> __cachedLookup = null;
		
		public int rebuildCount = 0;
		
		public UnigramModeler(int nc)
		{
			numChar = nc;
			
			// initialize the statistics map
			for(int i : Util.range(256))
				{ _statMap.put((char) i, 1); }	
		}
		
		public String getResult()
		{
			return _mirrBuf.toString();	
		}
		
		public void recModel(EncoderHook enchook)
		{
			for(int pos = 0; pos < numChar; pos++)
			{
				EventModeler<Character> evmod = EventModeler.build(getCachedSumLookup());
				
				if(isEncode())
				{ 
					char tosend = _origOutcome.charAt(pos);	
					evmod.setOriginal(tosend);
				}

				evmod.recModel(enchook);
				
				char reschar = evmod.getResult();
				
				_mirrBuf.append(reschar);
				
				// Increment the count map for the result character by one.
				Util.incHitMap(_statMap, reschar, 1);
				
				if((_mirrBuf.length() % FLUSH_INTERVAL) == 0)
					{ __cachedLookup = null; }	
					
			}
		}	
		
		private CachedSumLookup<Character> getCachedSumLookup()
		{
			if(__cachedLookup == null)
			{
				__cachedLookup = CachedSumLookup.build(_statMap);		
				rebuildCount++;
			}
			
			return __cachedLookup;
		}
	}	
	
	
	public static class BigramAdaptiveModeler extends ModelerTree<String>
	{
		private StringBuffer _mirrBuf = new StringBuffer();
		
		public final int numChar;
		
		private SortedMap<Character, SortedMap<Character, Integer>> bigramMap = Util.treemap();
		
		public BigramAdaptiveModeler(int nc)
		{
			numChar = nc;
			
			// Initialize each submap
			for(int i : Util.range(256))
			{
				char a = (char) i;
				
				SortedMap<Character, Integer> submap = Util.treemap();
				
				for(int j : Util.range(256))
				{
					char b = (char) j;
					
					submap.put(b, 1);
				}
				
				bigramMap.put(a, submap);
			}
		}
		
		public String getResult()
		{
			return _mirrBuf.toString();	
		}
		
		public void recModel(EncoderHook enchook)
		{
			for(int pos = 0; pos < numChar; pos++)
			{
				char prevchar = pos == 0 ? 'A' : _mirrBuf.charAt(pos-1);
				
				SortedMap<Character, Integer> submap = bigramMap.get(prevchar);
				
				EventModeler<Character> evmod = EventModeler.build(submap);
				
				if(isEncode())
				{ 
					char tosend = _origOutcome.charAt(pos);	
					evmod.setOriginal(tosend);
				}

				evmod.recModel(enchook);
				
				char reschar = evmod.getResult();
				
				_mirrBuf.append(reschar);
				
				// Update the specific bigram map
				Util.incHitMap(bigramMap.get(prevchar), reschar, 1);
			}
		}		
	}		

	
	static ModelerTree<String> getTextModeler(String mtype, int numchar)
	{
		if(mtype.equals("dumb"))
			{ return new DumbUniformModeler(numchar); }
		
		if(mtype.equals("unigram"))
			{ return new UnigramModeler(numchar); }
		
		if(mtype.equals("bigram"))
			{ return new BigramAdaptiveModeler(numchar); }			
		
		throw new RuntimeException("Unknown modeler code: " + mtype);
	}	
	
	static String removeNonAscii(String datastr, int trunclen)
	{
		StringBuffer sb = new StringBuffer();
		
		for(int i = 0; i < datastr.length(); i++)
		{
			char c = datastr.charAt(i);
			
			if(((int) c) >= 256)
				{ continue; }
			
			
			sb.append(c);
			
			if(sb.length() >= trunclen)
				{ break; }
		}
		
		return sb.toString();
	}	
	
	public static String getBookData(String label, int maxlen) throws IOException
	{
		String datapath = "/userdata/external/mirrenc/data/text/DiplomaticCorresp.txt";			
		
		List<String> datalist = Util.readLineList(datapath);
		
		return removeNonAscii(Util.join(datalist, "\n"), maxlen);
	}	
	
		
}	
