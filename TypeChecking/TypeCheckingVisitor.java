package TypeChecking;

import syntaxtree.*;
import visitor.GJDepthFirst;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TypeCheckingVisitor extends GJDepthFirst<String, Object> {
    /*------------------------
    * Does the type checking
    * based on the symbol tables
    * produced by the previous
    * visitor.
    ------------------------*/


    private SymbolTable symbolTable;
    private boolean idAsTypes; // If true , identifiers should return the var's type not their name!

    public String visit(NodeToken n, Object argu) {
        String id = n.toString(); //Returns the type instead of the var based on the boolean value.
        return (idAsTypes) ? GetVarTypeFromScope((FunctionT)argu,id) : id;
    } //Used for ids.
    public String visit(BooleanArrayType n, Object argu) { return "boolean[]"; } //Used for boolean array types.
    public String visit(IntegerArrayType n, Object argu) { return "int[]"; } //Used for int array types.

    public static boolean IsPrimitive(String type){
        return type.equals("int") || type.equals("int[]") ||
                type.equals("boolean") || type.equals("boolean[]") ||
                type.equals("String[]");
    }

    public void PrintOffsets(){
        for(ClassT curClass : symbolTable.GetClasses()){
            curClass.PrintOffsets();
        }
    }

    public TypeCheckingVisitor(SymbolTable table){
        this.symbolTable = table;
        this.idAsTypes = false;

        //Check if undefined types are declared from visitor1.
        for(ClassT curClass : symbolTable.GetClasses()){
            for(String curMember : curClass.GetMembers()){
                if(!IsPrimitive(curMember) && symbolTable.GetClass(curMember) == null)
                    throw new RuntimeException("Undefined type " + curMember + ".");
            }
            for(FunctionT currFunc : curClass.GetFunctions()){
                for(String curLocal : currFunc.GetLocals()) {
                    if (!IsPrimitive(curLocal) && symbolTable.GetClass(curLocal) == null)
                        throw new RuntimeException("Undefined type " + curLocal + ".");
                }
            }
        }
    }

    public String GetVarTypeFromScope(FunctionT func, String id){
        //Searches outer scope as well. Throws an exception if not found.
        //Should look upwards if the variable is a member variable of a super class.
        String type;

        if((type = func.GetVarType(id)) == null){ //Check locals.
            ClassT currClass = func.GetParentClass();
            do{
                if((type = currClass.GetMemberVar(id)) == null) {
                    currClass = symbolTable.GetClass(currClass.GetExtendType());
                    if(currClass == null)
                        throw new RuntimeException("Undeclared variable " + id + ".");
                }
            }while(type == null);
        }

        return type;
    }

    /** MAIN CLASS. PROVIDES THE FUNC TO SUB CALLS. used to pass the class obj.
     * f1 -> Identifier() main class id.
     * f15 -> ( Statement() )*
     */
    public String visit(MainClass n, Object argu) {
        String classId = n.f1.accept(this, argu);
        FunctionT curFunc = symbolTable.GetClass(classId).GetFunction("main");

        n.f15.accept(this, curFunc);
        return null;
    }

    /** CLASS DECL. used to pass the class obj.
     * f1 -> Identifier()
     * f4 -> ( MethodDeclaration() )*
     */
    public String visit(ClassDeclaration n, Object argu) {
        String classId = n.f1.accept(this, argu);
        ClassT currClass = symbolTable.GetClass(classId);
        n.f4.accept(this, currClass);
        return null;
    }

    /** CLASS DECL WITH EXTEND.
     * f1 -> Identifier()
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, Object argu) {
        String classId = n.f1.accept(this, argu);
        ClassT currClass = symbolTable.GetClass(classId);
        n.f6.accept(this, currClass);
        return null;
    }

    /** FUNCTION DECL.
     * f2 -> Identifier() func name.
     * f8 -> ( Statement() )* (pass func obj)
     * f10 -> Expression() return expr (check type)
     */
    public String visit(MethodDeclaration n, Object argu) {
        String funcId = n.f2.accept(this, argu);
        FunctionT func = ((ClassT)argu).GetFunction(funcId);

        n.f8.accept(this, func);

        //The return type should be the same as the function one.
        String returnExprType = n.f10.accept(this, func);

        CheckNormalAssignmentTypes(returnExprType,func.GetReturnType());

        return null;
    }

    public void CheckNormalAssignmentTypes(String rType, String lType){ //Only used on normal assignments , not arrays.
        String tempType = rType;
        while(!tempType.equals(lType)){ //Accessing higher super class types in case the type is polymorphic.
            ClassT currClass = symbolTable.GetClass(tempType);
            if(currClass == null)
                throw new RuntimeException("Unmatched types : " + lType + " = " + rType);
            else
                tempType = currClass.GetExtendType();
        }
    }

    /** NORMAL ASSIGNMENT.
     * f0 -> Identifier() LEFT SIDE.
     * f2 -> Expression() RIGHT SIDE.
     */
    public String visit(AssignmentStatement n, Object argu) { //Needs to accept sub class types as well.
        FunctionT currFunc = (FunctionT)argu;

        String leftId = n.f0.accept(this, argu);
        String lType = GetVarTypeFromScope(currFunc,leftId);
        String rType = n.f2.accept(this, argu);

        CheckNormalAssignmentTypes(rType,lType);

        return null;
    }

    /** AT ARR INDEX ASSIGNMENT.
     * f0 -> Identifier() arr id.
     * f2 -> Expression() index.
     * f5 -> Expression() right side.
     */
    public String visit(ArrayAssignmentStatement n, Object argu) {
        FunctionT currFunc = (FunctionT)argu;

        String arrId = n.f0.accept(this, argu);
        String arrType = GetVarTypeFromScope(currFunc,arrId);

        String indexType = n.f2.accept(this, argu);
        if(!indexType.equals("int")) //Index must be of type int.
            throw new RuntimeException("Array index is not an integer.Got " + indexType + " instead.");
        String exprType = n.f5.accept(this, argu);

        //We remove the "[]" part from the arrType to compare it with the indexed type.
        String subType = arrType.substring(0,arrType.length() - 2);
        if(!subType.equals(exprType))
            throw new RuntimeException("Unmatched types : " + subType + " = " + exprType);

        return null;
    }

    /** IF EXPR STATEMENT ELSE STATEMENT
     * f2 -> Expression()
     * f4 -> Statement()
     * f6 -> Statement()
     */
    public String visit(IfStatement n, Object argu) {
        String exprType = n.f2.accept(this, argu);
        if(!exprType.equals("boolean"))
            throw new RuntimeException("If statement requires a logical expression.Got " + exprType + " instead.");
        n.f4.accept(this, argu);
        n.f6.accept(this, argu);
        return null;
    }

    /** WHILE
     * f2 -> Expression()
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, Object argu) {
        String exprType = n.f2.accept(this, argu);
        if(!exprType.equals("boolean"))
            throw new RuntimeException("while statement requires a logical expression.Got " + exprType + " instead.");
        n.f4.accept(this, argu);
        return null;
    }

    /** PRINT
     * f2 -> Expression()
     */
    public String visit(PrintStatement n, Object argu) {
        String exprType = n.f2.accept(this, argu);
        if(!exprType.equals("int"))
            throw new RuntimeException("Print only supports int values.Got " + exprType + " instead.");
        return null;
    }

    /* EXPRESSIONS --------------------------------------- */

    public String visit(IntegerLiteral n, Object argu) { return "int"; } //Int literal.
    public String visit(TrueLiteral n, Object argu) { return "boolean"; } //True literal.
    public String visit(FalseLiteral n, Object argu) { return "boolean"; } //False literal.

    /** EXPRESSIONS INSIDE OF BRACKETS.
     * f1 -> Expression()
     */
    public String visit(BracketExpression n, Object argu) { return n.f1.accept(this, argu); }

    /** NEW BOOL ARRAY [ EXPR ]
     * f3 -> Expression()
     */
    public String visit(BooleanArrayAllocationExpression n, Object argu) {
        String arrSizeType = n.f3.accept(this, argu);
        if(!arrSizeType.equals("int")){
            throw new RuntimeException("Array size is not an integer.Got " + arrSizeType + " instead.");
        }
        return "boolean[]";
    }

    /** NEW INT ARRAY [ EXPR ]
     * f3 -> Expression()
     */
    public String visit(IntegerArrayAllocationExpression n, Object argu) {
        String arrSizeType = n.f3.accept(this, argu);
        if(!arrSizeType.equals("int")){
            throw new RuntimeException("Array size is not an integer.Got " + arrSizeType + " instead.");
        }
        return "int[]";
    }

    /** NEW ID()
     * f1 -> Identifier()
     */
    public String visit(AllocationExpression n, Object argu) {
        idAsTypes = false;
        String classId = n.f1.accept(this, argu);
        if(symbolTable.GetClass(classId) == null){
            throw new RuntimeException("unknown class type " + classId + ".");
        }
        idAsTypes = true;

        return classId;
    }

    /** NOT EXPR.
     * f1 -> Clause()
     */
    public String visit(NotExpression n, Object argu) {
        String clauseType = n.f1.accept(this, argu);
        if(!clauseType.equals("boolean")){
            throw new RuntimeException("Cannot use logical not (!) on non logical clauses.");
        }

        return "boolean";
    }

    /** THIS : RETURNS THE CLASS TYPE.
     * f0 -> "this"
     */
    public String visit(ThisExpression n, Object argu) {
        return ((FunctionT)argu).GetParentClass().GetName();
    }

    /** PRIMARY EXPRESSIONS. USED ONLY TO TURN ON THE ID-AS-TYPES BOOLEAN.
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */
    public String visit(PrimaryExpression n, Object argu) {
        // When reading primary expressions we might read an identifier
        // which should be returned as a type not as its name. For this reason
        // we enable the boolean idAsTypes.
        // [Exception] : allocation expression requires the name hence the re-enabling.

        idAsTypes = true;
        String type = n.f0.accept(this, argu);
        idAsTypes = false;
        return type;
    }

    //Used in && , < , + , * , - for checking whether or not the types are correct.
    public void ExprOpHelper(String type1,String type1Proper, String type2,String type2Proper, String op){
        if(!(type1.equals(type1Proper) && type2.equals(type2Proper)))
            throw new RuntimeException("Unmatched types [" + type1 + "," + type2 + "] in operator (" + op + ").");
    }

    /** && AND.
     * f0 -> Clause()
     * f2 -> Clause()
     */
    public String visit(AndExpression n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);
        ExprOpHelper(type1,type2,type2,type1,"&&");
        return "boolean";
    }

    /** < LESS THAN.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);
        ExprOpHelper(type1,"int",type2,"int","<");
        return "boolean";
    }

    /** PLUS.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);
        ExprOpHelper(type1,"int",type2,"int","+");
        return "int";
    }

    /** MINUS.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);
        ExprOpHelper(type1,"int",type2,"int","-");
        return "int";
    }

    /** TIMES.
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);
        ExprOpHelper(type1,"int",type2,"int","*");
        return "int";
    }

    /** ARRAY LOOKUP. expr [ expr ]
     * f0 -> PrimaryExpression()
     * f2 -> PrimaryExpression()
     */
    public String visit(ArrayLookup n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        String type2 = n.f2.accept(this, argu);

        if(!type1.contains("[]")) //Only accept array types.
            throw new RuntimeException(type1 + " is not an array.");
        if(!type2.equals("int"))
            throw new RuntimeException("Lookup index should be an integer.Got " + type2 + " instead.");

        return type1.substring(0,type1.length() - 2); //Return sub-type.
    }

    /** LENGTH. expr.length
     * f0 -> PrimaryExpression()
     */
    public String visit(ArrayLength n, Object argu) {
        String type1 = n.f0.accept(this, argu);
        if(!type1.contains("[]")) //Only accept array types.
            throw new RuntimeException(type1 + " is not an array.");
        return "int";
    }

    /** expr.id(expr?)
     * f0 -> PrimaryExpression()
     * f2 -> Identifier()
     * f4 -> ( ExpressionList() )?
     */
    public String visit(MessageSend n, Object argu) {
        String exprType1 = n.f0.accept(this, argu);
        ClassT currClass = symbolTable.GetClass(exprType1);

        //If we ensure no declarations of non existing class types are made this check should only detects primitive types.
        if(currClass == null)
            throw new RuntimeException(exprType1 + " is not a class type.");
        String funcId = n.f2.accept(this, argu);

        FunctionT func;
        do { // Find the func inside the inheritance hierarchy.
            func = currClass.GetFunction(funcId);
            if(func == null){
                if(!currClass.IsExtending())
                    throw new RuntimeException("Function " + funcId + " not found.");
                else
                    currClass = symbolTable.GetClass(currClass.GetExtendType()); //the first visitor already checked if the extended type exists.
            }
        }while(func == null);

        List<String> args = new LinkedList<String>();
        Object[] arguTuple = {argu,args}; //Hacky way to pass 2 parameters to the call.
        n.f4.accept(this, arguTuple);
        if(args.size() != func.GetArgs().size())
            throw  new RuntimeException("Wrong amount of arguments provided.");

        int i = 0;
        List<String> argsList = new LinkedList<String>(func.GetArgs());
        for(String typeR : args) //Check if values are assigned properly.
            CheckNormalAssignmentTypes(typeR, argsList.get(i++));

        return func.GetReturnType();
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public String visit(ExpressionList n, Object argu) {
        Object[] tempArgu = (Object[])argu;
        String exprType = n.f0.accept(this, (FunctionT)(tempArgu[0]));
        List<String> tempArgs = (List<String>)(tempArgu[1]);
        tempArgs.add(exprType);
        n.f1.accept(this, argu);
        return null;
    }

    /**
     * f1 -> Expression()
     */
    public String visit(ExpressionTerm n, Object argu) {
        Object[] tempArgu = (Object[])argu;
        String exprType = n.f1.accept(this, (FunctionT)(tempArgu[0]));
        List<String> tempArgs = (List<String>)(tempArgu[1]);
        tempArgs.add(exprType);

        return null;
    }

    /* EXPRESSIONS END --------------------------------- */

}
