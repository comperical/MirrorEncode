
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;
import java.util.function.*;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;


public class BiasedDiceExample
{
	static SortedMap<Integer, Integer> getUniform6Model()
	{
		SortedMap<Integer, Integer> unimap = Util.treemap();
		
		for(int i : Util.range(1, 7))
			{ unimap.put(i, 1); }
		
		return unimap;
	}
	
	static SortedMap<Integer, Integer> getBiased6Model()
	{
		SortedMap<Integer, Integer> biasmap = Util.treemap();
		
		biasmap.put(6, 100);
		
		for(int i : Util.range(1, 6))
			{ biasmap.put(i, 10); }

		return biasmap;
	}	
	
	
	public static class Flat6SideModel extends ModelerTree<List<Integer>>
	{
		SortedMap<Integer, Integer> _probMap;
		
		List<Integer> _mirrList = Util.listify();
		
		final int numSamp;
		
		Flat6SideModel(SortedMap<Integer, Integer> probmap, int numsamp)
		{
			_probMap = probmap;
			
			numSamp = numsamp;
		}
		
		@Override 
		protected void setOriginalSub()
		{	
			Util.massert(numSamp == _origOutcome.size(),
				"Model built with N=%d, but outcome list size is %d", numSamp, _origOutcome.size());
			
		}
		
		public void recModel(EncoderHook enchook)
		{
			while(_mirrList.size() < numSamp)
			{
				EventModeler<Integer> evmod = EventModeler.build(_probMap);
				
				if(isEncode())
				{
					int next2send = _origOutcome.get(_mirrList.size());
					evmod.setOriginal(next2send);
				}
				
				int mirror = evmod.decodeResult(enchook);
				
				_mirrList.add(mirror);
			}
		}
		
		public List<Integer> getResult()
		{
			return _mirrList;
		}
	}
	
	
	public static class Adaptive6SideModel extends ModelerTree<List<Integer>>
	{
		BufferedLookupTool<Integer> _buffTool;
		
		List<Integer> _mirrList = Util.listify();
		
		final int numSamp;
		
		static final int SAMPLE_PER_FLUSH = 100;
		
		Adaptive6SideModel(int numsamp)
		{
			numSamp = numsamp;
			
			Map<Integer, Integer> initmap = Util.treemap();
			
			for(int i : Util.range(1, 7))
				{ initmap.put(i, 10); }
			
			_buffTool = new BufferedLookupTool(initmap);
		}
		
		@Override 
		protected void setOriginalSub()
		{	
			Util.massert(numSamp == _origOutcome.size(),
				"Model built with N=%d, but outcome list size is %d", numSamp, _origOutcome.size());
			
		}
		
		public void recModel(EncoderHook enchook)
		{
			while(_mirrList.size() < numSamp)
			{
				EventModeler<Integer> evmod = EventModeler.build(_buffTool.getCurLookupTool());
				
				if(isEncode())
				{
					int next2send = _origOutcome.get(_mirrList.size());
					evmod.setOriginal(next2send);
				}
				
				int mirror = evmod.decodeResult(enchook);
				_mirrList.add(mirror);
				_buffTool.reportResult(mirror);
				
				if(_mirrList.size() % SAMPLE_PER_FLUSH == 0)
					{ _buffTool.flush(); }
			}
		}
		
		public List<Integer> getResult()
		{
			return _mirrList;
		}
	}
		
	
	static double encodeAndCheck(SortedMap<Integer, Integer> probmap, List<Integer> data)
	{
		Flat6SideModel encmod = new Flat6SideModel(probmap, data.size());
		Flat6SideModel decmod = new Flat6SideModel(probmap, data.size());
		
		return encodeAndCheck(encmod, decmod, data);
	}
	
	static double encodeAndCheck(ModelerTree<List<Integer>> encmod, ModelerTree<List<Integer>> decmod, List<Integer> data)
	{
		encmod.setOriginal(data);
		
		CodeLengthLogger codelog = new CodeLengthLogger();
		EncoderUtil.shrinkExpand(encmod, decmod, codelog);
		
		Util.massert(data.equals(decmod.getResult()), "Encoding error");
		return codelog.getCodeLength();
	}	
	

}	
