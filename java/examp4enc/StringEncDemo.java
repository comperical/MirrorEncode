
package net.danburfoot.examp4enc;

import java.util.*;
import java.io.*;

import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

public class StringEncDemo
{
	public static abstract class TextModeler extends ModelerTree<String>
	{
		protected StringBuffer _mirrBuf = new StringBuffer();
		
		public final int numChar;
		
		TextModeler(int nc)
		{
			numChar = nc;	
		}
		
		@Override
		public String getResult()
		{
			return _mirrBuf.toString();	
		}
		
	}
	
	static SortedMap<Character, Integer> getInitMap()
	{
		SortedMap<Character, Integer> cmap = Util.treemap();
		
		for(int i : Util.range(256))
			{ cmap.put((char) i, 1); }
		
		return cmap;
	}
	
	public static class DumbUniformModeler extends TextModeler
	{
		// {{{
		
		private CachedSumLookup<Character> _dumbLookup;
		
		public DumbUniformModeler(int nc)
		{
			super(nc);
		}
		
		public void recModel(EncoderHook enchook)
		{
			_dumbLookup = CachedSumLookup.build(getInitMap());
			
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
		
		// }}}
	}
	
	public static class UnigramModeler extends TextModeler
	{
		// {{{
		
		static final int FLUSH_INTERVAL = 100;
		
		private SortedMap<Character, Integer> _statMap = Util.treemap();
		
		private CachedSumLookup<Character> __cachedLookup = null;
		
		public int rebuildCount = 0;
		
		public UnigramModeler(int nc)
		{
			super(nc);
			
			// initialize the statistics map
			for(int i : Util.range(256))
				{ _statMap.put((char) i, 1); }	
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
		
		// }}}
	}	
	
	public static class BigramAdaptiveModeler extends TextModeler
	{
		// {{{
		
		private SortedMap<Character, SortedMap<Character, Integer>> _dataMap = Util.treemap();
		
		private Map<Character, CachedSumLookup<Character>> _lookupMap = Util.treemap();
		
		private int _charPerFlush = 1000;
		
		public BigramAdaptiveModeler(int nc)
		{
			super(nc);
			
			// Initialize each submap
			for(int i : Util.range(256))
			{
				char a = (char) i;
				_dataMap.put(a, getInitMap());
			}
		}
		
		public void recModel(EncoderHook enchook)
		{
			for(int pos = 0; pos < numChar; pos++)
			{
				char prevchar = pos == 0 ? 'A' : _mirrBuf.charAt(pos-1);
				
				EventModeler<Character> evmod = EventModeler.build(getLookupMap(prevchar));
				
				if(isEncode())
				{ 
					char tosend = _origOutcome.charAt(pos);	
					evmod.setOriginal(tosend);
				}

				char reschar = evmod.decodeResult(enchook);
				
				_mirrBuf.append(reschar);
				
				// Update the specific bigram map
				Util.incHitMap(_dataMap.get(prevchar), reschar, 1);
				
				if((_mirrBuf.length() % _charPerFlush) == 0)
					{ _lookupMap.clear(); }
			}
		}		
		
		private CachedSumLookup<Character> getLookupMap(char prevchar)
		{
			if(_lookupMap.isEmpty())
			{
				// Util.pf("Rebuilding lookup map....\n");
				
				for(Character c : _dataMap.keySet())
					{ _lookupMap.put(c, CachedSumLookup.build(_dataMap.get(c))); }
			}
			
			return _lookupMap.get(prevchar);
		}
		
		// }}}
	}		

	public static class TrigramAdaptiveModeler extends TextModeler
	{
		// {{{
		
		private SortedMap<String, BufferedLookupTool<Character>> _bufferMap = Util.treemap();
		
		private LinkedList<Character> _charHistory = new LinkedList<Character>();
		
		private int _charPerFlush = 10_000;
		
		public TrigramAdaptiveModeler(int nc)
		{
			super(nc);
			
			// Initialize each submap
			for(int i : Util.range(256))
			{
				for(int j : Util.range(256))
				{
					char a = (char) i;
					char b = (char) j;
					
					String keystr = getKeyString(a, b);
				
					_bufferMap.put(keystr, BufferedLookupTool.build(getInitMap()));
				}
			}
			
			_charHistory.add('a');
			_charHistory.add('a');
		}
		
		private String getKeyString(char a, char b)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(a);
			sb.append(b);
			return sb.toString().toLowerCase();
		}
		
		private String getHistoryKey()
		{
			return getKeyString(_charHistory.get(0), _charHistory.get(1));	
		}
		
		
		public void recModel(EncoderHook enchook)
		{
			for(int pos = 0; pos < numChar; pos++)
			{
				String prevkey = getHistoryKey();
				
				EventModeler<Character> evmod = EventModeler.build(_bufferMap.get(prevkey).getCurLookupTool());
				
				if(isEncode())
				{ 
					char tosend = _origOutcome.charAt(pos);	
					evmod.setOriginal(tosend);
				}

				char reschar = evmod.decodeResult(enchook);
				
				_mirrBuf.append(reschar);
				
				// Update the specific bigram map
				_bufferMap.get(prevkey).reportResult(reschar);
				
				if((_mirrBuf.length() % _charPerFlush) == 0)
				{
					_bufferMap.values().stream().forEach(blt -> blt.flush());
				}
				
				_charHistory.pollFirst();
				_charHistory.add(reschar);
			}
		}		
		
		// }}}
	}			
	
	public static class WordBasedModeler extends TextModeler
	{
		// {{{
		private static final Character END_EXTRA_CHAR = '.';
		
		// Additional characters to complete a word appears
		private BufferedLookupTool<Character> _extraDataBuffer;
		
		// Misc punctuation, for encoding non-word data
		private BufferedLookupTool<Character> _miscDataBuffer;
		
		// Statistics for complete words
		private BufferedLookupTool<String> _wordDataBuffer;
		
		private BufferedLookupTool<Boolean> _isMiscBuffer; 
		
		private int _charPerFlush = 1000;
		private int _nextFlushPoint;
		
		public WordBasedModeler(int nc)
		{
			super(nc);
			
			{
				Map<Character, Integer> extrainit = Util.treemap();
				Map<Character, Integer> miscinit = Util.treemap();
				
				for(int i : Util.range(256))
				{
					char c = (char) i;	
					
					if(Character.isLetterOrDigit(c))
						{ extrainit.put(c, 1); }
					else
						{ miscinit.put(c, 1); }
				}
				
				// Special addition to ExtarMap
				extrainit.put(END_EXTRA_CHAR, 10);
				
				_extraDataBuffer = new BufferedLookupTool<Character>(extrainit);
				_miscDataBuffer  = new BufferedLookupTool<Character>(miscinit);
			}
			
			{
				Map<String, Integer> wordmap = Util.treemap();
				wordmap.put("", 1);
				_wordDataBuffer = new BufferedLookupTool<String>(wordmap);
			}
			
			
			{
				SortedMap<Boolean, Integer> miscmap = Util.treemap();
				miscmap.put(true , 10);
				miscmap.put(false, 10);				
				_isMiscBuffer = BufferedLookupTool.build(miscmap);
			}
			
			
			_nextFlushPoint = _charPerFlush;
		}
		
		public void recModel(EncoderHook enchook)
		{
			
			while(_mirrBuf.length() < numChar)
			{
				// At the beginning of every loop, check to see if we should flush the cached lookups
				maybeFlushData();
				
				// This is the next block of characters we are going to send
				// It is either a full word, or a piece of punctuation
				String nextblock = isEncode() ? getNextBlock() : null;
				
				// Util.pf("Next Block is %s\n", nextblock);
				
				// First we have to encode a true/false indicating whether the block is a word or misc punctuation.
				boolean mirrmisc; 
				{
					EventModeler<Boolean> evmod = EventModeler.build(_isMiscBuffer.getCurLookupTool());
					
					if(isEncode())
					{
						boolean origmisc = (nextblock.length() == 1 && _miscDataBuffer.haveBaseItem(nextblock.charAt(0)));
						evmod.setOriginal(origmisc);
					}
					
					mirrmisc = evmod.decodeResult(enchook);
					
					_isMiscBuffer.reportResult(mirrmisc);
				}
				
				if(mirrmisc)
				{
					// Here, we are dealing with a simple punctuation character. Nothing fancy
					EventModeler<Character> evmod = EventModeler.build(_miscDataBuffer.getCurLookupTool());					
					
					if(isEncode())
					{
						evmod.setOriginal(nextblock.charAt(0));
					}
					
					char mirrchar = evmod.decodeResult(enchook);
				
					_miscDataBuffer.reportResult(mirrchar);
					
					_mirrBuf.append(mirrchar);
					
					continue;
				} 
				
				// Okay, we are dealing with a real word
				StringBuilder mirrword = new StringBuilder();
				{
					// First append the best match from the current dictionary
					EventModeler<String> evmod = EventModeler.build(_wordDataBuffer.getCurLookupTool());	
					
					if(isEncode())
					{
						String origmatch = getBestMatch(nextblock);	
						// Util.pf("Best match for block %s=%s\n", nextblock, origmatch);
						// getWordLookupTool().debugShowMap();
						evmod.setOriginal(origmatch);
					}
										
					String mirrmatch = evmod.decodeResult(enchook);
					mirrword.append(mirrmatch);
				}
				
				// Now encode additional characters to fill out the string from nextblock
				while(true)
				{
					EventModeler<Character> evmod = EventModeler.build(_extraDataBuffer.getCurLookupTool());					
					
					if(isEncode())
					{
						char nextorig = (mirrword.length() < nextblock.length() ? nextblock.charAt(mirrword.length()) : END_EXTRA_CHAR);	
						evmod.setOriginal(nextorig);
					}
					
					char mirrchar = evmod.decodeResult(enchook);
					
					_extraDataBuffer.reportResult(mirrchar);
					
					if(mirrchar == END_EXTRA_CHAR)
						{ break; }
					
					mirrword.append(mirrchar);
				}
				
				// Add the mirror word to the text buffer
				_mirrBuf.append(mirrword);
				
				_wordDataBuffer.reportResult(mirrword.toString());
			}
		}
		
		// Flush data if it is time to go to next flush point.
		private void maybeFlushData()
		{
			if(_mirrBuf.length() > _nextFlushPoint)
			{
				_wordDataBuffer.flush();
				_miscDataBuffer.flush();
				_extraDataBuffer.flush();
				_isMiscBuffer.flush();
				 
				_nextFlushPoint += _charPerFlush;
			}
		}

		// Find the longest prefix of FULLWORD that is in the current dictionary
		// Observe we need to use the haveBaseItem method.
		private String getBestMatch(String fullword)
		{
			for(int i = fullword.length(); i > 0; i--)
			{
				String checkstr = fullword.substring(0, i);
				
				if(_wordDataBuffer.haveBaseItem(checkstr))
					{ return checkstr; }
			}
			
			return "";	
		}

		// Gets the next "block" of the input string. 
		// This will be either a 1-character "miscellaneous" string, eg punctuation or whitespace
		// Or a multi-char string of adjacent letters+digits
		private String getNextBlock()
		{
			StringBuilder sb = new StringBuilder();
			
			char firstchar = _origOutcome.charAt(_mirrBuf.length());
			sb.append(firstchar);
			
			if(Character.isLetterOrDigit(firstchar))
			{			
				// Let's arbitrarily use 20 as a max length here
				for(int i = 1; i < 20; i++)
				{
					char nextchar = _origOutcome.charAt(_mirrBuf.length() + i);
					
					if(!Character.isLetterOrDigit(nextchar))
						{ break; }
					
					sb.append(nextchar);
				}
			}
			
			return sb.toString();
		}
		
		// }}}
	}			
	
	
	static ModelerTree<String> getTextModeler(String mtype, int numchar)
	{
		if(mtype.equals("dumb"))
			{ return new DumbUniformModeler(numchar); }
		
		if(mtype.equals("unigram"))
			{ return new UnigramModeler(numchar); }
		
		if(mtype.equals("bigram"))
			{ return new BigramAdaptiveModeler(numchar); }	
		
		if(mtype.equals("trigram"))
			{ return new TrigramAdaptiveModeler(numchar); }	
		
		if(mtype.equals("wordbased"))
			{ return new WordBasedModeler(numchar); }			
		
		
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
	
	public static String getBookData(String booklabel, int maxlen) throws IOException
	{
		File bookfile = ExampleUtil.getBookFile(booklabel+".txt");
		
		List<String> datalist = Util.readLineList(bookfile.getAbsolutePath());
		
		return removeNonAscii(Util.join(datalist, "\n"), maxlen);
	}	
}	
