/**
 * 依赖关系
 * @author 181850254 周杼彦
 */
public class CallRelation {
    private final String caller;
    private final String callee;

    public CallRelation(String caller, String callee) {
        this.caller = caller;
        this.callee = callee;
    }

    public String getDotFormat() {
        return "\"" + caller + "\"" + " " + "->" + " " + "\"" + callee + "\"" + ";\n";
    }

    /**
     * 在Java中equals与hashCode方法绑定，作为判断两个对象是否相等的方式
     * 这里重构了两个方式，便于对依赖关系进行判断
     * @param object
     * @return
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        CallRelation relation = (CallRelation) object;
        if (caller != null ? !caller.equals(relation.caller) : relation.caller != null) {
            return false;
        }
        return callee != null ? callee.equals(relation.callee) : relation.callee == null;
    }

    /**
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        int result = caller != null ? caller.hashCode() : 0;
        result = 31 * result + (callee != null ? callee.hashCode() : 0);
        return result;
    }

    public String getCaller() {
        return caller;
    }

    public String getCallee() {
        return callee;
    }

    public String getCallerClass(){
        return caller.substring(0,caller.lastIndexOf('.'));
    }

    public String getCalleeClass(){
        return callee.substring(0,callee.lastIndexOf('.'));
    }
}
