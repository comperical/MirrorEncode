package net.danburfoot.encoder;

import net.danburfoot.shared.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

import net.danburfoot.encoder.EncoderUtil.*;

/**
 * One node of a Modeler process. 
 * Can be either a Tree or an Event.
*/
public abstract class ModelerTree<R>
{
	protected R _origOutcome;
	
	public abstract R getResult();

	// Okay, the pure OO style of doing this would be to mark _origOutcome as final,
	// and supply the payload in the constructor. 
	// The reason we don't do this is a subtle usage thing: the constructors 
	// for various types of modelers have complex arguments, so we want to simplify
	// the code.
	public void setOriginal(R otc)
	{
		Util.massert(otc != null,
			"Attempt to set original to null value");
		
		_origOutcome = otc;
		
		setOriginalSub();
	}
	
	public boolean isEncode()
	{
		return (_origOutcome != null);	
	}	
	
	// Subclasses override to do postprocessing, we now have _origOutcome
	protected void setOriginalSub() {}
	
	public abstract void recModel(EncoderHook enchook);
	
	public final R decodeResult(EncoderHook enchook)
	{
		recModel(enchook);
		
		return getResult();
	}
	

	protected <T extends Comparable<T>> void onEncodeSet(ModelerTree<T> submod, Function<R, T> origfunc)
	{
		if(isEncode())
		{
			submod.setOriginal(origfunc.apply(_origOutcome));	
		}
	}
	
	
	
}
