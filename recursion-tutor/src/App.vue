<script lang="ts">

import { idCounterStore } from './plugins/idcounter';

</script>

<script setup lang="ts">
import Diagram from './components/Diagram.vue';
import { shallowRef } from 'vue';
import { appVersion } from './constants';
import CodeMirror from 'vue-codemirror6';
import { standardLibrary } from './stdlib';

const funDecl = shallowRef("let tracerec ...\n\n");
const expr = shallowRef("");
const stopAtRecCall = shallowRef(true);
const onlyShowRecDepth = shallowRef(false);
const shortenNodeContent = shallowRef(false);
const showDiagram = shallowRef(false);
const showTutorial = shallowRef();
const customFunctions = shallowRef();
const showCustumFunctionButtons = shallowRef(false);
const customFunctionsButtons = shallowRef();

if (localStorage.getItem("showTutorial") == "true") showTutorial.value = true;
else showTutorial.value = false;

customFunctions.value = localStorage.getItem("customFunctions");

const sleep = (ms: number) => { 
    return new Promise(resolve => setTimeout(resolve, ms))
  }

function createDiagram() {
  deleteDiagram();
  // for some reason this needs a delay for 0? ms to work
  sleep(0).then(() => {
    showDiagram.value = true;
  });  
}

function deleteDiagram() {
  showDiagram.value = false;

  // reset css vars
  const cssDecl = document.documentElement.style;
  cssDecl.setProperty("--node-spacing-h", "calc(1.5 * var(--node-size))");
  cssDecl.setProperty("--node-spacing-v", "100px");
  cssDecl.setProperty("--node-size", "50px");


  // reset global id counter
  idCounterStore().resetCounter();
}

function toggleTutorial() {
  showTutorial.value = !showTutorial.value;

  // store current state of the tutorial in browser local storage to remember on page reload
  localStorage.setItem("showTutorial", showTutorial.value); 
}

function readFile(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = target.files;
  if (!files || !files[0]) {
    return;
  }
  var reader = new FileReader();
  reader.onload = function(event) {
    const target = event.target;
    customFunctions.value = target?.result;
    localStorage.setItem("customFunctions", customFunctions.value); 
  };
  reader.readAsText(files[0]);
}

function createButtons() {
  console.log(customFunctions.value);

  let raw = customFunctions.value.replace(/\r/g,"").split("//#").map((s: string) => s.trim().split("\n"));
  raw.shift();
  let buttons = raw.map( (button: string[]) => {
    let inner = button.slice(1, button.length - 1).join("\n");
    return [button[0], inner, button[button.length - 1]];
  })
  customFunctionsButtons.value = buttons;
  showCustumFunctionButtons.value = true;
}

function factorial() {
  funDecl.value = "let tracerec factorial (n: Nat): Nat = \n\tif n = 0 then 1 else n * factorial (n - 1)";
  expr.value = "factorial 5";
}

function identity() {
  funDecl.value = "let tracerec id (n: Nat): Nat = \n\tif n = 0 then 0 else 1 + id (n - 1)";
  expr.value = "id 3";
}

function querprodukt() {
  funDecl.value = "let tracerec querprodukt (n: Nat): Nat = \n\tif n = 0 then 1 else (n % 10) * querprodukt (n / 10)";
  expr.value = "querprodukt 1234";
}

function fibonacci() {
  funDecl.value = "let tracerec fibonacci (n: Nat): Nat = \n\tif n = 0 then 0 else if n = 1 then 1 else fibonacci (n-1) + fibonacci (n-2)";
  expr.value = "fibonacci 4";
}

function iseven() {
  // funDecl.value ="let tracerec isEven (n: Nat): Bool = if n = 0 then true else if n = 1 then false else not (isEven (n-1))";
  funDecl.value ="let tracerec isEven (n: Nat): Bool = \n\tif n = 0 then true else if n = 1 then false else isEven (n-2)";
  expr.value = "isEven 7";
}

function even() {
  funDecl.value = "let tracerec even (n : Nat) : Bool =\n\tif n = 0 then true\n\telse not (even (n - 1))"
  expr.value = "even 3"
}

function evenOdd() {
  funDecl.value ="let tracerec isEven (n: Nat): Bool = \n\tif n = 0 then true else isOdd (n - 1)\n\nlet tracerec isOdd (n: Nat): Bool = \n\tif n <> 0 then isEven (n - 1) else false";
  expr.value = "isEven 4";
}

function facId() {
  funDecl.value = "let tracerec factorial (n: Nat): Nat = \n\tif n = 0 then 1 else (id n) * factorial (n - 1)\n\nlet tracerec id (n: Nat): Nat = \n\tif n = 0 then 0 else 1 + id (n - 1)";
  expr.value = "factorial 3";
}

function add() {
  funDecl.value = "let tracerec add (n: Nat) (m: Nat): Nat = \n\t if n = 0 then m else 1 + add (n-1) m";
  expr.value = "add 2 3";
}

function addTuple() {
  funDecl.value = "let tracerec add (n: Nat, m: Nat): Nat = \n\t if n = 0 then m else 1 + add (n-1, m)";
  expr.value = "add (2, 3)";
}

function isAnimal() {
  funDecl.value = "let tracerec isAnimal (a: Animal): Bool = \n\tmatch a with \n\t| NoAnimal -> false \n\t| Animal name -> true"
  expr.value = "isAnimal (Animal \"Dog\")"
}

function sumNats() {
  funDecl.value = "let tracerec sumNats (nats: Nats): Nat = \n\tmatch nats with \n\t| Nil -> 0 \n\t| Cons (n, ns) -> n + sumNats ns"
  expr.value = "sumNats (Cons (1, Cons(2, Nil)))"
}

function sumTree() {
  funDecl.value = "let tracerec sumTree (tree: Tree): Nat = \n\tmatch tree with \n\t| Leaf -> 0\n\t| Node (l, e, r) -> sumTree l + e + sumTree r"
  expr.value = "sumTree (Node (Node (Leaf, 2, Leaf), 3, Leaf))"
}

function addNats() {
  funDecl.value = "let tracerec cadd (m : Peano) (n : Peano) : Peano = \n\tmatch m with\n\t| Zero   -> n\n\t| Succ x -> Succ (cadd x n)"
  expr.value = "cadd (Succ (Succ Zero)) (Succ Zero)"
}
</script>

<template>
  <h1>Recursion-Tutor für F#</h1>
  <!-- <h1>Call-Flow-Diagram Generator for Recursive Functions in F#</h1> -->

  <div id="top">
    <div class="editor-wrapper">
      <button type="button" class="collaps" v-on:click="toggleTutorial()">{{ showTutorial ? "- Anleitung verbergen" : "+ Anleitung zeigen" }}</button>
      <div v-if="showTutorial" class="tutorial">
        <div class="t-l">
          <p>So benutzt man den Recursion-Tutor:</p>
          <ol>
            <li>Alle Funktionsdeklarationen in den Editor eingeben.</li>
            <li>Das Schlüsselwort "rec" in der Funktionsdeklaration mit "tracerec" für alle Funktionen, die im Diagramm anzeigen sollen, ersetzen.</li>
            <li>Im entsprechenden Feld den auszuwertenden Ausdruck eingeben, zum Beispiel einen Funktionsaufruf.</li>
            <li>Zum Auswerten den "Auswerten" Button klicken.</li>
            <li>Ein <b>Linksklick</b> auf einen Knoten wertet den entsprechenden Ausdruck bis zum nächsten rekursiven Aufruf aus, ein <b>Rechtsklick</b> wertet den Ausdruck komplett aus.</li>
            <li>Alle grauen Knoten können angeklickt werden. Gelbe Knoten zeigen an, dass der Ausdruck noch nicht vollständig ausgewertet werden konnte, weil Teillösungen fehlen. Grüne Knoten zeigen vollständig ausgewertete Ausdrücke an.</li>
          </ol>
        </div>
        <div class="t-r">
          <p>Anmerkungen:</p>
          <ul>
            <li>Funktionen müssen keine Parameter oder einen Rückgabetyp haben, aber Funktionen müssen gecurried werden.</li>
            <li>Data type Definitionen müssen nicht angegeben werden.</li>
            <li>Derzeit sind keine verschachtelten Datenkonstruktoren als Muster für match-with-Ausdrücke zulässig.</li>
            <li>Nur primitiv rekursive Funktionen werden unterstützt.</li>
            <li>Wenn das Diagramm zu groß wird, ab besten einen weniger komplexen Ausdruck auswählen oder auf die Checkbox unten klicken, um den Inhalt der Knoten und Kanten zu kürzen.</li>
            <li>Eine kleine Standardbibliothek mit den folgenden Funktionen steht zur Verfügung:
              <ul>
                <li v-for="fun in standardLibrary">
              {{ fun }}
            </li>
              </ul>
            </li>
          </ul>
        </div>
      </div>
      <div class="editor">
        <div>
          <label for="editor" class="label">Funktionsdeklarationen:</label>  
          <code-mirror id="editor" v-model="funDecl" :wrap="true" :tab="true" :lineNumbers="true" />
        </div>
      
        <div class="expr">
          <label for="expr" class="label">Auszuwertender Ausdruck:</label>
          <input v-model="expr" id="expr">
        </div>

        <div>
          <label for="settings" class="label">Einstellungen:</label>
          <div id="settings" class="settings">
            <!-- <label for="stopAtRecCall" class="label">Stop at recursive call(s)?</label> -->
            <!-- <input type="checkbox" id ="stopAtRecCall" v-model="stopAtRecCall"> -->
            <label for="onlyShowRecDepth" class="label">Zeige nur die Tiefe der Datenstruktur</label>
            <input type="checkbox" id ="onlyShowRecDepth" v-model="onlyShowRecDepth" :disabled="shortenNodeContent">
            <label for="shortenNodeContent" class="label">Kanten- und Knoteninhalt kürzen</label>
            <input type="checkbox" id ="shortenNodeContent" v-model="shortenNodeContent" :disabled="onlyShowRecDepth">
          </div>
        </div>
      </div>

      <div class="evaluate">
        <button id="evaluate" class="button" @click="createDiagram">Auswerten</button>
        <!-- <button class="button" @click="deleteDiagram">Reset</button> -->
      </div>

    </div>

    <div class="examples">
      <div>
        <label class="label">Beispielfunktionen:</label><br><br>
      </div>
      <div class="buttons">
        <button class="button" @click="factorial">Factorial</button>
        <button class="button" @click="even">Even</button>
        <button class="button" @click="add">Add</button>
        <button class="button" @click="addNats">AddNats</button>
        <button class="button" @click="sumNats">SumNats</button>
        <button class="button" @click="sumTree">SumTree</button>
      </div>
    </div>
  </div>

  <div id="bottom" v-if="showDiagram" >
    <Diagram :funDecl="funDecl" :expr="expr" :stopAtRecCall="stopAtRecCall" :onlyShowRecDepth="onlyShowRecDepth" :shortenNodeContent="shortenNodeContent"/>
  </div>

  <div class="footer">(Version {{ appVersion }})<br>Bitte die Versionsnummer bei Bug-Reports an ballat@rhrk.uni-kl.de anhängen.</div>
    
</template>

<style>

</style>