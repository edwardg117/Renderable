package io.github.edwardg117.renderable.hooks;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads a mixin's injectors and checks each one's target against the class it was applied to (spec addendum
 * §P5.D1). Pure ASM, so it is tested against small class nodes.
 *
 * <p>It checks the target, not the handler. When the plugin's {@code postApply} runs, MixinExtras has applied
 * neither {@code @WrapOperation}, a late injection, nor {@code @WrapMethod}, which its applicator extension
 * rewrites afterwards, so no call to their handlers exists yet. The target method is still there with its original
 * body: a plain {@code @Inject} leaves its call in place, and the wrapping injectors have not run. Another mod's
 * {@code @Redirect} of the same call would remove it, and the check would then report a hook that MixinExtras
 * still wraps; the hook's rows would show it working.
 */
final class InjectionCheck {
    /** The injector annotations read; {@code HookInstallsTest} fails on any other from an injector package. */
    static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

    private InjectionCheck() {
    }

    /**
     * One injector of a mixin.
     *
     * @param handler the handler method's name
     * @param methods the target method selectors: a name, or a name followed by a descriptor
     * @param calls   the calls its {@code INVOKE} injection points name, as {@code Lowner;name(desc)}; empty for
     *                {@code HEAD}, {@code RETURN} and {@code @WrapMethod}
     */
    record Injector(String handler, List<String> methods, List<String> calls) {
    }

    /** Every injector annotation on {@code mixin}'s methods, in the order the class file lists the methods. */
    static List<Injector> injectors(ClassNode mixin) {
        List<Injector> out = new ArrayList<>();
        for (MethodNode m : mixin.methods) {
            for (AnnotationNode a : annotations(m)) {
                if (!INJECTORS.contains(a.desc)) continue;
                List<String> methods = new ArrayList<>();
                List<String> calls = new ArrayList<>();
                for (int i = 0; a.values != null && i < a.values.size(); i += 2) {
                    Object name = a.values.get(i);
                    Object value = a.values.get(i + 1);
                    if ("method".equals(name)) {
                        for (Object o : asList(value)) methods.add((String) o);
                    } else if ("at".equals(name)) {
                        for (Object o : asList(value)) {
                            String call = invokeTarget((AnnotationNode) o);
                            if (call != null) calls.add(call);
                        }
                    }
                }
                out.add(new Injector(m.name, List.copyOf(methods), List.copyOf(calls)));
            }
        }
        return out;
    }

    /**
     * True when a method of {@code target} matches one of the injector's selectors and, for an {@code INVOKE}
     * injector, makes one of its calls. A bare name matches every method of that name, while Mixin injects into the
     * first only, so on an overloaded name an {@code INVOKE} injector could read installed when the first overload
     * lacks the call.
     */
    static boolean present(Injector injector, ClassNode target) {
        for (MethodNode m : target.methods) {
            if (!selected(injector.methods(), m)) continue;
            if (injector.calls().isEmpty()) return true;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call
                        && injector.calls().contains("L" + call.owner + ";" + call.name + call.desc)) return true;
            }
        }
        return false;
    }

    private static boolean selected(List<String> selectors, MethodNode m) {
        for (String s : selectors) {
            int paren = s.indexOf('(');
            String name = paren < 0 ? s : s.substring(0, paren);
            if (name.equals(m.name) && (paren < 0 || s.substring(paren).equals(m.desc))) return true;
        }
        return false;
    }

    /** The call an {@code @At("INVOKE")} names, or null for any other injection point. */
    private static String invokeTarget(AnnotationNode at) {
        String value = null;
        String target = null;
        for (int i = 0; at.values != null && i < at.values.size(); i += 2) {
            if ("value".equals(at.values.get(i))) value = (String) at.values.get(i + 1);
            if ("target".equals(at.values.get(i))) target = (String) at.values.get(i + 1);
        }
        return "INVOKE".equals(value) ? target : null;
    }

    private static List<AnnotationNode> annotations(MethodNode m) {
        List<AnnotationNode> all = new ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        return all;
    }

    /** An annotation value that is one element or an array of them: {@code @ModifyVariable}'s {@code at} is one. */
    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of(value);
    }
}
