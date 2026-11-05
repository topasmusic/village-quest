package de.quest.resource;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Bytecode contracts only: never starts Minecraft, a server, a world or the Mixin runtime. */
final class Native12111MixinContractTest {
    private static final Map<String,ClassNode> CACHE=new HashMap<>();
    @TestFactory Stream<DynamicTest> everyRegisteredMixinTargetsThePinnedNativeBytecode() throws Exception {
        try(var input=getClass().getResourceAsStream("/village-quest.mixins.json")) {
            var config=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonObject();
            var tests=new ArrayList<DynamicTest>();
            for(String group:List.of("mixins","client"))for(var name:config.getAsJsonArray(group)) {
                String full=config.get("package").getAsString()+"."+name.getAsString();
                tests.add(DynamicTest.dynamicTest(full,()->verify(full.replace('.','/'))));
            }
            return tests.stream();
        }
    }
    private static ClassNode node(String name) throws Exception {
        if(CACHE.containsKey(name))return CACHE.get(name);
        try(var stream=Native12111MixinContractTest.class.getResourceAsStream("/"+name+".class")) {
            assertNotNull(stream,"Missing bytecode "+name);var result=new ClassNode();new ClassReader(stream).accept(result,0);CACHE.put(name,result);return result;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> first,List<AnnotationNode> second) {
        var result=new ArrayList<AnnotationNode>();if(first!=null)result.addAll(first);if(second!=null)result.addAll(second);return result;
    }
    private static Object value(AnnotationNode a,String key) {
        if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;
    }
    private static boolean member(String owner,String selector,boolean field,Set<String> seen) throws Exception {
        if(owner==null || !seen.add(owner))return false;
        var n=node(owner);int split=selector.indexOf('(');String name=split<0?selector:selector.substring(0,split);String desc=split<0?null:selector.substring(split);
        if(field && n.fields.stream().anyMatch(f->f.name.equals(name)))return true;
        if(!field && n.methods.stream().anyMatch(m->m.name.equals(name)&&(desc==null || m.desc.equals(desc))))return true;
        if(name.equals("<init>") || name.equals("<clinit>"))return false;
        if(member(n.superName,selector,field,seen))return true;
        for(var parent:n.interfaces)if(member(parent,selector,field,seen))return true;return false;
    }
    private static void verify(String own) throws Exception {
        var mixin=node(own);var config=annotations(mixin.visibleAnnotations,mixin.invisibleAnnotations).stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        var targets=new ArrayList<String>();Object classes=value(config,"value");if(classes instanceof List<?> list)for(var c:list)targets.add(((Type)c).getInternalName());
        Object strings=value(config,"targets");if(strings instanceof List<?> list)for(var s:list)targets.add(s.toString().replace('.','/'));
        assertFalse(targets.isEmpty());
        for(var target:targets)for(var method:mixin.methods)for(var a:annotations(method.visibleAnnotations,method.invisibleAnnotations)) {
            String kind=a.desc.substring(a.desc.lastIndexOf('/')+1);
            if(kind.equals("Accessor;"))assertTrue(member(target,Objects.toString(value(a,"value")),true,new HashSet<>()),own+" accessor "+value(a,"value"));
            if(kind.equals("Invoker;"))assertTrue(member(target,Objects.toString(value(a,"value"))+method.desc,false,new HashSet<>()),own+" invoker "+value(a,"value")+method.desc);
            Object selectors=value(a,"method");
            if(selectors!=null)for(var selector:selectors instanceof List<?> list?list:List.of(selectors)) {
                assertTrue(member(target,selector.toString(),false,new HashSet<>()),own+" injection "+selector);
                if(kind.equals("Inject;") || kind.equals("WrapMethod;")) {
                    String text=selector.toString();int split=text.indexOf('(');String name=split<0?text:text.substring(0,split);
                    var args=Type.getArgumentTypes(method.desc);int count=0;
                    while(count<args.length && !args[count].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.")
                            && !args[count].getClassName().equals("com.llamalad7.mixinextras.injector.wrapoperation.Operation"))count++;
                    var prefix=Arrays.copyOf(args,count);
                    // An Inject handler may omit all native arguments; otherwise their full descriptor must agree.
                    boolean matches=kind.equals("Inject;") && count==0 || node(target).methods.stream()
                            .filter(m->m.name.equals(name)&&(split<0 || m.desc.equals(text.substring(split))))
                            .anyMatch(m->Arrays.equals(prefix,Type.getArgumentTypes(m.desc)));
                    assertTrue(matches,own+" handler arguments "+method.name+method.desc+" do not match "+selector);
                }
            }
            Object at=value(a,"at");
            if(at!=null)for(var annotation:at instanceof List<?> list?list:List.of(at)) {
                var point=(AnnotationNode)annotation;Object ref=value(point,"target");
                if(ref instanceof String s && s.startsWith("L") && s.contains(";")) {
                    int end=s.indexOf(';');String owner=s.substring(1,end),selector=s.substring(end+1);
                    assertTrue(member(owner,selector,false,new HashSet<>()),own+" invocation "+s);
                    boolean called=false;
                    for(var host:node(target).methods)for(var insn:host.instructions)if(insn instanceof MethodInsnNode call && ("L"+call.owner+";"+call.name+call.desc).equals(s))called=true;
                    assertTrue(called,own+" missing actual invocation "+s);
                }
            }
        }
        for(var target:targets)for(var field:mixin.fields)for(var a:annotations(field.visibleAnnotations,field.invisibleAnnotations))
            if(a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))assertTrue(member(target,field.name,true,new HashSet<>()),own+" shadow "+field.name);
    }
}
