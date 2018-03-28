
package net.danburfoot.examp4enc;

import java.io.*;
import java.util.*;


import net.danburfoot.shared.*;
import net.danburfoot.shared.Util.*;

import net.danburfoot.encoder.*;
import net.danburfoot.encoder.EncoderUtil.*;

public class ExampleUtil
{
	private static String _INSTALL_DIR;
	
	public enum EncodeDataType
	{
		image,
		text,
		stockraw,
		stockprep,
		statistical;
	}
	
	static void setInstallDir(String idir)
	{
		File installdir = new File(idir);
		
		Util.massert(installdir.exists(), "Installation directory %s does not exist", idir);
		Util.massert(installdir.isDirectory(), "Install dir path %s is not a director", idir);
		
		_INSTALL_DIR = installdir.getAbsolutePath();
	}
	
	static String getInstallDir()
	{
		Util.massert(_INSTALL_DIR != null,
			"Install Dir has not been set, you must call setInstallDir(..)");
		
		return _INSTALL_DIR;
	}

	
	public static String getDataFilePath(EncodeDataType dt, String subfile)
	{
		Util.massert(!subfile.contains("/"), 
			"Subfile path should be simple, found %s, don't include full directory path", subfile);
		
		return Util.sprintf("%s/data/%s/%s", getInstallDir(), dt, subfile);
	}
	
	public static File getDataFile(EncodeDataType dt, String subfile)
	{
		File datafile = new File(getDataFilePath(dt, subfile));
		
		// Util.massert(datafile.exists(), "Data File %s does not exist, did you spell correctly?", datafile.getAbsolutePath());
		
		return datafile;
	}
	
	public static File getImageFile(String bmppath)
	{
		Util.massert(bmppath.endsWith(".bmp"),
			"By convention, the BitMap file path should end with .bmp, found %s", bmppath);
		
		return getDataFile(EncodeDataType.image, bmppath);
	}
	
	public static File getBookFile(String txtfile)
	{
		Util.massert(txtfile.endsWith(".txt"),
			"By convention, the Book file path should end with .txt, found %s", txtfile);
		
		return getDataFile(EncodeDataType.text, txtfile);		
	}
	
	
	public static List<Integer> generateDataFromModel(SortedMap<Integer, Integer> probmap, int N, Random jr)
	{
		Util.massert(probmap.size() == 6 && probmap.containsKey(6),
						"Bad probmap keys for a 6-sided die");
		
		int total = Util.reduce(probmap.values(), 0, pr -> pr._1+pr._2);
		
		List<Integer> reslist = Util.listify();
		
		for(int i : Util.range(N))
		{
			int next = jr.nextInt(total);
			
			reslist.add(lookupSample(probmap, next));
		}
		
		return reslist;
	}
	
	private static <K extends Comparable<K>> K lookupSample(SortedMap<K, Integer> probmap, int randint)
	{
		for(K key : probmap.keySet())
		{
			int v = probmap.get(key);
			
			if(randint < v)
				{ return key; }
			
			randint -= v;
		}
		
		Util.massert(false, "Input number too large");
		return null;
	}	
	
}	
