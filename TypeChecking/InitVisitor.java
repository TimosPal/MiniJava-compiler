package TypeChecking;

import syntaxtree.*;
import visitor.GJDepthFirst;

public class InitVisitor extends GJDepthFirst<String, Object> {
    /*--------------------------------------------------------
    * Initializes the symbol table.Throws an error if it finds
    * 1) a double declaration
    * 2) a class that extends from a class type that has not been
    *    previously declared.
    * 3) an overloaded function doesn't have the same declaration.
    --------------------------------------------------------*/

    private SymbolTable symbolTable;

    // Used when casting argu to the appropriate type.
    // We have to differentiate between member variables and local variables.
    private boolean isAtFuncLevel;

    public InitVisitor(){
        this.symbolTable = new SymbolTable();
        this.isAtFuncLevel = false;
    }

    public String visit(NodeToken n, Object argu) { return n.toString(); } //Used for ids.
    public String visit(BooleanArrayType n, Object argu) { return "boolean[]"; } //Used for boolean array types.
    public String visit(IntegerArrayType n, Object argu) { return "int[]"; } //Used for int array types.
    public SymbolTable GetSymbolTable() { return symbolTable; }

    public void AddVariableSymbol(String varId, String varType, Object argu,boolean isArg){ //Used in parameter list / local variables or member variables.
        if(isAtFuncLevel){
            FunctionT currFunc = (FunctionT)argu;
            currFunc.AddVar(varId,varType);
            if(isArg)
                currFunc.AddArg(varId,varType);
        }else{
            ClassT currClass = (ClassT)argu;
            currClass.AddMemberVar(varId,varType);
        }
    }

    /** MAIN CLASS.
     * f1 -> Identifier()
     * f11 -> Identifier()
     * f14 -> ( VarDeclaration() )*
     */
    public String visit(MainClass n, Object argu) {
        //We don't need to check if the values were added since the main is at the start
        //so we know the table is empty. (non duplicate values)
        //We also don't use other functions since we manually plug the function / arguments.

        String mainClassId = n.f1.accept(this, argu); //Another Class cant have the same name.
        ClassT currClass = new ClassT(mainClassId,"",0,0);
        symbolTable.AddClass(mainClassId,currClass);

        FunctionT currFunc = new FunctionT("void",currClass,"main");

        currClass.AddFunction("main",currFunc); // Another function cant have the same name with main.

        String argArrId = n.f11.accept(this, argu); // Another local variable inside main cant have the same name.
        currFunc.AddVar(argArrId,"String[]");
        currFunc.AddArg(argArrId,"String[]");

        isAtFuncLevel = true;
        n.f14.accept(this, currFunc);
        return null;
    }

    /** VARIABLE DECLARATION : MEMBERS / LOCALS.
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(VarDeclaration n, Object argu) {
        String varType = n.f0.accept(this, argu);
        String varId = n.f1.accept(this, argu);
        AddVariableSymbol(varId,varType,argu,false);
        return null;
    }

    /** PARAMETER LIST VARIABLE.
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public String visit(FormalParameter n, Object argu) {
        String varType = n.f0.accept(this, argu);
        String varId = n.f1.accept(this, argu);
        AddVariableSymbol(varId,varType,argu,true);
        return null;
    }

    /** CLASS DECLARATION.
     * f1 -> Identifier()
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     */
    public String visit(ClassDeclaration n, Object argu) {
        String classId = n.f1.accept(this, argu);
        ClassT currClass = new ClassT(classId,"",0,0);
        symbolTable.AddClass(classId,currClass);

        isAtFuncLevel = false; //Member variables.
        n.f3.accept(this, currClass);
        n.f4.accept(this, currClass); //Member functions.

        return null;
    }

    /** CLASS DECLARATION THAT EXTENDS ANOTHER CLASS.
     * f1 -> Identifier()
     * f3 -> Identifier() EXTENDED ID.
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     */
    public String visit(ClassExtendsDeclaration n, Object argu) {
        String classId = n.f1.accept(this, argu);
        String extendedClassId = n.f3.accept(this, argu);
        ClassT extendClass;
        if((extendClass = symbolTable.GetClass(extendedClassId)) == null)
            throw new RuntimeException("Cant extend from class " + extendedClassId + " because it is not declared.");

        ClassT currClass = new ClassT(classId,extendedClassId,extendClass.GetMemberOffset(),extendClass.GetFuncOffset());
        symbolTable.AddClass(classId,currClass);

        isAtFuncLevel = false; //Member variables.
        n.f5.accept(this, currClass);
        n.f6.accept(this, currClass);

        return null;
    }

    public boolean IsSameFunc(FunctionT f1, FunctionT f2){
        return f1.GetArgs().equals(f2.GetArgs()); //WE ONLY CARE ABOUT THE TYPES NOT THE NAMES.
    }

    /** METHOD DECLARATION.
     * f1 -> Type()
     * f2 -> Identifier()
     * f4 -> ( FormalParameterList() )?
     * f7 -> ( VarDeclaration() )*
     */
    public String visit(MethodDeclaration n, Object argu) {
        String funcType = n.f1.accept(this, argu);
        String funcId = n.f2.accept(this, argu);

        FunctionT newFunc = new FunctionT(funcType,(ClassT)argu,funcId);
        ((ClassT)argu).AddFunction(funcId,newFunc);

        isAtFuncLevel = true; //Function variables.
        n.f4.accept(this, newFunc);

        boolean isOverride = false;
        //Check if similar fun exists in super class.
        ClassT curClass = symbolTable.GetClass(((ClassT)argu).GetExtendType());
        while(curClass != null) {
            FunctionT tempFunc;
            if((tempFunc = curClass.GetFunction(funcId)) != null){
                if(!(tempFunc.GetReturnType().equals(funcType) && IsSameFunc(tempFunc,newFunc))){
                    throw new RuntimeException("Overridden virtual methods cant have different declarations.");
                }else{
                    isOverride = true;
                    break;
                }
            }
            curClass = symbolTable.GetClass(curClass.GetExtendType());
        }

        if(!isOverride){
            ((ClassT)argu).AddOffset(false,funcId,"");
        }

        n.f7.accept(this, newFunc);
        return null;
    }

}
