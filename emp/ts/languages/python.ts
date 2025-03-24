import LanguageInterface from "../LanguageInterface";
import Hint from "../Hint.js";
import Trie from "../Trie.js";

//class for the python language
export default class PythonLang implements LanguageInterface {

    private keywordsTrie: Trie;

    constructor() {
        this.keywordsTrie = new Trie(this.dictionary.keys());
    }

    public getKeywordsTrie(): Trie {
        return this.keywordsTrie;
    }

    public getHint(keyword: string): Hint | undefined {
        return this.dictionary.get(keyword);
    }

    public color(input: string): string {

        //testbook output requires special attention
        if (input.includes("-\n\n#x1B[")) {
            input = this.cleanTestbookOutput(input);
        }

        // failed tests require special attention
        if (input.includes("\n&gt; ") && input.includes("\nE ")) {
            return this.highlightPyTestOutput(input);
        }

        let lines = input.split("\n");

        for (let i = 0; i < lines.length; i++) {
            //the ----> marking the important line
            if (lines[i].substring(0, 9) === "----&gt; ") {
                lines[i] = "<span class='importantEMP'>" + lines[i] + "</span>";
                // console.log(lines[i] + " ONE");
            }
            //tab and then one number for the line
            else if (/\s{6}\d+/.test(lines[i].substring(0, 7))) {
                lines[i] = "<span class='normalEMP'>" + lines[i] + "</span>";
                // console.log(lines[i] + " TWO");
            }
            //the ------ line that seperates python errors
            else if (/-{10}/.test(lines[i].substring(0, 10))) {
                lines[i] = "<span class='normalEMP'>" + lines[i] + "</span>";
                // console.log(lines[i] + " THREE");
            }
            //for a different style which is marked like this
            else if (lines[i].substring(0, 7) === "  File ") {
                let quoteEnd = lines[i].lastIndexOf("&quot;,");
                let lineNumberEnd = lines[i].indexOf(", in");
                lines[i] =
                    "<span class='normalEMP'>" +
                    //the filename is marked, it comes after "  File &quot;"
                    "  File &quot;<span class='importantEMP'>" + lines[i].substring(13, quoteEnd) + "</span>" +
                    //the line number is marked
                    lines[i].substring(quoteEnd, quoteEnd + 13) + "<span class='importantEMP'>" +
                    lines[i].substring(quoteEnd + 13, lineNumberEnd) + "</span>" + lines[i].substring(lineNumberEnd);
                "</span>";

                //the next line contains the code if it starts with four spaces
                if (/\s{4}/.test(lines[i + 1].substring(0, 4))) {
                    lines[i + 1] = "<span class='importantEMP'>" + lines[i + 1] + "</span>";
                    i++;
                }
                // console.log(lines[i] + " FOUR");
            }
        }

        //if there is an explaination in the last line it's important
        //find last line
        let lastLine = lines.length - 1;
        while (lines[lastLine] == "") {
            lastLine--;
        }

        if (lines[lastLine]) {
            let explainIndex = lines[lastLine].indexOf("Error: ");
            if (explainIndex !== -1) {
                lines[lastLine] = lines[lastLine].substring(0, explainIndex + 7)
                    + "<span class='importantEMP'>" + lines[lastLine].substring(explainIndex + 7)
                    + "</span>"
            }
        }
        

        //Mark everything else unimportant
        input = "<span class='unimportantEMP'>" + lines.join("\n") + "</span>"

        return input;
    }

    private cleanTestbookOutput(input: string): string {

        let weird = "#x1B[";

        //filter only the relevant part, which starts with the weird string
        let lines = input.split("\n");

        let firstOcc = 0;
        while (lines[firstOcc].substring(0, weird.length) !== weird) {
            firstOcc++;
        }

        let lastOcc = firstOcc;
        while (lines[lastOcc].substring(0, weird.length) === weird) {
            lastOcc++;
        }

        lines = lines.slice(firstOcc, lastOcc);

        input = lines.join("\n");

        //remove all of the weird strings from input,
        for (let i = 0; i < input.length; i++) {
            if (input.substring(i, i + weird.length) === weird) {

                let weirdUntil = i + weird.length;
                let maxSearchDepth = weirdUntil + 50;

                //weird string always ends with m
                while (input.charAt(weirdUntil) !== "m" || weirdUntil > maxSearchDepth) {
                    weirdUntil++;
                }
                weirdUntil++;

                input = [input.slice(0, i), input.slice(weirdUntil)].join("");

                //search again from same position
                i--;
            }
        }
        return input;
    }

    private highlightPyTestOutput(input: string): string {

        let endOfInitialMessage = input.indexOf("\n\n");

        input = input.substring(0, endOfInitialMessage + 1)
            + "</span>" + input.substring(endOfInitialMessage + 1);


        let lines = input.split("\n");

        lines[0] = "<span class='importantEMP'>" + lines[0] + "</span><span class='normalEMP'>";

        let i = 1;
        let messagePresent = false;
        while (lines[i] && !messagePresent) {
            if (lines[i].substring(0, 3) === " : ") {
                messagePresent = true;
                lines[i] = "<span class='importantEMP'>" + lines[i];
            }

            i++;
        }

        if (messagePresent) {
            lines[i] = "</span>";
        }

        for (let j = 0; j < lines.length; j++) {
            if (lines[j].substring(0, 6) === "&gt;  ") {
                lines[j] = "<span class='normalEMP'>" + lines[j] + "</span>";
            }
        }

        //Mark everything else unimportant
        input = "<span class='unimportantEMP'>" + lines.join("\n") + "</span>";

        return input;
    }

    private dictionary = new Map<string, Hint>(
        [
            //exceptions https://docs.python.org/3/library/exceptions.html
            ["AssertionError", new Hint(
                //english:
                `An error occurred when an \"assert\" 
statement was run. 
This usually happens with 
failed automated tests.
Different types of asserts exist. 
Example: assert x == 6.
It is expected that x has the value 6.
However, if x is not 6 this error
is thrown.`,

                //german
                `Es gab einen Fehler dabei ein \"assert\" 
auszuführen. 
Dies passiert normalerweise bei 
automatisierten Tests, die nicht klappen.
Es gibt verschiedene Arten von asserts.
Beispiel: assert x == 6
Hier wird erwartet, dass x den Wert 6 hat.
Wenn dies nicht der Fall ist,
gibt es einen AssertionError.
Assert (En) = Behaupten (De)`
            )],

            ["AttributeError", new Hint(
                //english:
                `An Error occurred when trying to 
use an invalid attribute. An attribute is something that can 
be accessed like so: x.some_attribute
Example: 
   x = 5
   x.hello
Will throw this error because the number x
doesn't have a \"hello\" attribute.`,

                //german
                `Es wurde versucht, ein unbekanntes
Attribut zu nutzen. 
Ein Attribut ist etwas, auf das so
zugegriffen wird: x.ein_attribut
Beispiel:
   x = 5
   x.hallo
Wird diesen Fehler ergeben, da die Zahl x
kein Attribut \"hallo\" hat.
Attribute (En) = Attribut / Eigenschaft (De)`
            )],

            ["EOFError", new Hint(
                //english:
                `EOF is short for End Of File.
This error usually occurs when an
input function is interrupted somehow.
Example: input(\"Enter your name: \")
Will raise this error if the user
interrupts the resulting prompt with Ctl + D\n`,

                //german
                `Dieser Fehler wird normalerweise geworfen,
wenn eine Eingabe unterbrochen wird.
Beispiel: input(\"Gib deinen Namen ein: \")
Ergibt diesen Fehler, wenn die resultierende
Aufforderung mit Strg + D unterbrochen wird.
EOF = End Of File (En) = Ende der Datei (De)`
            )],

            ["FloatingPointError", new Hint(
                //english:
                `This exception is currently not in use,
how did you end up here?`,

                //german
                `Dieser Fehler ist zur Zeit ungenutzt,
wie bist du hier gelandet?`
            )],

            ["GeneratorExit", new Hint(
                //english:
                `This exception should not be thrown.
Do you perhaps raise it yourself somewhere?`,

                //german
                `Dieser Fehler sollte nicht geworfen werden.
Raist du ihn selbst irgendwo?`
            )],

            ["ImportError", new Hint(
                //english:
                `Something that you are trying to import
doesn't load properly.
Did you change something about the files of
the module you are trying to import?`,

                //german
                `Etwas, das du importieren möchtest,
kann nicht richtig geladen werden.
Hast du an den zugehörigen Dateien
etwas verändert?`
            )],

            ["ModuleNotFoundError", new Hint(
                //english:
                `A module that you are trying to import
can't be found.
Did you make a typo, or have you
possibly not installed it?
Example: import some_module
Will throw this error when
some_module can't be found`,

                //german
                `Ein Modul, das du importieren möchtest,
kann nicht gefunden werden.
Hast du vielleicht einen Tippfehler gemacht,
oder das Modul nicht installiert?
Beispiel: import ein_modul
Wird diesen Fehler ergeben,
wenn ein_modul nicht gefunden wird.
Module (En) = Modul / Bauelement (De)`
            )],

            ["IndexError", new Hint(
                //english:
                `You are trying to access an index
that can't be accessed.
Remember that in programming
we start counting from zero!
The first elemt of a list is at 0
and the last onr at it's length - 1.
Example:
   x = [\"A\", \"B\"]
   print(x[2])
Will throw this error, as the list x
only has the indices 0 and 1 for A and B.`,

                //german
                `Du versuchst eine Stelle zu nutzen,
die nicht existiert.
Vergiss nicht, dass beim Programmieren
bei Null angefangen wird zu zählen!
Das erste Element einer Liste ist also bei 0 und das letzte bei ihrer Länge - 1.
Beispiel:
   x = [\"A\", \"B\"]
   print(x[2])
Wird diesen Fehler ergeben, weil die Liste x
nur die Stellen 0 und 1 für A und B hat.
index (En) = Index / Verzeichnis (De)`
            )],

            ["KeyError", new Hint(
                //english:
                `You are trying to access a key in a dictionary,
that doesn't contain the key.
Did you possibly make a typo?
Example:
   x = {\"my_key\": \"Hello\"}
   x[\"wrong_key\"]
Will throw this error, as the dictionary x
doesn't contain the wrong_key.
It only contains my_key.`,

                //german
                `Du versuchst einen Wert in einem Dictionary
zu nutzen, der darin nicht existiert.
Beispiel:
   x = {\"mein_key\": \"Hello\"}
   x[\"falscher_key\"]
Wird diesen Fehler produzieren, da das Dictionary
nicht falscher_key enthält.
Es enthält nur mein_key.
key (En) = Schlüssel (De)
dictionary (En) = Wörterbuch (De)`
            )],

            ["KeyboardInterrupt", new Hint(
                //english:
                `The execution was interrupted by keyboard input.
Did you accidentally press Ctrl + C or delete?`,

                //german
                `Die Ausführung wurde durch eine
Tastatur Eingabe unterbrochen.
Hast du aus Versehen Strg + C
oder Entfernen gedrückt?
keyboard (En) = Tastatur (De)
interrupt (En) = Unterbrechung (De)`
            )],

            ["MemoryError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["NameError", new Hint(
                //english:
                `This error is thrown when you
try to use something that is not
yet defined.
Did you make a typo, or have you
declared a variable after using it?
Example: print(a)
will throw this error, if a is not defined.`,

                //german
                `Dieser Fehler ergibt sich, wenn
man versucht, etwas zu nutzen,
das noch nicht definiert ist.
Hast du einen Schreibfehler gemacht,
oder nutzt du evtl. eine Variable, bevor
sie definiert wurde?
Beispiel: print(a)
Wird diesen Fehler ergeben,
wenn a nicht definiert ist.`
            )],

            ["NotImplementedError", new Hint(
                //english:
                `You are trying to use a function,
that is not yet implemented,
but that is supposed to be.`,

                //german
                `Du versuchst eine Funktion zu nutzen,
die noch nicht implementiert ist,
es aber sein sollte.`
            )],

            ["OSError", new Hint(
                //english:
                `Something related to the operating system
went wrong. You need to figure this out
on your own with the error message, sorry!`,

                //german
                `Etwas im Zusammenhang mit dem Betriebssystem
ist schiefgelaufen. Du musst selber mit dieser
Fehlermeldung herausfinden, was. Sorry!`
            )],

            ["OverflowError", new Hint(
                //english:
                `Something is overflowing,
this means it's becoming too large
for Python.
Example:
   import math
   print(math.exp(1000))
Causes this error,
as the value of math.exp(1000) is too large.`,

                //german
                `Etwas wird zu groß für Python.
Beispiel:
   import math
   print(math.exp(1000))
Ergibt diesen Fehler,
da der Wert von math.exp(1000) zu groß ist.
overflow (En) = Überlauf (De)`
            )],

            ["RecursionError", new Hint(
                //english:
                `This error occurs when a function calls itself
too many times in a row.
The usual maximum amount is 1000 times.
Is it possible that your function never stops
calling itself?",
                    "Example:
   def function():
       return function():
   function()
will throw this exception, as it will
infinitely call itself.`,

                //german
                `Dieser Fehler entsteht, wenn sich eine Funktion
selbst zu oft hintereinander aufruft.
Das normale Maximum ist 1000 Mal.
Ist es möglich, dass deine Funktion nie
aufhört, sich selbst aufzurufen?
Beispiel:
   def funktion():
       return funktion():
   funktion()
wird diesen Fehler ergeben, da sich
die Funktion unendlich oft selbst aufruft.
recurisive (En) = rekursiv / selbstaufrufend (De)`
            )],

            ["ReferenceError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["RuntimeError", new Hint(
                //english:
                `Something went wrong when running your code,
and no other exception fits it.
Read what it says after this in your traceback.`,

                //german
                `Etwas ist schiefgelaufen, während dein Programm
ausgeführt wurde und kein anderer Fehler passt.
Lies dir durch was hier nach in deinem Traceback steht.
runtime (En) = Laufzeit (De)`
            )],

            ["StopIteration", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["StopAsyncIteration", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["SyntaxError", new Hint(
                //english:
                `You wrote invalid Python code somewhere.
Example: a = 1 +% 2
will throw this exception,
as +% is not a valid Python code.`,

                //german
                `Du hast falschen Python code geschrieben.
Beispiel: a = 1 +% 2
wird diesen Fehler ergeben,
da +% kein richtiger Python-Code ist.`
            )],

            ["IndentationError", new Hint(
                //english:
                `Your indentation is wrong somewhere.
Example:
    def function():
    return "Hello!"
Will cause this, as the return is not
indentated correctly.`,

                //german
                `Deine Einrückung ist falsch.
Example:
    def funktion():
    return "Hallo!"
Wird diesen Fehler ergeben, weil das
return nicht richtig eingerückt ist.
indentation (En) = Einrückung (De)`
            )],

            ["TabError", new Hint(
                //english:
                `Looks like you mixed tab and space Characters
in you indentations.`,

                //german
                `Du hast Tab und Leerzeichen bei der
Einrückung vermischt.`
            )],

            ["SystemError", new Hint(
                //english:
                `Something internally went wrong with the Python interpreter.
How did you do this?!`,

                //german
                `Ein Python interner Fehler ist aufgetreten.
Wie hast du das geschafft?!`
            )],

            ["SystemExit", new Hint(
                //english:
                `This exception should not be thrown or caught.
Are you doing this?`,

                //german
                `Dieser Fehler sollte nicht geworfen oder gefangen werden.
Machst du das?`
            )],

            ["TypeError", new Hint(
                //english:
                `You are trying to use wrong types somewhere.
Types are automatically detected by Python.
Example: a = \"number: \" + 10
will throw this error, as the type of
\"number: \" (str) can't be added to
the type fo 10 (int).`,

                //german
                `Du versuchst, falsche Typen zu verwenden.
Typen werden automatisch von Python erkannt.
Beispiel: a = \"numer: \" + 10
wird diesen Fehler produzieren, da der Typ von
\"nummer: \" (str) nicht mit dem Typ von
10 (int) addiert werden kann.
type (En) = Typ / Art (De)`
            )],

            ["UnboundLocalError", new Hint(
                //english:
                `You are trying to assign a value to
a variable outside its scope.
To use variables from outside of functions
inside them, you need to use \"global my_variable\"
inside the function.
Example:
   x = 10
   def function():
       # global x <- this would fix it
       x += 1
   function()
will result in this error, as the variable x
is not defined inside the function.`,

                //german
                `Du versuchst eine Variable außerhalb des Bereichs,
in dem sie definiert ist, zu nutzen.
Um eine Variable von außerhalb einer Funktion
in ihr zu verwenden, musst du
\"global meine_variable\" nutzen.
Beispiel:
   x = 10
   def funktion():
       # global x <- das würde es beheben.
       x += 1
   funktion()
wird diesen Fehler ergeben, da die Variable x
nicht in der Funktion definiert wurde.`
            )],

            ["UnicodeError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["UnicodeEncodeError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["UnicodeDecodeError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["UnicodeTranslateError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ValueError", new Hint(
                //english:
                `You called a function with a parameter that
is the correct type, but still doesn't work.
Example:
   list = []
   list.remove(\"x\")
will cause this error, as the list does not
contain the value \"x\".`,

                //german
                `Du hast eine Funktion mit einem Parameter
aufgerufen, der zwar den richtigen Typ hat,
aber trotzdem nicht funktioniert.
Beispiel:
   liste = []
   liste.remove(\"x\")
wird diesen Fehler ergeben, da die Liste
nicht den Wert \"x\" enthält.
value (En) = Wert (De)`
            )],

            ["ZeroDivisionError", new Hint(
                //english:
                `Your code is trying to divide by zero.
This is not mathematically possible.
Make sure to handle the case in which
the denominator is zero.
Example:
   x = 0
   print(100 / x)
Will cause this error, as Python can't
calculate 100 divided by 0.`,

                //german
                `Dein Programm versucht durch null zu teilen.
Das ist mathematisch nicht möglich.
Diesen Sonderfall solltest du irgendwie
umgehen.
Beispiel:
   x = 0
   print(100 / x)
Ergibt diesen Fehler, da Python
nicht 100 geteilt durch 0 berechnen kann.
zero division (En) = Teilung durch null (De)`
            )],

            ["BlockingIOError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ChildProcessError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ConnectionError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["BrokenPipeError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ConnectionAbortedError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ConnectionRefusedError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["ConnectionResetError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["FileExistsError", new Hint(
                //english:
                `You are trying to create a file that already exists.
Don't do this.`,

                //german
                `Du versuchst eine Datei zu erstellen, die schon existiert.
Mach das nicht.",
                    "file (En) = Datei (De)`
            )],

            ["FileNotFoundError", new Hint(
                //english:
                `You are trying to access a file or directory that
does not exist. Did you make a typo?`,

                //german
                `Du versuchst eine Datei oder einen Ordner zu nutzen,
die oder der nicht existiert.
Hast du einen Tippfehler gemacht?
file (En) = Datei (De)`
            )],

            ["InterruptedError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["IsADirectoryError", new Hint(
                //english:
                `You are trying to do something with a directory
that only works with files.
Example: You can't delete a directory with the
os.remove() function.`,

                //german
                `Du versuchst etwas mit einem Ordner zu tun,
das nur mit Dateien funktioniert.
Beispiel: Du kannst einen Ordner nicht mit
der os.remove() Funktion löschen.
directory (En) = Ordner (De)`
            )],

            ["NotADirectoryError", new Hint(
                //english:
                `You are trying to do something on a non-directory
that only works with directories.
Example: you can't use os.listdir() on
a file.`,

                //german
                `Du versuchst etwas mit einem Nicht-Ordner zu tun,
das nur mit Ordnern funktioniert.
Beispiel: Du kannst os.lisdirt() nicht
auf einer Datei nutzen.
directory (En) = Ordner (De)`
            )],

            ["PermissionError", new Hint(
                //english:
                `You are trying to run some operation on
your operating system that Python doesn't
have the permission for.
Example: writing a file that requires
admin privileges.`,

                //german
                `Du versucht eine Operation auf deinem
Betriebssystem auszuführen, für die
Python nicht die Berechtigungen hat.
Beispiel: Eine Datei schreiben, die Admin-
Rechte benötigt.
permission (En) = Berechtigung (De)`
            )],

            ["ProcessLookupError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            ["TimeoutError", new Hint(
                //english:
                `TODO`,

                //german
                `TODO`
            )],

            //add keywords too? TODO

            ["Traceback (most recent call last)", new Hint(
                //english:
                `A traceback is a report containing
the function calls made in your code
that were involved in producing this error.
In other languages, this is called a stack trace,
or backtrace.
\"most recent call last\" means,
the very bottom part of the traceback
was the one that occurred closest to
this error.`,

                //german
                `Ein Traceback ist ein Bericht,
der Funktionsaufrufe in deinem Code enthält,
die zu diesem Fehler geführt haben.
In anderen Sprachen nennt man dies auch
einen stack trace, oder backtrace.
\"most recent call last\" heißt,
dass der unterste Teil des traceback
am nächsten zu diesem Fehler aufgerufen wurde.
traceback (En) = Zurückverfolgung (De)
most recent call last (En) = Jüngste Aufforderung zuletzt (De)`

            )]
        ]);

}
