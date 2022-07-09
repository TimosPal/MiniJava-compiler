# MiniJava-compiler
A compiler for the minijava language.
A syntax tree is generated and analyzed for possible syntax errors. This is acomplished via 2 visitors. The first visitor initializes the appropriate symbol tables. The second visitor does the actuall type checking with the now populated structures. LinkedHashTables are used for the actuall representation.
Produces intermediate code. (llvm, .ll files)

The minijava language's grammar can be found here
http://www.cs.tufts.edu/~sguyer/classes/comp181-2006/minijava.html

## Compilation
A makefile is provided. While compiling all the generated files from jtb are generated using the provided jar files.

## Arguments
Multiple files can be passed to be compiled.

## Tools used
JTB , a syntax tree builder to be used with the Java Compiler Compiler (JavaCC) parser generator (appropriate .jj file included in the repo)
