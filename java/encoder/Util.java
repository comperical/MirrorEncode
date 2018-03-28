package net.danburfoot.shared;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.function.*;

/**
* Copy of subset of DCB Util code
*/
public abstract class Util
{
	public static <A, B> TreeMap<A,B> treemap()
	{
		return new TreeMap<A,B>();
	}	
	
	public static <A, B> LinkedHashMap<A,B> linkedhashmap()
	{
		return new LinkedHashMap<A,B>();
	}		
	
	public static <A> Vector<A> vector()
	{
		return new Vector<A>();
	}	
	
	@SafeVarargs
	public static <A> Vector<A> listify(A... elems)
	{
		Vector<A> mv = vector();
		for(A a : elems)
			{ mv.add(a); }
		return mv;
	}
	
	
	public static String join(List<? extends Object> olist, String glue)
	{
		return olist.toString();	
	}
	
	public static double curtime()
	{
		return System.currentTimeMillis();	
	}
	
	public static <A, B> B reduce(Collection<A> mycol, B initval, Function<Pair<B, A>, B> myfunc)
	{	
		B myval = initval;
		
		for(A item : mycol)
		{
			myval = myfunc.apply(Pair.build(myval, item));
		}
		
		return myval;
	}	
		
	@SuppressWarnings("unchecked")	
	public static <A> A cast(Object o)
	{
		return (A) o;
	}
	
	public static <T> void incHitMap(SortedMap<T, Integer> countmap, T key)
	{
		incHitMap(countmap, key, 1);	
	}
	
	public static <T> void incHitMap(SortedMap<T, Integer> countmap, T key, int incval)
	{
		int origval = countmap.containsKey(key) ? countmap.get(key) : 0;
		countmap.put(key, origval + incval);
	}
	
	
	public static String sprintf(String formatCode, Object... args)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		(new PrintStream(baos)).printf(formatCode, args);
		// baos.close(); Java API docs sez: closing ByteArrayOutputStream has no effect
		return baos.toString();
	}	
	
	public static void pf(String x, Object... vargs)
	{
		System.out.printf(x, vargs);
	}
	
	
	public static void massert(boolean t, String pfmessg, Object... pfargs)
	{
		if(!t)	
		{
			// System.out.printf("\n\n");	
			throw new RuntimeException("\nassertion failed: " + sprintf(pfmessg, pfargs));
		}		
	}
	
        public static List<Integer> range(int s, int n)
        {
		List<Integer> x = vector();
		
		for(int i = s; i < n; i++)
			{ x.add(i); }
		
		return x;        	
        }
	
	public static List<Integer> range(int n)
	{
		return range(0, n);
	}	
	
	public static class Pair<A, B> implements Serializable, Comparable<Pair<A, B>>
	{
		
		public A _1;
		public B _2;
		
		public Pair(A a, B b)
		{
			_1 = a;
			_2 = b;
		}
		
		public int compareTo(Pair<A, B> that)
		{
			Comparable<A> ca = Util.cast(_1);
			
			int f = ca.compareTo(that._1);
			
			if(f != 0 || !(_2 instanceof Comparable))
				return f;
			
			Comparable<B> cb = Util.cast(_2);
			return cb.compareTo(that._2);
		}	
		
		
		public Pair<B, A> reversePair()
		{
			return Pair.build(_2, _1);
		}
		
		public boolean equals(Object t)
		{
			Pair<A, B> that = Util.cast(t);
			return _1.equals(that._1) && _2.equals(that._2);
		}
		
		public String toString()
		{
			return Util.sprintf("[%s, %s]", _1, _2);
		}
		
		public static <A, B> Pair<A, B> build(A a, B b)
		{
			return new Pair<A, B>(a, b);
		}
		
		public static <A, B> Pair<A, B> build(Map.Entry<A, B> mapent)
		{
			return new Pair<A, B>(mapent.getKey(), mapent.getValue());
		}
		
		public static <A, B> List<A> getOneList(Collection<Pair<A, B>> paircol)
		{
			List<A> flist = Util.vector();
			
			for(Pair<A, B> onepair : paircol)
				{ flist.add(onepair._1); }
			
			return flist;
		}
		
		public static <A, B> List<B> getTwoList(Collection<Pair<A, B>> paircol)
		{
			List<B> flist = Util.vector();
			
			for(Pair<A, B> onepair : paircol)
				{ flist.add(onepair._2); }
			
			return flist;
		}		
	}
	
	public static abstract class ArgMapRunnable
	{
		protected ArgMap _argMap = new ArgMap();
		
		public void initFromArgMap(ArgMap amap)
		{
			_argMap = amap;	
		}
		
		public abstract void runOp() throws Exception;
	
		public void runOpE() 
		{
			try { runOp(); }
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}	
		
	
	public static void writeData2Path(Collection datacol, String fpath) throws Exception
	{
		BufferedWriter bwrite = new BufferedWriter(new FileWriter(fpath));
		
		for(Object o : datacol)
		{
			bwrite.write(o.toString());
			bwrite.write("\n");
		}
		
		bwrite.close();
	}
	
	public static List<String> readLineList(String fpath) throws IOException
	{
		return readLineList(fpath, s -> s);	
	}
	
	
	public static <A> List<A> readLineList(String fpath, Function<String, A> myfunc) throws IOException
	{
		InputStream instream = new FileInputStream(new File(fpath));
		
		if(fpath.endsWith(".gz"))
			{ instream = new GZIPInputStream(instream); }
		
		BufferedReader bread = new BufferedReader(new InputStreamReader(instream));
		
		List<A> flist = Util.vector();
		
		while(true)
		{
			String s = bread.readLine();
			
			if(s == null)
				{ break; }
			
			flist.add(myfunc.apply(s));
		}
		
		bread.close();
		
		return flist;
	}
	
	public static <A> void writeLineList(File fpath, Collection<A> itemcol, Function<A, String> myfunc) throws IOException
	{
		OutputStream outstream = new FileOutputStream(fpath);
		
		if(fpath.getAbsolutePath().endsWith(".gz"))
			{ outstream = new GZIPOutputStream(outstream); }
		
		PrintWriter pwrite = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outstream)));
		
		for(A item : itemcol)
		{
			String s = myfunc.apply(item);
			pwrite.write(s);
			pwrite.write("\n");
		}
		
		pwrite.close();
	}
}
