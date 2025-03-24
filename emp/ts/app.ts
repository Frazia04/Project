import insertHintsEMP from "./highlighterExport.js";

const codeOutputHTML = document.getElementById("codeOutput")!;
const spoLangSelector = document.getElementById("spoLang")! as HTMLSelectElement;
const proLangSelector = document.getElementById("proLang")! as HTMLSelectElement;

let stringToHighlight = "";

//rehighlight on input or different language selected
document.addEventListener('paste', (e) => {
    if (e.clipboardData) {
        stringToHighlight = (e.clipboardData).getData('text');
        rehighlight();
    }
}, false);

spoLangSelector.addEventListener("change", () => {
    rehighlight();
}, false);

proLangSelector.addEventListener("change", () => {
    rehighlight();
}, false);

codeOutputHTML.addEventListener('contextmenu', async (e) => {
    e.preventDefault();
    let text = await navigator.clipboard.readText();
    if (text) {
        stringToHighlight = text;
        rehighlight();
    }
    return false;
}, false);


//main function that is called when something changes in input to readjust output
function rehighlight(): void {

    let spoLang = spoLangSelector.value;
    let proLang = proLangSelector.value;

    codeOutputHTML.innerHTML = insertHintsEMP(stringToHighlight, proLang, spoLang);

}