package de.quest.util;

/** Resolve pinned 1.21.11 native names in development and remapped production.
 * Intermediary symbols are verified against this target's actual Loom mappings. */
public final class MinecraftReflection {
    private MinecraftReflection() {}
    private record Symbol(String name,String descriptor) {}
    private static final java.util.Map<String,Symbol> FIELDS=java.util.Map.ofEntries(
            java.util.Map.entry("net.minecraft.class_1743#STRIPPABLES",new Symbol("field_7898","Ljava/util/Map;")),
            java.util.Map.entry("net.minecraft.class_1794#TILLABLES",new Symbol("field_8023","Ljava/util/Map;")),
            java.util.Map.entry("net.minecraft.class_1821#FLATTENABLES",new Symbol("field_8912","Ljava/util/Map;")),
            java.util.Map.entry("net.minecraft.class_1827#wallBlock",new Symbol("field_8918","Lnet/minecraft/class_2248;")),
            java.util.Map.entry("net.minecraft.class_1845#containers",new Symbol("field_51403","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845#potionMixes",new Symbol("field_51404","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845#containerMixes",new Symbol("field_51405","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845$class_9665#containers",new Symbol("field_51406","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845$class_9665#potionMixes",new Symbol("field_51407","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845$class_9665#containerMixes",new Symbol("field_51408","Ljava/util/List;")),
            java.util.Map.entry("net.minecraft.class_1845$class_1846#from",new Symbol("comp_2190","Lnet/minecraft/class_6880;")),
            java.util.Map.entry("net.minecraft.class_1845$class_1846#ingredient",new Symbol("comp_2191","Lnet/minecraft/class_1856;")),
            java.util.Map.entry("net.minecraft.class_1845$class_1846#to",new Symbol("comp_2192","Lnet/minecraft/class_6880;")),
            java.util.Map.entry("net.minecraft.class_2298#deadBlock",new Symbol("field_10818","Lnet/minecraft/class_2248;")),
            java.util.Map.entry("net.minecraft.class_2297#deadBlock",new Symbol("field_10817","Lnet/minecraft/class_2248;")),
            java.util.Map.entry("net.minecraft.class_2301#deadBlock",new Symbol("field_10833","Lnet/minecraft/class_2248;")),
            java.util.Map.entry("net.minecraft.class_2299#deadBlock",new Symbol("field_10819","Lnet/minecraft/class_2248;")));
    private static final java.util.Map<String,Symbol> METHODS=java.util.Map.ofEntries(
            java.util.Map.entry("net.minecraft.class_1845$class_1846#from",new Symbol("comp_2190","()Lnet/minecraft/class_6880;")),
            java.util.Map.entry("net.minecraft.class_1845$class_1846#ingredient",new Symbol("comp_2191","()Lnet/minecraft/class_1856;")),
            java.util.Map.entry("net.minecraft.class_1845$class_1846#to",new Symbol("comp_2192","()Lnet/minecraft/class_6880;")),
            java.util.Map.entry("net.minecraft.class_2263#pickupBlock",new Symbol("method_9700","(Lnet/minecraft/class_1309;Lnet/minecraft/class_1936;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)Lnet/minecraft/class_1799;")),
            java.util.Map.entry("net.minecraft.class_4865#getMaxAgeState",new Symbol("method_38232","(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2680;")),
            java.util.Map.entry("net.minecraft.class_4865#isMaxAge",new Symbol("method_38233","(Lnet/minecraft/class_2680;)Z")));
    public static java.lang.reflect.Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        try { return owner.getDeclaredField(name); }
        catch(NoSuchFieldException missing) {
            var resolver=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
            String intermediary=resolver.unmapClassName("intermediary",owner.getName());
            var symbol=FIELDS.get(intermediary+"#"+name);
            if(symbol==null)throw missing;
            return owner.getDeclaredField(resolver.mapFieldName("intermediary",intermediary,symbol.name(),symbol.descriptor()));
        }
    }
    public static java.lang.reflect.Method method(Class<?> actual,Class<?> definition,String name,Class<?>... parameters) throws ReflectiveOperationException {
        try { return actual.getMethod(name,parameters); }
        catch(NoSuchMethodException missing) {
            var resolver=net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver();
            String intermediary=resolver.unmapClassName("intermediary",definition.getName());
            var symbol=METHODS.get(intermediary+"#"+name);
            if(symbol==null)throw missing;
            return actual.getMethod(resolver.mapMethodName("intermediary",intermediary,symbol.name(),symbol.descriptor()),parameters);
        }
    }
}
