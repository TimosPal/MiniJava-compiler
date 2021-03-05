package TypeChecking;

import java.util.LinkedList;
import java.util.Map;

public class FunctionT {
    private String returnT;
    private String name;
    private ClassT parentClass;

    private Map<String, String> localVariablesTable;
    private Map<String,String> args; //Used for the argument list check.
    private Map<String,String> registerTypes;

    public FunctionT(String returnT,ClassT parentClass,String name){
        this.localVariablesTable = SymbolTable.CreateMap();
        this.registerTypes = SymbolTable.CreateMap();
        this.returnT = returnT;
        this.parentClass = parentClass;
        this.name = name;
        this.args = SymbolTable.CreateMap();
    }

    public String GetName() { return name;}

    public String GetVarType(String id){
        return localVariablesTable.get(id);
    }

    public Map<String,String> GetRegisterHash() { return registerTypes; }

    public void AddVar(String id,String nLocalVar){
        if(localVariablesTable.put(id,nLocalVar) != null){
            throw new RuntimeException("Local variable " + id + " is already defined.");
        }
    }

    public String GetReturnType(){
        return returnT;
    }

    public ClassT GetParentClass() { return parentClass; }

    public void AddArg(String id,String type) { args.put(id,type); }
    public LinkedList<String> GetArgs() { return new LinkedList<>(args.values()); }
    public Map<String,String> GetArgsHash() { return args; }

    public LinkedList<String> GetLocals() { return new LinkedList<>(localVariablesTable.values()); }
    public Map<String,String> GetLocalsHash() { return localVariablesTable; }



}
