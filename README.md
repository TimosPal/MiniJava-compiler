# MiniJava-compiler
A compiler for the minijava language.
A syntax tree is generated and analyzed for possible syntax errors.
Produces intermediate code. (llvm , .ll files)

The minijava language grammar can be found here
http://www.cs.tufts.edu/~sguyer/classes/comp181-2006/minijava.html

## Compilation
A makefile is provided. While compiling all the generated files from jtb are generated using the provided jar files.

## Arguments
Multiple files can be passed to be compiled.

## Tools used
JTB , a syntax tree builder to be used with the Java Compiler Compiler (JavaCC) parser generator
