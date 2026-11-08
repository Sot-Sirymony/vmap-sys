Here are the main principles from *Clean Code* (Robert C. Martin, 2008), arranged roughly in the book's chapter order. Each one has a short before/after example. The ideas are paraphrased, not quoted.

---

## 1. Meaningful names

Names should tell you **why something exists, what it does and how it's used**. If a name needs a comment to explain it, the name is wrong.

- **Show intent:** `d` → `elapsedTimeInDays`
- **Don't mislead:** don't call something `accountList` unless it really is a list.
- **Make real distinctions:** `getActiveAccount()`, `getActiveAccountInfo()` and `getActiveAccountData()` mean nothing different to a reader.
- **Use names you can say and search for:** `genymdhms` → `generationTimestamp`. Use named constants instead of magic numbers: `if (status == 4)` → `if (status == STATUS_CLOSED)`.
- **Skip encodings and prefixes:** no Hungarian notation (`strName`), no `m_` member prefixes.
- **Classes are nouns** (`Customer`, `InvoiceParser`). **Methods are verbs** (`save()`, `calculateTotal()`).
- **One word per concept:** don't mix `fetch`, `retrieve` and `get` for the same idea.
- **Use domain terms:** `JobQueue` and `Visitor` are fine because programmers will read your code.

```python
# Before
def get(l):
    r = []
    for x in l:
        if x[0] == 4:
            r.append(x)
    return r

# After
def get_flagged_cells(game_board):
    return [cell for cell in game_board if cell.is_flagged()]
```

---

## 2. Functions

This is the most influential chapter.

- **Small, then smaller.** Aim for a few lines. Blocks inside `if`, `else` and `while` should usually be one line, often a function call.
- **Do one thing.** Test: if you can pull another function out of it with a name that isn't just a restatement, it was doing more than one thing.
- **One level of abstraction per function.** Don't mix high-level policy (`renderPage()`) with low-level detail (`html.append("<br>")`).
- **The Stepdown Rule:** the code should read top to bottom like a story, each function followed by the ones it calls.
- **Few arguments.** Zero is best, then one, then two. Avoid three. If you need more, group them into an object (`createCircle(x, y, radius)` → `createCircle(center, radius)`).
- **No flag arguments.** `render(true)` means the function does two things, so split it into `renderForSuite()` and `renderForSingleTest()`.
- **No side effects.** `checkPassword()` must not quietly start a session as well.
- **Command-Query Separation:** a function either *does* something or *answers* something, never both. `if (set("user", "bob"))` is confusing.
- **Use exceptions, not error codes.** Error codes force nested `if`s at every call site.
- **Extract try/catch bodies** into their own functions. Error handling counts as "one thing."
- **DRY (Don't Repeat Yourself):** duplication is the root of much bad design.

```javascript
// Before: does validation, calculation and persistence
function processOrder(order, save) {
  if (!order.items || order.items.length === 0) throw new Error("Empty");
  let total = 0;
  for (const i of order.items) total += i.price * i.qty;
  if (order.coupon) total *= 0.9;
  order.total = total;
  if (save) db.insert(order);
}

// After
function processOrder(order) {
  validate(order);
  order.total = calculateTotal(order);
}
function saveOrder(order) { db.insert(order); }
function calculateTotal(order) {
  return applyDiscount(subtotal(order.items), order.coupon);
}
```

---

## 3. Comments

Martin's view is that **a comment usually means the code failed to explain itself**. Comments also go stale, while code is the actual truth.

```java
// Before
// Check if employee is eligible for full benefits
if ((employee.flags & HOURLY_FLAG) && (employee.age > 65))

// After
if (employee.isEligibleForFullBenefits())
```

**Good comments:** legal headers, explaining *why* (intent or a non-obvious decision), warnings ("not thread-safe"), `TODO`s, and docs for public APIs.
**Bad comments:** repeating what the code says, journal or changelog comments (that's what git is for), closing-brace markers like `} // end while`, author attributions, and **commented-out code**. Delete it, because version control remembers.

---

## 4. Formatting

- **Newspaper metaphor:** high-level ideas at the top of a file, details further down.
- **Vertical openness:** blank lines separate concepts.
- **Vertical density:** lines that belong together stay together.
- **Keep related things close:** declare variables near where you use them. A caller should sit just above its callee.
- **Team rules beat personal taste.** Pick one style, enforce it automatically (Prettier, Black, gofmt), and move on.

---

## 5. Objects and data structures

- **Objects** hide their data and expose behavior. **Data structures** expose data and have no behavior. Don't build hybrids that do half of each.
- **Law of Demeter:** a method should only talk to its close friends. Avoid "train wrecks":

```java
// Before: knows the internal structure of three objects
String path = ctxt.getOptions().getScratchDir().getAbsolutePath();

// After: tell the object what you want
BufferedOutputStream bos = ctxt.createScratchFileStream(fileName);
```

---

## 6. Error handling

- Use **exceptions**, not return codes.
- **Write the try/catch/finally first** when code can fail. It sets out what the caller can expect.
- Give **context** in exceptions: what failed and why.
- **Wrap third-party exceptions** in your own types so a vendor's API doesn't leak through your codebase.
- **Don't return null.** Return an empty list, an Optional, or a *Special Case / Null Object*.
- **Don't pass null** into methods.

```python
# Before
employees = get_employees()
if employees is not None:
    for e in employees: total += e.pay

# After: get_employees() returns [] when there are none
for e in get_employees():
    total += e.pay
```

---

## 7. Boundaries (third-party code)

Wrap external libraries behind your own interface, for example a `Sensors` class instead of passing a raw `Map<String, Sensor>` around. That makes them easy to replace and keeps their quirks in one place. Write **learning tests** to check how a library behaves, and run them again when you upgrade.

---

## 8. Unit tests

- **The three laws of TDD:** (1) write no production code until a failing test exists; (2) write only enough test to fail; (3) write only enough code to pass.
- **Test code deserves the same care as production code.** Messy tests rot and get dropped.
- **One concept per test.** Use the **Build–Operate–Check** pattern (Arrange–Act–Assert).
- **F.I.R.S.T.:** tests should be **F**ast, **I**ndependent, **R**epeatable, **S**elf-validating (pass or fail, with no manual checking) and **T**imely (written before or with the code).

```python
def test_withdraw_reduces_balance():
    account = Account(balance=100)   # Arrange
    account.withdraw(30)             # Act
    assert account.balance == 70     # Assert
```

---

## 9. Classes

- **Small,** measured by *responsibilities*, not lines.
- **Single Responsibility Principle (SRP):** a class should have one reason to change. If its name needs "and", "Manager" or "Processor", be suspicious.
- **High cohesion:** most methods should use most of the fields. When a subset of fields is used only by a subset of methods, that's a class waiting to be pulled out.
- **Open/Closed Principle:** add new behavior by adding classes, not by editing existing ones. For example, `Sql` becomes subclasses `SelectSql` and `InsertSql`.
- **Dependency Inversion:** depend on abstractions (interfaces), not concrete classes. This makes code testable.

```python
# Before: one class, many reasons to change
class Employee:
    def calculate_pay(self): ...
    def save_to_db(self): ...
    def generate_report_html(self): ...

# After
class Employee: ...              # business data and rules
class PayCalculator: ...         # payroll policy
class EmployeeRepository: ...    # persistence
class EmployeeReportView: ...    # presentation
```

---

## 10. Systems and emergence

- **Separate construction from use.** Build objects in `main`, factories or a DI container, not inside business logic.
- **Kent Beck's four rules of simple design**, in priority order:
  1. Runs all the tests
  2. Contains no duplication
  3. Expresses the programmer's intent
  4. Minimizes the number of classes and methods

---

## 11. Concurrency (short version)

Keep concurrency code apart from other code (SRP). Limit shared data, prefer copies and immutable data, keep synchronized sections small, and treat odd one-off failures as real threading bugs, not "cosmic rays."

---

## 12. Code smells and heuristics (Chapter 17)

Use this as a review checklist:

| Smell | Fix |
|---|---|
| Long function / large class | Extract function or class |
| Duplicated code | Extract and reuse |
| Too many arguments | Group into an object |
| Flag arguments | Split the function |
| Dead code, commented-out code | Delete it |
| Magic numbers | Named constants |
| Feature envy (a method uses another class's data more than its own) | Move the method |
| Nested conditionals | Guard clauses; encapsulate the condition as `shouldBeDeleted(timer)` |
| Negative conditionals | `if (!buffer.shouldNotCompact())` → `if (buffer.shouldCompact())` |
| `switch` on type repeated in many places | Polymorphism |
| Hidden temporal coupling | Make the order explicit through arguments or return values |
| Inconsistent naming | One word per concept |

---

## How to apply it in practice

1. **The Boy Scout Rule:** leave the code a little cleaner than you found it. Rename one variable or extract one function each time you touch a file. Don't stop everything for a big rewrite.
2. **Write it, then clean it.** Martin says his first drafts are messy too. Get it working, then refactor while the tests protect you.
3. **Get tests in place first** around code you plan to change, so refactoring is safe.
4. **Automate the boring parts:** a formatter, a linter, and complexity limits in CI.
5. **Order of value:** better names → smaller functions → no duplication → better error handling → class structure.

**One caveat:** some advice from 2008 is now debated. Very tiny functions can scatter logic across too many files, and the examples are heavily Java- and OOP-centric. Use the principles with judgment, especially in functional or scripting languages. The aim is code that is **easy to read and safe to change**, not to hit a line count.

If you share some of your code and tell me the language, I can review it against these principles and show a refactored version.