package TypeChecking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SymbolTable {
    private Map<String,ClassT> symbolTable;

    public static <T1,T2> Map<T1, T2> CreateMap(){ //Every map is initialized with this function so map's implementation is easily changed.
        return new LinkedHashMap<>();
    }

    public static <T1,T2> Map<T1, T2> CreateMap(Map<T1,T2> map){
        return new LinkedHashMap<>(map);
    }

    public SymbolTable(){
        this.symbolTable = SymbolTable.CreateMap();
    }

    public  ClassT GetClass(String id){
        return symbolTable.get(id);
    }

    public void AddClass(String id,ClassT nClass){
        if(symbolTable.put(id,nClass) != null){
            throw new RuntimeException("Class " + id + " is already defined.");
        }
    }

    public Collection<ClassT> GetClasses() { return symbolTable.values(); }

}
