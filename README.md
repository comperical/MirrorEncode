
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


### Installation

1. create an installation directory. 
1. unzip the source code or clone the git repository into that directory
1. In this example, the installation directory is /Users/burfoot/Desktop/mirrtest:


bash-3.2$ pwd
/Users/burfoot/Desktop/mirrtest
bash-3.2$ ls
LICENSE	data	java	jclass	script

Edit the script/CompJava.py file so that the INSTALL_DIR variable is your install directory:

INSTALL_DIR = "/Users/burfoot/Desktop/mirrtest"

- Now compile the code by running CompJava:

bash-3.2$ cd script/
bash-3.2$ ./CompJava.py 
javac -cp /Users/burfoot/Desktop/mirrtest/jclass -d /Users/burfoot/Desktop/mirrtest/jclass /Users/burfoot/Desktop/mirrtest/java/encoder/*.java
javac -cp /Users/burfoot/Desktop/mirrtest/jclass -d /Users/burfoot/Desktop/mirrtest/jclass /Users/burfoot/Desktop/mirrtest/java/examp4enc/*.java

RUNNING THE EXAMPLES:
---------------------


There are two main example types, one for images and one for text. 
There are a couple of test data items in the data/ directory.

Both types are invoked using the RunExample.py script. To run the text encoder, use the following command:

./RunExample.py RunStrEncDemo modeltype=<modelname> bookname=<bookname>

For example, the following command runs the "wordbased" model on the book "Sherlock":

./RunExample.py RunStrEncDemo modeltype=wordbased bookname=Sherlock
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry RunStrEncDemo modeltype=wordbased bookname=Sherlock installdir=/Users/burfoot/Desktop/mirrtest
Got string length 594915 for book Sherlock
Encode success confirmed, required 224575 bytes, 0.377 byte/char, took 4.242 sec

For text encoding, the model name options are:
- "dumb"  - simplistic uniform encoding, achieves no more or less than 1 byte/character
- "unigram" - unigram character modeling, adapts to encode more common characters with shorter codes
- "bigram" - bigram character modeling, tries to predict the next character from the preceding character
- "trigram" - same as bigram except used previous two characters
- "wordbased" - the most complex and powerful, encodes based on the probability of whole words. 

Most of these encoding options are basically quite simple, but the "wordbased" one uses a couple of good tricks.
This compression method provides a good example of the power of the mirror-encoding style,
	because it would otherwise be quite difficult to avoid encoder/decoder synchronization bugs for the word-based model. 
	
For image encoding, the system can be run using a command of the form:
./RunExample.py TestImageEncode modelername=<modelname> bitmapname=<bitmapname>

EG:
./RunExample.py TestImageEncode modelername=adaptivebasic bitmapname=OrigImage
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry TestImageEncode modelername=adaptivebasic bitmapname=OrigImage installdir=/Users/burfoot/Desktop/mirrtest
Using modeler class AdaptiveBasicModeler
Encoded byte size is 2299941, extra size is 0, pixel count is 2359296,  7.799 bit/pixel, took 4.666 sec

For image encoding, the model options are:
"uniform" - simplistic uniform modeling, should get 1 byte/pixel
"adaptivebasic" - unigram pixel modeling, adjusts to prefer more common pixel values
"offlinepredict" - for each prediction pixel, calculates a full distribution of probabilities for the next pixel. 
	Writes this data to a Gzip file, that is supposed to be sent "offline"
	Encoder and decoder use this data for prediction.
	This code reports the additional cost of the Gzip file to avoid "cheating"
"adaptivepredict" - like offline predict, but adaptively computes the distribution as the image is being sent.

To encode an image, it must be in Bitmap for in the data/image directory. You can prepare a PNG image as follows:

bash-3.2$ ./RunExample.py PrepareImageFile imagepath=/Users/burfoot/Desktop/SimpleArrow.png
java -cp /Users/burfoot/Desktop/mirrtest/jclass net.danburfoot.examp4enc.ExampleEntry PrepareImageFile imagepath=/Users/burfoot/Desktop/SimpleArrow.png installdir=/Users/burfoot/Desktop/mirrtest
Wrote bit map file to /Users/burfoot/Desktop/mirrtest/data/image/SimpleArrow.bmp


