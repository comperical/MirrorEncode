
package net.danburfoot.encoder; 

import java.io.*; 
import java.math.BigInteger;

import net.danburfoot.shared.*;
import net.danburfoot.encoder.EncoderUtil.*;

public abstract class HighPrecCoder 
{ 
	long cde; 
	long low; 
	long hgh; 
	
	long underFlowBits;
	
	ArithBitio bitio; 
	
	public static final int MAX_BITS = EncoderUtil.MAX_BITS;
	
	// Max value allowable, all bits set
	
	// 1111111.....11111
	public static long HIGH_MAX;
	
	// Single bit, in second-highest position
	// 01000000....00000
	public static long HALF_MAX;
	
	// Single bit, in third-highest position.
	// 00100000....00000
	public static long QUARTMAX;
	
	static
	{
		BigInteger highMaxCalc = BigInteger.ZERO;
		for(int i = 0; i < MAX_BITS; i++)
			{ highMaxCalc = highMaxCalc.setBit(i); }
		
		HIGH_MAX = highMaxCalc.longValue();
		
		HALF_MAX = BigInteger.ZERO.setBit(MAX_BITS-1).longValue();
		QUARTMAX = BigInteger.ZERO.setBit(MAX_BITS-2).longValue();
	}
	
	// public PrintStream logStr = null; 
	
	/*
	public void closeLogFile() throws IOException 
	{ 
		if(logStr != null)
			logStr.close();
	}
	*/
	
	/**
	* This routine must be called to initialize the encoding process.
	* The high register is initialized to all 1s, and it is assumed that
	* it has an infinite string of 1s to be shifted into the lower bit
	* positions when needed.
	*/
	public HighPrecCoder(ArithBitio bitio)
	{
		hgh = HIGH_MAX;		
		low = 0;
		cde = 0;
		
		underFlowBits = 0;				
		this.bitio = bitio; 
	}
	
	void rescale(Symbol s)
	{ 
		Util.massert(s.getTotScale() > 0, "Symbol with zero scale found");
		
		// These three lines rescale high and low for the new symbol.		
		//long range = (long) ( hgh-low ) + 1;
		BigInteger range = BigInteger.valueOf(hgh-low+1);
		long prvlow = low;
		
		hgh = range.multiply(BigInteger.valueOf(s.getHghCount())).divide(BigInteger.valueOf(s.getTotScale())).longValue();
		low = range.multiply(BigInteger.valueOf(s.getLowCount())).divide(BigInteger.valueOf(s.getTotScale())).longValue();
		
		hgh += prvlow-1;
		low += prvlow;
		
		//hgh = (long) (low + (( range * s.getHghCount() ) / s.getTotScale() - 1 ));  
		//low = (long) (low + (( range * s.getLowCount() ) / s.getTotScale()     )); 

		if(low > hgh)
		{ 
			// Thank god this doesn't happen anymore.
			// System.out.printf("\nRange is %d, symL/H/S = %d/%d/%d", 
			//	range, s.getLowCount(), s.getHghCount(), s.getTotScale()); 
			
			throw new RuntimeException("Encoding error: low > hgh");
		} 
	} 
	
	// Conceptually, this operation sloughs bits off the "left" side of the queue,
	// and adds a new "1" bit to the right side of the queue for the HGH value.
	void ship()
	{ 
		low <<= 1;
		hgh <<= 1;
		cde <<= 1;
		
		// Need to get rid of the bits above the max threshold 
		hgh &= HIGH_MAX; 
		low &= HIGH_MAX; 		
		cde &= HIGH_MAX;
		
		hgh |= 1;
	} 	
	
	
	public static class Decoder extends HighPrecCoder implements AcDecoder
	{ 
		/**
		* This routine is called to initialize the state of the arithmetic
		* decoder.  This involves initializing the high and low registers
		* to their conventional starting values, plus reading the first
		* 16 bits from the input stream into the code value.
		*/
		public Decoder(InputStream in)
		{ 
			super(new ArithBitio(in)); 
			
			// Read first 16 bits into cde
			for ( int i = 0 ; i < MAX_BITS ; i++ )
			{
				cde <<= 1;
				cde += bitio.inputBit();
			}		
		}
		
		public boolean isEncode()
		{
			return false;	
		}
		
		/**
		* Just figuring out what the present symbol is doesn't remove
		* it from the input bit stream.  After the character has been
		* decoded, this routine has to be called to remove it from the
		* input stream.
		*/
		public void removeSymbol(Symbol s)
		{
			rescale(s); 
			
			// if(logStr != null)
			//	logStr.printf("\n%s", s);
				//logStr.printf("\nlow is %d, hgh is %d, cde is %d", low, hgh, cde);  
			
			// Next, any possible bits are shipped out.
			for ( ; ; )
			{
				// If the MSDigits match, the bits will be shifted out.
				if ( ( hgh & HALF_MAX ) == ( low & HALF_MAX ) ) { } 
				
				// Else, if underflow is threatining, shift out the 2nd MSDigit.
				else if ((low & QUARTMAX) == QUARTMAX  && (hgh & QUARTMAX) == 0 )
				{
					cde ^= QUARTMAX;
					low &= (QUARTMAX-1);
					hgh |= QUARTMAX;
				}
				// Otherwise, nothing can be shifted out, so I return.
				else
					{ return; } 
				
				
				ship(); 
				
				cde += bitio.inputBit();
			}
		}
		
		/**
		* When decoding, this routine is called to figure out which symbol
		* is presently waiting to be decoded.  This routine expects to get
		* the current model scale in the s.scale parameter, and it returns
		* a count that corresponds to the present floating point code:
		*
		*  code = count / s.scale
		*/
		public long getCurrentCount( long scale )
		{
			BigInteger count = BigInteger.valueOf(cde-low+1).multiply(BigInteger.valueOf(scale));
			count = count.subtract(BigInteger.ONE).divide(BigInteger.valueOf(hgh-low+1));
			
			//range = (long) (hgh-low) + 1;
			//count =  ((((long) (cde-low) + 1) * scale-1) / range);
			
			if(count.longValue() < 0)
				throw new RuntimeException("Count is negative."); 
			
			//if(logStr != null) 
			//	logStr.printf(", cnt is %d", count);  
			
			return count.longValue();
		}	
	}
	
	public static class Encoder extends HighPrecCoder implements AcEncoder
	{ 		
		public Encoder(OutputStream ot)
		{ super(new ArithBitio(ot)); } 
		
		
		public boolean isEncode()
		{
			return true;	
		}		
		
		/**
		* This routine is called to encode a symbol.  The symbol is passed
		* in the SYMBOL structure as a low count, a high count, and a range,
		* instead of the more conventional probability ranges.  The encoding
		* process takes two steps.  First, the values of high and low are
		* updated to take into account the range restriction created by the
		* new symbol.  Then, as many bits as possible are shifted out to
		* the output stream.  Finally, high and low are stable again and
		* the routine returns.
		*/
		public void encodeSymbol(Symbol s)
		{
			rescale(s); 
			
			// if(logStr != null)
			//	logStr.printf("\n%s", s);
			//logStr.printf("\nlow is %d, hgh is %d", low, hgh);  
			
			// Turns out new bits until high and low are far enough
			// apart to have stabilized
			for ( ; ; )
			{
				// If this test passes, it means that the MSDigits match, 
				// and can be sent to the output stream.			
				if ( ( hgh & HALF_MAX ) == ( low & HALF_MAX ) )
				{
					bitio.outputBit((hgh & HALF_MAX) > 0 ? 1 : 0);
					
					while ( underFlowBits > 0 )
					{
						// TODO: what does this really do? 
						bitio.outputBit((~hgh & HALF_MAX) > 0 ? 1 : 0);
						underFlowBits--;
					}
				}
				
				// If this test passes, the numbers are in danger of underflow, because
				// the MSDigits don't match, and the 2nd digits are just one apart.
				else if(((low & QUARTMAX) != 0) && ((hgh & QUARTMAX) == 0))
				{
					underFlowBits += 1;
					low &= (QUARTMAX-1);
					hgh |= QUARTMAX;
				}
				else
					{ return; }   		 		
				
				ship(); 
			}
		}
		
		/**
		* At the end of the encoding process, there are still significant
		* bits left in the high and low registers.  We output two bits,
		* plus as many underflow bits as are necessary.
		* TODO: this should really be called close()
		*/
		public void flush()
		{
			bitio.outputBit((low & QUARTMAX) > 0 ? 1 : 0);
			underFlowBits++;
			while ( underFlowBits-- > 0 )
				bitio.outputBit((~low & QUARTMAX) > 0 ? 1 : 0);
			
			bitio.flushNClose(); 
		}	
	} 
	
	

}	



