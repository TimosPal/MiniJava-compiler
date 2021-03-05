package TypeChecking;

import java.util.Collection;
import java.util.Map;

public class ClassT {
    private String extendsT;
    private String name; //Needed for (this).
    private int prevMemberOffset;
    private int prevFuncOffset;

    private Map<String,FunctionT> functionsTable;
    private Map<String,String> memberVariablesTable;

    private Map<String, Integer> memberOffsets;
    private Map<String, Integer> funcOffsets;

    public int GetFuncOffset(String name,SymbolTable table) {
        ClassT temp = this;
        while(temp != null){
            if(temp.funcOffsets.containsKey(name))
                return temp.funcOffsets.get(name);
            temp = table.GetClass(temp.GetExtendType());
        }
        return -1;
    }

    public ClassT(String name,String extendsT,int memberOffset,int funcOffset){
        this.functionsTable = SymbolTable.CreateMap();
        this.memberVariablesTable = SymbolTable.CreateMap();
        this.memberOffsets = SymbolTable.CreateMap();
        this.funcOffsets = SymbolTable.CreateMap();
        this.extendsT = extendsT;
        this.name = name;
        this.prevMemberOffset = memberOffset;
        this.prevFuncOffset = funcOffset;
    }

    public int GetMemberOffset() { return prevMemberOffset; }
    public int GetFuncOffset() { return prevFuncOffset; }

    public void PrintOffsets() {
        for (Map.Entry<String, Integer> entry : memberOffsets.entrySet())
            System.out.println(name + "." + entry.getKey() + ":" + entry.getValue().toString());
        for (Map.Entry<String, Integer> entry : funcOffsets.entrySet())
            System.out.println(name + "." + entry.getKey() + ":" + entry.getValue().toString());
    }

    public Map<String,Integer> GetMemberOffsetsHash() { return memberOffsets; }

    public FunctionT GetFunction(String id){
        return functionsTable.get(id);
    }

    public void AddFunction(String id,FunctionT nFunc){
        if(functionsTable.put(id,nFunc) != null)
            throw new RuntimeException("Function " + id + " is already defined.");
    }

    public String GetMemberVar(String id){
        return memberVariablesTable.get(id);
    }

    public void AddMemberVar(String id,String type){ //Returns false if value already exists.
        if(memberVariablesTable.put(id,type) != null)
            throw new RuntimeException("Member variable " + id + " is already defined.");
        AddOffset(true,id,type);
    }

    public String GetExtendType(){
        return extendsT;
    }

    public boolean IsExtending(){
        return extendsT.length() >= 1;
    }

    public String GetName() { return  name; }

    public Collection<FunctionT> GetFunctions() { return functionsTable.values(); }
    public Map<String, FunctionT> GetFunctionsHash() { return functionsTable; }
    public Collection<String> GetMembers() { return memberVariablesTable.values(); }
    public Map<String,String> GetMembersHash() { return memberVariablesTable; }

    public void AddOffset(boolean forMembers,String id, String type){
        int prevOffset = (forMembers) ? prevMemberOffset : prevFuncOffset;
        ((forMembers) ? memberOffsets : funcOffsets).put(id,prevOffset);
        if(type.equals("int"))
            prevMemberOffset += 4;
        else if(type.equals("boolean"))
            prevMemberOffset += 1;
        else //Array or custom class.
            if(forMembers)
                prevMemberOffset += 8;
            else
                prevFuncOffset += 8;
    }

}
