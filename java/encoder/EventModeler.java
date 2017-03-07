package net.danburfoot.encoder;

import net.danburfoot.shared.*;

import java.io.*;
import java.util.*;

import net.danburfoot.encoder.EncoderUtil.*;

/**
 * Leaf of the modeler tree.  
 * This is an actual Event/Outcome
*/
public class EventModeler<R extends Comparable<R>> extends ModelerTree<R>
{
	protected R _mirrOutcome;
	
	// This is for logging purposes only.
	protected ContextInfo _contextInfo;
	
	private final LookupMap<R> probMap;
	
	private EventModeler(LookupMap<R> pmap)
	{
		Util.massert(pmap != null,
			"Attempt to build EventModeler with null LookupMap");
		
		probMap = pmap;	
	}
	
	public static <T extends Comparable<T>> EventModeler<T> build(LookupMap<T> pmap)
	{
		return new EventModeler<T>(pmap);	
	}
	
	public static <T extends Comparable<T>> EventModeler<T> build(SortedMap<T, Integer> pmap)
	{
		return new EventModeler<T>(new CachedSumLookup<T>(pmap));	
	}
	
	public static <T extends Comparable<T>> EventModeler<T> buildUniform(Collection<T> pcol)
	{
		return build(EncoderUtil.buildUniformCountMap(pcol));
	}

	
	@Override
	public void setOriginalSub()
	{
		if(!probMap.hasOutcome(_origOutcome))
		{
			throw new ImpossibleOutcomeException(_origOutcome, _contextInfo, this.getClass());
		}
	}
	
	public R getResult()
	{
		Util.massert(_mirrOutcome != null,
			"Mirror outcome is null for ContextInfo %s, did you call recEncode(...)?", _contextInfo);
		
		return _mirrOutcome;
	}

	@Override
	public void recModel(EncoderHook enchook)
	{
		// Util.massert(_contextInfo != null, "Got null ContextInfo for EventModeler");
		
		if(enchook.isEncode)
			{ shrink((HighPrecCoder.Encoder) enchook.encOrDec, enchook.evLog); }			
		else
			{ expand((HighPrecCoder.Decoder) enchook.encOrDec, enchook.evLog); }		 
	}
	
	private void expand(HighPrecCoder.Decoder decoder, EventLogger evlog)
	{
		long scale = probMap.getScale();
		
		long count = decoder.getCurrentCount(scale);
		
		R outc = probMap.decodeOutcome(count);
		
		Symbol symtarg = probMap.lookupSymbol(outc);
		
		decoder.removeSymbol(symtarg);

		_mirrOutcome = outc;
			
		evlog.logEvent(this, outc, symtarg, -1);
	}	

	// PRNZ::LazyLookupMap::Lookup maps should only construct CDFs when required
	private void shrink(HighPrecCoder.Encoder encoder, EventLogger evlog)
	{
		Util.massert(isEncode(), "Attempt to called shrink(..) in decode mode");

		_mirrOutcome = _origOutcome;
		
		// Okay, this is an attempt to do a FAST probability calculation without
		// building big cum sum maps.
		Symbol symtarg = encoder == null ? 
					probMap.getFastProbSymbol(_origOutcome) : 
					probMap.lookupSymbol(_origOutcome) ;
		
		// This happens when we want to just get the codelength.
		if(encoder != null)
		{ 	
			encoder.encodeSymbol(symtarg); 
		}		
		
		evlog.logEvent(this, _origOutcome, symtarg, -1);
	}
	
	
	public EventModeler<R> setContextInfo(ContextInfo cinfo) 
	{
		_contextInfo = cinfo; 
		return this;
	}
	
	public EventModeler<R> setContextInfo(String gen1)
	{
		return setContextInfo(gen1, "xxx");	
	}
	
	public EventModeler<R> setContextInfo(String gen1, Object... varglist) 
	{
		return setContextInfo(new GenericContextInfo(gen1, varglist));
	}	
	
	public ContextInfo getContextInfo() { return _contextInfo; }	
	
	
	public void debugShowMap()
	{
		probMap.debugShowMap();
	}
}
