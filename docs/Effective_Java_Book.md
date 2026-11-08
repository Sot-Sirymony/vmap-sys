Here's a practical guide to the most useful ideas from Joshua Bloch's *Effective Java* (3rd edition, 90 items in 11 chapters). I've grouped them by chapter, explained each in my own words, and added short before/after code. Item numbers are from the 3rd edition.

---

## 1. Creating and destroying objects

**Item 1 – Use static factory methods instead of constructors**
A static factory can have a meaningful name, can return a cached object, and can return a subtype.
```java
// Instead of: new Boolean(true)
Boolean b = Boolean.valueOf(true);   // reuses a cached instance
List<String> list = List.of("a", "b");
```

**Item 2 – Use a Builder when there are many parameters**
This avoids long chains of constructors and objects left half-built by setters.
```java
public class Pizza {
    private final String size; private final boolean cheese, ham;
    private Pizza(Builder b) { size = b.size; cheese = b.cheese; ham = b.ham; }
    public static class Builder {
        private final String size; private boolean cheese, ham;
        public Builder(String size) { this.size = size; }
        public Builder cheese() { cheese = true; return this; }
        public Builder ham()    { ham = true; return this; }
        public Pizza build()    { return new Pizza(this); }
    }
}
Pizza p = new Pizza.Builder("L").cheese().ham().build();
```

**Item 3 – Enforce singletons with an enum.** `public enum Config { INSTANCE; }` is safe against serialization and reflection attacks.

**Item 4 – Use a private constructor for utility classes** so nobody can create an instance of them.

**Item 5 – Use dependency injection instead of hard-wiring resources.** Pass dependencies in through the constructor. The class becomes testable and flexible.
```java
public class SpellChecker {
    private final Dictionary dict;
    public SpellChecker(Dictionary dict) { this.dict = Objects.requireNonNull(dict); }
}
```

**Item 6 – Avoid creating unnecessary objects.** Watch for autoboxing in loops (`Long sum` vs `long sum`) and compile a `Pattern` once instead of inside a method that's called often.

**Item 7 – Remove obsolete object references.** Set them to `null` in classes that manage their own memory, like a custom stack. Also watch caches and listeners.

**Item 9 – Use try-with-resources instead of try-finally.**
```java
try (BufferedReader br = new BufferedReader(new FileReader(path))) {
    return br.readLine();
}
```

---

## 2. Methods common to all objects

- **Item 10 – Follow the `equals` contract:** reflexive, symmetric, transitive, consistent, and `x.equals(null)` is false.
- **Item 11 – Always override `hashCode` when you override `equals`.** If you don't, `HashMap` and `HashSet` break.
- **Item 12 – Override `toString`** with useful information.
- **Item 13 – Be careful with `clone`.** Prefer copy constructors or copy factories.
- **Item 14 – Implement `Comparable` when there's a natural order.** Use comparator construction methods:
```java
private static final Comparator<Person> COMPARATOR =
    Comparator.comparing(Person::lastName).thenComparing(Person::firstName);
```
Tip: Java **records** (Java 16+) generate `equals`, `hashCode` and `toString` for you.

---

## 3. Classes and interfaces

**Item 15 – Keep accessibility as low as you can.** Fields should be private. Make things public only if they're part of your API.

**Item 16 – In public classes, use accessor methods, not public fields.**

**Item 17 – Minimize mutability.** Immutable objects are simpler, thread-safe and freely shareable. The rules:
- no setters
- make the class `final`
- make all fields `private final`
- make defensive copies of mutable components
```java
public final class Money {
    private final BigDecimal amount;
    public Money(BigDecimal amount) { this.amount = amount; }
    public Money add(Money o) { return new Money(amount.add(o.amount)); } // returns a new object
}
```

**Item 18 – Prefer composition over inheritance.** Inheritance across packages is fragile because changes in the superclass can break subclasses. Wrap the object and forward calls instead (decorator pattern).

**Item 19 – Design and document classes for inheritance, or else prohibit it** (`final`).

**Item 20 – Prefer interfaces to abstract classes.** You can add default methods or provide a skeletal implementation (`AbstractXxx`).

**Item 22 – Use interfaces only to define types.** Don't make "constant interfaces".

**Item 23 – Prefer class hierarchies to tagged classes.** Use polymorphism instead of `switch (type)` fields.

**Item 24 – Prefer static member classes to non-static ones.** A non-static inner class holds a hidden reference to its outer instance, which can leak memory.

---

## 4. Generics

- **Item 26 – Don't use raw types.** Write `List<String>`, not `List`.
- **Item 27 – Eliminate unchecked warnings.** If one is truly safe, suppress it in the smallest possible scope and comment why.
- **Item 28 – Prefer lists to arrays.** Generics catch type errors at compile time. Arrays fail at runtime.
- **Item 31 – Use bounded wildcards for flexible APIs. PECS: Producer `extends`, Consumer `super`.**
```java
public void pushAll(Iterable<? extends E> src) { for (E e : src) push(e); }
public void popAll(Collection<? super E> dst)  { while (!isEmpty()) dst.add(pop()); }
```

---

## 5. Enums and annotations

- **Item 34 – Use enums instead of `int` constants.** You get type safety, and enums can have fields and methods.
```java
public enum Operation {
    PLUS("+") { double apply(double x, double y) { return x + y; } },
    TIMES("*") { double apply(double x, double y) { return x * y; } };
    private final String symbol;
    Operation(String s) { symbol = s; }
    abstract double apply(double x, double y);
}
```
- **Item 35 – Use instance fields instead of `ordinal()`.**
- **Item 36/37 – Use `EnumSet` instead of bit fields, and `EnumMap` instead of ordinal indexing.**
- **Item 40 – Always use `@Override`.** The compiler then catches mistakes like accidental overloading.

---

## 6. Lambdas and streams

- **Item 42 – Prefer lambdas to anonymous classes.**
- **Item 43 – Prefer method references when they're clearer:** `map(String::toUpperCase)`.
- **Item 44 – Use standard functional interfaces** (`Function`, `Supplier`, `Predicate`, `UnaryOperator`…) before writing your own.
- **Item 45 – Use streams judiciously.** Don't force everything into a stream. Sometimes a loop is more readable.
- **Item 46 – Prefer side-effect-free functions in streams.** Use collectors, not `forEach` that changes outside state.
```java
// Bad
Map<String, Long> freq = new HashMap<>();
words.forEach(w -> freq.merge(w, 1L, Long::sum));
// Good
Map<String, Long> freq = words.stream()
    .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));
```
- **Item 47 – Prefer `Collection` to `Stream` as a return type.**
- **Item 48 – Be careful with parallel streams.** Measure before using them. They can be slower or even wrong.

---

## 7. Methods

- **Item 49 – Check parameters for validity** early: `Objects.requireNonNull(x)`, and throw `IllegalArgumentException` for bad input.
- **Item 50 – Make defensive copies when needed.**
```java
public Period(Date start, Date end) {
    this.start = new Date(start.getTime());   // copy first, then validate
    this.end   = new Date(end.getTime());
    if (this.start.after(this.end)) throw new IllegalArgumentException();
}
```
(Better still, use immutable `java.time.Instant`.)
- **Item 51 – Design method signatures carefully.** Use good names, avoid long parameter lists (more than 4), and prefer interfaces as parameter types. Use enums instead of `boolean` flags.
- **Item 52 – Overload carefully.** Avoid overloads with the same number of parameters.
- **Item 54 – Return empty collections or arrays, not `null`.**
- **Item 55 – Return `Optional` carefully.** Use it for return values that might be absent. Don't use it for fields, parameters or collections.
- **Item 56 – Write Javadoc for every exposed API element.**

---

## 8. General programming

- **Item 57 – Minimize the scope of local variables.** Declare them where they're first used.
- **Item 58 – Prefer for-each loops** to traditional loops.
- **Item 59 – Know and use the libraries** (`java.util`, `java.time`, `ThreadLocalRandom`, etc.).
- **Item 60 – Avoid `float`/`double` when you need exact answers.** Use `BigDecimal`, `int` or `long` for money.
- **Item 61 – Prefer primitives to boxed primitives.** `==` on `Integer` compares references, and unboxing `null` throws an NPE.
- **Item 62 – Avoid strings where other types are better** (enums, real objects).
- **Item 63 – Beware the performance of string concatenation in loops.** Use `StringBuilder`.
- **Item 64 – Refer to objects by their interfaces:** `List<X> l = new ArrayList<>();`
- **Item 67 – Optimize judiciously.** Write good, clear code first, then measure.
- **Item 68 – Follow naming conventions.**

---

## 9. Exceptions

- **Item 69 – Use exceptions only for exceptional conditions,** never for normal control flow.
- **Item 70 – Use checked exceptions for recoverable conditions** and runtime exceptions for programming errors.
- **Item 71 – Avoid unnecessary checked exceptions.**
- **Item 72 – Favor standard exceptions:** `IllegalArgumentException`, `IllegalStateException`, `NullPointerException`, `UnsupportedOperationException`.
- **Item 73 – Throw exceptions that fit the abstraction.** Wrap low-level exceptions and keep the cause:
```java
try { ... } catch (SQLException e) { throw new RepositoryException("Load user failed", e); }
```
- **Item 75 – Include failure details in exception messages** (the values that caused the failure).
- **Item 76 – Strive for failure atomicity.** After a failure, the object should still be in a valid state.
- **Item 77 – Don't ignore exceptions.** An empty `catch {}` is a bug waiting to happen.

---

## 10. Concurrency

- **Item 78 – Synchronize access to shared mutable data.** Both reads and writes need it, or use `volatile` or atomic classes.
- **Item 79 – Avoid excessive synchronization.** Don't call unknown ("alien") methods while holding a lock.
- **Item 80 – Prefer executors, tasks and streams to raw threads.** On Java 21+, virtual threads are another option.
- **Item 81 – Prefer concurrency utilities** (`ConcurrentHashMap`, `CountDownLatch`) to `wait`/`notify`.
- **Item 82 – Document thread safety.**
- **Item 83 – Use lazy initialization judiciously.** For static fields, use the holder-class idiom.

---

## 11. Serialization

- **Item 85 – Prefer alternatives to Java serialization** (JSON, Protobuf). Deserialization is a major security risk.
- **Items 86–90:** if you must implement `Serializable`, do it with great care. Write a defensive `readObject` and consider the serialization proxy pattern.

---

## A checklist to start improving your code

1. Make fields `private final` and classes immutable where you can (Item 17).
2. Use builders for objects with many fields (Item 2) and constructor injection for dependencies (Item 5).
3. Override `equals` and `hashCode` together, or use records (Items 10–11).
4. Never return `null` collections. Use `Optional` only for return values (Items 54–55).
5. Validate parameters at the start of methods (Item 49).
6. Use enums, `EnumSet` and `EnumMap` instead of int or string constants (Items 34–37).
7. Prefer composition over inheritance (Item 18).
8. Use try-with-resources (Item 9) and never swallow exceptions (Item 77).
9. Use `BigDecimal` for money (Item 60) and avoid boxed types in calculations (Item 61).
10. Prefer standard libraries and concurrency utilities to hand-rolled code (Items 59, 81).

If you paste a class or module from your project, I can review it against these items and show you exactly what to change. I can also turn this guide into a document you can keep and share with your team.