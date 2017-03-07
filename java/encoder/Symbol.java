
package net.danburfoot.encoder; 

import net.danburfoot.shared.*;

public class Symbol
{ 
	public final long hghCount; 
	public final long lowCount; 
	public final long totScale;
	
	// Use primitive doubles here that are initialized to -1
	private double _symProb = -1D;
	private double _symCode = -1D;
	
	
	public Symbol(long low, long hgh, long s)
	{
		lowCount = low;
		hghCount = hgh;
		
		totScale = s;
	}
	
	public long getHghCount() { return hghCount; }
	public long getLowCount() { return lowCount; }
	public long getTotScale() { return totScale; }
	
	public double getProb()
	{
		if(_symProb < 0)
		{
			_symProb = hghCount;
			_symProb -= lowCount;
			_symProb /= totScale;
		}
		
		return _symProb;
	}
	
	public double getCode()
	{
		if(_symCode < 0)
		{
			// This initializes _symProb, if it's not already
			getProb();
			
			_symCode = -Math.log(_symProb);
			_symCode *= EncoderUtil.INV_LOG2; 
		}
		
		return _symCode;	
	}
	
	
	public String toString()
	{
		return Util.sprintf("low=%d, hgh=%d, scl=%d", lowCount, hghCount, totScale);
	}
	
	public boolean isOkay()
	{
		return lowCount >= 0 && 
			hghCount > 0 &&
			lowCount < hghCount &&
			hghCount <= totScale;
	}

	@Override
	public boolean equals(Object o)
	{
		Symbol that = (Symbol) o;
		
		return this.lowCount == that.lowCount && 
			this.hghCount == that.hghCount && 
			this.totScale == that.totScale;
	}
} 

