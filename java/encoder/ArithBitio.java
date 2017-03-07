
package net.danburfoot.encoder; 


import java.util.*; 
import java.io.*; 

// Tool for converting from a byte stream to a bit stream
// Both backwards and forwards
public class ArithBitio 
{ 
	public static final int BUFFER_SIZE = 512; 
	
	byte[] buffer;  
	int curByte; 
	
	int outputMask; 
	
	int inputBytesLeft; 
	int inputBitsLeft; 
	int pastEof; 
	
	InputStream is; 
	OutputStream os; 
		
	public ArithBitio(InputStream is)
	{ 
		this.is = is; 
		this.os = null; 
		
		curByte = 0;
		buffer = new byte[BUFFER_SIZE+2]; 		
		outputMask = 0x80; 		
		
		/**
		 * Bit oriented input is set up so that the next time the input_bit
		 * routine is called, it will trigger the read of a new block.  That
		 * is why input_bits_left is set to 0.
		 */		
		inputBitsLeft = 0; 
		inputBytesLeft = 1; 
		pastEof = 0; 		
	} 
	
	public ArithBitio(OutputStream os)
	{ 
		this.is = null; 
		this.os = os;  
		
		curByte = 0;
		buffer = new byte[BUFFER_SIZE+2]; 		
		outputMask = 0x80; 		
	} 
	
	public void outputBit(long bit)
	{ outputBit((int) bit); }
	
	// what the hell does this do?
	public void outputBit(int bit) 
	{ 
		if(bit != 0)
			buffer[curByte] |= outputMask; 
		
		outputMask >>= 1;
		
		if(outputMask == 0)
		{ 
			outputMask = 0x80; 
			curByte++; 
			
			if(curByte == BUFFER_SIZE)
			{ 
				try { os.write(buffer, 0, BUFFER_SIZE); }
				catch (IOException ioex) { throw new RuntimeException(ioex); }
				
				curByte = 0; 
			} 
			
			buffer[curByte] = 0; 
		} 
	} 
	
	/** 
	 * When the encoding is done, there will still be a lot of bits and
	 * bytes sitting in the buffer waiting to be sent out.  This routine
	 * is called to clean things up at that point.
	 */
	void flushNClose()
	{
		// fwrite( buffer, 1, (size_t)( current_byte - buffer ) + 1, stream );				

		try {
			os.write(buffer, 0, curByte+1);
			os.close(); 
		} catch (IOException ioex) { 
			throw new RuntimeException(ioex);
		}
		
		curByte = 0;
	}	
		
	/**
	* This routine reads bits in from a file.  The bits are all sitting
	* in a buffer, and this code pulls them out, one at a time.  When the
	* buffer has been emptied, that triggers a new file read, and the
	* pointers are reset.  This routine is set up to allow for two dummy
	* bytes to be read in after the end of file is reached.  This is because
	* we have to keep feeding bits into the pipeline to be decoded so that
	* the old stuff that is 16 bits upstream can be pushed out.
	*/
	int inputBit()
	{
		if ( inputBitsLeft == 0 )
		{
			curByte++;
			inputBytesLeft--;
			inputBitsLeft = 8;
			
			if ( inputBytesLeft == 0 )
			{
				try { inputBytesLeft = is.read(buffer, 0, BUFFER_SIZE); }
				catch (IOException ioex) { throw new RuntimeException(ioex); }
				
				if (inputBytesLeft == 0)
				{
					if (pastEof != 0)
					{
						System.err.printf("\nBad input file"); 						
						System.exit(-1); 
					}
					else
					{
						pastEof = 1;
						inputBytesLeft = 2;
					}
				}
				curByte = 0; 
			}
		}
		inputBitsLeft--;
		return ( (buffer[curByte] >> inputBitsLeft) & 1);  
	}
} 
