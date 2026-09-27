package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JvmFlagsTest {

    @Test
    void onlyHeapAndCollectorFlagsSurviveAndNeverAPath() {
        List<String> args = List.of(
                "-Xmx8G", "-Xms4G", "-Xss2m",
                "-XX:+UseZGC", "-XX:+ZGenerational", "-XX:-UseAdaptiveSizePolicy",
                "-XX:MaxGCPauseMillis=50", "-XX:G1NewSizePercent=20", "-XX:+UnlockExperimentalVMOptions",
                "-Djava.library.path=C:\\Users\\bob\\natives", "-javaagent:C:\\tools\\agent.jar",
                "-XX:HeapDumpPath=C:\\dumps", "-XX:ErrorFile=/tmp/err.log", "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "-cp", "C:\\Users\\bob\\libs.jar", "-XX:MaxRAMPercentage=75.0", "-XX:SoftMaxHeapSize=C:\\x");

        assertEquals(List.of("-Xmx8G", "-Xms4G", "-Xss2m", "-XX:+UseZGC", "-XX:+ZGenerational",
                        "-XX:MaxGCPauseMillis=50", "-XX:G1NewSizePercent=20", "-XX:+UnlockExperimentalVMOptions",
                        "-XX:MaxRAMPercentage=75.0"),
                JvmFlags.kept(args));

        // Every value a flag on the list really takes: a count, a size and a decimal. G1's own switches stay too.
        List<String> real = List.of("-XX:ParallelGCThreads=4", "-XX:ConcGCThreads=2", "-XX:SoftMaxHeapSize=6g",
                "-XX:InitialRAMPercentage=12.5", "-XX:G1HeapRegionSize=16M", "-XX:G1ReservePercent=15",
                "-XX:G1ConcMarkStepDurationMillis=5.0", "-XX:-G1UseAdaptiveIHOP");
        assertEquals(real, JvmFlags.kept(real));

        // The JVM starts with a retired G1 flag whatever its value (G1ConcRSHotCardLimit, since 21), and with an
        // unknown one under -XX:+IgnoreUnrecognizedVMOptions, so the value test alone keeps a path or a dotted name
        // out: a value is a number with an optional size suffix, or a bare word.
        assertEquals(List.of(), JvmFlags.kept(List.of("-XX:G1ConcRSHotCardLimit=users.bob.appdata",
                "-XX:G1Foo=bob.smith", "-XX:G1Foo=.bob", "-XX:G1Foo=...", "-XX:G1Foo=10.0.0.1",
                "-XX:G1Foo=C:\\Users\\bob", "-XX:G1Foo=/home/bob")));
    }
}
