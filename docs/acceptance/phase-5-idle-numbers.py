"""Turns the idle-cost recordings into the numbers of phase-5-idle.md (spec §9 check 5, spec addendum §P5.C1).

Usage:
    python docs/acceptance/phase-5-idle-numbers.py run1.jfr run2.jfr run3.jfr run4.jfr
    python docs/acceptance/phase-5-idle-numbers.py one.jfr        (a single recording: just its numbers)

Needs the JDK's jfr tool, on the PATH or under JAVA_HOME/bin.

Renderable's code shows in a recording in two ways: classes in its own package, and the mixin handlers Mixin
merges into the classes they hook. Each handler's own name starts "renderable$", and Mixin renames it to
"<injector>$<id>$renderable$<rest>" (wrapMethod$zza000$renderable$timeEntityTick, say), so a handler is a method
whose name contains "renderable$". A sample is Renderable's when its top frame is either. An idle hook runs only
its handler, and a wrapper handler is on the stack of everything it wraps, so "anywhere on the stack" would count
the wrapped game code too.
"""
import json
import os
import shutil
import subprocess
import sys

PACKAGE = ("io.github.edwardg117.renderable", "io/github/edwardg117/renderable")
HANDLER = "renderable$"


def jfr_tool():
    found = shutil.which("jfr")
    if found:
        return found
    home = os.environ.get("JAVA_HOME")
    if home:
        for name in ("jfr", "jfr.exe"):
            path = os.path.join(home, "bin", name)
            if os.path.exists(path):
                return path
    sys.exit("Cannot find the JDK's jfr tool: put it on the PATH or set JAVA_HOME")


def events(recording, kind, depth):
    """One event type, with stack traces cut to depth frames; whole traces would run to hundreds of megabytes."""
    done = subprocess.run([jfr_tool(), "print", "--json", "--stack-depth", str(depth), "--events", kind, recording],
                          capture_output=True, text=True, encoding="utf-8")
    if done.returncode != 0:
        sys.exit(f"jfr print failed on {recording}: {done.stderr.strip()}")
    return json.loads(done.stdout)["recording"]["events"]


def frames(event):
    return (event["values"].get("stackTrace") or {}).get("frames") or []


def ours(frame):
    method = frame.get("method") or {}
    cls = (method.get("type") or {}).get("name") or ""
    return cls.startswith(PACKAGE) or HANDLER in (method.get("name") or "")


def after_first(allocations):
    """Each thread's allocation samples after its first. A sample weighs what its thread allocated since the one
    before, but JFR weighs a thread's first sample in a recording from the thread's start, so it carries all the
    thread allocated before the recording. In a dev-client recording that began 30 s after launch, the first
    samples came to 12 GiB, over a third of the total. jfr print lists events in time order."""
    seen, kept = set(), []
    for e in allocations:
        thread = e["values"]["eventThread"]["javaThreadId"]
        if thread in seen:
            kept.append(e)
        seen.add(thread)
    return kept


def numbers(recording):
    allocations = after_first(events(recording, "jdk.ObjectAllocationSample", 3))
    # Every reading but the first, which is often 0% or a stray figure rather than a measurement.
    loads = [e["values"]["jvmUser"] + e["values"]["jvmSystem"] for e in events(recording, "jdk.CPULoad", 0)[1:]]
    samples = events(recording, "jdk.ExecutionSample", 1)
    return {
        "allocatedMiB": sum(e["values"]["weight"] for e in allocations) / 1048576,
        # Allocation whose site, or one of the two calls above it, is Renderable's: a handler's argument array for
        # Operation.call, and the boxes in it. No deeper: the code a wrapper wraps runs under the Operation's
        # hidden lambda class and a bridge, so the handler is the fourth frame of that code's own allocation. A
        # lower bound: MixinExtras builds part of a wrapper's arguments in the hooked method itself.
        "oursKiB": sum(e["values"]["weight"] for e in allocations if any(ours(f) for f in frames(e))) / 1024,
        "meanCpu": sum(loads) / len(loads) if loads else float("nan"),
        "samples": len(samples),
        "ours": sum(1 for s in samples if frames(s) and ours(frames(s)[0])),
    }


def show(label, n):
    share = 100.0 * n["ours"] / n["samples"] if n["samples"] else float("nan")
    print(f"{label}: allocated {n['allocatedMiB']:.1f} MiB ({n['oursKiB']:.1f} KiB in Renderable's frames), "
          f"mean CPU {100 * n['meanCpu']:.2f}%, {n['samples']} execution samples, "
          f"{n['ours']} with Renderable on top ({share:.3f}%)")


def change(before, after):
    return 100.0 * (after - before) / before


def main(paths):
    if len(paths) == 1:
        show(paths[0], numbers(paths[0]))
        return
    if len(paths) != 4:
        sys.exit(__doc__)
    runs = [numbers(p) for p in paths]
    for i, (path, n) in enumerate(zip(paths, runs), 1):
        show(f"run {i} ({path})", n)
    allocation = change(runs[0]["allocatedMiB"], runs[1]["allocatedMiB"])
    cpu = change(runs[0]["meanCpu"], runs[1]["meanCpu"])
    share = 100.0 * runs[1]["ours"] / runs[1]["samples"]
    passed = abs(allocation) <= 2 and abs(cpu) <= 2 and share < 0.1
    sensitive = runs[3]["oursKiB"] > 0
    removed = runs[1]["oursKiB"] <= 0.1 * runs[3]["oursKiB"]
    total_control = change(runs[2]["allocatedMiB"], runs[3]["allocatedMiB"])
    print()
    print(f"1. Run 2 against run 1: allocation {allocation:+.2f}%, mean CPU {cpu:+.2f}% (each within 2%);"
          f" Renderable on top of {share:.3f}% of run 2's samples (under 0.1%): {'PASS' if passed else 'FAIL'}")
    print(f"2. The control: with escape analysis off, run 4 allocated {runs[3]['oursKiB']:.1f} KiB in Renderable's"
          f" frames (must be above zero): {'sensitive' if sensitive else 'NOT sensitive, so check 3 means nothing'}."
          f" Total allocation, run 4 against run 3, for context: {total_control:+.2f}%.")
    print(f"3. With escape analysis on, run 2 allocated {runs[1]['oursKiB']:.1f} KiB in Renderable's frames"
          f" (at most a tenth of run 4's): {'PASS' if removed else 'FAIL'}")


if __name__ == "__main__":
    main(sys.argv[1:])
