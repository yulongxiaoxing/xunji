package cn.xunji.probe;
import java.util.*;
public class NapCatPolicyTest {
    public static void main(String[] args){Set<String> selected=new HashSet<>(Arrays.asList("12345"));Set<String> old=new HashSet<>(Arrays.asList("old"));
        check(NapCatPolicy.allowed(selected,"group","12345","new",101,100,old));
        check(!NapCatPolicy.allowed(selected,"private","12345","new",101,100,old));
        check(!NapCatPolicy.allowed(selected,"group","98765","new",101,100,old));
        check(!NapCatPolicy.allowed(selected,"group","12345","old",101,100,old));
        check(!NapCatPolicy.allowed(selected,"group","12345","new",99,100,old));
        check(!NapCatPolicy.allowed(selected,"group","12345","",101,100,old));
        check(!NapCatPolicy.allowed(selected,"group","012345","new",101,100,old));
        check(!NapCatPolicy.allowed(Collections.emptySet(),"group","12345","new",101,100,old));
        System.out.println("8 NapCat group/private/baseline boundary checks passed");
    }static void check(boolean good){if(!good)throw new AssertionError("NapCat boundary regression");}
}
