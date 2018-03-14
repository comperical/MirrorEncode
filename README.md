
### Lossless BYOM Data Compression with the Mirror Technique

This code is an implementation of the arithmetic encoding method for lossless data compression using the "mirror" technique.
Arithmetic encoding is powerful because it enables the encoder to be adapative - the model can change after every update.
More importantly, the system can use a suite of different event/outcome types,
	and the overall progression of the encoding procedure
	can depend in arbitrary ways on the results of different events. 
As a simple example, a compressor can first send the length of a String, 
	and then execute a for-loop for a number of times equal to the length, 
	sending an individual character in each iteration.
	
A major difficulty of building complex data compression systems
	is the problem of **keeping the encoder and decoder in synch**.
If the internal data structures used by the two programs
	are different by even a single number,
	the compression process will derail hopelessly.

(Before developing this technique, 
	I spent many long, frustrated nights trying vainly to debug
	these types of errors.)
 
The mirror technique avoids this problem by running the SAME code for both the encoder and the decoder.
The encoder builds up a "mirror image" of the data that the decoder sees,
	and refers to the mirror data to perform all calculations. 
For example, if we are using an adaptive scheme where
	the model is updated with statistics with each incoming data point,
	the encoder will refer to information in the mirror package
	to perform these updates, 
	even though it has access to the original.
In this example code snippet,
	with the sole exception of the if block under `isEncode`,
	the entire process is run exactly the same way in both programs.	
	
<pre>
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
</pre>
	
	
	
	
Using the mirror technique, we can easily build up very complex compression methods
	that require complex control flow branching, that depends on the 
	values of the data being sent.
In addition, compressor code written using the mirror technique is generally quite compact,
	partly for the simple reason that both the encoder and decoder run the same method.
One of the programs in the example package (AdaptivePredictionModeler)
	shows a quite effective image compressor that requires only about 100 lines. 
	
This package has several pieces of example code that serve to illustrate the techniques.
The examples are meant to be illustrative, not practically useful.

The most technically sophisticated example is the WordBasedModeler in the StringEncDemo.
This example shows how the encoder can take different logical paths 
	through the encoding process, depending on the data it is sending. 
In particular, it detects whether the next block of text to send is a 
	miscellaneous character (punctuation or whitespace) or a regular word string. 
If it is just a character,
	it transmits the character using a simple character model. 
If it is a full word,
	it encodes the longest prefix of the word that has been seen in the data so far.
Then it transmits any additional characters required to fill out the word. 
Of course, it must transmit an additional flag to the decoder to indicate
	which option is coming down the pipe (misc. char or full word).

### Potential Use Cases

There are a lot of interesting use cases where you might want to use this technique.
The main limitation of this code in comparison to other methods of lossless data compression are:
	1) you must supply your own model
	2) it is not optimized for speed
Given those limitations, what might it be useful for?

**Experimentation and rapid prototyping for data compression**.
High-performance, industrial strength
	data compression techniques are time-consuming to develop.
	Furthermore, because of the No Free Lunch Theorem of data compression, you can never really know 
	in advance what compression rates a new method will achieve on a particular data set.
So you can use this library as a rapid-prototyping tool.
You build the new method using MirrEnc and test it out on the target data.
If it succeeds in achieving a desired compression rate, 
	you can take the time to code up a fast implementation.
If not, go back to the drawing board and keep experimenting.

**Validate the performance of your machine-learning models**.
I had a friend who tried his hand at algorithmic trading using ML techniques.
His method was doing well for a couple of weeks,
	but then he made a minor Python indentation mistake,
	and because of that he lost tens of thousands of dollars.
The basic problem is that ML models (and numeric computation in general)
	cannot be unit- and regresion-tested like other types of software.
Hooking up your ML model to a data compression test harness allows you
	to be very confident you haven't introduced any bugs in your development cycle.
To do this, transform your ML model into a compressor using MirrEnc,
	and run the newest version of your code against some standard benchmark data set every night.
Then if you introduce a bug, you will either see an absolute compression failure
	(the input doesn't match the output) or a spike in the compression rate.

**Pedagogical Tool in Data Science/Information Theory/Statistics class**.
A lot of ideas in the world of data science, information theory, and statistics can be off-puttingly abstract.
What, exactly, does the Kullback-Liebler divergence mean? 
What's the relationship between conditional entropy and mutual information?
Students can connect these abstract ideas to real, tangible code 
	using data compression software.
For example, students can demonstrate by direct verification the basic 
	fact that the best compression rate for a data set is achieved by using
	a model that matches the distribution that generated the data set

**Build Complex, Multi-Model Data Compressors**.
This is the application for which I originally developed the technique.
In my work in Natural Language Processing,
	I compress sentences using a highly complex model
	that uses dozens of different submodels, rules, filters, and event types.
For example, there is a component of this model 
	that "knows" about English verb conjugation,
	and uses this information to save bits when compressing grammatical English sentences.
Due to the complexity of this model, 
	it would be impossible to overcome the encoder/decoder synchronization problem
	mentioned above without the mirror technique
	(and the resulting code would also be unbearably ugly and hard to maintain).

	

### Installation

1. create an installation directory. 
1. unzip the source code or clone the git repository into that directory
1. In this example, the installation directory is /Users/burfoot/Desktop/mirrtest:


<pre>
bash-3.2$ pwd
/Users/burfoot/Desktop/mirrtest
bash-3.2$ ls
LICENSE	data	java	jclass	script
</pre>

Edit the `script/CompJava.py` file so that the INSTALL_DIR variable is your install directory:

<pre>
INSTALL_DIR = "/Users/burfoot/Desktop/mirrtest"
</pre>

Now compile the code by running `CompJava.py`. You could also just run the appropriate `javac` commands yourself.

<pre>
bash-3.2$ cd script/
bash-3.2$ ./CompJava.py 
javac -cp /Users/burfoot/Desktop/mirrtest/jclass -d /Users/burfoot/Desktop/mirrtest/jclass /Users/burfoot/Desktop/mirrtest/java/encoder/*.java
javac -cp /Users/burfoot/Desktop/mirrtest/jclass -d /Users/burfoot/Desktop/mirrtest/jclass /Users/burfoot/Desktop/mirrtest/java/examp4enc/*.java
</pre>

Check that the Java class files have appeared in the `jclass` directory:

<pre>
bash-3.2$ find jclass | head
jclass
jclass/net
jclass/net/danburfoot
jclass/net/danburfoot/encoder
jclass/net/danburfoot/encoder/ArithBitio.class
jclass/net/danburfoot/encoder/EncoderUtil$1.class
jclass/net/danburfoot/encoder/EncoderUtil$AcDecoder.class
jclass/net/danburfoot/encoder/EncoderUtil$AcEncoder.class
jclass/net/danburfoot/encoder/EncoderUtil$BufferedLookupTool.class
jclass/net/danburfoot/encoder/EncoderUtil$CachedSumLookup.class
</pre>

### Running the Examples

There are two main example types, one for images and one for text. 
There are a couple of test data items in the data/ directory.

Both types are invoked using the `RunExample.py` script. To run the text encoder, use the following command:

<pre>
./RunExample.py RunStrEncDemo modeltype=<modelname> bookname=<bookname>
</pre>

For example, the following command runs the "wordbased" model on the book "Sherlock":

<pre>
bash-3.2$ ./RunExample.py RunStrEncDemo modeltype=wordbased bookname=Sherlock
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry RunStrEncDemo modeltype=wordbased bookname=Sherlock installdir=/Users/burfoot/Desktop/mirrtest
Got string length 594915 for book Sherlock
Encode success confirmed, required 224575 bytes, 0.377 byte/char, took 4.242 sec
</pre>


For text encoding, the model name options are:

1. "dumb"  - simplistic uniform encoding, achieves no more or less than 1 byte/character
1. "unigram" - unigram character modeling, adapts to encode more common characters with shorter codes
1. "bigram" - bigram character modeling, tries to predict the next character from the preceding character
1. "trigram" - same as bigram except used previous two characters
1. "wordbased" - the most complex and powerful, encodes based on the probability of whole words. 

Most of these encoding options are basically quite simple, but the `wordbased` one uses a couple of good tricks.
This compression method provides a good example of the power of the mirror-encoding style,
	because it would otherwise be quite difficult to avoid encoder/decoder synchronization bugs for the word-based model. 
	
For image encoding, the system can be run using a command of the form:

<pre>
./RunExample.py TestImageEncode modelername=<modelname> bitmapname=<bitmapname>
</pre>

<pre>
bash-3.2$ ./RunExample.py TestImageEncode modelername=adaptivebasic bitmapname=OrigImage
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry TestImageEncode modelername=adaptivebasic bitmapname=OrigImage installdir=/Users/burfoot/Desktop/mirrtest
Using modeler class AdaptiveBasicModeler
Encoded byte size is 2299941, extra size is 0, pixel count is 2359296,  7.799 bit/pixel, took 4.666 sec
</pre>

For image encoding, the model options are:

1. "uniform" - simplistic uniform modeling, should get 1 byte/pixel
1. "adaptivebasic" - unigram pixel modeling, adjusts to prefer more common pixel values
1. "offlinepredict" - for each prediction pixel, calculates a full distribution of probabilities for the next pixel. 
	Writes this data to a Gzip file, that is supposed to be sent "offline".
	Encoder and decoder use this data for prediction.
	This code reports the additional cost of the Gzip file to avoid "cheating".
1. "adaptivepredict" - like offline predict, but adaptively computes the distribution as the image is being sent.

To encode an image, it must be in Bitmap for in the data/image directory. You can prepare a PNG image as follows:

<pre>
bash-3.2$ ./RunExample.py PrepareImageFile imagepath=/Users/burfoot/Desktop/SimpleArrow.png
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry PrepareImageFile imagepath=/Users/burfoot/Desktop/SimpleArrow.png installdir=/Users/burfoot/Desktop/mirrtest
Wrote bit map file to /Users/burfoot/Desktop/mirrtest/data/image/SimpleArrow.bmp
</pre>


