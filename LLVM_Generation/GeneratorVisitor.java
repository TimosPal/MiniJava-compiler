package LLVM_Generation;

import TypeChecking.ClassT;
import TypeChecking.FunctionT;
import TypeChecking.SymbolTable;
import syntaxtree.*;
import visitor.GJDepthFirst;

import java.io.FileWriter;
import java.util.LinkedList;
import java.util.Map;

public class GeneratorVisitor extends GJDepthFirst<String, Object> {

    SymbolTable symbolTable;
    int varCounter;
    int labelCounter;
    int indentLevel;
    FileWriter writer;

    Map<String,String> funDeclHash;

    private void Emmit(String str){
        //System.out.println(str);
        try {
            writer.write(str+"\n");
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    private String GetVar(){
        return String.format("_%d.t",varCounter++);
    }

    private String GetLabel() { return String.format("_%d.label",labelCounter++);}

    private void ResetVarCounter(){
        varCounter = 0;
    }

    private void ResetLabelCounter() { labelCounter = 0;}

    private String Indent(){
        return "\t".repeat(Math.max(0, indentLevel));
    }

    private String TypeToLLVM(String type){
        switch (type){
            case "int":
                return "i32";
            case "int[]":
                return "i32*";
            case "boolean":
                return "i1";
            case "boolean[]":
                return "i1*"; //NOTE: maybe change to i8*. -> return , assignment etc.
            default: //Class types.
                return "i8*";
        }
    }

    private String Bitcast(String fromType,String toType,String value,boolean withPar){
        if(withPar)
            return String.format("bitcast (%s %s to %s)",fromType,value,toType);
        else
            return String.format("bitcast %s %s to %s",fromType,value,toType);
    }

    private String FunctionToVtableElement(FunctionT fun){
        // Format : i8* bitcast (returnType (this,args)* @FuncName to i8*)

        StringBuilder funcPtr = new StringBuilder(String.format("%s (i8*",TypeToLLVM(fun.GetReturnType())));
        for(String varType : fun.GetArgs()){
            funcPtr.append(String.format(", %s", TypeToLLVM(varType)));
        }
        funcPtr.append(")*");
        funDeclHash.put(fun.GetName(),funcPtr.toString());

        return "i8* " + Bitcast(funcPtr.toString(),"i8*",String.format("@%s.%s",fun.GetParentClass().GetName(),fun.GetName()),true);
    }

    private String CreateVTable(ClassT classT){
        // Format : className_table  global [#funcs X i8* ] [ func_elements ]

        Map<String,FunctionT> childFuncs = SymbolTable.CreateMap();

        ClassT parentClass = symbolTable.GetClass(classT.GetExtendType());
        while (parentClass != null) { //Finds all the rest inherited functions.
            childFuncs.putAll(parentClass.GetFunctionsHash());
            parentClass = symbolTable.GetClass(parentClass.GetExtendType());
        }
        childFuncs.putAll(classT.GetFunctionsHash());

        StringBuilder vtableListValues = new StringBuilder();
        int funCounter = 0;
        for(FunctionT fun : childFuncs.values()){
            funCounter++;
            if(fun.GetName().equals("main")) { //We remove main since it's static.
                childFuncs.remove("main");
            }else{
                vtableListValues.append(String.format("%s", FunctionToVtableElement(fun)));
                if(funCounter < childFuncs.values().size())
                    vtableListValues.append(",");
            }
        }
        return String.format("@.%s_VTable = global [%s x i8*] [%s]",classT.GetName(),childFuncs.size(), vtableListValues.toString());
    }

    private String Define_DeclarationPart(FunctionT fun){
        //format : define return_type @Class.func(i8* %this, type %.argName , ...) {
        String returnTypeLLVM = TypeToLLVM(fun.GetReturnType());
        String className = fun.GetParentClass().GetName();
        String funName = fun.GetName();

        if(!funName.equals("main")) {
            StringBuilder temp = new StringBuilder(String.format("define %s @%s.%s(i8* %%this", returnTypeLLVM, className, funName));
            for(Map.Entry<String, String> currArg : fun.GetArgsHash().entrySet())
                temp.append(String.format(",%s %%.%s", TypeToLLVM(currArg.getValue()), currArg.getKey()));

            temp.append(")");
            return temp.toString();
        }else{
            return "define i32 @main()";
        }
    }

    private String Alloca(String type){
        return String.format("alloca %s",TypeToLLVM(type));
    }

    private String Store(String fromType , String fromValue , String toType , String toValue){
        return String.format("store %s %s , %s %s",fromType,fromValue,toType,toValue);
    }

    private String Load(String fromType,String fromValue,String toType,String toValue){
        return String.format("%s = load %s, %s %s",toValue,toType,fromType,fromValue);
    }

    private String Ret(String type,String value){
        return String.format("ret %s %s",type,value);
    }

    private String Add(String toValue, String id1, String id2){
        return String.format("%s = add %s %s , %s",toValue, "i32",id1,id2);
    }

    private String Sub(String toValue, String id1, String id2){
        return String.format("%s = sub %s %s , %s",toValue, "i32",id1,id2);
    }

    private String Times(String toValue, String id1, String id2){
        return String.format("%s = mul %s %s , %s",toValue, "i32",id1,id2);
    }

    private String Compare(String toValue,String id1,String id2,boolean smallerThan){
        return String.format("%s = icmp %s i32 %s , %s",toValue,(smallerThan)?"slt":"sge",id1,id2);
    }

    private boolean IsNumber(String str){
        return str.matches("-?(0|[1-9]\\d*)");
    }

    private boolean IsLiteral(String str){
        return IsNumber(str) || str.equals("true") || str.equals("false");
    }

    private String Call(String returnType,String funName,String args){
        return String.format("call %s %s(%s)",returnType,funName,args);
    }

    private int GetObjectSize(ClassT classT){
        return classT.GetMemberOffset() + 8;
    }

    private String LoadExpression(String expr,FunctionT fun){
        // Loads  if expr is a local / arg / member variable
        // Else return expr back since it's already a loaded temp.

        if(fun.GetLocalsHash().containsKey(expr)) { // If local var.
            String val = GetVar();
            String type = TypeToLLVM(fun.GetLocalsHash().get(expr));
            Emmit(Indent() + Load(type+"*", "%" + expr, type, "%" + val));
            return val;
        }

        String type = GetVariableType(expr,fun,true);
        if(type != null) {
            String val = GetMemberPtr(expr, fun);
            String temp = GetVar();
            Emmit(Indent() + Load(type + "*", "%" + val, type, "%" + temp));
            return temp;
        }

        return expr; // If temp var.
    }

    private String Getelementptr(String fromType,String fromValue,String toValue,String byteOffset){
        return String.format("%s = getelementptr %s, %s* %s, i32 %s",toValue,fromType,fromType,fromValue,byteOffset);
    }

    private int GetVarOffset(String var,FunctionT fun){
        int bytes;
        ClassT tempClass = fun.GetParentClass();
        while(tempClass != null){
            if(tempClass.GetMembersHash().containsKey(var)){
                return tempClass.GetMemberOffsetsHash().get(var);
            }
            tempClass = symbolTable.GetClass(tempClass.GetExtendType());
        }

        throw  new RuntimeException("var does not exist.");
    }

    private String GetMemberPtr(String val , FunctionT fun){
        if(!fun.GetLocalsHash().containsKey(val)) { // If it's not a local it's a member var.
            String temp = GetVar();
            Emmit(Indent() + Getelementptr("i8","%this","%" + temp,GetVarOffset(val,fun) + 8 + ""));
            String temp2 = GetVar();
            String type = GetVariableType(val,fun,true);
            Emmit(Indent() + "%" + temp2 + " = " + Bitcast("i8*",type+"*","%" + temp,false));
            return temp2;
        }else{
            return val;
        }
    }

    private String GetVariableType(String var, FunctionT fun,boolean asLLVM){
        if(fun.GetLocalsHash().containsKey(var)) { // If local var.
            String type = fun.GetLocalsHash().get(var);
            if(type.equals("String[]"))
                return type;
            else
                return (asLLVM) ? TypeToLLVM(type) : type;
        }

        ClassT tempClass = fun.GetParentClass();
        while(tempClass != null) { // If member var.
            if(tempClass.GetMembersHash().containsKey(var)) {
                return (asLLVM) ? TypeToLLVM(tempClass.GetMembersHash().get(var)) : tempClass.GetMembersHash().get((var));
            }
            tempClass = symbolTable.GetClass(tempClass.GetExtendType());
        }

        return null;
    }

    private FunctionT GetFuncFromHierachy(String name, ClassT classT){
        ClassT temp = classT;
        while(temp != null){
            if(temp.GetFunctionsHash().containsKey(name))
                return temp.GetFunctionsHash().get(name);
            temp = symbolTable.GetClass(temp.GetExtendType());
        }
        return null;
    }

    public GeneratorVisitor(SymbolTable symbolTable,FileWriter writer){
        this.symbolTable = symbolTable;
        this.varCounter = 0;
        this.labelCounter = 0;
        this.indentLevel = 0;
        this.funDeclHash = SymbolTable.CreateMap();
        this.writer = writer;

        for(ClassT currClass : symbolTable.GetClasses())
            Emmit(CreateVTable(currClass));

        //Standard declarations that have to be generated for each file.
        Emmit("\ndeclare i8* @calloc(i32, i32)\n" +
                "declare i32 @printf(i8*, ...)\n" +
                "declare void @exit(i32)\n" +
                "\n" +
                "@_cint = constant [4 x i8] c\"%d\\0a\\00\"\n" +
                "@_cOOB = constant [15 x i8] c\"Out of bounds\\0a\\00\"\n" +
                "@_cNSZ = constant [15 x i8] c\"Negative size\\0a\\00\"\n" +
                "\n" +
                "define void @print_int(i32 %i) {\n" +
                "    %_str = bitcast [4 x i8]* @_cint to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str, i32 %i)\n" +
                "    ret void\n" +
                "}\n" +
                "\n" +
                "define void @throw_oob() {\n" +
                "    %_str = bitcast [15 x i8]* @_cOOB to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
                "    call void @exit(i32 1)\n" +
                "    ret void\n" +
                "}\n" +
                "\n" +
                "define void @throw_nsz() {\n" +
                "    %_str = bitcast [15 x i8]* @_cNSZ to i8*\n" +
                "    call i32 (i8*, ...) @printf(i8* %_str)\n" +
                "    call void @exit(i32 1)\n" +
                "    ret void\n" +
                "}\n");
    }

    public String visit(NodeToken n, Object argu) { return n.toString(); } //Used for ids.
    public String visit(BooleanArrayType n, Object argu) { return "boolean[]"; } //Used for boolean array types.
    public String visit(IntegerArrayType n, Object argu) { return "int[]"; } //Used for int array types.

    /**
     * f1 -> Identifier() class name.
     * f4 -> ( MethodDeclaration() )*
     */
    public String visit(ClassDeclaration n, Object argu) {
        String name = n.f1.accept(this, argu);
        n.f4.accept(this, symbolTable.GetClass(name));
        return null;
    }

    /**
     * f1 -> Identifier() class name.
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, Object argu) {
        String name = n.f1.accept(this, argu);
        n.f6.accept(this, symbolTable.GetClass(name));
        return null;
    }

    /**
     * f1 -> Identifier()
     * f11 -> Identifier() arg
     * f15 -> ( Statement() )*
     */
    public String visit(MainClass n, Object argu) {
        ResetVarCounter();
        ResetLabelCounter();
        indentLevel = 1;

        String className = n.f1.accept(this, argu);
        ClassT currClass = symbolTable.GetClass(className);
        FunctionT fun = currClass.GetFunction("main");
        String argName = n.f11.accept(this, argu);
        AllocateLocalsAndHeaD(fun);

        n.f15.accept(this, fun);
        Emmit("\n\tret i32 0\n\n}\n");
        return null;
    }

    public void AllocateLocalsAndHeaD(FunctionT fun){
        StringBuilder temp = new StringBuilder(Define_DeclarationPart(fun) + "{");

        //Allocate args / local vars.
        for(Map.Entry<String, String> currVar : fun.GetLocalsHash().entrySet()) {
            if(currVar.getValue().equals("String[]"))
                continue;

            temp.append(String.format("\n%s%%%s = %s",Indent(), currVar.getKey(),Alloca(currVar.getValue())));
            if(fun.GetArgsHash().containsKey(currVar.getKey())){
                String fromType = TypeToLLVM(currVar.getValue());
                String fromValue = "%."+currVar.getKey();
                String toType = TypeToLLVM(currVar.getValue())+"*";
                String toValue = "%"+currVar.getKey();
                temp.append(String.format("\n%s%s",Indent(),Store(fromType,fromValue,toType,toValue)));
            }
        }
        temp.append("\n");
        Emmit(temp.toString());
    }

    /**
     * f2 -> Identifier() func name.
     * f8 -> ( Statement() )*
     * f10 -> Expression() return expr.
     */
    public String visit(MethodDeclaration n, Object argu) {
        ResetVarCounter();
        ResetLabelCounter();
        indentLevel = 1;

        ClassT currClass = (ClassT)argu;
        String funName = n.f2.accept(this, argu);
        FunctionT fun = currClass.GetFunction(funName);
        AllocateLocalsAndHeaD(fun);

        n.f8.accept(this, fun);

        String expr = n.f10.accept(this, fun);
        String returnTypeLLVM = TypeToLLVM(fun.GetReturnType());
        String val = (IsLiteral(expr)) ? expr : "%" + LoadExpression(expr,fun);
        Emmit(String.format("\n%s%s\n}\n",Indent(),Ret(returnTypeLLVM,val)));

        return null;
    }

    /** ADD.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr1 = n.f0.accept(this, argu);
        String expr2 = n.f2.accept(this, argu);

        // We make sure we don't load temporary values but only actual stack values.
        String aVal = LoadExpression(expr1,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;
        String bVal = LoadExpression(expr2,fun);
        if(!IsNumber(bVal)) bVal = "%" + bVal;

        String resultVar = GetVar();
        Emmit(Indent() + Add("%" + resultVar,aVal,bVal) + "\n");

        fun.GetRegisterHash().put(resultVar,"i32");
        return resultVar;
    }

    /** MINUS.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr1 = n.f0.accept(this, argu);
        String expr2 = n.f2.accept(this, argu);

        // We make sure we don't load temporary values but only actual stack values.
        String aVal = LoadExpression(expr1,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;
        String bVal = LoadExpression(expr2,fun);
        if(!IsNumber(bVal)) bVal = "%" + bVal;


        String resultVar = GetVar();
        Emmit(Indent() + Sub("%" + resultVar,aVal,bVal) + "\n");

        fun.GetRegisterHash().put(resultVar,"i32");
        return resultVar;
    }

    /** TIMES.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr1 = n.f0.accept(this, argu);
        String expr2 = n.f2.accept(this, argu);

        // We make sure we don't load temporary values but only actual stack values.
        String aVal = LoadExpression(expr1,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;
        String bVal = LoadExpression(expr2,fun);
        if(!IsNumber(bVal)) bVal = "%" + bVal;

        String resultVar = GetVar();
        Emmit(Indent() + Times("%" + resultVar,aVal,bVal) + "\n");

        fun.GetRegisterHash().put(resultVar,"i32");
        return resultVar;
    }

    /** <
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr1 = n.f0.accept(this, argu);
        String expr2 = n.f2.accept(this, argu);

        // We make sure we don't load temporary values but only actual stack values.
        String aVal = LoadExpression(expr1,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;
        String bVal = LoadExpression(expr2,fun);
        if(!IsNumber(bVal)) bVal = "%" + bVal;

        String resultVar = GetVar();
        Emmit(Indent() + Compare("%" + resultVar,aVal,bVal,true) + "\n");

        fun.GetRegisterHash().put(resultVar,TypeToLLVM("boolean"));
        return resultVar;
    }

    /** ( ... )
     * f1 -> Expression()
     */
    public String visit(BracketExpression n, Object argu) {
        return n.f1.accept(this, argu);
    }

    /** Print.
     * f2 -> Expression()
     */
    public String visit(PrintStatement n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f2.accept(this, argu);

        String aVal = LoadExpression(expr,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;
        Emmit(Indent() + String.format("call void (i32) @print_int(i32 %s)",aVal));
        return null;
    }

    /** new CLASS().
     * f1 -> Identifier()
     */
    public String visit(AllocationExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;

        String className = n.f1.accept(this, argu);
        ClassT classT = symbolTable.GetClass(className);
        String temp = GetVar();
        Emmit(Indent() + "%" +  temp + " = " + Call("i8*","@calloc",String.format("i32 1 , i32 %d",GetObjectSize(classT))));
        String temp2 = GetVar();
        Emmit(Indent() + "%" + temp2 + " = " + Bitcast("i8*","i8***","%" + temp,false));
        String temp3 = GetVar();
        String arrayType = String.format("[%d x i8*]",classT.GetFuncOffset() / 8);
        Emmit(Indent() + String.format("%s = getelementptr %s , %s %s , i32 0 , i32 0","%" + temp3,arrayType,arrayType+"*","@." + className +"_VTable"));
        Emmit(Indent() + Store("i8**","%" + temp3,"i8***","%" + temp2));

        fun.GetRegisterHash().put(temp,className);
        return temp;
    }

    public void CompareArrValue(String aVal,String num,boolean negArr,boolean smallerThan){
        String lab1 = GetLabel();
        String lab2 = GetLabel();
        String tempNegCheck = GetVar();
        Emmit(Indent() + Compare("%"+tempNegCheck,aVal,num,smallerThan));
        Emmit(Indent() + Branch("%"+tempNegCheck,"%"+lab1,"%"+lab2));
        Emmit(Indent() + lab1 + ":");
        if(negArr) {
            Emmit(Indent() + Call("void", "@throw_nsz", ""));
        }else{
            Emmit(Indent() + Call("void", "@throw_oob", ""));
        }
        Emmit(Indent() + Jump("%"+lab2));
        Emmit(Indent() + lab2 + ":");
    }

    /** new int[]
     * f3 -> Expression() size.
     */
    public String visit(IntegerArrayAllocationExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f3.accept(this, argu);
        String aVal = LoadExpression(expr,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;

        CompareArrValue(aVal,"0",true,true);

        String temp = GetVar();
        Emmit(Indent() + Add("%" + temp,aVal,"1"));
        String temp2 = GetVar();
        Emmit(Indent() + "%" + temp2 + " = " + Call("i8*","@calloc",String.format("i32 4 , i32 %s","%" + temp)));
        String temp3 = GetVar();
        Emmit(Indent() + "%" + temp3 + " = " + Bitcast("i8*","i32*","%" + temp2,false));
        Emmit(Indent() + Store("i32",aVal,"i32*","%" + temp3));

        fun.GetRegisterHash().put(temp3,"i32*");
        return temp3;
    }

    /** new boolean[]
     * f3 -> Expression()
     */
    public String visit(BooleanArrayAllocationExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f3.accept(this, argu);
        String aVal = LoadExpression(expr,fun);
        if(!IsNumber(aVal)) aVal = "%" + aVal;

        CompareArrValue(aVal,"0",true,true);

        String temp = GetVar();
        Emmit(Indent() + Add("%" + temp,aVal,"4"));
        String temp2 = GetVar();
        Emmit(Indent() + "%" + temp2 + " = " + Call("i8*","@calloc",String.format("i32 1 , i32 %s","%" + temp)));
        String temp3 = GetVar();
        Emmit(Indent() + "%" + temp3 + " = " + Bitcast("i8*","i32*","%" + temp2,false));
        Emmit(Indent() + Store("i32",aVal,"i32*","%" + temp3));
        String temp4 = GetVar();
        Emmit(Indent() + "%" + temp4 + " = " + Bitcast("i32*","i1*","%" + temp3,false));

        fun.GetRegisterHash().put(temp4,TypeToLLVM("boolean[]"));
        return temp4;
    }

    /** ASSIGNMENT E1 = E1.
     * f0 -> Identifier()
     * f2 -> Expression()
     */
    public String visit(AssignmentStatement n, Object argu) {
        String id = n.f0.accept(this, argu);

        FunctionT fun = (FunctionT)argu;
        String idType = GetVariableType(id,fun,true);
        if(idType != null && idType.equals("String[]")){ // Only case where arg is used is ignored.
            return null;
        }

        String expr = n.f2.accept(this, argu);
        String aVal = LoadExpression(expr,fun);
        if(!IsLiteral(aVal)) aVal = "%" + aVal;
        String bVal = GetMemberPtr(id, fun);

        Emmit(Indent() + Store(idType,aVal,idType + "*","%" + bVal));

        return null;
    }

    public String GetArrLength(String expr,FunctionT fun){
        String aVal = LoadExpression(expr,fun);

        String type = GetVariableType(expr,fun,true);
        if(type == null)
            type = fun.GetRegisterHash().get(aVal);
        String temp = GetVar();
        Emmit(Indent() + "%" + temp + " = " + Bitcast(type,"i32*","%"+aVal,false));
        String temp2 = GetVar();
        Emmit(Indent() + Getelementptr("i32","%" + temp,"%" + temp2,0 + ""));
        String temp3 = GetVar();
        Emmit(Indent() + Load("i32*","%" + temp2,"i32","%" + temp3));

        fun.GetRegisterHash().put(temp3,"i32");
        return temp3;
    }

    /** expr.length
     * f0 -> PrimaryExpression()
     */
    public String visit(ArrayLength n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f0.accept(this, argu);
        return GetArrLength(expr,fun);
    }

    /**
     * f0 -> PrimaryExpression() obj name.
     * f2 -> Identifier() fun name.
     * f4 -> ( ExpressionList() )? args.
     */
    public String visit(MessageSend n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f0.accept(this, argu);
        String aVal = LoadExpression(expr,fun);

        String type = GetVariableType(expr,fun,false); // if it's a var.
        if(type == null) // if it's a returned register.
            type = fun.GetRegisterHash().get(aVal);
        if(type == null){ // if it's this.
            type = fun.GetParentClass().GetName();
        }

        String funName = n.f2.accept(this, argu);

        FunctionT callFun = GetFuncFromHierachy(funName,symbolTable.GetClass(type));
        LinkedList<String> funArgs = new LinkedList();
        Object[] acceptArg = { argu , funArgs};
        n.f4.accept(this, acceptArg);

        StringBuilder argString = new StringBuilder();
        int i = 0;
        for(String var : funArgs){
            String tempVal = LoadExpression(var,fun);
            if(!IsLiteral(tempVal)) tempVal = "%" + tempVal;
            argString.append(", ").append(TypeToLLVM(callFun.GetArgs().get(i))).append(" ").append(tempVal);
            i++;
        }

        String temp = GetVar();
        Emmit(Indent() + "%" + temp + " = " + Bitcast("i8*","i8***","%" + aVal,false));
        String temp2 = GetVar();
        Emmit(Indent() + Load("i8***","%"+temp,"i8**","%" + temp2));
        String temp3 = GetVar();
        int offset = symbolTable.GetClass(type).GetFuncOffset(funName,symbolTable) / 8;
        Emmit(Indent() + Getelementptr("i8*","%" + temp2,"%" + temp3,offset + ""));
        String temp4 = GetVar();
        Emmit(Indent() + Load("i8**","%"+temp3,"i8*","%" + temp4));
        String temp5 = GetVar();
        Emmit(Indent() + "%" + temp5 + " = " + Bitcast("i8*",funDeclHash.get(funName),"%" + temp4,false));
        String temp6 = GetVar();
        String argList = "i8* " + "%" + aVal + argString;
        String returnType = callFun.GetReturnType();
        Emmit(Indent() + "%" + temp6 + " = " + Call(TypeToLLVM(returnType),"%" + temp5,argList));

        fun.GetRegisterHash().put(temp6,returnType);
        return temp6;
    }

    /** arg list.
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public String visit(ExpressionList n, Object argu) {
        Object[] acceptArgs = (Object[])argu;
        FunctionT fun = (FunctionT)acceptArgs[0];
        LinkedList<String> argList = (LinkedList<String>)acceptArgs[1];

        String expr = n.f0.accept(this, fun);
        argList.add(expr);
        n.f1.accept(this, acceptArgs);

        return null;
    }

    /** , expr ... , ... )
     * f1 -> Expression()
     */
    public String visit(ExpressionTerm n, Object argu) {
        Object[] acceptArgs = (Object[])argu;
        FunctionT fun = (FunctionT)acceptArgs[0];
        LinkedList<String> argList = (LinkedList<String>)acceptArgs[1];

        String expr = n.f1.accept(this, fun);
        argList.add(expr);

        return null;
    }

    /** array assignment.
     * f0 -> Identifier() arr name
     * f2 -> Expression() index
     * f5 -> Expression() val
     */
    public String visit(ArrayAssignmentStatement n, Object argu) { //TODO: out of bounds check.
        FunctionT fun = (FunctionT)argu;

        String arrayId = n.f0.accept(this, argu);
        String type = GetVariableType(arrayId,fun,true);

        String tempIndex = n.f2.accept(this, argu);
        String indexVal = LoadExpression(tempIndex,fun);
        if(!IsLiteral(indexVal)) indexVal = "%" + indexVal; //indexVal has the register or the raw value of the index

        String tempVal = n.f5.accept(this, argu);
        String val = LoadExpression(tempVal,fun);
        if(!IsLiteral(val)) val = "%" + val; // tempVal has the register of raw value of the RVALUE.

        String arrReg = LoadExpression(arrayId,fun);

        String arrSize = GetArrLength(arrayId,fun);
        CompareArrValue(indexVal,"0",false,true);
        CompareArrValue(indexVal,"%"+arrSize,false,false);

        //Do the assignment.
        String elementType = type.substring(0,type.length() - 1);
        String temp = GetVar();
        int sizeSkipOffset = (elementType.equals("i32")) ? 1 : 4;
        Emmit(Indent() + Add("%" + temp,indexVal,sizeSkipOffset + ""));
        String temp2 = GetVar();
        Emmit(Indent() + Getelementptr(elementType,"%" + arrReg,"%" + temp2,"%" + temp));
        Emmit(Indent() + Store(elementType,val,type,"%" + temp2));

        return null;
    }

    /** LOOKUP
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(ArrayLookup n, Object argu) {
        FunctionT fun = (FunctionT)argu;

        String arrExpr = n.f0.accept(this, argu);
        String arrReg = LoadExpression(arrExpr,fun); //if expr is a var.

        String type = GetVariableType(arrExpr,fun,true); // if it's a var.
        if(type == null) // if it's a returned register.
            type = fun.GetRegisterHash().get(arrReg);

        String indexExpr = n.f2.accept(this, argu);
        String indexVal = LoadExpression(indexExpr,fun);
        if(!IsLiteral(indexVal)) indexVal = "%" + indexVal; //indexVal has the register or the raw value of the index

        String arrSize = GetArrLength(arrExpr,fun);
        CompareArrValue(indexVal,"0",false,true);
        CompareArrValue(indexVal,"%"+arrSize,false,false);

        //Do the assignment.
        String elementType = type.substring(0,type.length() - 1);
        String temp = GetVar();
        int sizeSkipOffset = (elementType.equals("i32")) ? 1 : 4;
        Emmit(Indent() + Add("%" + temp,indexVal,sizeSkipOffset + ""));
        String temp2 = GetVar();
        Emmit(Indent() + Getelementptr(elementType,"%" + arrReg,"%" + temp2,"%" + temp));
        String temp3 = GetVar();
        Emmit(Indent() + Load(type,"%" + temp2,elementType,"%" + temp3));

        return temp3;
    }

    private String Branch(String value,String lab1,String lab2){
        return String.format("br i1 %s , label %s , label %s",value,lab1,lab2);
    }

    private String Jump(String lab1){
        return String.format("br label %s",lab1);
    }

    /**
     * f2 -> Expression() bool expr.
     * f4 -> Statement()
     * f6 -> Statement() else stm
     */
    public String  visit(IfStatement n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String expr = n.f2.accept(this, argu);
        String boolExpr = LoadExpression(expr,fun);
        if(!IsLiteral(boolExpr)) boolExpr = "%" + boolExpr;

        //branching.
        String lab1 = GetLabel();
        String lab2 = GetLabel();
        String lab3 = GetLabel();

        Emmit(Indent() + Branch(boolExpr,"%"+lab1,"%"+lab2));
        // if () {
            Emmit(Indent() + lab1 + ":");
            n.f4.accept(this, argu);
            Emmit(Indent() + Jump("%"+lab3));
        // } else {
            Emmit(Indent() + lab2 + ":");
            n.f6.accept(this, argu);
            Emmit(Indent() + Jump("%"+lab3));
        // }
        Emmit(Indent() + lab3 + ":");

        return null;
    }

    /** NOT
     * f1 -> Clause()
     */
    public String visit(NotExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;
        String clause = n.f1.accept(this, argu);
        String boolExpr = LoadExpression(clause,fun);
        if(!IsLiteral(boolExpr)) boolExpr = "%" + boolExpr;
        String temp = GetVar();
        Emmit(Indent() + "%" + temp + " = " + String.format("xor i1 %s , true",boolExpr));
        return temp;
    }

    /** WHILE
     * f2 -> Expression()
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, Object argu) {
        String lab1 = GetLabel();
        Emmit(Indent() + Jump("%"+lab1));
        Emmit(Indent() + lab1 + ":");

        FunctionT fun = (FunctionT)argu;
        String expr = n.f2.accept(this, argu);
        String boolExpr = LoadExpression(expr,fun);
        if(!IsLiteral(boolExpr)) boolExpr = "%" + boolExpr;

        String lab2 = GetLabel();
        String lab3 = GetLabel();

        /*while*/ Emmit(Indent() + Branch(boolExpr,"%"+lab2,"%"+lab3));
        // {
            Emmit(Indent() + lab2 + ":");
            n.f4.accept(this, argu);
            Emmit(Indent() + Jump("%"+lab1));
        // }
        Emmit(Indent() + lab3 + ":");

        return null;
    }

    /** AND.
     * f0 -> Clause()
     * f2 -> Clause()
     */
    public String visit(AndExpression n, Object argu) {
        FunctionT fun = (FunctionT)argu;

        String lab1 = GetLabel();
        String lab2 = GetLabel();
        String lab3 = GetLabel();

        String clause1 = n.f0.accept(this, argu);
        String boolExpr1 = LoadExpression(clause1,fun);
        if(!IsLiteral(boolExpr1)) boolExpr1 = "%" + boolExpr1;
        Emmit(Indent() + Branch(boolExpr1,"%"+lab2,"%"+lab1));

        Emmit(Indent() + lab1 + ":");
        Emmit(Indent() + Jump("%"+lab3));

        Emmit(Indent() + lab2 + ":");
        String clause2 = n.f2.accept(this, argu);
        String boolExpr2 = LoadExpression(clause2,fun);
        if(!IsLiteral(boolExpr2)) boolExpr2 = "%" + boolExpr2;
        Emmit(Indent() + Jump("%"+lab3));

        Emmit(Indent() + lab3 + ":");
        String temp = GetVar();
        Emmit(Indent() + "%" + temp + " = " + String.format("phi i1 [false, %s] , [%s, %s]","%"+lab1,boolExpr2,"%"+lab2));

        return temp;
    }


}
