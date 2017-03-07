#!/usr/bin/python

import re, os, sys, fileinput

from shutil import copyfile

# TODO: even this is not really necessary, we can get the package list from 
# Builder4J
def getPackageList():
	return LocalConf.getPackageList()
 	
def getClassFileCount(dstdir):
	
	kflist = [kidfile for kidfile in os.listdir(dstdir) if kidfile.endswith(".class")]
	
	return len(kflist)
		
def getPickupPackList():
	
	jb = LocalConf.getBuilder4J()
	jb.findPackSet()
	
	packlist = getPackageList()
	
	for i in range(len(packlist)):
		
		classdstdir = jb.getDstDir4ShortPack(packlist[i])		
		
		if getClassFileCount(classdstdir) == 0:
			return packlist[i:]
			

	print "Attempted to resume, but found no empty class file directories, maybe you need to run CleanJClass?"
	exit(1)
	
 	
def buildPackList(packlist):
	
	# First clear out the compile error file
	if os.path.exists(LocalConf.getCompErrorPath()):
		os.remove(LocalConf.getCompErrorPath())
		print "Deleted old compile error file"
	
	jb = LocalConf.getBuilder4J()
	
	jb.addExtraArgs("-Xlint:unchecked -Xlint:deprecation")
	jb.findPackSet()
	
	for onepack in packlist:
		jb.buildSimplePack(onepack)
		
		# If there are compile errors, don't try to keep going
		if os.path.getsize(LocalConf.getCompErrorPath()) > 0:
			print "Found compile errors in package %s" % onepack
			
			# Okay, want to delete class files in the output directory, so that the "pickup" 
			# option will work.
			delcall = "rm %s/*.class" % (jb.getDstDir4ShortPack(onepack))
			os.system(delcall)			
			break
		
def getSrcFileList():
	return ["EncoderUtil", "Symbol", "HighPrecCoder", "EventModeler", "ArithBitio", "ModelerTree"]
		
def copyCode2Dir():
	
	for onesrc in getSrcFileList():
		
		farrpath = "/userdata/crm/src/java/encoder/%s.java" % (onesrc)
		nearpath = "/userdata/external/mirrenc/java/encoder/%s.java" % (onesrc)
		
		print "Going to copy %s ---> %s" % (farrpath, nearpath)
		
		# Change file to writable
		chmodcall = "chmod 644 %s" % (nearpath)
		os.system(chmodcall)		
		
		copyfile(farrpath, nearpath)
		
		# Change file back to read-only
		chmodcall = "chmod 444 %s" % (nearpath)
		os.system(chmodcall)

def compileDir(dirname):

	jcall = "javac -cp /userdata/external/mirrenc/jclass -d /userdata/external/mirrenc/jclass /userdata/external/mirrenc/java/%s/*.java" % (dirname)
	
	print("%s" % (jcall))
	
	os.system(jcall)


if __name__ == "__main__":

	""" 
	1) Copy the code from the main trunk
	2) Compile it
	""" 
	
	copyCode2Dir()

	compileDir('encoder')
	compileDir('examp4enc')

