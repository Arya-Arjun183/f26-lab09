# BOUNDARIES.md

One section per milestone. Fill them in in order (the sketch before you write
anything about it, the copies next, the proposal last).

Draw Milestone 1's sketch from the code. A TA may ask where each arrow came
from.

Keep it short and specific. Point at modules, files, and callers.

---

## Milestone 1: The architecture as built

**The sketch.** Draw the modules, what each one writes, and the path an
availability request and a booking request each take from the entry point to
storage. Any form works (a Mermaid diagram in this file, or a photo of paper
kept beside it or linked here).

One page. The sketch is done when someone could use it to predict which module
a given change goes in.

**Where your sketch and `ARCHITECTURE.md` disagree.** List at least two
places where your drawing and the repository's drawing are not the same
picture, each in one line. Include things you drew that it does not, and things it draws
that you did not.

**How you decided which one is right.** For each disagreement above, name the
check that settled it (the imports you read, the callers you listed, the wiring
you traced, the test that pins the behavior). "It looked that way" is not a
check, and neither is "the document says so". Your agent may produce the trace,
but check it before you rely on it.

---

## Milestone 2: Run it as copies

**Your stack.** The command that brought the stack up with two copies, and how
many distinct `X-Upstream` values your runs saw.

### Finding 1: tokens

**The claim.** One sentence, the one your script checks.

**The script and its result.** The file in `load/`, its threshold, and what a
run printed.

**Why, from the code.** The class that keeps tokens and how it is wired into
each copy. Say how you found it.

**The kind of problem.** Use the vocabulary from lecture.

### Finding 2: bookings

**The claim.**

**The script and its result.** Say how it reaches each copy directly, and why
it has to.

**Why, from the code.**

**The kind of problem.**

### What you checked

One thing your agent proposed that you checked (what it proposed, how you
checked it, and what came of it).

---

## Milestone 3: One boundary

Take one problem from Milestone 1 or 2 and propose the boundary that fixes it.

**The boundary.** The port, what it promises, and which side implements it. Say what
moves and what stays.

**Who owns the data.** Name the module that owns the data this boundary is about,
and say what everybody else is allowed to do with it.

**The one invariant that must not cross.** Exactly one, stated so that a reader
can tell whether a given piece of code violates it.

**What the boundary costs.** An interface to keep, a second implementation to
maintain, a performance argument to have again. Include what it leaves unfixed.

Feel free to read the appendix on what a good proposal looks like in the Lab 3 handout
before you write this section.
