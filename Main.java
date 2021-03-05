import LLVM_Generation.GeneratorVisitor;
import TypeChecking.InitVisitor;
import TypeChecking.TypeCheckingVisitor;
import syntaxtree.Goal;

import java.io.*;

public class Main {

    public static void main (String [] args){
        for(String currFilePath : args) {

            //
            try {
                File myObj = new File(currFilePath.substring(0,currFilePath.length()-5)+".ll");
                if (myObj.createNewFile()) {
                    CheckFile(currFilePath,myObj);
                } else {
                    System.out.println("File already exists.");
                }
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
            //

        }
    }

    public static void CheckFile(String filePath,File writeFile){
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(filePath);
            MiniJavaParser parser = new MiniJavaParser(fis);
            Goal root = parser.Goal();
            //System.err.println("Program parsed successfully.");

            try {


                try {
                    FileWriter myWriter = new FileWriter(writeFile.getAbsolutePath());

                    //InitVisitor initializes some data structures.
                    InitVisitor init = new InitVisitor();
                    root.accept(init, null);
                    TypeCheckingVisitor typechecker = new TypeCheckingVisitor(init.GetSymbolTable());
                    root.accept(typechecker,null);
                    //System.err.println("Program is semantically correct.");
                    //typechecker.PrintOffsets();
                    GeneratorVisitor gen = new GeneratorVisitor(init.GetSymbolTable(),myWriter);
                    root.accept(gen,null);

                    myWriter.close();
                } catch (IOException e) {
                    System.out.println("An error occurred.");
                    e.printStackTrace();
                }

            }catch(RuntimeException ex){
                System.out.println(ex.toString());
            }

        } catch(ParseException ex){
            System.out.println(ex.getMessage());
        } catch(FileNotFoundException ex){
            System.err.println(ex.getMessage());
        } finally{
            try{
                if(fis != null) fis.close();
            } catch(IOException ex){
                System.err.println(ex.getMessage());
            }
        }
    }
}
